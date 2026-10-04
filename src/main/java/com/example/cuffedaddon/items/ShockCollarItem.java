package com.example.cuffedaddon.items;

import com.example.cuffedaddon.client.ClientCollaredState;
import com.example.cuffedaddon.collar.RevokedBindingsSavedData;
import com.example.cuffedaddon.collar.ShockCollarUtil;
import com.example.cuffedaddon.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The Shock Collar - ONE registered item with two states, per [stated]'s spec:
 *
 * <ul>
 *   <li><b>Unbound</b> (freshly crafted from Unbound Collar + Remote): its own
 *       texture, its own name "Shock Collar". Right-clicking a player puts the
 *       collar on them; crouch + right-click with nothing targeted puts it on
 *       yourself.</li>
 *   <li><b>Bound</b> (after being applied): the SAME item stack, now acting as
 *       that collaring's remote. Swaps to the Remote's texture and renames to
 *       the wearer's username in italics. Right-click (held) shocks them.</li>
 * </ul>
 *
 * Removing the collar with its own bound remote reverts the stack to the
 * unbound state - default texture, default name - so it's immediately reusable
 * without re-crafting.
 *
 * <p><b>Italics come for free.</b> Vanilla auto-italicizes any item carrying a
 * custom hover name, so {@link #bind} just calls {@code setHoverName} and
 * {@link #unbind} calls {@code resetHoverName} - no manual Style/ChatFormatting.
 * Same mechanism {@code PlayerPickerItem} already relies on for its own
 * captured-player name.
 *
 * <p><b>The texture swap</b> is a standard item property override: a
 * {@code cuffedaddon:bound} property (registered in {@code ClientModEvents})
 * reads {@link #isBound} and the model JSON's {@code overrides} block points at
 * {@code shock_collar_bound}, which uses the Remote's own texture.
 *
 * <p><b>Held right-click</b> uses the vanilla "item in use" mechanic
 * ({@link #getUseDuration} + {@link #onUseTick} + {@link #releaseUsing}) rather
 * than custom key-state networking. [stated] explicitly accepted the one
 * trade-off that carries: vanilla slows a player to ~20% walk speed the whole
 * time they're using an item, exactly as when drawing a bow, so the remote
 * holder is slowed while shocking.
 */
public class ShockCollarItem extends Item {

    private static final String TAG_BINDING = "CollarBinding";
    private static final String TAG_WEARER = "CollarWearer";

    /** Long enough to be effectively "until released", same value vanilla's bow uses. */
    private static final int USE_DURATION = 72000;

    public ShockCollarItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------ NBT state

    public static boolean isBound(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_BINDING);
    }

    @Nullable
    public static UUID getBindingId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_BINDING) ? tag.getUUID(TAG_BINDING) : null;
    }

    @Nullable
    public static String getWearerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_WEARER) ? tag.getString(TAG_WEARER) : null;
    }

    public static void bind(ItemStack stack, UUID bindingId, String wearerName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BINDING, bindingId);
        tag.putString(TAG_WEARER, wearerName);
        stack.setHoverName(Component.literal(wearerName));
    }

    public static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_BINDING);
            tag.remove(TAG_WEARER);
        }
        stack.resetHoverName();
    }

    // ---------------------------------------------------------------- usage

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player,
            @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (isBound(stack)) {
            // Crouch + right-click with nothing targeted takes YOUR OWN collar
            // off. This mirrors the self-application rule below, and exists
            // because removal otherwise runs only through
            // PlayerInteractEvent.EntityInteract - an event you can never fire
            // on yourself, which left a self-collared player with no way out
            // but struggling. [stated] confirmed losing crouch-self-shock for
            // this is fine.
            //
            // It only fires when the crouching player is the wearer of THIS
            // remote's collar; removeCollar checks the binding. Crouching with
            // someone else's remote falls through and shocks as normal.
            if (player.isShiftKeyDown() && tryCrouchRemove(level, player, stack)) {
                return InteractionResultHolder.success(stack);
            }

            // Start (or continue) shocking. The actual effect application is
            // server-side in onUseTick; starting the use on both sides keeps
            // the client's own "item in use" state in sync.
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        // Unbound: self-application. [stated]: "or to yourself if crouching +
        // right click + not looking at another player". The "not looking at
        // another player" half is already guaranteed by vanilla - targeting an
        // entity routes the click to the EntityInteract path instead and
        // Item#use never runs - so only the crouch needs checking here.
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (ShockCollarUtil.applyCollar(serverPlayer, serverPlayer, stack)) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    /**
     * The self-removal branch, split out because the two sides answer it
     * differently.
     *
     * <p>The server does the real work. The client can't - it never learns which
     * binding its own collar carries, only whether it is collared at all - so it
     * answers with that instead. Getting the client's answer roughly right
     * matters because the alternative branch calls {@code startUsingItem}, and a
     * client that starts "using" an item the server didn't sits at ~20% walk
     * speed until the button is released.
     */
    private static boolean tryCrouchRemove(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return ClientCollaredState.isCollared();
        }
        return player instanceof ServerPlayer serverPlayer
                && ShockCollarUtil.removeCollar(serverPlayer, stack);
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack) {
        return USE_DURATION;
    }

    @Nonnull
    @Override
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        // NONE: no bow-draw / eating pose. The movement slowdown that comes
        // with being "in use" still applies (see this class's doc) - that's a
        // separate vanilla mechanic from the animation.
        return UseAnim.NONE;
    }

    /**
     * Runs every tick the right-click is held. Re-applying the 2-second
     * Electrization each tick is what makes a held press roll the window
     * forward continuously instead of letting it lapse mid-hold; releasing
     * simply stops the refresh, leaving the last 2 seconds to play out.
     */
    @Override
    public void onUseTick(@Nonnull Level level, @Nonnull LivingEntity entity, @Nonnull ItemStack stack,
            int remainingUseDuration) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer holder)) {
            return;
        }
        UUID binding = getBindingId(stack);
        MinecraftServer server = holder.getServer();
        if (binding == null || server == null) {
            holder.stopUsingItem();
            return;
        }
        ServerPlayer wearer = ShockCollarUtil.findWearer(server, binding);
        if (wearer == null) {
            // Wearer offline, or this binding is dead. Nothing to shock.
            return;
        }
        // [stated]: works at any distance, but NOT across dimensions.
        if (wearer.level() != holder.level()) {
            return;
        }
        ShockCollarUtil.shock(wearer);
    }

    @Override
    public void releaseUsing(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity entity,
            int timeCharged) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer holder)) {
            return;
        }
        UUID binding = getBindingId(stack);
        MinecraftServer server = holder.getServer();
        if (binding == null || server == null) {
            return;
        }
        ServerPlayer wearer = ShockCollarUtil.findWearer(server, binding);
        if (wearer != null) {
            ShockCollarUtil.onShockReleased(wearer);
        }
    }

    /**
     * Catches remotes whose collar was struggled out of. See
     * {@link RevokedBindingsSavedData} for why a persisted revocation list is
     * the right mechanism here and "can't find the wearer" is NOT - an offline
     * wearer must not destroy a perfectly good remote.
     */
    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity,
            int slotId, boolean isSelected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID binding = getBindingId(stack);
        if (binding == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        RevokedBindingsSavedData revoked = RevokedBindingsSavedData.get(server);
        if (!revoked.isRevoked(binding)) {
            return;
        }

        boolean salvage = revoked.shouldSalvage(binding);
        // 1.5.22: read salvage FIRST, then take the notice down - consume clears
        // it from both sets, so asking afterwards would always say "destroyed".
        // See RevokedBindingsSavedData#consume for why the board is pruned at all
        // and the one-remote-per-binding assumption it rests on.
        revoked.consume(binding);
        stack.shrink(stack.getCount());
        if (salvage) {
            // The collar came apart rather than being destroyed, so this remote
            // reverts to the plain component it was crafted from. Offered to the
            // inventory first and dropped at the player only if there's no room.
            ItemStack spare = new ItemStack(ModItems.SHOCK_REMOTE.get());
            if (!player.getInventory().add(spare)) {
                player.drop(spare, false);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 0.8f, ShockCollarUtil.randomPitch(player));
    }

    /**
     * A bound remote carries the enchantment glint, per [stated] - it's the
     * clearest at-a-glance tell that this particular stack is live and tied to
     * someone, on top of the texture swap and the italic name.
     */
    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return isBound(stack);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
            @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        if (isBound(stack)) {
            tooltip.add(Component.translatable("item.cuffedaddon.shock_collar.tooltip.bound")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.cuffedaddon.shock_collar.tooltip.unbound")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}

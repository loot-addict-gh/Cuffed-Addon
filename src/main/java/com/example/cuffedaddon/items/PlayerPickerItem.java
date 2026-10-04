package com.example.cuffedaddon.items;

import com.example.cuffedaddon.client.PlayerPickerItemRenderer;
import com.example.cuffedaddon.picker.PlayerPickerUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reusable "capture slot" item - NOT single-use/consumable (see
 * /areas/cuffedaddon.md for [stated]'s explicit confirmation of this
 * reading of "1 durability": it's a capacity of exactly one captured
 * player at a time, not a break-after-use item). The generic/empty and
 * loaded states are both represented purely by this item's own NBT (a
 * stored GameProfile once loaded, mirroring how vanilla's own Player Head
 * item stores "SkullOwner" - see NbtUtils.writeGameProfile/readGameProfile)
 * - there is no separate "durability bar" actually wired up; the item's
 * display name doubles as the occupied/empty indicator instead (see
 * setPickedPlayer/clearPickedPlayer).
 *
 * The PICK action (right-click a restrained player) is NOT handled here as
 * a normal Item#interactLivingEntity override - see PlayerPickerEvents'
 * own doc for why (competing with Cuffed's own interaction dispatch, same
 * class of problem LiePoseEvents/WallPoseEvents already solve with a
 * HIGHEST-priority EntityInteract listener). This class only handles the
 * RELEASE action (right-click a block - "plant it into the ground"),
 * which has no equivalent competing-dispatch risk, so a plain Item#useOn
 * override (same pattern as BlockLockerItem) is fine.
 */
public class PlayerPickerItem extends Item {

    private static final String TAG_PROFILE = "PickedProfile";

    public PlayerPickerItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * CORRECTED (was a RegisterClientExtensionsEvent listener in ClientModEvents -
     * that event doesn't exist in this project's actual Forge version, per the
     * real compiler error [stated] pasted). The confirmed-correct 1.20.1 hook is
     * this override, directly on the Item - the standard, officially-documented
     * pattern (docs.minecraftforge.net/items/bewlr). Safe to reference the
     * client-only PlayerPickerItemRenderer/IClientItemExtensions here despite
     * this being a common (both-sides) class: initializeClient is only ever
     * actually CALLED by Forge on the client, and Consumer<T>'s generic erasure
     * means the method's raw bytecode signature doesn't hard-reference
     * IClientItemExtensions either - a dedicated server never resolves or
     * executes any of this.
     */
    @Override
    public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return PlayerPickerItemRenderer.instance();
            }
        });
    }

    public static boolean hasPickedPlayer(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(TAG_PROFILE);
    }

    @Nullable
    public static GameProfile getPickedProfile(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_PROFILE)) return null;
        return NbtUtils.readGameProfile(tag.getCompound(TAG_PROFILE));
    }

    /**
     * Stores the captured player's profile (their own GameProfile already
     * carries a resolved "textures" property for an authenticated online
     * player, the same source vanilla's own player-death Player Head drop
     * uses - no separate async profile lookup needed) and swaps the
     * display name to their username in italics. Vanilla auto-italicizes
     * ANY item with a custom hover name in its tooltip (see
     * ItemStack#getHoverName()/the tooltip-building code that checks
     * hasCustomHoverName()) - no manual Style/ChatFormatting needed here.
     */
    public static void setPickedPlayer(ItemStack stack, ServerPlayer target) {
        CompoundTag profileTag = NbtUtils.writeGameProfile(new CompoundTag(), target.getGameProfile());
        stack.getOrCreateTag().put(TAG_PROFILE, profileTag);
        stack.setHoverName(Component.literal(target.getGameProfile().getName()));
    }

    public static void clearPickedPlayer(ItemStack stack) {
        if (stack.getTag() != null) {
            stack.getTag().remove(TAG_PROFILE);
        }
        stack.resetHoverName();
    }

    /**
     * Release: only works if the captured player is currently ONLINE (see
     * this class's own doc + PlayerPickerEvents' onPlayerLoggedOut for why
     * a captured player is only ever actively "floating" while online at
     * all - if they're offline there's nothing dangling to release, and
     * restoring their capability state while offline isn't possible
     * through normal capability access). Silently fails otherwise, same
     * "no toast" convention the rest of this addon's restraint items use.
     */
    @Nonnull
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        GameProfile profile = getPickedProfile(stack);
        if (profile == null) {
            return InteractionResult.FAIL; // empty picker, nothing to release
        }

        ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(profile.getId());
        if (target == null) {
            return InteractionResult.FAIL; // captured player not currently online
        }

        // Restores gamemode, dismounts the anchor entity, clears the capability
        // and syncs the client. Shared with the logout auto-release, /unpick and
        // the grace-period backstop as of 1.4.40 - see
        // PlayerPickerUtil#releasePicked. Returns false when the item and the
        // capability are out of sync (shouldn't happen), in which case the item
        // is still emptied so it isn't stuck "loaded" with nobody inside.
        if (!PlayerPickerUtil.releasePicked(target)) {
            clearPickedPlayer(stack);
            return InteractionResult.FAIL;
        }

        BlockPos releasePos = context.getClickedPos().above();
        target.teleportTo(serverLevel, releasePos.getX() + 0.5, releasePos.getY(), releasePos.getZ() + 0.5,
                target.getYRot(), target.getXRot());

        clearPickedPlayer(stack);

        return InteractionResult.CONSUME;
    }
}

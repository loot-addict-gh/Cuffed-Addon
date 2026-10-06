package com.example.cuffedaddon.fakeplayer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.necklace.NecklaceUtil;
import com.example.cuffedaddon.pose.LiePoseProvider;
import com.example.cuffedaddon.pose.LiePoseUtil;
import com.example.cuffedaddon.pose.WallPoseProvider;
import com.example.cuffedaddon.pose.WallPoseUtil;
import com.lazrproductions.cuffed.items.base.AbstractRestraintKeyItem;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

import java.util.UUID;

/**
 * Wires fake players into the addon: capability attachment, the apply/remove
 * click, the immobilise goal, and sync to newly-tracking clients.
 *
 * <h2>The interaction is EntityInteractSpecific, deliberately</h2>
 * Every other competing-dispatch listener in this addon uses
 * {@code PlayerInteractEvent.EntityInteract}, but this one needs
 * {@link PlayerInteractEvent.EntityInteractSpecific#getLocalPos()} - the hit
 * position on the entity - because Cuffed picks which slot a restraint goes into
 * from the height you clicked at, and matching that is what makes an ambiguous
 * item like Rope behave "just as if it was a real player". {@code EntityInteract}
 * carries no hit position at all.
 *
 * <h2>Not interfering with their customization</h2>
 * [stated] was explicit that none of this may disturb Fake Players' own options.
 * Two things guarantee it:
 * <ul>
 *   <li><b>Crouching is never touched.</b> Their {@code mobInteract} opens the
 *       customization menu on a crouched right-click, so this listener ignores
 *       crouched interactions entirely - the menu, poses, skin, name and AI
 *       buttons all behave exactly as before.</li>
 *   <li><b>The event is only cancelled for items that are ours to handle</b> - a
 *       Cuffed restraint or a restraint key. Anything else falls through
 *       untouched, so their own interaction registry (shears, wool, beds,
 *       banners, paper, name tag) still runs.</li>
 * </ul>
 * The Player Picker and the Shock Collar are excluded on purpose: [stated] ruled
 * both out, since neither means anything on something that is not a real player.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class FakePlayerEvents {

    public static final ResourceLocation FAKE_RESTRAINED_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "fake_restrained");

    /** Pillory state - see {@link IFakeDetained}. */
    public static final ResourceLocation FAKE_DETAINED_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "fake_detained");

    /**
     * The Bed Restraint and Wall Restraint states are this addon's OWN
     * {@code ILiePose}/{@code IWallPose}, attached to a fake player under their own
     * ids so the pose machinery already built for players works on one unchanged -
     * see {@code FakeStationaryUtil} for why reuse rather than a parallel copy.
     */
    public static final ResourceLocation FAKE_LIE_POSE_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "fake_lie_pose");

    public static final ResourceLocation FAKE_WALL_POSE_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "fake_wall_pose");

    /**
     * The only result this listener may ever return. <b>Never change this to
     * {@code SUCCESS}</b> - doing so restrains the player who is holding the
     * restraint, and jams their right-click. That is not a figure of speech; it is
     * the 1.5.4 bug, and this constant exists so the reason travels with the value.
     *
     * <h2>Why SUCCESS is actively dangerous here</h2>
     * The two results differ in one respect that looks cosmetic and is not:
     * <pre>
     *   consumesAction()  SUCCESS = true   CONSUME = true    (both stop the fall-through)
     *   shouldSwing()     SUCCESS = true   CONSUME = false   (only SUCCESS swings the arm)
     * </pre>
     * {@code Minecraft#startUseItem} swings the arm when the result
     * {@code shouldSwing()}, the swing goes to the server as a
     * {@code ServerboundSwingPacket}, and Cuffed's {@code PlayerMixin} watches the
     * server-side {@code swinging} flag:
     * <pre>
     *   if (SERVER_CONFIG.ALLOW_SELF_RESTRAINING.get()) {      // defaults to TRUE
     *       if (swinging &amp;&amp; !hasProcessedSwing) {
     *           hasProcessedSwing = true;
     *           attemptToRemoveRestraint(me, cap);             // -&gt; onInteractedByOther(me, me, ...)
     *       }
     *   }
     * </pre>
     * {@code attemptToRemoveRestraint} restrains you with whatever is in your main
     * hand, picking the slot purely from your look PITCH - it never checks crouch,
     * and it never checks what you are looking at. So every SUCCESS this listener
     * returned made the player cuff themselves:
     * <ul>
     *   <li><b>on apply</b>, with the next restraint off the same stack - and in
     *       creative, where the stack never shrinks, every single time;</li>
     *   <li><b>on remove</b>, with the restraint that had just been handed back
     *       into the then-empty main hand by {@code giveBack}.</li>
     * </ul>
     * Self-cuffed arms then block interaction in Cuffed, which is what "my
     * right-click gets bugged and is constantly held down" was: not a stuck key,
     * but the player being restrained by their own click.
     *
     * <p>Cuffed's own real-player handler avoids this by never setting a
     * cancellation result on the restraining path at all, so a real-player
     * interaction returns PASS and no swing is ever sent. This addon has to cancel
     * - the fake player's own menu is on the other side of the event - so CONSUME
     * is the equivalent: it stops the fall-through exactly as SUCCESS did, without
     * the swing that fed the self-restrain.
     */
    private static final InteractionResult CONSUMED_WITHOUT_SWINGING = InteractionResult.CONSUME;

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IFakeRestrained.class);
        event.register(IFakeDetained.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (FakePlayerSupport.isFakePlayer(event.getObject())) {
            event.addCapability(FAKE_RESTRAINED_ID, new FakeRestrainedProvider());
            event.addCapability(FAKE_DETAINED_ID, new FakeDetainedProvider());
            // 1.5.11 - the same two providers players get. LiePoseCapabilityEvents
            // and WallPoseCapabilityEvents attach these to players; attaching them
            // here as well is the whole of what the Bed and Wall Restraints needed
            // on the state side.
            event.addCapability(FAKE_LIE_POSE_ID, new LiePoseProvider());
            event.addCapability(FAKE_WALL_POSE_ID, new WallPoseProvider());
        }
    }

    /**
     * Gives every fake player the immobilise goal as it enters the world. Adding
     * it here rather than by injecting their {@code registerGoals} keeps this
     * mixin-free: {@code registerGoals} is a vanilla-derived override, so hooking
     * it would have meant another hand-maintained refmap entry for no benefit.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!FakePlayerSupport.isFakePlayer(event.getEntity())) {
            return;
        }
        reportOnce(event.getEntity());

        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            // Priority is NEGATIVE on purpose - see FakePlayerImmobilizeGoal.PRIORITY.
            // The look goal shares it: it has to outrank RandomLookAroundGoal at 6,
            // which would otherwise keep winning LOOK and leave the head twitching
            // one block at a time. Different flags, so the two never contend.
            mob.goalSelector.addGoal(FakePlayerImmobilizeGoal.PRIORITY, new FakePlayerImmobilizeGoal(mob));
            mob.goalSelector.addGoal(FakePlayerImmobilizeGoal.PRIORITY, new FakePlayerRestrainedLookGoal(mob));
        }
    }

    private static boolean reportedClient;
    private static boolean reportedServer;

    /**
     * Writes one line to the log the first time a fake player loads, saying
     * whether the two things this feature silently depends on actually happened.
     *
     * <h2>Why this is worth a log line</h2>
     * Both failure modes are invisible in game and look identical from the
     * outside - you right-click and nothing happens:
     * <ul>
     *   <li>the mixin config is not registered in build.gradle's jar manifest, so
     *       the entity never becomes an {@code IRestrainableEntity};</li>
     *   <li>the mixin is registered but failed to apply against a changed
     *       version of that mod, which the {@code "required": false} config
     *       swallows by design.</li>
     * </ul>
     * Neither throws, neither prints anything of its own, and no amount of
     * in-game poking distinguishes them. One line at INFO turns that into a
     * question anyone can answer from latest.log in five seconds, which is worth
     * far more than the line costs.
     */
    private static void reportOnce(Entity entity) {
        // Once PER SIDE, not once per process. Both sides attach the capability
        // and both apply the mixin, and in single-player they are different
        // classloaded states reached from different threads - so reporting once
        // globally meant whichever side happened to load a fake player first was
        // the only side described, and a server-side failure could be masked by
        // the client's own healthy copy. The two flags also make the write
        // thread-local in practice rather than a race on one field.
        boolean client = entity.level().isClientSide();
        if (client ? reportedClient : reportedServer) {
            return;
        }
        if (client) {
            reportedClient = true;
        } else {
            reportedServer = true;
        }
        boolean mixinApplied = entity instanceof com.lazrproductions.cuffed.entity.base.IRestrainableEntity;
        boolean capAttached = FakePlayerRestraintUtil.get(entity) != null;
        if (mixinApplied && capAttached) {
            CuffedAddon.LOGGER.info(
                    "Fake Players compat active on the {}: entity mixin applied, restraint capability attached.",
                    client ? "client" : "server");
        } else {
            CuffedAddon.LOGGER.warn(
                    "Fake Players compat is NOT fully active on the " + (client ? "client" : "server")
                            + " - restraints on fake players will not work properly. "
                            + "entity mixin applied = {}, restraint capability attached = {}. "
                            + "If the mixin did not apply, check that build.gradle's jar manifest MixinConfigs "
                            + "attribute lists mixins.cuffedaddon.fakeplayer.json alongside mixins.cuffedaddon.json.",
                    mixinApplied, capAttached);
        }
    }

    /**
     * Both interact events are handled, and BOTH compute the interaction height
     * the same way - with {@link #cuffedInteractionHeight}, never with
     * {@code getLocalPos()}.
     *
     * <h2>Why both events</h2>
     * A right-click on an entity produces two packets, and which one reaches this
     * addon depends on what else is listening - any mod that cancels an event
     * stops later handlers from seeing it. Handling both means the interaction
     * cannot be lost; the second is deduplicated against the first.
     *
     * <h2>Why not getLocalPos, which is right there on this event</h2>
     * Because Cuffed does not use it, and matching Cuffed is the whole point. Its
     * real-player handler is on the general {@code EntityInteract} event, which
     * carries no hit position, so it derives the height itself - and the 0.33 and
     * 1.5 band edges were chosen to sit where THAT derivation puts them. Feeding
     * the true ray-box hit height into bands calibrated for a different
     * measurement is what made legs so hard to hit in 1.5.5. One formula for both
     * paths also removes the chance of a click behaving differently depending on
     * which event happened to arrive.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithFakePlayer(PlayerInteractEvent.EntityInteractSpecific event) {
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        handle(event, target, cuffedInteractionHeight(event.getEntity(), target), "specific");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithFakePlayerGeneral(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer actor && alreadyHandled(actor, target)) {
            return;
        }
        handle(event, target, cuffedInteractionHeight(event.getEntity(), target), "general");
    }

    private static void handle(PlayerInteractEvent event, Entity rawTarget, double interactionHeight,
                                String path) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(rawTarget instanceof LivingEntity target) || !FakePlayerSupport.isFakePlayer(target)) {
            return;
        }

        // CROUCHING IS THEIRS, WHATEVER IS IN YOUR HAND.
        //
        // Their mobInteract opens the customization menu on any crouched
        // right-click - it does not care what you are holding. Until 1.5.6 this
        // check sat inside the empty-hand branch only, so crouching while holding
        // handcuffs, rope or a key was swallowed here and the menu could not be
        // opened at all: pose cycling, skin, name and the AI buttons all became
        // unreachable while a restraint was in hand, which reads as the menu
        // randomly breaking. Nothing is lost by yielding, because applying a
        // restraint never needed crouch in the first place - Cuffed applies on a
        // plain right-click for real players too.
        //
        // AND THAT IS ALL IT DOES NOW. 1.5.10 added a client-side cancel here that
        // tried to keep their menu open while suppressing the arm swing that comes
        // with it, because their mobInteract returns SUCCESS and Cuffed cuffs you on
        // any swing while ALLOW_SELF_RESTRAINING is on. [stated] tested it, found it
        // did not work, and asked for it to be DELETED rather than chased further:
        //
        //     "lets keep this as intended behavior so that if you want to access the
        //      fake players menu, you should simply not have anything in your hand"
        //
        // So crouching at a fake player with a restraint in hand cuffing YOU is now
        // documented behaviour, not a bug. Do not reintroduce a suppression here -
        // the SUCCESS is theirs, on the other side of this event, and every route to
        // stopping it that does not also close their menu has been tried.
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }

        ItemStack stack = event.getItemStack();

        // THE STATIONARY RESTRAINTS COME FIRST (1.5.11).
        //
        // Applying a Bed Restraint, or releasing a bed/wall-restrained fake player
        // with a Handcuffs Key, is not a body-slot interaction at all: there is no
        // height band to resolve and no restraint id to store, so it must be settled
        // before the slot dispatch below, not inside it. The pillory is not here
        // because Cuffed's pillory is released by right-clicking the BLOCK, the same
        // as for a real player - see PilloryBlockFakePlayerMixin.
        if (handleStationary(event, target, stack)) {
            return;
        }

        // THEN THE KEY NECKLACE (1.6.5), ahead of the body slots.
        //
        // [stated] asked for the necklace to work on a fake player too, as a
        // cosmetic in its own slot, "with the same apply/remove keys as we set".
        // Apply is literally the same gesture as for a real player - a plain
        // right-click with one in hand. REMOVAL is the one deliberate deviation,
        // and it is the SAME deviation this file already makes one branch below
        // for keyless restraints: a real player's necklace comes off with
        // crouch + empty hand, but crouch + anything is Fake Players' customization
        // menu and must stay theirs (see the crouch yield above, and [stated]'s
        // instruction behind it). So on a fake player it is a PLAIN empty-handed
        // right-click, exactly as a Rope or Straitjacket comes off one.
        //
        // Ahead of the body slots for the same reason the necklace goes ahead of a
        // keyless restraint everywhere else: an empty-handed click should lift the
        // key off their neck before it starts undoing what they are wearing.
        if (handleNecklace(event, target, stack)) {
            return;
        }

        boolean emptyHanded = stack.isEmpty();
        if (emptyHanded) {
            // AN EMPTY HAND IS HOW A KEYLESS RESTRAINT COMES OFF - Rope and the
            // Straitjacket have no key item, and that is what their tooltips mean
            // by "my key: empty hand". Cuffed itself wants crouch + empty hand for
            // this, but crouch + empty hand is ALSO exactly how Fake Players opens
            // its customization menu, and [stated] was explicit that this feature
            // must not disturb that. So this is the one deliberate deviation from
            // real-player behaviour: on a fake player, a PLAIN right-click with an
            // empty hand removes a keyless restraint, and crouching keeps opening
            // their menu untouched (which the check above now guarantees for every
            // held item, not just an empty hand).
            if (!anythingToRemove(target)) {
                return;
            }
        } else if (!isOursToHandle(stack)) {
            // Anything that is not a restraint or a key is none of our business -
            // their menu, their pose cycling, their skin and name items all still
            // work exactly as before, crouching included.
            return;
        }

        // THE CLIENT MUST CONSUME THIS TOO. Fixed in 1.5.3.
        //
        // Previously this returned early on the client because only the server can
        // change restraint state. But vanilla's Minecraft#startUseItem falls
        // through to using the ITEM when the entity interaction did not consume:
        //
        //     if (!interactionresult.consumesAction())
        //         interactionresult = this.gameMode.interact(...);   // then USE_ITEM
        //
        // With the client never consuming, every click also sent USE_ITEM, and
        // Cuffed's own restraint item self-applies on crouch+use - which is exactly
        // [stated]'s "duplicates them and applies them to myself too". The client
        // has to answer for itself, the same way the Shock Collar's own
        // crouch-removal branch does.
        if (!(event.getEntity() instanceof ServerPlayer actor)) {
            event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
            event.setCanceled(true);
            return;
        }

        RestraintType slot = FakePlayerRestraintUtil.slotForHeight(interactionHeight);
        boolean isKey = emptyHanded || stack.getItem() instanceof AbstractRestraintKeyItem;

        // A BED- OR WALL-RESTRAINED FAKE PLAYER TAKES HEAD RESTRAINTS ONLY (1.5.11).
        //
        // Exactly the rule a bed- or wall-posed PLAYER already lives under - see
        // LiePoseEvents' round 19 revert, which reinstated it after an attempt to
        // allow all three. The reason is a rendering one and applies identically
        // here: Cuffed's own RestraintEntityLayer copies whatever pose is in effect
        // into its restraint model, so a real pair of cuffs gets drawn on top of the
        // spread pose, in mid-air where the wrists are not - on top of the cosmetic
        // cuffs this addon already always draws for these poses.
        //
        // The PILLORY deliberately does NOT get this restriction: Cuffed allows every
        // restraint type on a pilloried real player, and its pose is Cuffed's own, so
        // matching Cuffed is right there. Removal (a key, or an empty hand) is always
        // allowed regardless, so nothing can get stuck on.
        if (!isKey && slot != RestraintType.Head
                && (LiePoseUtil.isPosed(target) || WallPoseUtil.isPosed(target))) {
            event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
            event.setCanceled(true);
            return;
        }

        boolean handled = isKey
                ? FakePlayerRestraintUtil.tryRemove(actor, target, stack, slot)
                : FakePlayerRestraintUtil.tryApply(actor, target, stack, slot);

        reportInteraction(path, stack, slot, interactionHeight, isKey, handled);
        markHandled(actor, target);

        // Swallowed either way, and ALWAYS without swinging - see the constant.
        // A restraint item that did not fit this slot must not fall through to
        // their menu, their interaction registry, or Item#use - the same reasoning
        // that made the Shock Collar's failed apply CONSUME rather than FAIL, so a
        // miss does nothing instead of something surprising.
        event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
        event.setCanceled(true);
    }

    /**
     * The Key Necklace apply and removal, for a fake player. Returns true if this
     * click was one of those and has been dealt with (and swallowed).
     *
     * <p>Shaped exactly like {@link #handleStationary}: decide from state both
     * sides have, consume on the client as well (the 1.5.3 USE_ITEM fall-through
     * fix), then do the real work server-side and swallow the click without
     * swinging.
     *
     * <p>The necklace needs no fake-player-specific state of its own - the same
     * {@code INecklaced} capability is attached to their entity, and
     * {@code NecklaceUtil} is {@code LivingEntity}-typed throughout. See
     * {@code NecklaceEvents#onAttachCapabilities} for why that was possible here
     * when Cuffed's restraints needed a whole parallel representation.
     */
    private static boolean handleNecklace(PlayerInteractEvent event, LivingEntity target, ItemStack stack) {
        boolean applying = stack.is(ModItems.KEY_NECKLACE.get());
        boolean removing = stack.isEmpty() && NecklaceUtil.isWearing(target);
        if (!applying && !removing) {
            return false;
        }
        if (applying && NecklaceUtil.isWearing(target)) {
            // Slot occupied. Swallow it rather than letting a necklace fall
            // through to their menu or to Item#use - same "a miss does nothing
            // instead of something surprising" rule as the body-slot dispatch.
            event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
            event.setCanceled(true);
            return true;
        }

        if (!(event.getEntity() instanceof ServerPlayer actor)) {
            event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
            event.setCanceled(true);
            return true;
        }

        if (applying) {
            NecklaceUtil.applyNecklace(actor, target, stack);
        } else {
            NecklaceUtil.removeNecklace(actor, target);
        }

        markHandled(actor, target);
        event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
        event.setCanceled(true);
        return true;
    }

    /**
     * The Bed Restraint apply and the key release, for a fake player. Returns true if
     * this click was one of those and has been dealt with (and swallowed).
     *
     * <h2>Why the Bed Restraint is handled here and not in BedRestraintItem</h2>
     * {@code BedRestraintItem#interactLivingEntity} is where a real player's version
     * lives, and it already gets called for a fake player - it just returns PASS,
     * because it needs a {@code ServerPlayer} target. Making it accept a fake player
     * there instead of here would have meant returning SUCCESS from an
     * {@code Item} method, and SUCCESS is exactly what
     * {@link #CONSUMED_WITHOUT_SWINGING} exists to stop: it swings the arm, and
     * Cuffed cuffs the swinger. Routing it through this listener means it inherits
     * everything this listener already gets right - the crouch yield to their
     * customization menu, the specific/general event deduplication, and CONSUME
     * rather than SUCCESS.
     *
     * <h2>Why the whole click is swallowed even when nothing happens</h2>
     * Same reasoning as the body-slot dispatch below: a Bed Restraint that could not
     * be applied (not on a bed, already restrained, arms bound) must not then fall
     * through to their menu or to {@code Item#use}. A miss does nothing, rather than
     * something surprising. Silent either way - no toast - matching the real-player
     * path, which [stated] asked to have its failure message removed from at 1.4.x.
     */
    private static boolean handleStationary(PlayerInteractEvent event, LivingEntity target, ItemStack stack) {
        boolean bedRestraint = stack.is(ModItems.BED_RESTRAINT.get());
        boolean releaseKey = stack.is(com.lazrproductions.cuffed.init.ModItems.HANDCUFFS_KEY.get())
                && (LiePoseUtil.isPosed(target) || WallPoseUtil.isPosed(target));

        if (!bedRestraint && !releaseKey) {
            return false;
        }

        // The client has to consume too, or vanilla falls through to USE_ITEM - the
        // 1.5.3 fix, see the long comment further down.
        if (!(event.getEntity() instanceof ServerPlayer actor)) {
            event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
            event.setCanceled(true);
            return true;
        }

        if (releaseKey) {
            boolean wasBed = LiePoseUtil.isPosed(target);
            if (wasBed) {
                FakeStationaryUtil.releaseBed(target);
                // The item comes back, mirroring the real-player release (and how
                // Cuffed returns a restraint's item on unequip but not on a break).
                giveBack(actor, new ItemStack(ModItems.BED_RESTRAINT.get()));
            } else {
                FakeStationaryUtil.releaseWall(target);
            }
        } else {
            BlockPos footPos = findBedFootUnder(target);
            if (footPos != null) {
                // Both halves of a bed carry the same FACING, so reading it off the
                // foot half is the same value findBedFootUnder itself used.
                BlockState footState = target.level().getBlockState(footPos);
                if (footState.hasProperty(BedBlock.FACING)
                        && FakeStationaryUtil.tryBed(target, footPos, footState.getValue(BedBlock.FACING))) {
                    FakeStationaryUtil.reportFirstApply("bed restraint", true);
                    stack.shrink(1);
                }
            }
        }

        markHandled(actor, target);
        event.setCancellationResult(CONSUMED_WITHOUT_SWINGING);
        event.setCanceled(true);
        return true;
    }

    /**
     * The FOOT half of the bed this fake player is standing on, or null if it is not
     * standing on one.
     *
     * <p>Transcribed from {@code BedRestraintItem#findBedBelow} plus its caller's
     * foot-half resolution, for the same reasons stated there: a bed's collision box
     * is shorter than a full block, so a standing entity's feet Y still floors into
     * the BED's own cell rather than the one above, and FACING points from the foot
     * toward the head. {@code Block#isBed} is what makes this work on Cuffed's Bunk
     * and its Reinforced Bed as well as on a vanilla bed, with no Cuffed-specific
     * class involved.
     */
    @Nullable
    private static BlockPos findBedFootUnder(LivingEntity target) {
        Level level = target.level();
        BlockPos bedPos = null;
        BlockPos own = target.blockPosition();
        BlockState ownState = level.getBlockState(own);
        if (ownState.getBlock().isBed(ownState, level, own, target)) {
            bedPos = own;
        } else {
            BlockPos below = own.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.getBlock().isBed(belowState, level, below, target)) {
                bedPos = below;
            }
        }
        if (bedPos == null) {
            return null;
        }

        BlockState bedState = level.getBlockState(bedPos);
        // Cuffed's Bunk and Reinforced Bed share vanilla's own FACING/PART property
        // objects (confirmed from its source), so this needs no per-block special
        // case - but a third-party "bed" that reports isBed() without them would
        // throw, so it is checked rather than assumed.
        if (!bedState.hasProperty(BedBlock.FACING) || !bedState.hasProperty(BedBlock.PART)) {
            return null;
        }
        Direction facing = bedState.getValue(BedBlock.FACING);
        return bedState.getValue(BedBlock.PART) == BedPart.FOOT
                ? bedPos
                : bedPos.relative(facing.getOpposite());
    }

    private static void giveBack(ServerPlayer actor, ItemStack stack) {
        if (!actor.getInventory().add(stack)) {
            actor.drop(stack, false);
        }
    }

    /**
     * The interaction height, computed EXACTLY the way Cuffed computes it for a
     * real player. This is a transcription, not a derivation - do not "fix" it.
     *
     * <h2>The original, from {@code ModServerEvents#playerInteractEntity}</h2>
     * <pre>
     *   double maxDist = player.getEyePosition().distanceTo(target.position());
     *   Vec3 interactionPos = new Vec3(
     *           target.position().x,
     *           player.getLookAngle().multiply(maxDist, maxDist, maxDist)
     *                 .add(player.getEyePosition()).y,
     *           target.position().z);
     *   ... onInteractedByOther(target, player,
     *           interactionPos.y - target.position().y, ...)
     * </pre>
     * So: walk along the unit look vector for as many blocks as the straight-line
     * distance from your eye to the target's FEET, and take the height you reach.
     *
     * <h2>Why this and not the geometrically correct answer</h2>
     * It is not where the ray actually meets the entity, and it is not what this
     * addon used in 1.5.5 either. 1.5.5 projected the ray onto the target's
     * horizontal distance, which is the textbook answer and reads lower on the
     * body for the same pitch. Both are defensible; only one matches Cuffed.
     *
     * <p>The band edges are the reason it has to be Cuffed's. 0.33 and 1.5 were
     * chosen to sit where THIS formula puts the waist and the neck, and the two
     * formulas disagree by a lot as you get closer: at two blocks away, a 30-degree
     * downward look gives 0.335 here - just into the leg band - and 0.47 under the
     * 1.5.5 projection, which is still arms. That gap is exactly the reported
     * "you have to be looking very far down, even if youre facing at the fake
     * players legs". Copying the formula makes a fake player's bands sit at the
     * same pitches as a real player's, which is what was asked for.
     */
    private static double cuffedInteractionHeight(Player actor, Entity target) {
        Vec3 eye = actor.getEyePosition();
        Vec3 feet = target.position();
        double maxDist = eye.distanceTo(feet);
        double hitY = actor.getLookAngle().multiply(maxDist, maxDist, maxDist).add(eye).y;
        return hitY - feet.y;
    }

    /**
     * The last fake player each actor's click was handled on, as
     * {@code {entity id, game time}}, so the general event can tell whether the
     * specific event already dealt with this very click.
     *
     * <h2>Why a map and not a field</h2>
     * Two players can right-click two different fake players in the same tick on a
     * server, and a single shared "last handled" field would make the second click
     * look like a duplicate of the first and silently do nothing. One entry per
     * actor is the smallest thing that cannot get that wrong; it is cleared on
     * logout so it does not accumulate over a long-running server.
     */
    private static final java.util.Map<UUID, long[]> LAST_HANDLED = new java.util.HashMap<>();

    /**
     * Whether this actor's click on this target was already handled this tick.
     *
     * <p>A right-click on an entity can produce both an {@code EntityInteractSpecific}
     * and an {@code EntityInteract}, and both are listened to here (see
     * {@link #onInteractWithFakePlayerGeneral}). Without this check a single click
     * could apply a restraint twice, or apply one and then immediately unlock it
     * again from the second pass.
     *
     * <p>The tick is part of the key rather than a timestamp: both events for one
     * click are always processed in the same server tick, and a genuine second
     * click is always a later one. Holding the button down repeats no faster than
     * once every four ticks, so no real interaction is ever swallowed.
     */
    private static boolean alreadyHandled(ServerPlayer actor, Entity target) {
        long[] last = LAST_HANDLED.get(actor.getUUID());
        return last != null && last[0] == target.getId() && last[1] == actor.level().getGameTime();
    }

    private static void markHandled(ServerPlayer actor, Entity target) {
        LAST_HANDLED.put(actor.getUUID(), new long[] { target.getId(), actor.level().getGameTime() });
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HANDLED.remove(event.getEntity().getUUID());
    }

    private static int interactionsLogged;

    /**
     * Logs the first few interactions with their resolved slot.
     *
     * <p>Bounded to ten so it cannot become spam. The slot and the height are the
     * two values that have been wrong or unknowable in every round of this feature
     * so far, and no amount of in-game observation reveals either - "it went on the
     * arms" and "it resolved to Arm because the height was wrong" look identical
     * from the outside.
     */
    private static void reportInteraction(String path, ItemStack stack, RestraintType slot,
                                           double height, boolean isKey, boolean handled) {
        if (interactionsLogged >= 10) {
            return;
        }
        interactionsLogged++;
        CuffedAddon.LOGGER.info(
                "Fake Players compat: {} {} via the {} event at height {} -> {} slot, handled = {}.",
                isKey ? "unlock with" : "apply", stack.getItem(), path,
                String.format("%.2f", height), slot, handled);
    }

    /**
     * Whether this fake player is wearing anything at all. Used to decide whether
     * a plain empty-handed right-click means anything here - if it is wearing
     * nothing, the click is left entirely alone so it reaches Fake Players' own
     * handling exactly as it would without this addon installed.
     */
    private static boolean anythingToRemove(LivingEntity target) {
        IFakeRestrained cap = FakePlayerRestraintUtil.get(target);
        return cap != null && cap.isRestrained();
    }

    /**
     * Only Cuffed restraint items and restraint keys. The Player Picker and the
     * Shock Collar are excluded explicitly rather than by omission, because both
     * would otherwise look like plausible things to try on a fake player.
     */
    private static boolean isOursToHandle(ItemStack stack) {
        if (stack.isEmpty()) {
            // Handled separately - see the empty-hand branch in handle().
            return false;
        }
        if (stack.is(ModItems.PLAYER_PICKER.get()) || stack.is(ModItems.SHOCK_COLLAR.get())) {
            return false;
        }
        return RestraintAPI.isRestraintItem(stack) || stack.getItem() instanceof AbstractRestraintKeyItem;
    }

    /**
     * A fake player with its arms or legs bound cannot acquire a target.
     *
     * <h2>Why job gating was not enough</h2>
     * {@code FakePlayerJobRules} already forbids the Guard job while the arms are
     * bound, on the basis that Guard is the combat job. It is - but combat does
     * not only come from the job. Their {@code registerGoals} unconditionally
     * installs a vanilla {@code MeleeAttackGoal} in the goal selector and a
     * {@code FakeHurtByTargetGoal} in the TARGET selector, and the target selector
     * is a separate arbitration from the one {@code FakePlayerImmobilizeGoal}
     * takes part in. So a handcuffed fake player with its legs free would still
     * retaliate when hit, and punch back - which is not something a restrained
     * real player can do.
     *
     * <p>Cancelling the target change is the narrow fix: it leaves every goal in
     * place and simply never gives them anything to act on. Legs are included as
     * well as arms because a fake player that cannot move should not be lining up
     * attacks either.
     */
    @SubscribeEvent
    public static void onFakePlayerChangeTarget(LivingChangeTargetEvent event) {
        // getNewTarget, not getNewAboutToBeSetTarget - that older name is from the
        // pre-1.19 shape of this event and does not exist in 1.20.1.
        //
        // The null check matters: this event also fires when a target is being
        // CLEARED, and cancelling that would pin a target the entity acquired
        // before it was restrained.
        if (event.getNewTarget() == null) {
            return;
        }
        if (!FakePlayerSupport.isFakePlayer(event.getEntity())) {
            return;
        }
        IFakeRestrained cap = FakePlayerRestraintUtil.get(event.getEntity());
        if (cap == null) {
            return;
        }
        if (cap.has(RestraintType.Arm) || cap.has(RestraintType.Leg)) {
            event.setCanceled(true);
        }
    }

    /**
     * And the same for a fake player held by one of the stationary restraints
     * (1.5.11). Separate listener rather than another clause in the one above,
     * because that one needs the restraint capability and this one does not - a
     * pilloried fake player wearing nothing at all still must not acquire a target.
     */
    @SubscribeEvent
    public static void onStationaryFakePlayerChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() == null || !FakePlayerSupport.isFakePlayer(event.getEntity())) {
            return;
        }
        if (FakeStationaryUtil.isStationary(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Belt and braces for the above: a bound fake player deals no melee damage
     * even if something hands it a target by a route that never posts
     * {@code LivingChangeTargetEvent}.
     */
    @SubscribeEvent
    public static void onFakePlayerAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || !FakePlayerSupport.isFakePlayer(attacker)) {
            return;
        }
        if (attacker instanceof LivingEntity living && FakeStationaryUtil.isStationary(living)) {
            event.setCanceled(true);
            return;
        }
        IFakeRestrained cap = FakePlayerRestraintUtil.get(attacker);
        if (cap != null && (cap.has(RestraintType.Arm) || cap.has(RestraintType.Leg))) {
            event.setCanceled(true);
        }
    }

    /**
     * A restrained fake player drops what it was wearing when it dies, instead of
     * taking it with it - see {@code FakePlayerRestraintUtil#dropAllOnDeath}.
     *
     * <p>{@code LivingDeathEvent} rather than {@code LivingDropsEvent}: the latter
     * is not posted at all when the entity's own drops are suppressed (keepInventory
     * style rules, a mod cancelling it), and these items were never part of its
     * loot table in the first place - they belong to whoever put them on it.
     *
     * <p>At {@code LOWEST} priority so that anything which CANCELS the death - a
     * totem-style revive, a protection mod - has already had its say. Dropping at
     * default priority would hand the restraints back on a death that then never
     * happened, leaving a live, unrestrained fake player and the items on the
     * floor. Forge skips an already-cancelled event for this listener, so running
     * last is all that is needed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFakePlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!FakePlayerSupport.isFakePlayer(event.getEntity())) {
            return;
        }
        FakePlayerRestraintUtil.dropAllOnDeath(event.getEntity());
        // 1.5.11: and let go of whatever was holding it, so a Wall Restraint panel
        // goes back to unoccupied and nothing is left pinning a corpse in place.
        // Nothing is handed back here - see FakeStationaryUtil#releaseAll.
        FakeStationaryUtil.releaseAll(event.getEntity());
    }

    /**
     * Someone walking into range of an already-restrained fake player missed the
     * broadcast that set it, so send them the current state now. Without this a
     * fake player restrained before you arrived renders unrestrained - exactly the
     * gap the Shock Collar hit in 1.4.32.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target) || !FakePlayerSupport.isFakePlayer(target)) {
            return;
        }
        // 1.5.11: the stationary state is sent unconditionally of the body-slot
        // state, since a fake player can be pilloried or bed-restrained while wearing
        // nothing at all - the early return below only concerns the three body slots.
        FakeStationaryUtil.syncTo(watcher, target);

        IFakeRestrained cap = FakePlayerRestraintUtil.get(target);
        if (cap == null || !cap.isRestrained()) {
            return;
        }
        com.example.cuffedaddon.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> watcher),
                new com.example.cuffedaddon.network.FakeRestraintSyncPacket(target.getId(), cap.serializeNBT()));
    }
}

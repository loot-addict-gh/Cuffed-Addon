package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.CuffedAddon;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes a Cuffed bug: a restraint loaded from NBT is the registry's SHARED
 * SINGLETON, so unrelated players end up wearing the same restraint object.
 *
 * <h2>The bug</h2>
 * {@code RestraintAPI.getRestraintFromTag} calls {@code getNewRestraintByKey},
 * which is {@code Registries.get(key)} - the one instance the DeferredRegister
 * built at registration - and then calls {@code deserializeNBT} straight into it.
 * Cuffed's author knows the instance is shared (that method's own javadoc warns
 * "this is not to be assigned as the worn restraint of any player before the data
 * is modified or deserialized") and evidently treats deserializing into it as
 * sufficient. It is not: deserializing does not make it unshared, it just
 * overwrites whatever was in it.
 *
 * <p>The apply path is fine - {@code getRestraintFromStack} reflectively builds a
 * new instance. Only the two load-from-NBT callers are affected, and between them
 * they cover a lot:
 *
 * <ol>
 *   <li><b>{@code RestrainableCapability#deserializeNBT}</b>, server side, on world
 *       load. Two players wearing the same kind of restraint share one object after
 *       a restart, and therefore share its durability, its enchantments and its
 *       captor - whichever loaded last wins. This is why Cuffed's own Famine and
 *       Shroud behave erratically in multiplayer.</li>
 *   <li><b>{@code RestraintEquippedPacket}</b>, CLIENT side, on every equip and
 *       unequip. This one bites even in singleplayer, because that handler assigns
 *       {@code cap.armRestraint = getRestraintFromTag(newTag)} and then, a few
 *       lines later, builds {@code oldRestraint = getRestraintFromTag(oldTag)} and
 *       calls {@code onUnequippedClient} on it.
 *       When the old and new restraints are the same TYPE, {@code oldRestraint} is
 *       the very object just assigned to {@code cap.armRestraint}, and
 *       deserializing the old tag into it overwrites the data of the restraint that
 *       was just equipped.</li>
 * </ol>
 *
 * <p>[stated] asked for this fixed for Cuffed's own enchantments as well as this
 * addon's: <i>"I always wondered why Cuffed's enchantments were so inconsistent and
 * as if they did nothing in multiplayer. This bug explains it. Please fix it [...]
 * this isnt acceptable to stay around"</i>. Fixing it here rather than in our own
 * read path fixes it for durability and captor too, and for Cuffed's six
 * enchantments, not only ours.
 *
 * <h2>Why RETURN rather than HEAD</h2>
 * Injecting at HEAD would have meant parsing the {@code Id} string into a
 * ResourceLocation ourselves and repeating the registry lookup. At RETURN, Cuffed
 * has already done both, so the restraint it found hands us its exact class for
 * free - no key parsing, no {@code ResourceLocation} API to get wrong, and no
 * duplicated lookup that could drift from theirs.
 *
 * <p>The cost is that Cuffed's original code still ran, so the shared singleton is
 * still written to as a side effect. That is <b>exactly today's behaviour</b> and
 * therefore no new risk: the singleton was already being scribbled on, and the two
 * places that read it back ({@code RestraintEntityLayer} for rendering and
 * {@code LocalPlayerMixin} for blocked key codes) only call methods that do not
 * depend on instance state. What changes is that the object handed to the
 * CAPABILITY is now nobody else's.
 *
 * <h2>Failure is a no-op, not a crash</h2>
 * Every restraint class on both sides of the house has a public no-arg constructor
 * - it has to, since every one is registered through a {@code ::new} supplier - so
 * the reflection below always succeeds in practice. If some future addon registers
 * a restraint without one, we log once and hand back Cuffed's shared instance,
 * which is what would have been returned anyway. Nothing regresses; that one
 * restraint keeps the old bug.
 *
 * <h2>No refmap entry</h2>
 * {@code RestraintAPI} is another mod's class and mods are not obfuscated in
 * production Forge, so neither it nor {@code getRestraintFromTag} needs an SRG
 * name. {@code remap = false} says so.
 */
@Mixin(value = RestraintAPI.class, remap = false)
public class RestraintAPIFreshInstanceMixin {

    /**
     * Deliberately declared with NO initializer. Mixin cannot merge a static
     * initializer into the target class, and {@code = false} would emit one;
     * leaving it off gives the same starting value with nothing to merge.
     */
    private static boolean cuffedaddon$reportedFailure;

    @Inject(method = "getRestraintFromTag", at = @At("RETURN"), cancellable = true, require = 1)
    private static void cuffedaddon$returnAFreshInstance(CompoundTag tag,
                                                         CallbackInfoReturnable<AbstractRestraint> cir) {
        AbstractRestraint shared = cir.getReturnValue();
        if (shared == null) {
            // Cuffed found nothing - no Id, or an unregistered one. Leave it.
            return;
        }
        try {
            AbstractRestraint fresh = shared.getClass().getDeclaredConstructor().newInstance();
            // The same tag Cuffed just deserialized, into an object of our own. It
            // cannot throw where theirs did not: we only get here if theirs
            // returned normally.
            fresh.deserializeNBT(tag);
            cir.setReturnValue(fresh);
        } catch (Exception e) {
            if (!cuffedaddon$reportedFailure) {
                cuffedaddon$reportedFailure = true;
                CuffedAddon.LOGGER.warn("Could not build a private instance of restraint {} - it has no usable "
                                + "no-argument constructor, so it keeps Cuffed's shared-instance behaviour "
                                + "(durability, enchantments and captor may leak between wearers).",
                        shared.getClass().getName(), e);
            }
            // Fall through: Cuffed's own return value stands.
        }
    }
}

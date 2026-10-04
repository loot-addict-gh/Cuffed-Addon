package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.example.cuffedaddon.fakeplayer.IFakePlayerPhysicalState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a fake player's own pose (STANDING/SITTING/LAYING) readable and writable to
 * this addon, and freezes it while a stationary restraint is on.
 *
 * <h2>What [stated] asked for</h2>
 * <i>"can you also make it so that poses are disabled in the stationary restraints"</i>.
 * A pillory, a Bed Restraint and a Wall Restraint each put the body in a pose of
 * their own, and their pose offsets are ADDED on top of whatever this addon sets
 * (see {@code FakePlayerSittingPoseMixin} for exactly how) - so a sitting fake
 * player in a pillory reads as neither sitting nor pilloried. Two halves to
 * stopping that: force STANDING when the restraint goes on, and refuse any change
 * while it is on.
 *
 * <h2>Why the setter injection takes no arguments</h2>
 * {@code setPhysicalState} takes a {@code FakePlayerEntity.PhysicalState}, a type
 * this addon must never name. Mixin allows an injector handler to declare only
 * {@code CallbackInfo} and omit the target's own arguments entirely, which is
 * exactly what is wanted here - the decision does not depend on which pose was
 * requested, only on whether any is allowed.
 *
 * <h2>Why cancelling the setter cannot break loading a saved world</h2>
 * Their {@code readAdditionalSaveData} writes the synched slot DIRECTLY
 * ({@code entityData.set(PHYSICAL_STATE, nbt.getInt("State"))}) rather than going
 * through the setter, so a restrained fake player still loads with its stored pose
 * intact and this addon restores or overrides it from the tick afterwards.
 *
 * <p>Targets their class by STRING with {@code remap = false}, and needs no refmap
 * entry - mods are not obfuscated in production Forge. The static-field shadow is
 * the same shape {@code FakePlayerJobMixin} already uses for their {@code AI_STATE}.
 */
@Mixin(targets = "dev.duzo.players.entities.FakePlayerEntity", remap = false)
public abstract class FakePlayerPhysicalStateMixin implements IFakePlayerPhysicalState {

    @Shadow
    @Final
    private static EntityDataAccessor<Integer> PHYSICAL_STATE;

    @Override
    public int cuffedaddon$getPhysicalState() {
        return ((Entity) (Object) this).getEntityData().get(PHYSICAL_STATE);
    }

    @Override
    public void cuffedaddon$setPhysicalState(int ordinal) {
        ((Entity) (Object) this).getEntityData().set(PHYSICAL_STATE, ordinal);
    }

    @Inject(method = "setPhysicalState", at = @At("HEAD"), cancellable = true, remap = false)
    private void cuffedaddon$refusePoseWhileStationary(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && FakeStationaryUtil.isStationary(living)) {
            ci.cancel();
        }
    }
}

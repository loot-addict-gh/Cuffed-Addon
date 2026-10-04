package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.fakeplayer.FakePlayerJobRules;
import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.example.cuffedaddon.fakeplayer.IFakeRestrained;
import net.minecraft.nbt.CompoundTag;
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
 * Stops a Fake Players job that the entity's restraints forbid.
 *
 * <p>Deliberately its own mixin, separate from {@code FakePlayerEntityMixin}: this
 * is the fragile half (a {@code @Shadow} of one of their private static fields,
 * plus an {@code @Inject} into one of their private methods), and a mixin that
 * fails to apply is dropped ENTIRELY. Keeping it apart means that if their
 * internals shift, job gating stops working while restraints still apply and
 * still render - rather than the whole feature silently disappearing, which is
 * what the single combined mixin risked in 1.5.0.
 *
 * <p>The containing config keeps {@code injectors.defaultRequire} at 1 on purpose.
 * The config as a whole is {@code "required": false} so that Fake Players being
 * absent is a no-op, but if the mod IS present and this inject stops matching,
 * that is a loud failure in the log rather than silence.
 */
@Mixin(targets = "dev.duzo.players.entities.FakePlayerEntity", remap = false)
public abstract class FakePlayerJobMixin {

    /**
     * Their private synched AIState tag. Shadowed rather than reached through
     * their {@code getAIState()} so that no type from their mod is named here -
     * the tag is a plain vanilla CompoundTag and the job is an int inside it.
     */
    @Shadow
    @Final
    private static EntityDataAccessor<CompoundTag> AI_STATE;

    /** Their own "this job is paused" setter, used rather than worked around. */
    @Shadow
    public abstract void setJobPaused(boolean paused);

    /**
     * Sets THEIR pause flag, from the tail of the method that owns it.
     *
     * <h2>Why not cancel {@code tickJobExecutor}, which is what 1.5.6 did</h2>
     * Because cancelling it at HEAD skips the part of it that matters on the way
     * out. That method is where the {@code active != jobActivePrev} transition
     * lives, and that transition is what calls {@code jobExecutor.onPause(this)}.
     * Cancelling meant {@code onPause} was never called and {@code jobActivePrev}
     * stayed true for the whole time the restraints were on. Their executors do
     * real cleanup in there:
     * <ul>
     *   <li>the Miner, Crafter, Farmer and Courier call
     *       {@code JobHelpers.closeContainer}, which drops the entry from their
     *       STATIC open-containers map and closes the block. Restrain one while it
     *       is standing at a chest and that chest stays open forever, and the map
     *       entry leaks past the entity's own death;</li>
     *   <li>the Fisherman calls {@code clearHook()}, the only thing that removes
     *       its fishing hook entity - restrain one mid-cast and the hook is
     *       orphaned in the world;</li>
     *   <li>the Crafter clears its display item, so a restrained one would
     *       otherwise keep holding it through the tied-arms pose.</li>
     * </ul>
     *
     * <p>Setting {@code jobPaused} instead makes their own {@code active} compute
     * to false, so {@code onPause} fires, {@code onResume} fires when the
     * restraints come off, and the executor is skipped in between - all through
     * their code paths rather than around them.
     *
     * <p>The injection point is the tail of {@code updateFollowOverridePause},
     * which is the method that owns this flag and runs immediately before
     * {@code tickJobExecutor} in their tick. That ordering is the whole reason
     * this works: writing the flag anywhere else would be overwritten by this
     * method a moment later, which is what the earlier version of this mixin
     * concluded and which is true of every point except after it.
     *
     * <p>It SUPPRESSES rather than clears: their {@code running} flag and the
     * chosen job are untouched, so unlocking resumes whatever the owner had set.
     */
    @Inject(method = "updateFollowOverridePause", at = @At("TAIL"), remap = false)
    private void cuffedaddon$pauseJobsWhileRestrained(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        // 1.5.11: a fake player in a pillory, on a Bed Restraint or on a Wall
        // Restraint runs no job at all, per [stated]'s spec for the stationary
        // restraints. Checked before the body-slot restraints below because it is
        // unconditional - there is no allow-list to consult.
        if (self instanceof LivingEntity living && FakeStationaryUtil.isStationary(living)) {
            setJobPaused(true);
            return;
        }

        IFakeRestrained cap = self.getCapability(ModCapabilities.FAKE_RESTRAINED).orElse(null);
        if (cap == null || !cap.isRestrained()) {
            return;
        }
        CompoundTag state = self.getEntityData().get(AI_STATE);
        if (!FakePlayerJobRules.jobAllowed(state.getInt(FakePlayerJobRules.TAG_JOB), cap)) {
            setJobPaused(true);
        }
    }
}

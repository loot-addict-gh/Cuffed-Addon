package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.LiePoseUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ROUND (this session, corrected): originally written on the theory that
 * vanilla's entity-entity "push apart" physics was the cause of the
 * position jitter [stated] reported (nearby entities nudging a lie-posed
 * player slightly, repeatedly). [stated] confirmed via video that this
 * fix did NOT resolve the jitter - the real cause turned out to be
 * something else entirely (vanilla's own periodic 60-tick forced position
 * resync to observers, worked around client-side instead - see
 * ClientLiePoseEvents' onClientTick).
 *
 * Kept anyway, for the SEPARATE thing [stated] asked about in the same
 * message: the restrained player's hitbox pushing the ACTOR away while
 * standing near/on top of them. That's a real, independent effect of
 * Entity#isPushable() (confirmed via mappings.dev/Bat's override, whose
 * SRG id m_6094_ is shared by the whole isPushable() override chain
 * including Entity's own base declaration - "Returns whether this entity
 * can be pushed by other entities") regardless of whether it explains the
 * jitter - EntitySelector.pushableBy(...), which pushEntities() filters
 * candidates through, excludes any entity this returns false for, so a
 * lie-posed player is excluded from BOTH directions of the push (nobody
 * pushes them, and they push nobody). Not yet specifically re-confirmed
 * by [stated] on its own merits since the jitter theory turned out wrong -
 * worth flagging if the "pushed while standing near" behavior still
 * happens.
 *
 * Runs on both sides deliberately (not registered under the "client"-only
 * mixin list) since the actual push-force application this prevents is
 * server-authoritative gameplay physics, not rendering.
 */
@Mixin(Entity.class)
public abstract class EntityPushableLiePoseMixin {

    @Inject(
            method = "isPushable",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cuffedaddon$notPushableIfPosed(CallbackInfoReturnable<Boolean> cir) {
        // LivingEntity, not Player - 1.5.11. A bed-restrained fake player should
        // no more be shoved off its bed than a bed-restrained player.
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && LiePoseUtil.isPosed(living)) {
            cir.setReturnValue(false);
        }
    }
}

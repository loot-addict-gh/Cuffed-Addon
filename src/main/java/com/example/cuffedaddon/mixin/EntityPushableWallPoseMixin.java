package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.WallPoseUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors cuffedaddon's EntityPushableLiePoseMixin exactly - a wall-posed
 * player shouldn't be shoved off their locked spot by nearby entities, nor
 * push anyone else out of the way while standing pinned to the panel.
 */
@Mixin(Entity.class)
public abstract class EntityPushableWallPoseMixin {

    @Inject(
            method = "isPushable",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cuffedaddon$notPushableIfPosed(CallbackInfoReturnable<Boolean> cir) {
        // LivingEntity, not Player - 1.5.11, same reason as the lie-pose twin.
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && WallPoseUtil.isPosed(living)) {
            cir.setReturnValue(false);
        }
    }
}

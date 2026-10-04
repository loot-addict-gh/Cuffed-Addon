package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.ILiePose;
import com.example.cuffedaddon.pose.LiePoseUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Puts the first-person camera at a low, ground-level eye position matching
 * the lying-flat pose, instead of vanilla's normal standing eye height
 * (~1.62 blocks up).
 *
 * The entity's actual position is always the FEET (that's what entity
 * position is), and it's also the pivot point
 * LivingEntityRendererLiePoseMixin rotates the whole model around to lie it
 * flat - so a camera placed straight above that point (as round 1 of this
 * mixin did) ends up hovering over the LEGS, not the head, once the body is
 * rotated flat and the head has swung out to one side. Round 1's in-game
 * test confirmed exactly that ("camera attached to the legs").
 *
 * Fixed (round 2) by offsetting toward wherever the body actually renders,
 * using LiePoseUtil.bodyExtendDirection(yaw) - HEAD_OFFSET blocks along
 * that direction, EYE_HEIGHT blocks vertically (vanilla's own real sleeping
 * eye height). HEAD_OFFSET (1.52) matches a standing player's real eye
 * height minus a touch - since the "up" axis becomes the "forward" axis
 * after the 90° flip, a point that far up when standing ends up that far
 * out horizontally once lying flat.
 *
 * IMPORTANT: this used to compute its own direction directly
 * (Vec3.directionFromRotation(0, lockedYaw)) instead of going through
 * bodyExtendDirection - round 2's in-game test showed that direction did
 * NOT match where the body actually rendered (camera went toward the
 * player's ORIGINAL facing / "into the tree", while the body itself
 * rendered off to the side) - two independent formulas with no guarantee of
 * agreeing. Routing both through the same bodyExtendDirection helper (which
 * folds in LiePoseUtil.BODY_YAW_OFFSET, the tunable correction for the
 * render mixin's own non-obvious direction) makes them structurally
 * guaranteed to agree - see LiePoseUtil's doc for the full reasoning behind
 * that offset value and how to retune it if it's still not exactly right.
 *
 * 1.5.11 NOTE: the RENDER no longer derives its yaw by adding BODY_YAW_OFFSET at
 * all - it was a mirror of lockedYaw, not an offset from it, which is why beds
 * running east/west rendered backwards while the camera and hitbox were right (see
 * LiePoseUtil.renderYawFor). Nothing here changed: this path was the correct one
 * all along, and the render now agrees with it at EVERY yaw rather than only at
 * two of them.
 *
 * setPosition(Vec3) is protected on Camera, reached via reflection (same
 * pattern as the old Bed Restraint line's CameraBedPoseMixin). Unlike that
 * version, this looks up the SRG name (m_90581_) FIRST and only falls back
 * to the literal "setPosition" for a Gradle dev environment - the old
 * version only ever tried "setPosition", which is why the camera fix had
 * "no visible effect" both times it was tried: setPosition is a PROTECTED
 * method, so on a real (non-dev) Forge install its actual bytecode name is
 * the obfuscated SRG one, not "setPosition" at all - the reflection lookup
 * was silently throwing NoSuchMethodException on every real install, making
 * CAMERA_SET_POSITION null and the whole fix a no-op. m_90581_ was checked
 * against mappings.dev across many 1.20.x-1.21.x versions and is stable
 * throughout (SRG ids don't change once assigned, for as long as the method
 * itself doesn't change signature) - see chat for the sources. This part IS
 * confirmed fixed by round 1's test (the camera did move/drop - it was just
 * anchored to the wrong point on the body).
 *
 * The "setup" @Inject target + refmap SRG mapping (m_90575_) are copied
 * verbatim from the old Bed Restraint entry, which loaded without error on
 * a real (non-dev) Forge 1.20.1 install - reusing a target already
 * confirmed to resolve, rather than guessing a new one.
 */
@Mixin(Camera.class)
public abstract class CameraLiePoseMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("cuffedaddon/liepose");

    private static final float EYE_HEIGHT = 0.2F;
    private static final double HEAD_OFFSET = 1.52;

    private static final Method CAMERA_SET_POSITION;

    static {
        Method method;
        try {
            // Real installed (production) Forge: SRG-obfuscated name.
            method = Camera.class.getDeclaredMethod("m_90581_", Vec3.class);
        } catch (NoSuchMethodException srgFailed) {
            try {
                // Gradle dev run (runClient/runServer): official/dev-mapped name.
                method = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
            } catch (NoSuchMethodException officialFailed) {
                LOGGER.error("[cuffedaddon] Could not find Camera#setPosition(Vec3) via reflection under "
                        + "either SRG or official name - lie-posed first-person camera correction will not apply.", officialFailed);
                method = null;
            }
        }
        if (method != null) {
            method.setAccessible(true);
        }
        CAMERA_SET_POSITION = method;
    }

    @Inject(
            method = "setup",
            at = @At("TAIL")
    )
    private void cuffedaddon$fixLiePoseCameraPosition(BlockGetter level, Entity entity,
                                                        boolean thirdPerson, boolean inverseView,
                                                        float partialTicks, CallbackInfo ci) {
        if (thirdPerson) return; // leave third person alone

        if (!(entity instanceof Player player) || player != Minecraft.getInstance().player) return;
        ILiePose cap = LiePoseUtil.get(player);
        if (cap == null || !cap.isPosed()) return;
        if (CAMERA_SET_POSITION == null) return;

        Vec3 towardHead = LiePoseUtil.bodyExtendDirection(cap.getLockedYaw());
        Vec3 eyePos = new Vec3(player.getX(), player.getY(), player.getZ())
                .add(towardHead.scale(HEAD_OFFSET))
                .add(0.0, EYE_HEIGHT, 0.0);

        try {
            CAMERA_SET_POSITION.invoke(this, eyePos);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[cuffedaddon] Camera#setPosition(Vec3) reflection call failed.", e);
        }
    }
}

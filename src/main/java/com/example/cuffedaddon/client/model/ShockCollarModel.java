package com.example.cuffedaddon.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * The worn Shock Collar, drawn by {@code ShockCollarLayer}.
 *
 * <p>Structurally this is the same additive wrap idea as
 * {@code RopeArmsWrapModel}/{@code StraitjacketArmsWrapModel}, per [stated]'s
 * explicit request - but TORSO ONLY. No arms, no legs, no head: only the "body"
 * part carries geometry, everything else is declared empty, the same convention
 * Cuffed's own DuckTape*Model classes use to split one HumanoidModel skeleton
 * across several cosmetic overlays.
 *
 * <p><b>Why a full torso wrap for something that's only a collar.</b> The visible
 * collar is painted into the texture rather than cut into the geometry: the band
 * is drawn across the top rows of the body's UV footprint and the rest of the
 * footprint is left transparent. That keeps the geometry a plain, well-understood
 * box with a documented UV layout identical to the two wraps already in this
 * project, and it leaves room to paint more than a bare band later (straps down
 * the chest, a battery pack, whatever) without any model change at all.
 *
 * <p><b>Inflate is 0.4f</b>, continuing the established ladder in this addon -
 * Rope's wrap sits at 0.25f, Straitjacket's at 0.35f (raised from an initial
 * 0.3f, which [stated] found z-fought with the vanilla second/"jacket" skin
 * layer), and the collar sits outside both at 0.4f so it never fights either of
 * them when a player is wearing more than one at once.
 *
 * <p>The "body" part is declared at {@link PartPose#ZERO} deliberately:
 * {@code ShockCollarLayer} calls {@code copyPropertiesTo} every frame, which
 * overwrites each part's position and rotation from the real player skeleton, so
 * any pose baked in here would be discarded immediately.
 */
public class ShockCollarModel<T extends LivingEntity> extends HumanoidModel<T> {

    public ShockCollarModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (64x32, textures/entity/shock_collar.png).
     *
     * <p>One box, {@code texOffs(16,0)}, {@code addBox(-4,0,-2, 8,12,4)} - the
     * same body block Rope's and Straitjacket's wraps use, so the body half of
     * either of those textures lines up with this one pixel for pixel:
     *
     * <pre>
     *   right face:  x[16,20) y[4,16)
     *   front face:  x[20,28) y[4,16)
     *   left face:   x[28,32) y[4,16)
     *   back face:   x[32,40) y[4,16)
     *   top face:    x[20,28) y[0,4)
     *   bottom face: x[28,36) y[0,4)
     * </pre>
     *
     * The collar band itself is the TOP few rows of the four side faces - i.e.
     * y[4,7)-ish across x[16,40) - since the body box runs from the neck (local
     * y=0, texture row 4) down to the waist. Everything else in the footprint,
     * and all of x[0,16) and x[40,64), is unused and left transparent.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.4f);

        // Unused parts - kept empty, same pattern as the existing wrap models.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        // FULL TORSO BOX, 8x12x4 at 0.4f inflate - the collar is PAINTED onto its
        // top two rows rather than being geometry of its own.
        //
        // <h2>The 2-tall band box was tried at 1.5.23 and REVERTED - do not
        // re-propose it</h2>
        // The reasoning was sound on paper: cutting the band out into its own
        // short box (8x2x4, same inflate) gives it a real underside and its own
        // top cap, surfaces at a different angle to the front face, which is what
        // normally makes something read as three-dimensional rather than as a
        // decal. [stated] built it and looked at it in game: "I don't see any sort
        // of 3D effect. It's really unnoticeable."
        //
        // Why it failed, for anyone tempted to try again: at the same inflate the
        // silhouette is identical, and the two new surfaces are both nearly
        // invisible in practice. The top cap sits under the head cube, which is 8
        // deep against this box's 4.8 and therefore overhangs it. The underside
        // only catches the eye from below. Straight on at eye level - which is how
        // a collar is actually seen - you are looking at the exact same front face
        // either way.
        //
        // The only version that would read as 3D is a PROTRUDING one, and that
        // means raising the inflate above 0.4f. That is ruled out for a reason
        // that has nothing to do with looks: [stated] has this collar tuned to sit
        // just inside the Leashable Collars mod's own collar so theirs stays
        // visible around it, and inflate is the outward radius. Raising it undoes
        // that fit. Real hardware as separate protruding cuboids (buckle, D-ring)
        // has the same problem and additionally needs new UV rectangles painted.
        //
        // So: this collar is a painted band, deliberately, and the 1.5.22-era
        // geometry is the final answer unless the nesting requirement changes.
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 0)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }
}

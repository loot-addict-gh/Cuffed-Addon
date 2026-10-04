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
 * NEW, additive rope-wrap overlay: covers the full height of both arms plus
 * the torso, in one texture (textures/entity/rope_wrap_arms.png).
 *
 * This does NOT replace or touch DuckTapeArmsModel / rope.png / the
 * RopeArmsRestraint.getModelInterface() overlay - that pipeline (owned by
 * Cuffed's RestraintEntityLayer) is left completely alone. This model is
 * rendered by our own RopeWrapEntityLayer, added on top as a second,
 * independent layer.
 *
 * Only "body", "right_arm" and "left_arm" have real geometry - head/hat/legs
 * are declared empty (CubeListBuilder.create() with no addBox calls), same
 * convention DuckTapeArmsModel/DuckTapeLegsModel/DuckTapeHeadModel already
 * use to split one HumanoidModel skeleton across several cosmetic overlays.
 *
 * The top-level "body"/"right_arm"/"left_arm" parts are declared at
 * PartPose.ZERO on purpose: RopeWrapEntityLayer calls
 * parentModel.copyPropertiesTo(this model) every frame, which overwrites
 * each part's position/rotation with the real player skeleton's current
 * pose (ModelPart.copyFrom copies x/y/z/xRot/yRot/zRot). Whatever pose we
 * bake in here is irrelevant and gets replaced immediately - exactly why
 * the existing DuckTape*Model classes do the same thing.
 *
 * <h2>1.4.41 - the symmetric arm box was a real bug, now fixed</h2>
 * This model used to declare its arm box symmetric around local x=0
 * (-2..+2, width 4) rather than copying vanilla's off-centre arm box, as a
 * deliberate simplification: left_arm could then reuse right_arm's exact
 * addBox call with only .mirror() added for the UV flip, with no risk of
 * getting handedness or offset wrong, and the 0.25 inflate was assumed to
 * cover the difference.
 *
 * <p><b>It did not.</b> Vanilla's arm box is not centred on its own pivot -
 * {@code HumanoidModel} declares the right arm at {@code addBox(-3,-2,-2,
 * 4,12,4)} and the left at {@code addBox(-1,...)}, hanging OUTWARD from
 * pivots at x=-5/+5. A centred box is therefore shifted one whole pixel
 * inboard on both sides: its inner face sinks into the torso and the outer
 * pixel of a normal-width arm is left uncovered. On a SLIM arm (3 wide) the
 * centred box happens to cover completely, so the one model looked right on
 * slim players and wrong on normal ones - it was never "defaulting to slim",
 * it fit slim by accident.
 *
 * <p>Found first on the straitjacket wrap, which had inherited this geometry
 * and showed it much more obviously at its larger 0.35f inflate, and fixed
 * there in 1.4.40. [stated] then reported the same fault here and asked for
 * the same treatment. So this now bakes TWO variants with vanilla's real arm
 * boxes - see {@link #createBodyLayer(boolean)} - picked by
 * {@code RopeWrapEntityLayer} from the skin variant it was constructed for.
 */
public class RopeArmsWrapModel<T extends LivingEntity> extends HumanoidModel<T> {

    public RopeArmsWrapModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (64x32, textures/entity/rope_wrap_arms.png):
     *  - Arm block (shared by both arms, right_arm normal / left_arm mirrored):
     *      right face:  x[0,4)   y[4,16)
     *      front face:  x[4,8)   y[4,16)
     *      left face:   x[8,12)  y[4,16)
     *      back face:   x[12,16) y[4,16)
     *      top face:    x[4,8)   y[0,4)
     *      bottom face: x[8,12)  y[0,4)
     *  - Body block:
     *      right face:  x[16,20) y[4,16)
     *      front face:  x[20,28) y[4,16)
     *      left face:   x[28,32) y[4,16)
     *      back face:   x[32,40) y[4,16)
     *      top face:    x[20,28) y[0,4)
     *      bottom face: x[28,36) y[0,4)
     * (x[40,64) and unused rows are spare space, left fully transparent.)
     *
     * <p>The face rects above describe the WIDE (4-pixel) arm. The slim
     * variant's arm is 3 wide, so per the project's box-UV formula its
     * front/back/top/bottom faces are a pixel narrower and its left and back
     * faces start a pixel earlier - exactly what vanilla does for slim skins.
     * The wide variant samples precisely the same pixels the old single model
     * did, so existing art is untouched there. The body block is unchanged in
     * both.
     *
     * @param slim true for the 3-wide arm box vanilla's PlayerModel uses for
     *             "Alex"-proportioned skins, false for the 4-wide "Steve" one.
     */
    public static LayerDefinition createBodyLayer(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.25f);

        // Unused parts - kept empty, same pattern as the existing DuckTape*Model classes.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 0)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        // Matched to vanilla PlayerModel#createMesh exactly:
        //   wide  right addBox(-3, -2, -2, 4, 12, 4)   left addBox(-1, -2, -2, 4, 12, 4)
        //   slim  right addBox(-2, -2, -2, 3, 12, 4)   left addBox(-1, -2, -2, 3, 12, 4)
        // Note the LEFT arm's origin is -1 in BOTH variants; only the width
        // changes. Both arms still share texOffs(0, 0) with .mirror() on the
        // left - mirroring flips the UV, it doesn't move the box, so the two
        // arms having different local offsets doesn't change which pixels
        // either one samples.
        float armWidth = slim ? 3.0f : 4.0f;
        float rightArmOriginX = slim ? -2.0f : -3.0f;

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(rightArmOriginX, -2.0f, -2.0f, armWidth, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-1.0f, -2.0f, -2.0f, armWidth, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }
}

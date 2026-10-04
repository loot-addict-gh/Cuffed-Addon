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
 * Same shape/purpose as RopeArmsWrapModel (an additive overlay covering the
 * full height of both arms plus the torso, geometry copied verbatim from
 * that class - see its own doc for the full rationale on the symmetric box/
 * mirror() trick and the PartPose.ZERO parts being overwritten every frame
 * by copyPropertiesTo), rendered by StraitjacketWrapEntityLayer as a second,
 * independent layer on top of whatever StraitjacketArmsRestraint's own
 * getModelInterface() already draws (the small HandcuffsArmsPoseModel wrist
 * cuffs).
 *
 * The ONE deliberate difference from RopeArmsWrapModel: a bigger inflate
 * (0.35f vs Rope's 0.25f - bumped from an initial 0.3f attempt, which
 * [stated] found sat basically level with the vanilla second/"jacket" skin
 * layer and z-fought with it) per [stated]'s explicit request that this sit
 * slightly further out than Rope's own wrap layer, so it fully covers both
 * the vanilla skin layer AND Rope's wrap geometry underneath it, instead of
 * potentially z-fighting/clipping with it if a player somehow had both
 * layers active at once.
 *
 * <h2>1.4.40 - the wrong-arm-width bug, FIXED</h2>
 * [stated]: <i>"when using a normal sized player (not slim) the straitjacket
 * arms default to slim, and are clipping inside the player's thick arms."</i>
 * Exactly right about the symptom, and the cause turned out not to be model
 * SELECTION at all - there was only ever one model, and its arm box was the
 * wrong shape in the wrong place.
 *
 * <p>The box was inherited from RopeArmsWrapModel, which deliberately declares
 * its arm <b>symmetric around local x=0</b> ({@code addBox(-2, ...)}, width 4)
 * as a simplification, so that the left arm can reuse the right arm's addBox
 * call with only {@code .mirror()} added. But vanilla's arm box is NOT centred
 * on its own pivot: {@code HumanoidModel} declares the right arm at
 * {@code addBox(-3, -2, -2, 4, 12, 4)} and the left at {@code addBox(-1, ...)},
 * i.e. hanging OUTWARD from a pivot at x=-5/+5. A centred box is therefore
 * shifted one whole pixel inboard on both sides: it buries its inner face in
 * the torso and leaves the outer pixel of a wide arm sticking out uncovered.
 * On a SLIM arm (3 wide, local x[-2,1] / [-1,2]) the centred box happens to
 * cover the arm completely, which is why the one model looked correct on slim
 * players and visibly wrong on normal ones - the wrap wasn't "defaulting to
 * slim", it just fit slim by accident. Rope's own wrap has the identical
 * geometry and so the identical shift; it is simply far less obvious at 0.25f
 * inflate with a rope texture, and is deliberately left alone here rather than
 * churning a fully-confirmed feature (flagged to [stated] instead).
 *
 * <p>So this now bakes TWO variants with vanilla's real arm boxes - see
 * {@link #createBodyLayer(boolean)} - and {@code StraitjacketWrapEntityLayer}
 * picks between them. It gets told which at construction time, from the skin
 * name in {@code EntityRenderersEvent.AddLayers}: Forge builds one
 * {@code PlayerRenderer} per skin variant ("default" and "slim") and a slim
 * renderer only ever renders slim players, so the decision is already made by
 * the time the layer exists. No per-frame entity lookup, and no reliance on
 * {@code AbstractClientPlayer#getModelName}.
 */
public class StraitjacketArmsWrapModel<T extends LivingEntity> extends HumanoidModel<T> {

    public StraitjacketArmsWrapModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (64x32, textures/entity/straitjacket_arms_wrap.png) -
     * identical UV layout to RopeArmsWrapModel's rope_wrap_arms.png, see
     * that class's own doc for the exact face breakdown.
     *
     * <p><b>Note on the slim variant's UVs.</b> A box's UV footprint is derived
     * from its W/H/D, not its position, so the wide variant samples exactly the
     * same pixels the single old model did - existing art is untouched there.
     * The slim variant's arm is 3 wide rather than 4, so per the project's
     * box-UV formula its front/back/top/bottom faces are one pixel narrower and
     * its left/back faces start one pixel earlier. That is the same thing
     * vanilla does for slim skins. For a uniform fabric wrap it is invisible; if
     * it ever reads wrong on a slim player, the alternative is to keep the
     * 4-wide box for slim too and recentre it on the narrower arm, which keeps
     * the UVs pixel-identical at the cost of a slightly puffier sleeve.
     *
     * @param slim true for the 3-wide arm box vanilla's PlayerModel uses for
     *             "Alex"-proportioned skins, false for the 4-wide "Steve" one.
     */
    public static LayerDefinition createBodyLayer(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.35f);

        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        // Torso is identical either way - vanilla's body box is 8x12x4 for both
        // skin variants; only the arms differ between them.
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 0)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        // Matched to vanilla PlayerModel#createMesh exactly:
        //   wide  right addBox(-3, -2, -2, 4, 12, 4)   left addBox(-1, -2, -2, 4, 12, 4)
        //   slim  right addBox(-2, -2, -2, 3, 12, 4)   left addBox(-1, -2, -2, 3, 12, 4)
        // Both arms still share texOffs(0, 0) with .mirror() on the left, as
        // before - mirroring flips the UV, it doesn't move the box, so the two
        // arms having different local offsets doesn't affect which pixels either
        // one samples.
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

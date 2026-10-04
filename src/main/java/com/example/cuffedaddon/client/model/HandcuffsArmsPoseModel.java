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

import javax.annotation.Nonnull;

/**
 * Wrist-cuff geometry for the lie pose, one cuff on each arm. This is
 * Cuffed's own real HandcuffsModel geometry (com.lazrproductions.cuffed.
 * restraints.client.model.HandcuffsModel, decompiled from the jar the user
 * provided plus cross-checked against github.com/LazrProductions/cuffed) -
 * the cube/texOffs/PartPose values for "LeftCuff_r1"/"RightCuff_r1" below
 * are copied verbatim, NOT re-derived, so this ends up looking exactly like
 * the real Cuffed handcuffs model.
 *
 * The ONE deliberate change: Cuffed's original also includes a "Chain_r1"
 * cube on the left arm - a thin connecting link meant to visually bridge
 * the two cuffs when arms are held together in front of the body (Cuffed's
 * normal restrained pose). That doesn't make sense for THIS pose, where
 * arms are spread wide apart (see HumanoidModelLiePoseMixin) - a chain
 * stretched across that gap would look wrong. Left out here entirely,
 * matching the user's own edited texture (chain pixels removed).
 *
 * Rendered by LiePoseHandcuffsLayer, gated on LiePoseUtil.isPosed(entity) -
 * completely independent of Cuffed's own restraint/RestraintEntityLayer
 * system (no HandcuffsArmsRestraint involved). copyPropertiesTo (called by
 * the layer every frame, same as Cuffed's own layer does) is what makes
 * these cuffs follow wherever the parent model's arms actually are,
 * including our spread-out lie pose angles - nothing here needs to know
 * about that pose itself.
 */
public class HandcuffsArmsPoseModel<T extends LivingEntity> extends HumanoidModel<T> {

    private final ModelPart root;

    public HandcuffsArmsPoseModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition leftarm = partdefinition.getChild("left_arm");
        PartDefinition leftcuff = leftarm.addOrReplaceChild("leftcuff", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        leftcuff.addOrReplaceChild("LeftCuff_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -3.0F, 1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 4).addBox(-2.0F, 1.0F, 1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 8).addBox(-3.0F, -2.0F, 1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 8).addBox(1.0F, -2.0F, 1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 5.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        // Cuffed's original "Chain_r1" (left arm connecting chain) intentionally omitted - see class doc.

        PartDefinition rightarm = partdefinition.getChild("right_arm");
        PartDefinition rightcuff = rightarm.addOrReplaceChild("rightcuff", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        rightcuff.addOrReplaceChild("RightCuff_r1", CubeListBuilder.create().texOffs(8, 8).addBox(-4.0F, -2.0F, 6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 8).addBox(0.0F, -2.0F, 6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-3.0F, -3.0F, 6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 4).addBox(-3.0F, 1.0F, 6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        // Declared coordinate space matches Cuffed's own original (16x16) -
        // the actual bound texture is 32x32 (a uniform 2x upscale in both
        // dimensions), which keeps every texOffs UV lining up correctly.
        return LayerDefinition.create(meshdefinition, 16, 16);
    }

    @Override
    public void renderToBuffer(@Nonnull com.mojang.blaze3d.vertex.PoseStack stack,
                                @Nonnull com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                int packedLight, int blockLight,
                                float partialTick, float r, float g, float b) {
        root.render(stack, buffer, packedLight, blockLight);
        super.renderToBuffer(stack, buffer, packedLight, blockLight, partialTick, r, g, b);
    }
}

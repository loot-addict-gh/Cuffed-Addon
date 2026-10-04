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
 * Ankle-cuff geometry for the lie pose, one cuff on each leg. Same idea as
 * HandcuffsArmsPoseModel - this is Cuffed's own real LegcuffsModel geometry
 * (com.lazrproductions.cuffed.restraints.client.model.LegcuffsModel) copied
 * verbatim, with the connecting chain cube left out (Cuffed's "cube_r2" on
 * the left leg - a thin 5x2x0 box, same shape/texture-region as the arm
 * model's chain piece) since it wouldn't make sense once legs are spread
 * apart - see HandcuffsArmsPoseModel's class doc for the full reasoning,
 * which applies identically here.
 */
public class HandcuffsLegsPoseModel<T extends LivingEntity> extends HumanoidModel<T> {

    private final ModelPart root;

    public HandcuffsLegsPoseModel(ModelPart root) {
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

        PartDefinition left_leg = partdefinition.getChild("left_leg");
        PartDefinition right_leg = partdefinition.getChild("right_leg");

        PartDefinition right_cuff = right_leg.addOrReplaceChild("right_cuff", CubeListBuilder.create(), PartPose.offset(0.0F, 8.0F, 0.0F));
        right_cuff.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 8).addBox(1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 8).addBox(-3.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 4).addBox(-2.0F, 1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-2.0F, -3.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 1.5708F, 0.0F, 0.0F));

        PartDefinition left_cuff = left_leg.addOrReplaceChild("left_cuff", CubeListBuilder.create(), PartPose.offset(0.1F, 8.0F, 0.0F));
        // Cuffed's original "cube_r2" (left leg connecting chain) intentionally omitted - see class doc.
        left_cuff.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(8, 8).addBox(-3.1F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 8).addBox(0.9F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 4).addBox(-2.1F, 1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-2.1F, -3.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

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

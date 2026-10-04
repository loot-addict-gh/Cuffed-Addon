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
 * Same shape/purpose as RopeLegsWrapModel - see that class and
 * StraitjacketArmsWrapModel's own docs for the full rationale. Only
 * difference from RopeLegsWrapModel is the bigger 0.3f inflate (vs Rope's
 * 0.25f), so this sits slightly further out and fully covers the skin layer.
 */
public class StraitjacketLegsWrapModel<T extends LivingEntity> extends HumanoidModel<T> {

    public StraitjacketLegsWrapModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (64x32, textures/entity/straitjacket_legs_wrap.png) -
     * identical UV layout to RopeLegsWrapModel's rope_wrap_legs.png.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.35f);

        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }
}

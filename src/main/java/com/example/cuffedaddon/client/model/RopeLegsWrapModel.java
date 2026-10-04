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
 * NEW, additive rope-wrap overlay: covers the full height of both legs, in
 * one texture (textures/entity/rope_wrap_legs.png). See RopeArmsWrapModel
 * for the full rationale (ZERO poses overwritten each frame by
 * copyPropertiesTo, symmetric box + mirror() for the shared leg texture).
 * Independent of the existing DuckTapeLegsModel / rope.png pipeline.
 */
public class RopeLegsWrapModel<T extends LivingEntity> extends HumanoidModel<T> {

    public RopeLegsWrapModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (64x32, textures/entity/rope_wrap_legs.png):
     *  - Leg block (shared by both legs, right_leg normal / left_leg mirrored):
     *      right face:  x[0,4)   y[4,16)
     *      front face:  x[4,8)   y[4,16)
     *      left face:   x[8,12)  y[4,16)
     *      back face:   x[12,16) y[4,16)
     *      top face:    x[4,8)   y[0,4)
     *      bottom face: x[8,12)  y[0,4)
     * (x[16,64) and unused rows are spare space, left fully transparent.)
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.25f);

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

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
 * NEW, additive rope-wrap overlay for the head: covers all six sides of the
 * head box, in one texture (textures/entity/rope_wrap_head.png). See
 * RopeArmsWrapModel for the full rationale (ZERO pose overwritten each
 * frame by copyPropertiesTo). Independent of the existing
 * DuckTapeHeadModel / rope.png pipeline that RopeHeadRestraint already
 * uses via getModelInterface() - that is left completely alone. This model
 * is rendered by RopeWrapEntityLayer, added on top as a second, independent
 * layer, active only while the player's head restraint is Rope.
 *
 * Unlike the arms/legs wrap boxes (which were deliberately re-centered to
 * be symmetric so one addBox could be mirrored for both limbs), the head
 * is a single part, so it just uses a single standard 8x8x8 box - no
 * mirroring needed.
 *
 * Uses the same 0.25f inflate as the arms/legs wrap boxes (kept consistent
 * across all three wrap models). A larger inflate was tried first to try
 * to clear the vanilla hat/helmet overlay layer, but it also pushed this
 * layer outside Cuffed's own existing rope head render (rope.png via
 * DuckTapeHeadModel), which looked wrong - reverted to 0.25f.
 */
public class RopeHeadWrapModel<T extends LivingEntity> extends HumanoidModel<T> {

    public RopeHeadWrapModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (32x16, textures/entity/rope_wrap_head.png). This is
     * simply the standard Minecraft box-UV unwrap for an 8x8x8 box at
     * texOffs(0,0) - it happens to tile the full 32x16 texture with no
     * spare space, unlike the arms/legs wrap textures:
     *   top face:    x[8,16)  y[0,8)
     *   bottom face: x[16,24) y[0,8)
     *   right face:  x[0,8)   y[8,16)
     *   front face:  x[8,16)  y[8,16)
     *   left face:   x[16,24) y[8,16)
     *   back face:   x[24,32) y[8,16)
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.25f);

        // Unused parts - kept empty, same pattern as RopeArmsWrapModel/RopeLegsWrapModel.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, inflate),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 32, 16);
    }
}

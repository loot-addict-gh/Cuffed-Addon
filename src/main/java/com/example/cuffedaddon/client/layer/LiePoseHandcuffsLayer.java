package com.example.cuffedaddon.client.layer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.client.ModClientModelLayers;
import com.example.cuffedaddon.client.model.HandcuffsArmsPoseModel;
import com.example.cuffedaddon.client.model.HandcuffsLegsPoseModel;
import com.example.cuffedaddon.pose.LiePoseUtil;
import com.example.cuffedaddon.pose.WallPoseUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;

/**
 * Purely cosmetic handcuffs (wrists + ankles) for BOTH the lie pose AND
 * the wall pose - gated on LiePoseUtil.isPosed(entity) OR
 * WallPoseUtil.isPosed(entity), completely independent of Cuffed's own
 * restraint system (no HandcuffsArmsRestraint/HandcuffsLegsRestraint
 * check - this renders whether or not the player actually has Cuffed's
 * real handcuffs item applied). Originally lie-pose-only; widened when
 * Wall Restraint was merged in from its own temporary project rather than
 * keeping a second, duplicate layer/model set - the geometry is identical
 * either way (both poses reuse the same limb-angle convention, see
 * HumanoidModelWallPoseMixin's own doc for why that's expected to just
 * work). Structurally this mirrors com.lazrproductions.cuffed.restraints.
 * client.layer.RestraintEntityLayer's own copyPropertiesTo + renderToBuffer
 * pattern (same ItemRenderer.getArmorFoilBuffer / RenderType.armorCutoutNoCull
 * approach that mod uses for its own restraint models), just hardcoded to
 * these two models and gated on our own pose state instead of doing the
 * generic "look up every registered restraint's RestraintModelInterface"
 * dance.
 *
 * Uses the addon's OWN texture (assets/cuffedaddon/textures/entity/
 * bed_cuffs.png - renamed from handcuffs.png for clarity, since it's
 * specifically the Lie Pose/Bed Restraint (and now also Wall Restraint)
 * cosmetic cuffs, not to be confused with Cuffed's own real handcuffs
 * item/restraint - a copy of Cuffed's real handcuffs.png with the
 * connecting chain pixels removed, since HandcuffsArmsPoseModel/
 * HandcuffsLegsPoseModel both omit the chain geometry entirely), NOT
 * Cuffed's own cuffed:textures/entity/handcuffs.png.
 */
public class LiePoseHandcuffsLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/bed_cuffs.png");

    private final HandcuffsArmsPoseModel<T> armsModel;
    private final HandcuffsLegsPoseModel<T> legsModel;

    public LiePoseHandcuffsLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        // 1.5.22: shared bake - see ModClientModelLayers.bake. Same reason as
        // RopeWrapEntityLayer: this runs per frame per visible fake player.
        this.armsModel = new HandcuffsArmsPoseModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.HANDCUFFS_ARMS_POSE_LAYER));
        this.legsModel = new HandcuffsLegsPoseModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.HANDCUFFS_LEGS_POSE_LAYER));
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight,
                        @Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTick,
                        float ageInTicks, float netHeadYaw, float headPitch) {
        // LivingEntity, not Player - 1.5.11. FakePlayerRendererMixin now adds this
        // layer to Fake Players' real renderer too, so a bed- or wall-restrained
        // fake player wears the same cosmetic cuffs a restrained player does.
        if (!(LiePoseUtil.isPosed(entity) || WallPoseUtil.isPosed(entity))) {
            return;
        }

        this.getParentModel().copyPropertiesTo(this.armsModel);
        this.armsModel.body.visible = true;
        this.renderModel(poseStack, buffer, packedLight, this.armsModel);

        this.getParentModel().copyPropertiesTo(this.legsModel);
        this.legsModel.body.visible = true;
        this.renderModel(poseStack, buffer, packedLight, this.legsModel);
    }

    private void renderModel(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HumanoidModel<T> model) {
        VertexConsumer vertexConsumer = ItemRenderer.getArmorFoilBuffer(
                buffer, RenderType.armorCutoutNoCull(TEXTURE), false, false);
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}

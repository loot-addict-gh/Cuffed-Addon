package com.example.cuffedaddon.client.layer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.client.ModClientModelLayers;
import com.example.cuffedaddon.client.model.RopeArmsWrapModel;
import com.example.cuffedaddon.client.model.RopeHeadWrapModel;
import com.example.cuffedaddon.client.model.RopeLegsWrapModel;
import com.example.cuffedaddon.restraints.RopeArmsRestraint;
import com.example.cuffedaddon.restraints.RopeHeadRestraint;
import com.example.cuffedaddon.restraints.RopeLegsRestraint;
import com.example.cuffedaddon.restraints.SleepMaskRopeRestraint;
import com.lazrproductions.cuffed.entity.base.IRestrainableEntity;
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

import javax.annotation.Nonnull;

/**
 * Second, independent rope overlay on top of whatever Cuffed's own
 * RestraintEntityLayer already draws via RestraintModelInterface/rope.png
 * (RopeArmsRestraint / RopeLegsRestraint / RopeHeadRestraint - all
 * untouched). This layer draws three brand new wrap models when
 * the player's current arm/leg/head restraint is Rope, using three new
 * textures the user paints by hand:
 *   - textures/entity/rope_wrap_arms.png  (both arms + torso)
 *   - textures/entity/rope_wrap_legs.png  (both legs)
 *   - textures/entity/rope_wrap_head.png  (all six sides of the head)
 *
 * 1.3.x: the SleepMaskRopeRestraint combo also uses this same headWrapModel
 * geometry, but with its own separate texture
 * (textures/entity/sleep_mask_rope_wrap.png, a copy of rope_wrap_head.png)
 * so the two can be edited independently - see the render() method below.
 *
 * Structurally this mirrors com.lazrproductions.cuffed.restraints.client.
 * layer.RestraintEntityLayer (same copyPropertiesTo + renderToBuffer
 * pattern via ItemRenderer.getFoilBuffer / RenderType.entityCutoutNoCull),
 * just hardcoded to our two new models instead of doing the generic
 * "look up every registered restraint's RestraintModelInterface" thing,
 * since we only ever draw these three.
 */
public class RopeWrapEntityLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation ARMS_WRAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/rope_wrap_arms.png");
    private static final ResourceLocation LEGS_WRAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/rope_wrap_legs.png");
    private static final ResourceLocation HEAD_WRAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/rope_wrap_head.png");
    // Sleep Mask + Rope's OWN copy of the above, independently editable -
    // [stated] wants every combo's art separable from its source items,
    // including this inner/under layer, not a shared reference.
    private static final ResourceLocation SLEEP_MASK_ROPE_WRAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/sleep_mask_rope_wrap.png");

    private final RopeArmsWrapModel<T> armsWrapModel;
    private final RopeLegsWrapModel<T> legsWrapModel;
    private final RopeHeadWrapModel<T> headWrapModel;

    /**
     * @param slim whether the {@code PlayerRenderer} this layer is being added
     *             to is the "slim"/Alex-proportioned one. Decided once here,
     *             at construction, rather than per frame - Forge builds a
     *             separate PlayerRenderer per skin variant and a slim renderer
     *             only ever draws slim players. Only the ARMS wrap varies;
     *             vanilla's leg and head boxes are the same in both variants,
     *             so those two models are shared. See RopeArmsWrapModel's doc
     *             for the geometry bug this fixes (1.4.41).
     */
    public RopeWrapEntityLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context,
                                boolean slim) {
        super(parent);
        // 1.5.22: ModClientModelLayers.bake instead of context.bakeLayer - this
        // constructor runs once per FRAME per visible fake player (see that
        // method's doc), and baking three model trees at frame rate was the
        // single most expensive thing this addon did.
        this.armsWrapModel = new RopeArmsWrapModel<>(ModClientModelLayers.bake(context, slim
                ? ModClientModelLayers.ROPE_ARMS_WRAP_SLIM_LAYER
                : ModClientModelLayers.ROPE_ARMS_WRAP_LAYER));
        this.legsWrapModel = new RopeLegsWrapModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.ROPE_LEGS_WRAP_LAYER));
        this.headWrapModel = new RopeHeadWrapModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.ROPE_HEAD_WRAP_LAYER));
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight,
                        @Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTick,
                        float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof IRestrainableEntity restrainable)) {
            return;
        }

        if (RopeArmsRestraint.ID.equals(restrainable.getArmRestraintId())) {
            this.getParentModel().copyPropertiesTo(this.armsWrapModel);
            this.renderWrapModel(poseStack, buffer, packedLight, this.armsWrapModel, ARMS_WRAP_TEXTURE);
        }

        if (RopeLegsRestraint.ID.equals(restrainable.getLegRestraintId())) {
            this.getParentModel().copyPropertiesTo(this.legsWrapModel);
            this.renderWrapModel(poseStack, buffer, packedLight, this.legsWrapModel, LEGS_WRAP_TEXTURE);
        }

        ResourceLocation headRestraintId = restrainable.getHeadRestraintId();
        // 1.3.x: Sleep Mask + Rope is the one combo that also gets this
        // layer - see SleepMaskRopeRestraint's own doc for why it's the
        // only one of the 4 combos where this would actually be visible.
        // It uses its own copy of the texture (SLEEP_MASK_ROPE_WRAP_TEXTURE),
        // not Rope's original, so the two can be edited independently.
        if (RopeHeadRestraint.ID.equals(headRestraintId)) {
            this.getParentModel().copyPropertiesTo(this.headWrapModel);
            this.renderWrapModel(poseStack, buffer, packedLight, this.headWrapModel, HEAD_WRAP_TEXTURE);
        } else if (SleepMaskRopeRestraint.ID.equals(headRestraintId)) {
            this.getParentModel().copyPropertiesTo(this.headWrapModel);
            this.renderWrapModel(poseStack, buffer, packedLight, this.headWrapModel, SLEEP_MASK_ROPE_WRAP_TEXTURE);
        }
    }

    private void renderWrapModel(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  HumanoidModel<T> model, ResourceLocation texture) {
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(
                buffer, RenderType.entityCutoutNoCull(texture), false, false);
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
    }
}

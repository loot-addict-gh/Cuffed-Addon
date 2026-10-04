package com.example.cuffedaddon.client.layer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.client.ModClientModelLayers;
import com.example.cuffedaddon.client.model.StraitjacketArmsWrapModel;
import com.example.cuffedaddon.client.model.StraitjacketLegsWrapModel;
import com.example.cuffedaddon.restraints.StraitjacketArmsRestraint;
import com.example.cuffedaddon.restraints.StraitjacketLegsRestraint;
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
 * Straitjacket's own second, additive overlay on top of whatever
 * StraitjacketArmsRestraint/StraitjacketLegsRestraint's own
 * getModelInterface() already draws (the small HandcuffsArmsPoseModel/
 * HandcuffsLegsPoseModel wrist/ankle cuffs, same geometry+texture Bed/Wall
 * Restraint use) - structurally this is RopeWrapEntityLayer's exact pattern
 * (copyPropertiesTo + renderToBuffer via ItemRenderer.getFoilBuffer /
 * RenderType.entityCutoutNoCull), just pointed at Straitjacket's own two new
 * wrap models/textures instead of Rope's three.
 *
 * No head branch here - see StraitjacketHeadRestraint's own doc: the head
 * reuses Cuffed's own BundleModel + ModModelLayers.BUNDLE_LAYER directly via
 * getModelInterface(), the same geometry Bundle/Sleep Mask/the 4 combos
 * already use for their own head coverage, rather than a custom box here
 * (an earlier custom-box attempt didn't reliably clear the vanilla hat
 * layer on the head).
 *
 * Uses textures/entity/straitjacket_arms_wrap.png and
 * straitjacket_legs_wrap.png - [stated]'s own final art since 1.4.26, same
 * 64x32 UV layout as Rope's own rope_wrap_arms.png/rope_wrap_legs.png (see
 * StraitjacketArmsWrapModel/StraitjacketLegsWrapModel).
 *
 * <h2>1.4.40 - slim vs normal arm width</h2>
 * The arms wrap now comes in two bakes, because vanilla's own arm box differs
 * between the two skin proportions and a single box cannot fit both (see
 * StraitjacketArmsWrapModel's doc for the full bug: the old single box was
 * centred on its pivot, which fit slim arms by accident and sank into normal
 * ones).
 *
 * <p><b>Which one is decided at CONSTRUCTION, not per frame.</b> Forge builds a
 * separate {@code PlayerRenderer} per skin variant and hands them to
 * {@code EntityRenderersEvent.AddLayers} keyed by name ("default" / "slim"), so
 * ClientModEvents already knows which renderer it is attaching to, and a slim
 * renderer only ever draws slim players. That is both cheaper than asking the
 * entity every frame and free of any assumption about
 * {@code AbstractClientPlayer}'s own model-name accessor. The LEGS wrap is
 * shared - vanilla's leg box is the same in both variants.
 */
public class StraitjacketWrapEntityLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation ARMS_WRAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/straitjacket_arms_wrap.png");
    private static final ResourceLocation LEGS_WRAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/straitjacket_legs_wrap.png");

    private final StraitjacketArmsWrapModel<T> armsWrapModel;
    private final StraitjacketLegsWrapModel<T> legsWrapModel;

    /**
     * @param slim whether the {@code PlayerRenderer} this layer is being added
     *             to is the "slim"/Alex-proportioned one.
     */
    public StraitjacketWrapEntityLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context,
                                        boolean slim) {
        super(parent);
        // 1.5.22: shared bake - see ModClientModelLayers.bake. Same reason as
        // RopeWrapEntityLayer: this runs per frame per visible fake player.
        this.armsWrapModel = new StraitjacketArmsWrapModel<>(ModClientModelLayers.bake(context, slim
                ? ModClientModelLayers.STRAITJACKET_ARMS_WRAP_SLIM_LAYER
                : ModClientModelLayers.STRAITJACKET_ARMS_WRAP_LAYER));
        this.legsWrapModel = new StraitjacketLegsWrapModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.STRAITJACKET_LEGS_WRAP_LAYER));
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight,
                        @Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTick,
                        float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof IRestrainableEntity restrainable)) {
            return;
        }

        if (StraitjacketArmsRestraint.ID.equals(restrainable.getArmRestraintId())) {
            this.getParentModel().copyPropertiesTo(this.armsWrapModel);
            this.renderWrapModel(poseStack, buffer, packedLight, this.armsWrapModel, ARMS_WRAP_TEXTURE);
        }

        if (StraitjacketLegsRestraint.ID.equals(restrainable.getLegRestraintId())) {
            this.getParentModel().copyPropertiesTo(this.legsWrapModel);
            this.renderWrapModel(poseStack, buffer, packedLight, this.legsWrapModel, LEGS_WRAP_TEXTURE);
        }
    }

    private void renderWrapModel(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  HumanoidModel<T> model, ResourceLocation texture) {
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(
                buffer, RenderType.entityCutoutNoCull(texture), false, false);
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
    }
}

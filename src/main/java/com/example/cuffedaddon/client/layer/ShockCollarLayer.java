package com.example.cuffedaddon.client.layer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.client.ModClientModelLayers;
import com.example.cuffedaddon.client.model.ShockCollarModel;
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
 * Draws the worn Shock Collar on a player. Structurally identical to
 * {@code StraitjacketWrapEntityLayer} / {@code RopeWrapEntityLayer} -
 * copyPropertiesTo, then renderToBuffer through
 * {@code ItemRenderer.getFoilBuffer} with {@code RenderType.entityCutoutNoCull}
 * - and deliberately so: [stated] asked for the collar to use the same kind of
 * wrap layer as Rope and Straitjacket, torso only, at a 0.4f inflate.
 *
 * <p>The one structural difference from those two: they read their state from
 * Cuffed's own {@code IRestrainableEntity} (which Cuffed already syncs to
 * clients for exactly this purpose), whereas the collar lives in this addon's
 * own capability, which Forge does NOT sync. That's what
 * {@code ShockCollarSyncPacket}'s broadcast-to-trackers send exists for - by the
 * time this layer runs, that packet has already mirrored the wearer's collar
 * state onto this client's copy of the entity.
 */
public class ShockCollarLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation COLLAR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/shock_collar.png");

    private final ShockCollarModel<T> collarModel;

    public ShockCollarLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        // 1.5.22: shared bake - see ModClientModelLayers.bake. This layer only
        // ever goes on the real PlayerRenderer (built once at startup), so it is
        // not paying the per-frame cost the other three were; routed through the
        // cache anyway so every layer in this package asks for its geometry the
        // same way.
        this.collarModel = new ShockCollarModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.SHOCK_COLLAR_LAYER));
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight,
            @Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        boolean collared = entity.getCapability(ModCapabilities.COLLARED)
                .map(cap -> cap.isCollared())
                .orElse(false);
        if (!collared) {
            return;
        }

        this.getParentModel().copyPropertiesTo(this.collarModel);
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(
                buffer, RenderType.entityCutoutNoCull(COLLAR_TEXTURE), false, false);
        this.collarModel.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
    }
}

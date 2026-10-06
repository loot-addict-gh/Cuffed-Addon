package com.example.cuffedaddon.client.layer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.client.ModClientModelLayers;
import com.example.cuffedaddon.client.model.KeyNecklaceModel;
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
 * Draws the worn Key Necklace on a player. Structurally identical to
 * {@code ShockCollarLayer} - shared bake through
 * {@code ModClientModelLayers.bake}, {@code copyPropertiesTo}, then
 * {@code renderToBuffer} through {@code ItemRenderer.getFoilBuffer} with
 * {@code RenderType.entityCutoutNoCull} - which is the whole family's shape
 * (Rope's and the Straitjacket's wraps do the same).
 *
 * <p>Like the collar's, the state it reads lives in one of this addon's OWN
 * capabilities, which Forge does not sync; {@code NecklaceSyncPacket}'s
 * broadcast-to-trackers send is what has already mirrored it onto this client's
 * copy of the entity by the time this runs. (The Rope and Straitjacket layers
 * get theirs free from Cuffed's own {@code IRestrainableEntity}, which Cuffed
 * already syncs.)
 */
public class KeyNecklaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation NECKLACE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/key_necklace.png");

    private final KeyNecklaceModel<T> necklaceModel;

    public KeyNecklaceLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.necklaceModel = new KeyNecklaceModel<>(
                ModClientModelLayers.bake(context, ModClientModelLayers.KEY_NECKLACE_LAYER));
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight,
            @Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        boolean wearing = entity.getCapability(ModCapabilities.NECKLACED)
                .map(cap -> cap.isWearing())
                .orElse(false);
        if (!wearing) {
            return;
        }

        this.getParentModel().copyPropertiesTo(this.necklaceModel);
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(
                buffer, RenderType.entityCutoutNoCull(NECKLACE_TEXTURE), false, false);
        this.necklaceModel.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
    }
}

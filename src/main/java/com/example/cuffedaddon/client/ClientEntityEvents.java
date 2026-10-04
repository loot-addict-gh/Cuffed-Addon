package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.init.ModEntityTypes;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers renderers for the addon's non-visual entity types. Both use
 * vanilla's NoopRenderer (draws nothing):
 * - ANCHOR_KNOT: the chain-line render from an anchored entity to its
 *   anchor (ChainUtils.renderChainTo, hooked in via Cuffed's own
 *   EntityRendererMixin on EntityRenderer#render) is entirely
 *   generic-by-Entity and already works with zero changes needed here, so
 *   a visible model on the knot itself is a pure cosmetic extra, not
 *   required for the feature to work. Swap this for a real EntityRenderer +
 *   model later if a visible marker at the anchor point is wanted
 *   (mirroring Cuffed's own ChainKnotEntityRenderer/ChainKnotEntityModel).
 * - PLAYER_PICKER_ANCHOR: the invisible carrier a picked player rides (see
 *   PlayerPickerAnchorEntity's own doc) - genuinely never meant to be seen,
 *   NoopRenderer is the permanent choice here, not a placeholder.
 *
 * The two special arrows (1.6.0) are the first entities here that ARE meant to
 * be seen. Both get vanilla's ArrowRenderer by way of one parameterised
 * subclass that differs only in its texture - see ReinforcedArrowRenderer.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEntityEvents {

    private static final ResourceLocation RESTRAINT_ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CuffedAddon.MODID, "textures/entity/projectiles/arrow_of_restraint.png");
    private static final ResourceLocation TIPPED_ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "textures/entity/projectiles/tipped_arrow.png");

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.ANCHOR_KNOT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PLAYER_PICKER_ANCHOR.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ARROW_OF_RESTRAINT.get(),
                context -> new ReinforcedArrowRenderer<>(context, RESTRAINT_ARROW_TEXTURE));
        // Vanilla's own tipped-arrow texture, not a copy of it: the Arrow of
        // Electrization is meant to look like a tipped arrow and [stated] asked
        // for a custom in-flight texture "for the restraint arrows only".
        event.registerEntityRenderer(ModEntityTypes.ARROW_OF_ELECTRIZATION.get(),
                context -> new ReinforcedArrowRenderer<>(context, TIPPED_ARROW_TEXTURE));
    }
}

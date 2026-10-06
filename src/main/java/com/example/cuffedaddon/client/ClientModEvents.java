package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.client.layer.KeyNecklaceLayer;
import com.example.cuffedaddon.client.layer.LiePoseHandcuffsLayer;
import com.example.cuffedaddon.client.layer.RopeWrapEntityLayer;
import com.example.cuffedaddon.client.layer.ShockCollarLayer;
import com.example.cuffedaddon.client.layer.StraitjacketWrapEntityLayer;
import com.example.cuffedaddon.init.ModBlocks;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.items.ShockCollarItem;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * NOTE: this listens on the MOD event bus (bus = Bus.MOD), unlike the
 * existing ClientEvents class which listens on the regular Forge event bus
 * (ClientChatEvent etc). RegisterLayerDefinitions/AddLayers are both
 * mod-bus, fired once during client setup - they are not the same bus as
 * gameplay events.
 *
 * We don't (and can't) touch PlayerRendererMixin - that's compiled into
 * Cuffed's jar and is how Cuffed's own RestraintEntityLayer gets added.
 * AddLayers is Forge's own, addon-friendly extension point for exactly
 * this: adding more layers onto the SAME PlayerRenderer instances Cuffed's
 * mixin already added its layer to, alongside it rather than replacing it.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        ModClientModelLayers.registerLayers(event);
    }

    /**
     * "default" and "slim" player skin variants - our layers go on both.
     *
     * <p>1.4.40: the skin name is now PASSED ON to the straitjacket wrap layer,
     * because its arm geometry has to differ between the two (vanilla's own arm
     * box is 4 wide for "default" and 3 wide for "slim" - see
     * {@code StraitjacketArmsWrapModel}). Forge hands out one PlayerRenderer per
     * variant here and a given renderer only ever draws players of that variant,
     * so this loop is the natural place to decide it - once, at setup, instead of
     * re-deriving it for every entity every frame.
     *
     * <p>1.4.41: the ROPE wrap layer gets the same flag. Its arms model had the
     * identical centred-box fault - [stated] spotted it on rope once the
     * straitjacket fix showed them what the bug looked like.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinName : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skinName);
            if (renderer != null) {
                boolean slim = "slim".equals(skinName);
                renderer.addLayer(new RopeWrapEntityLayer<>(renderer, event.getContext(), slim));
                renderer.addLayer(new LiePoseHandcuffsLayer<>(renderer, event.getContext()));
                renderer.addLayer(new StraitjacketWrapEntityLayer<>(renderer, event.getContext(), slim));
                renderer.addLayer(new ShockCollarLayer<>(renderer, event.getContext()));
                // Key Necklace (1.6.5), added AFTER the collar so that when a
                // player wears both, the necklace draws second. At 0.45f
                // against the collar's 0.4f they do not overlap in space, so
                // this is belt-and-braces rather than load-bearing.
                renderer.addLayer(new KeyNecklaceLayer<>(renderer, event.getContext()));
            }
        }

        // NOTE: Fake Players' entity is deliberately NOT handled here. Its
        // registered renderer is a wrapper whose model is null and whose render()
        // never calls super.render(), so a layer added to it would never be drawn
        // - see FakePlayerRendererMixin, which hooks the real renderer instead.
    }

    /**
     * Wall Restraint - merged in from the temporary standalone
     * wallrestraint project. The block's texture has real per-pixel alpha
     * (the front/back artwork's gaps), and Forge's default SOLID render
     * layer ignores alpha entirely, so this needs an explicit translucent
     * registration - belt-and-suspenders alongside the block models' own
     * "render_type": "minecraft:translucent".
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.WALL_RESTRAINT.get(), RenderType.translucent()));
    }

    /**
     * Registers the Shock Collar's {@code cuffedaddon:bound} item property, which
     * is what its model JSON's {@code overrides} block switches on to swap
     * between the collar texture and the remote texture.
     *
     * <h2>Why this is in LOAD COMPLETE and not client setup</h2>
     * It was in {@code FMLClientSetupEvent} in 1.4.33-1.4.36 and caused a
     * launch crash, intermittently:
     *
     * <pre>
     * Cuffed (cuffed) encountered an error during the sided_setup event phase
     * java.util.ConcurrentModificationException
     *   at java.util.HashMap.computeIfAbsent
     *   at net.minecraft.client.renderer.item.ItemProperties.register
     *   at com.lazrproductions.cuffed.CuffedMod$ClientModEvents.onClientSetup
     * </pre>
     *
     * {@code ItemProperties.register} writes into a plain static HashMap with no
     * synchronisation. Cuffed calls it DIRECTLY from its own client-setup
     * handler, which Forge runs on a ForkJoinPool worker - no
     * {@code enqueueWork}. Our call was correctly wrapped in
     * {@code enqueueWork}, but that only moves it to the main thread; the main
     * thread drains that queue WHILE the parallel handlers are still running.
     * So both threads hit {@code computeIfAbsent} on the same map and one of
     * them blew up. Being a race, it only fired on some launches.
     *
     * <p>Since Cuffed's side can't be changed, the fix is to stop overlapping
     * with it. Forge finishes every mod's {@code FMLClientSetupEvent} before it
     * dispatches {@code FMLLoadCompleteEvent}, so by the time this runs,
     * Cuffed's registration is long done and the map has a single writer again.
     * This is still well before model baking, which happens on the first
     * resource reload, so the override resolves normally.
     *
     * <p><b>Don't move this back into client setup</b>, and treat any other
     * non-thread-safe vanilla client registry the same way if Cuffed also
     * touches it. The Reinforced Bow's two draw-animation properties (1.6.0)
     * are registered here for exactly the same reason and must stay here too.
     */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.SHOCK_COLLAR.get(),
                    ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "bound"),
                    (stack, level, entity, seed) -> ShockCollarItem.isBound(stack) ? 1.0F : 0.0F);

            // Reinforced Bow (1.6.0) - the two properties that drive a bow's
            // three-stage draw animation. Vanilla registers "pulling" and
            // "pull" against Items.BOW SPECIFICALLY (in ItemProperties'
            // static block), not against BowItem as a class, so a custom bow
            // gets no draw animation at all unless it registers its own - the
            // item would just sit there unbent while being drawn.
            //
            // Both are registered under the MINECRAFT namespace on purpose.
            // The map is keyed per item AND per property id, so there is no
            // clash with vanilla's bow, and using minecraft:pull /
            // minecraft:pulling lets reinforced_bow.json's overrides block use
            // the plain unprefixed predicate names a bow model normally uses -
            // which in turn means the model file is a byte-for-byte copy of
            // vanilla's bow.json apart from its texture paths.
            //
            // These two bodies are vanilla's, unchanged.
            ItemProperties.register(
                    ModItems.REINFORCED_BOW.get(),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null) {
                            return 0.0F;
                        }
                        return entity.getUseItem() != stack
                                ? 0.0F
                                : (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0F;
                    });
            ItemProperties.register(
                    ModItems.REINFORCED_BOW.get(),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "pulling"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }

    /**
     * The Shock Collar's HUD (slot icon + durability bar, and the full-screen
     * Electrization overlay) can't go through CompactHudRenderer - that's
     * driven by a mixin on Cuffed's own per-restraint-slot rendering, which
     * never fires for a slot Cuffed doesn't know about. So it's registered as
     * its own Forge overlay instead, drawn just above the hotbar.
     */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(),
                ShockCollarHudRenderer.OVERLAY_ID, new ShockCollarHudRenderer());
    }

    /**
     * Registering the KeyMapping instances is this mod-bus event's job;
     * the fields themselves and the per-tick consumeClick() polling live
     * on CuffedAddonKeybinds (regular Forge bus) - see that class's own
     * doc for why the two are split across classes/buses.
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CuffedAddonKeybinds.RELEASE_ARMS);
        event.register(CuffedAddonKeybinds.RELEASE_HEAD);
        event.register(CuffedAddonKeybinds.RELEASE_LEGS);
    }
}

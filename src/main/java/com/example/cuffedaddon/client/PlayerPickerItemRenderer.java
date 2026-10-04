package com.example.cuffedaddon.client;

import com.example.cuffedaddon.items.PlayerPickerItem;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renderer registration + several API details CORRECTED against [stated]'s
 * actual compiler output (real errors, not guesses) - see /areas/cuffedaddon.md
 * for the full list. Confirmed via Forge's own docs + mappings.dev, not just
 * re-guessed: BlockEntityWithoutLevelRenderer lives directly in
 * net.minecraft.client.renderer (not a .blockentity subpackage), and
 * renderByItem's second parameter is net.minecraft.world.item.ItemDisplayContext
 * - that type has been used since well before 1.20.1 (confirmed against
 * Forge's own 1.19.x docs), my original "that's a 1.20.2+ rename" note was
 * simply wrong. Registration itself also moved - see PlayerPickerItem's own
 * initializeClient override, since RegisterClientExtensionsEvent (what this
 * was originally registered through, in ClientModEvents) doesn't exist at
 * all in this project's actual Forge version.
 *
 * Still not compiled/run anywhere - if anything ELSE in this class doesn't
 * compile, paste the exact error; SkinManager#registerSkins' precise
 * signature and ModelLayers.PLAYER's exact constant name are the two
 * remaining least-certain details.
 *
 * Always draws the same 3D player-figure shape (vanilla's own PlayerModel -
 * NOT the supplied resourcepack's hand-built cuboid totem model; using the
 * real model directly is more robust since its geometry/UVs are already
 * guaranteed to match the skin layout exactly, for both arm-width variants,
 * for free) - only the bound texture and (as of 1.4.19) the wide-vs-slim
 * model instance differ between the generic/idle state and a loaded one.
 */
public class PlayerPickerItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static PlayerPickerItemRenderer instance;

    public static PlayerPickerItemRenderer instance() {
        if (instance == null) {
            instance = new PlayerPickerItemRenderer();
        }
        return instance;
    }

    // FIXED LAST ROUND: this used to point at textures/item/player_picker.png
    // (copied from the supplied resourcepack's totem_of_undying.png) and got
    // bound directly onto the real PlayerModel's cuboids. That texture is the
    // GOLDEN TOTEM'S OWN texture, laid out for the totem's own hand-built
    // item-model UVs - NOT a real 64x64 Minecraft skin layout (head/body/arm/
    // leg regions in the standard positions). Applying it to PlayerModel's
    // real body-part UVs sampled essentially random/wrong regions of that
    // image, which is exactly what [stated] saw in-game: a garbled golden
    // blob, not a player shape. Last round's fix used
    // DefaultPlayerSkin.getDefaultSkin(UUID) with a fixed placeholder UUID as
    // a stopgap real-skin-format texture.
    //
    // THIS ROUND: [stated] supplied a real, specific 64x64 skin PNG to use as
    // the permanent generic/no-player-picked texture instead of that
    // placeholder-UUID stopgap. Shipped at
    // assets/cuffedaddon/textures/entity/player_picker/generic_skin.png -
    // a real skin-layout image, not a totem/item texture, so it binds onto
    // PlayerModel's body-part UVs correctly.
    private static final ResourceLocation GENERIC_SKIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            com.example.cuffedaddon.CuffedAddon.MODID, "textures/entity/player_picker/generic_skin.png");

    // Populated asynchronously by SkinManager#registerSkins as real skins
    // resolve; until an entry lands, PENDING falls back to a vanilla
    // default (Steve/Alex by UUID hash) rather than rendering broken.
    private static final Map<UUID, ResourceLocation> RESOLVED_SKINS = new ConcurrentHashMap<>();

    // FIXED THIS ROUND (1.4.19): slim-arm ("Alex") detection, previously
    // deferred. Populated the same way as RESOLVED_SKINS - the
    // registerSkins callback's MinecraftProfileTexture carries a real
    // "model" metadata key ("slim" or absent/"default") for the ACTUAL
    // chosen skin, which is authoritative; until that lands, falls back to
    // DefaultPlayerSkin.getSkinModelName(UUID) - the same UUID-hash-based
    // heuristic vanilla itself uses to guess arm width before a skin is
    // resolved, so it already matches most real players correctly even
    // for the generic/pending state.
    private static final Map<UUID, Boolean> RESOLVED_SLIM = new ConcurrentHashMap<>();

    // Two separate PlayerModel instances are required - the "slim" flag
    // passed to PlayerModel's constructor is baked into which ModelLayer
    // it renders (arm cuboid width differs), it isn't a per-frame switch.
    private final PlayerModel<net.minecraft.world.entity.player.Player> playerModelWide;
    private final PlayerModel<net.minecraft.world.entity.player.Player> playerModelSlim;

    private PlayerPickerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.playerModelWide = new PlayerModel<>(
                Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        this.playerModelSlim = new PlayerModel<>(
                Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay) {
        GameProfile profile = usableProfile(PlayerPickerItem.getPickedProfile(stack));
        ResourceLocation texture = profile != null ? resolveSkin(profile) : resolveGenericSkin();
        boolean slim = profile != null && resolveSlim(profile);
        PlayerModel<net.minecraft.world.entity.player.Player> playerModel = slim ? playerModelSlim : playerModelWide;

        poseStack.pushPose();
        if (transformType == ItemDisplayContext.GUI) {
            // NEW THIS ROUND (1.4.20): [stated] wants the INVENTORY icon
            // specifically (not the held-in-hand look) slightly bigger and
            // rotated 180 - it was showing the model's back, since the
            // same transform used everywhere else was never tuned
            // separately for the GUI context. This branch only applies to
            // ItemDisplayContext.GUI (inventory/hotbar/JEI icon); hand
            // contexts use the other branch below.
            poseStack.translate(0.5, 0.2, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            // "Slightly scaled up" from the 0.4375 used everywhere else.
            // 1.4.23: [stated] confirmed the 1.4.22 bump (0.4375 -> 0.5) was
            // a real improvement and asked for MORE of the same adjustment -
            // applying the same delta again (0.5 -> 0.5625).
            poseStack.scale(-0.5625F, -0.5625F, 0.5625F);
            poseStack.translate(0.0, -1.5, 0.0);
        } else {
            // Held-in-hand (first/third person, either hand) and
            // ground/fixed contexts. FIXED THIS ROUND: further upward
            // nudge (0.2 -> 0.4, was "still all the way down" at 0.2 per
            // [stated]) plus a left turn, so the figure isn't rendered
            // facing flat at the camera - [stated] asked for this only for
            // the hand-held look, not the inventory icon (see the GUI
            // branch above). The rotation is applied in the same "real"
            // translate-space as the Y nudge, right after it and before
            // the mirror scale below, so it turns the model as a whole
            // rather than interacting with the mirror's flipped chirality.
            // 1.4.23: [stated] confirmed the 1.4.22 direction/amount (30
            // degrees, left turn) was a real improvement and asked for MORE
            // of the same - same left-turn direction, 90 more degrees, so
            // the figure faces the holder.
            poseStack.translate(0.5, 0.4, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(120.0F));
            // X and Y both negated, matching vanilla's own
            // LivingEntityRenderer#render (poseStack.scale(-1, -1, 1))
            // before calling any HumanoidModel#renderToBuffer - negating
            // only Y is a MIRROR (determinant -1, inverts chirality/
            // winding), negating both is orientation-preserving (a proper
            // 180-degree spin).
            poseStack.scale(-0.4375F, -0.4375F, 0.4375F);
            poseStack.translate(0.0, -1.5, 0.0);
        }

        var consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        playerModel.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }

    /**
     * NEW IN 1.4.40 - CRASH FIX. Normalises whatever profile came out of the
     * item's NBT into one that is safe to render, or null to fall back to the
     * generic skin.
     *
     * <p><b>The crash this fixes.</b> {@code NbtUtils.readGameProfile} happily
     * returns a profile with a NULL id when the NBT has a {@code Name} but no
     * {@code Id} - and {@link DefaultPlayerSkin#getDefaultSkin(UUID)} calls
     * {@code hashCode()} on that id to pick Steve vs Alex, so it threw
     * {@code NullPointerException: Cannot invoke "java.util.UUID.hashCode()"}
     * straight out of the render thread. Because it is a RENDERER, it fired on
     * every single frame the stack was visible, which meant a client that could
     * not open its inventory or look at the item on the ground without crashing
     * again immediately. [stated] hit this from a hand-written
     * {@code /give ... player_picker{PickedProfile:{Name:"..."}}}, but any
     * incomplete or third-party-written stack reaches it the same way, so the
     * guard belongs here regardless of how the NBT got that way.
     *
     * <p>Two other methods would have thrown on the same null id -
     * {@link DefaultPlayerSkin#getSkinModelName(UUID)} in {@link #resolveSlim},
     * and {@code SkinManager#registerSkins} itself, which compares the
     * profile's id against the local user's. Repairing the profile once, here,
     * covers all three rather than sprinkling null checks down the chain.
     *
     * <p>The repair derives the same OFFLINE uuid vanilla uses for an
     * unauthenticated name ({@code UUID.nameUUIDFromBytes("OfflinePlayer:" +
     * name)} - spelled out rather than calling
     * {@code UUIDUtil.createOfflinePlayerUUID} so this has no dependency on
     * that helper's exact mapped name). That gives a stable, non-null id, so
     * such a stack renders as a plain default skin figure instead of crashing.
     * With neither an id nor a name there is nothing to work from, so the item
     * renders in its generic/idle state.
     */
    @Nullable
    private static GameProfile usableProfile(@Nullable GameProfile profile) {
        if (profile == null || profile.getId() != null) {
            return profile;
        }
        String name = profile.getName();
        if (name == null || name.isEmpty()) {
            return null;
        }
        return REPAIRED_PROFILES.computeIfAbsent(name,
                n -> new GameProfile(offlineUUID(n), n));
    }

    /** Cached so a broken stack doesn't allocate a fresh GameProfile every frame. */
    private static final Map<String, GameProfile> REPAIRED_PROFILES = new HashMap<>();

    private static UUID offlineUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * How many times to ask for one profile's skin before giving up on it, and
     * how long to wait between attempts.
     *
     * <h2>Why this is bounded (1.5.22)</h2>
     * This used to call {@code registerSkins} on every frame the cache missed,
     * on the reasoning that SkinManager caches internally so repeating it is
     * free. That holds on an ONLINE-MODE server, where the captured
     * {@code GameProfile} was written to NBT complete with its signed textures
     * property: the very first call resolves, {@link #RESOLVED_SKINS} fills, and
     * nothing asks again.
     *
     * <p>It does not hold with {@code online-mode=false}. An unauthenticated
     * profile carries NO textures property and its uuid is the derived offline
     * one (see {@link #offlineUUID}), which belongs to no Mojang account - so
     * the lookup comes back empty, the SKIN callback never fires,
     * {@link #RESOLVED_SKINS} never fills, and the next frame asked again.
     * Holding a Player Picker with somebody in it meant a fresh resolution
     * attempt sixty times a second, for as long as the item was on screen.
     *
     * <p>Three attempts five seconds apart keeps a transient failure on a normal
     * server recoverable while making the never-resolves case cost fifteen
     * seconds and then stop. A profile that gives up renders on the default
     * Steve/Alex skin, which is what a cracked server can show anyway.
     */
    private static final int MAX_SKIN_ATTEMPTS = 3;
    private static final long SKIN_RETRY_MILLIS = 5_000L;

    /** Per profile: {attempts so far, last attempt's timestamp}. Render thread only. */
    private static final Map<UUID, long[]> SKIN_ATTEMPTS = new HashMap<>();

    private static ResourceLocation resolveSkin(GameProfile profile) {
        UUID id = profile.getId();
        ResourceLocation cached = RESOLVED_SKINS.get(id);
        if (cached != null) {
            return cached;
        }

        long now = Util.getMillis();
        long[] attempt = SKIN_ATTEMPTS.computeIfAbsent(id, k -> new long[] {0L, Long.MIN_VALUE});
        if (attempt[0] >= MAX_SKIN_ATTEMPTS || now - attempt[1] < SKIN_RETRY_MILLIS) {
            return DefaultPlayerSkin.getDefaultSkin(id);
        }
        attempt[0]++;
        attempt[1] = now;

        // Kick off async resolution; render the default skin in the meantime.
        // Same callback also records the resolved skin's real arm-width
        // model ("slim"/"default" via MinecraftProfileTexture#getMetadata
        // ("model")) into RESOLVED_SLIM below - this is the authoritative
        // value (the player's actual chosen skin type), not just the
        // UUID-hash heuristic used as a fallback until it lands.
        Minecraft.getInstance().getSkinManager().registerSkins(profile,
                (type, location, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        RESOLVED_SKINS.put(id, location);
                        RESOLVED_SLIM.put(id, "slim".equals(texture.getMetadata("model")));
                    }
                }, false);

        return DefaultPlayerSkin.getDefaultSkin(id);
    }

    /**
     * NEW THIS ROUND (1.4.19): [stated] asked whether a picked player with
     * a slim ("Alex"-style) skin can render with the correspondingly
     * slim-armed model instead of always the wide/"Steve" one. Uses the
     * real resolved skin's model metadata once available (see resolveSkin
     * above); until then falls back to DefaultPlayerSkin.getSkinModelName
     * (UUID) - the same UUID-parity heuristic vanilla itself uses to guess
     * arm width before a skin has loaded, so the pending/generic-state
     * guess already matches most real players.
     */
    private static boolean resolveSlim(GameProfile profile) {
        UUID id = profile.getId();
        Boolean cached = RESOLVED_SLIM.get(id);
        if (cached != null) {
            return cached;
        }
        return "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
    }

    /**
     * The generic/no-player-picked state - [stated]'s own supplied skin
     * texture, shipped as a real asset in this jar (see GENERIC_SKIN_TEXTURE
     * above), not a runtime-resolved one.
     */
    private static ResourceLocation resolveGenericSkin() {
        return GENERIC_SKIN_TEXTURE;
    }
}

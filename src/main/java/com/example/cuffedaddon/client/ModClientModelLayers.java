package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.client.model.HandcuffsArmsPoseModel;
import com.example.cuffedaddon.client.model.HandcuffsLegsPoseModel;
import com.example.cuffedaddon.client.model.KeyNecklaceModel;
import com.example.cuffedaddon.client.model.RopeArmsWrapModel;
import com.example.cuffedaddon.client.model.RopeHeadWrapModel;
import com.example.cuffedaddon.client.model.RopeLegsWrapModel;
import com.example.cuffedaddon.client.model.ShockCollarModel;
import com.example.cuffedaddon.client.model.StraitjacketArmsWrapModel;
import com.example.cuffedaddon.client.model.StraitjacketLegsWrapModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * ModelLayerLocations for the addon's own additive render-layer overlays,
 * separate from anything Cuffed itself registers (com.lazrproductions.
 * cuffed.init.ModModelLayers, e.g. DUCK_TAPE_ARM_LAYER) - these are our own
 * addon-owned layer definitions, namespaced under "cuffedaddon".
 */
public class ModClientModelLayers {
    public static final ModelLayerLocation ROPE_ARMS_WRAP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "rope_arms_wrap_layer"), "main");
    // 1.4.41: slim counterpart, same reason the straitjacket one exists at
    // 1.4.40 - vanilla's arm box differs between the two skin variants and a
    // single box cannot fit both. See RopeArmsWrapModel's own doc. Rope's LEGS
    // and HEAD wraps need no slim counterparts (vanilla's leg and head boxes
    // are identical in both variants).
    public static final ModelLayerLocation ROPE_ARMS_WRAP_SLIM_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "rope_arms_wrap_slim_layer"), "main");
    public static final ModelLayerLocation ROPE_LEGS_WRAP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "rope_legs_wrap_layer"), "main");
    public static final ModelLayerLocation ROPE_HEAD_WRAP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "rope_head_wrap_layer"), "main");

    public static final ModelLayerLocation HANDCUFFS_ARMS_POSE_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "handcuffs_arms_pose_layer"), "main");
    public static final ModelLayerLocation HANDCUFFS_LEGS_POSE_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "handcuffs_legs_pose_layer"), "main");

    // Straitjacket - see StraitjacketArmsWrapModel/StraitjacketLegsWrapModel
    // for why these are separate from Rope's own wrap layers above (bigger
    // inflate, own textures). The arms/legs restraints themselves reuse
    // HANDCUFFS_ARMS_POSE_LAYER/HANDCUFFS_LEGS_POSE_LAYER above (already
    // registered) for their own getModelInterface(), no new layer needed
    // for that part. The head has NO layer of its own here at all - per
    // [stated]'s explicit request it's wired the same way as Bundle/Sleep
    // Mask/the 4 combos (Cuffed's own BundleModel + Cuffed's own already-
    // registered ModModelLayers.BUNDLE_LAYER, see StraitjacketHeadRestraint),
    // not a custom box like the arms/legs wraps - an earlier StraitjacketHeadWrapModel
    // attempt at a custom head box (0.3f inflate) sat nowhere near covering
    // the vanilla hat/second-skin layer (which sits further out on the head
    // than the body's own second layer), so this reuses the SAME geometry
    // that already reliably covers every other head restraint instead of
    // re-deriving a custom box.
    public static final ModelLayerLocation STRAITJACKET_ARMS_WRAP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "straitjacket_arms_wrap_layer"), "main");
    // 1.4.40: a second bake of the arms wrap with vanilla's 3-wide "Alex" arm
    // box, because the two skin variants genuinely need different geometry -
    // see StraitjacketArmsWrapModel's own doc for the bug this fixes. The LEGS
    // wrap needs no slim counterpart: vanilla's leg box is 4x12x4 in both
    // variants, and that model already matches it.
    public static final ModelLayerLocation STRAITJACKET_ARMS_WRAP_SLIM_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "straitjacket_arms_wrap_slim_layer"), "main");
    public static final ModelLayerLocation STRAITJACKET_LEGS_WRAP_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "straitjacket_legs_wrap_layer"), "main");

    // Shock Collar (1.4.32) - an additive TORSO-ONLY wrap in the same family as
    // the Rope and Straitjacket wraps above (per [stated]'s explicit request),
    // at a 0.4f inflate so it sits outside both of them. No arms/legs/head
    // geometry: the visible collar band is painted into the top rows of the
    // body's UV footprint rather than cut into the mesh. See ShockCollarModel
    // for the documented UV layout.
    public static final ModelLayerLocation SHOCK_COLLAR_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "shock_collar_layer"), "main");

    // Key Necklace (1.6.5) - the SAME torso-only geometry as the collar above,
    // per [stated]'s "same height and texture detail as the shock collar", at
    // a 0.45f inflate so the two can be worn together without z-fighting. Its
    // own ModelLayerLocation rather than reusing the collar's because the
    // inflate differs, which makes it genuinely different geometry - a pure
    // retexture would have shared one (see the project's texture rules).
    public static final ModelLayerLocation KEY_NECKLACE_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "key_necklace_layer"), "main");


    // ------------------------------------------------------- the bake cache

    /**
     * Baked {@link ModelPart} trees, shared by every layer that asks for the
     * same {@link ModelLayerLocation}.
     *
     * <h2>Why this exists (1.5.22)</h2>
     * Fake Players' {@code FakePlayerRendererWrapper#render} constructs a whole
     * new {@code FakePlayerRenderer} <b>every frame, for every visible fake
     * player</b> - see {@code SafeRestraintEntityLayer}'s doc, which found this
     * first. That means {@code FakePlayerRendererMixin}'s {@code <init>}
     * injection, and therefore every layer constructor in it, runs at frame
     * rate. Those constructors between them called {@code context.bakeLayer}
     * SEVEN times (rope arms/legs/head, straitjacket arms/legs, handcuffs
     * arms/legs), and {@code bakeLayer} rebuilds the entire
     * ModelPart/Cube/Polygon/Vertex tree from its LayerDefinition every call.
     * With a few fake players on screen that was thousands of short-lived
     * objects per frame, paid whether or not any of them was wearing anything.
     *
     * <h2>Why sharing one ModelPart between layers is safe</h2>
     * A {@code ModelPart} is mutable - {@code x/y/z}, the three rotations and
     * {@code visible} are written during posing - so sharing one looks alarming
     * at first glance. It is exactly what vanilla already does: a
     * {@code LivingEntityRenderer} holds ONE model instance and poses it for
     * each entity immediately before drawing that entity. Every layer here
     * follows the same order - {@code copyPropertiesTo(model)} and then
     * {@code model.renderToBuffer(...)} back to back, inside one
     * {@code render} call - and {@code renderToBuffer} writes its vertices into
     * the buffer there and then rather than deferring them. So no two entities
     * are ever mid-pose at the same time, and nothing reads a part's state
     * after the next entity has overwritten it.
     *
     * <p>The slim and wide variants stay separate because they are separate
     * {@code ModelLayerLocation}s (see ROPE_ARMS_WRAP_SLIM_LAYER above) - the
     * key already carries that distinction, so an Alex-proportioned wrap can
     * never be handed to a Steve-proportioned renderer.
     *
     * <h2>Invalidation</h2>
     * Keyed on the {@link EntityModelSet} the parts were baked from rather than
     * cleared by an event listener. A resource reload builds a NEW model set and
     * re-fires {@code AddLayers}, so the identity check below misses and the
     * cache rebuilds itself with no listener to register, forget, or get the
     * ordering wrong.
     *
     * <p>Render thread only (both {@code AddLayers} and fake-player rendering),
     * so a plain HashMap is right.
     */
    private static final Map<ModelLayerLocation, ModelPart> BAKED = new HashMap<>();

    /** The model set {@link #BAKED} was filled from; a different one clears it. */
    private static EntityModelSet bakedFrom;

    /**
     * Drop-in replacement for {@code context.bakeLayer(location)} that bakes each
     * layer definition once instead of once per caller.
     */
    public static ModelPart bake(EntityRendererProvider.Context context, ModelLayerLocation location) {
        EntityModelSet modelSet = context.getModelSet();
        if (modelSet != bakedFrom) {
            BAKED.clear();
            bakedFrom = modelSet;
        }
        return BAKED.computeIfAbsent(location, modelSet::bakeLayer);
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SHOCK_COLLAR_LAYER, ShockCollarModel::createBodyLayer);
        event.registerLayerDefinition(KEY_NECKLACE_LAYER, KeyNecklaceModel::createBodyLayer);
        event.registerLayerDefinition(ROPE_ARMS_WRAP_LAYER,
                () -> RopeArmsWrapModel.createBodyLayer(false));
        event.registerLayerDefinition(ROPE_ARMS_WRAP_SLIM_LAYER,
                () -> RopeArmsWrapModel.createBodyLayer(true));
        event.registerLayerDefinition(ROPE_LEGS_WRAP_LAYER, RopeLegsWrapModel::createBodyLayer);
        event.registerLayerDefinition(ROPE_HEAD_WRAP_LAYER, RopeHeadWrapModel::createBodyLayer);
        event.registerLayerDefinition(HANDCUFFS_ARMS_POSE_LAYER, HandcuffsArmsPoseModel::createBodyLayer);
        event.registerLayerDefinition(HANDCUFFS_LEGS_POSE_LAYER, HandcuffsLegsPoseModel::createBodyLayer);
        event.registerLayerDefinition(STRAITJACKET_ARMS_WRAP_LAYER,
                () -> StraitjacketArmsWrapModel.createBodyLayer(false));
        event.registerLayerDefinition(STRAITJACKET_ARMS_WRAP_SLIM_LAYER,
                () -> StraitjacketArmsWrapModel.createBodyLayer(true));
        event.registerLayerDefinition(STRAITJACKET_LEGS_WRAP_LAYER, StraitjacketLegsWrapModel::createBodyLayer);
    }
}

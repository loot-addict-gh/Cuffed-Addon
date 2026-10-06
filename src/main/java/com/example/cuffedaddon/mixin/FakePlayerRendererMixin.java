package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.client.layer.KeyNecklaceLayer;
import com.example.cuffedaddon.client.layer.LiePoseHandcuffsLayer;
import com.example.cuffedaddon.client.layer.RopeWrapEntityLayer;
import com.example.cuffedaddon.client.layer.SafeRestraintEntityLayer;
import com.example.cuffedaddon.client.layer.StraitjacketWrapEntityLayer;
import com.lazrproductions.cuffed.restraints.client.layer.PilloryEntityLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attaches the restraint render layers to Fake Players' REAL renderer.
 *
 * <h2>Why EntityRenderersEvent.AddLayers could never have worked here</h2>
 * 1.5.0 and 1.5.1 tried to add these layers the normal way, through
 * {@code AddLayers#getRenderer}. That was never going to draw anything, for two
 * independent reasons, and this is worth recording because it is not a shape you
 * meet often.
 *
 * <p>What Fake Players registers for its entity is not the renderer that draws it.
 * It registers {@code FakePlayerRendererWrapper}, which:
 * <pre>
 *   super(context, null, 0.5f);                 // its model is NULL
 *   public void render(entity, ...) {
 *       FakePlayerRenderer renderer = new FakePlayerRenderer(context, entity.isSlim());
 *       renderer.render(entity, ...);           // never calls super.render()
 *   }
 * </pre>
 * So firstly, {@code getModel()} on the registered renderer returns null, and
 * secondly - the fatal part - the wrapper's {@code render} never calls
 * {@code LivingEntityRenderer#render}, which is the method that iterates
 * {@code this.layers}. <b>Any layer added to the registered renderer is simply
 * never drawn.</b> The real renderer, with the real model, is constructed fresh on
 * every frame and thrown away, so there is no persistent object to attach to from
 * outside at all.
 *
 * <p>Hence this mixin: it hooks the inner renderer's own constructor, which is the
 * only moment that object is reachable. Injecting there also means the layers land
 * on an instance whose {@code render} is the inherited
 * {@code LivingEntityRenderer#render}, so they are actually iterated.
 *
 * <h2>A bonus: slimness comes for free</h2>
 * That constructor takes the {@code slim} flag (the wrapper passes
 * {@code entity.isSlim()}), so the wrap layers can finally be built for the right
 * arm width per entity. This is what the AddLayers approach fundamentally could
 * not do - one renderer for the whole entity type meant one baked arm model - and
 * it closes the slim-arm gap noted in 1.5.0 as a side effect.
 *
 * <h2>The cost, stated plainly</h2>
 * Because their renderer is rebuilt every frame, these layers are rebuilt every
 * frame too, and building a wrap layer bakes a model. That is wasteful. It is
 * however proportional to what that mod already does - it rebuilds a whole
 * renderer and bakes a full PlayerModel per frame per fake player regardless - so
 * this roughly adds to an existing cost rather than introducing a new kind of one.
 * If fake players ever visibly cost frames, caching the baked models by layer
 * location is the fix, and it belongs in the layer classes rather than here.
 *
 * <p>The superclass declaration plus the unused constructor is the standard Mixin
 * idiom for reaching an inherited protected member ({@code addLayer}); Mixin
 * discards the constructor. Cuffed's own {@code PlayerMixin extends LivingEntity}
 * does exactly the same thing.
 */
@Mixin(targets = "dev.duzo.players.client.renderers.FakePlayerRenderer", remap = false)
public abstract class FakePlayerRendererMixin
        extends LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> {

    protected FakePlayerRendererMixin(EntityRendererProvider.Context context,
                                       HumanoidModel<LivingEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void cuffedaddon$addRestraintLayers(EntityRendererProvider.Context context, boolean slim,
                                                 CallbackInfo ci) {
        // Cuffed's own layer draws every Cuffed-owned restraint (handcuffs,
        // shackles, duct tape, ...). It goes on WRAPPED - see
        // SafeRestraintEntityLayer for why adding it bare would be a crash.
        this.addLayer(new SafeRestraintEntityLayer<>(this, context));
        this.addLayer(new RopeWrapEntityLayer<>(this, context, slim));
        this.addLayer(new StraitjacketWrapEntityLayer<>(this, context, slim));

        // 1.5.11: the Bed/Wall Restraint cosmetic cuffs, so a bed- or
        // wall-restrained fake player wears the wrist and ankle cuffs a restrained
        // player does. The layer gates on this addon's own pose capabilities (both
        // widened to LivingEntity in the same round), so it draws nothing on an
        // unposed fake player.
        this.addLayer(new LiePoseHandcuffsLayer<>(this, context));

        // Cuffed's OWN pillory-on-the-head layer, the one its PlayerRendererMixin
        // adds to every PlayerRenderer. Nothing to do with the pillory BLOCK - it
        // draws the wearable PilloryRestraint (Cuffed registers the pillory block
        // item as a head restraint), whose RestraintModelInterface returns nulls, so
        // SafeRestraintEntityLayer above draws nothing for it and without this a
        // pillory applied to a fake player's head was invisible. Same class Cuffed
        // uses, so it looks identical.
        this.addLayer(new PilloryEntityLayer<>(this, context.getItemInHandRenderer()));

        // 1.6.5: the Key Necklace. It gates on this addon's own NECKLACED
        // capability, which is attached to their entity as well as to players, so
        // it draws nothing on a fake player that is not wearing one. The Shock
        // Collar's layer is deliberately NOT here - a collar cannot be applied to
        // a fake player at all, so there would be nothing for it to draw.
        this.addLayer(new KeyNecklaceLayer<>(this, context));
    }
}

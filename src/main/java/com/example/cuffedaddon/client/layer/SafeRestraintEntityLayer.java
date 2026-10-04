package com.example.cuffedaddon.client.layer;

import com.lazrproductions.cuffed.entity.base.IRestrainableEntity;
import com.lazrproductions.cuffed.restraints.client.layer.RestraintEntityLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cuffed's {@link RestraintEntityLayer} with an {@code instanceof} guard in front
 * of it.
 *
 * <h2>Why this exists (1.5.1 - a crash this addon would have caused)</h2>
 * Cuffed's layer opens with an <b>unguarded</b> cast:
 * <pre>IRestrainableEntity res = (IRestrainableEntity) entity;</pre>
 * That is perfectly safe where Cuffed itself installs the layer, because it only
 * ever adds it to the vanilla {@code PlayerRenderer} and every {@code Player} is
 * an {@code IRestrainableEntity} via Cuffed's own {@code PlayerMixin}. It is NOT
 * safe where this addon installs it - on Fake Players' entity renderer - because
 * there the interface arrives from {@code FakePlayerEntityMixin}, and a mixin can
 * fail to apply: the mod may be absent, its internals may have shifted, or the
 * config may simply not be registered in the build. In any of those cases the
 * unguarded cast is a {@code ClassCastException} thrown from the render thread,
 * on every frame the entity is visible - the same shape of fault as the Player
 * Picker renderer crash in 1.4.40, and just as hard to get out of.
 *
 * <p>So the layer is never added bare. This wrapper checks first and draws
 * nothing if the interface is missing, which turns "the mixin did not apply" from
 * a crash into restraints quietly not rendering - recoverable, and diagnosable
 * from the one-line report {@code FakePlayerEvents} logs on the first fake player
 * to load.
 *
 * <p>This addon's own wrap layers already guard themselves the same way, which is
 * why only Cuffed's needs wrapping.
 */
public class SafeRestraintEntityLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private final RenderLayerParent<T, M> parent;
    private final EntityRendererProvider.Context context;

    /**
     * Built on first use, not in the constructor.
     *
     * <h2>Why laziness matters a great deal here</h2>
     * Fake Players' {@code FakePlayerRendererWrapper#render} constructs a whole
     * new {@code FakePlayerRenderer} <b>every frame, for every visible fake
     * player</b> - so everything that renderer's constructor does, this addon's
     * added layers included, happens at frame rate rather than once at startup.
     *
     * <p>And Cuffed's {@code RestraintEntityLayer} constructor is not cheap: it
     * walks {@code RestraintAPI.Registries.getAllRestraints()} and, for each
     * restraint, bakes a model layer and reflectively instantiates its model
     * class. With Cuffed's own restraints plus this addon's that is around twenty
     * model-tree bakes. Doing that per frame per entity was a real cost, and it
     * was paid for every fake player on screen whether or not it was wearing
     * anything.
     *
     * <p>Deferring it to the first frame an actually-restrained fake player is
     * drawn removes that cost entirely for the ordinary case - an unrestrained
     * fake player now costs nothing beyond the guard below. It does not remove it
     * for a restrained one, because the whole renderer is still rebuilt each
     * frame; only caching the renderer itself can fix that, and that needs a hook
     * inside their wrapper.
     */
    @Nullable
    private RestraintEntityLayer<T, M> delegate;

    public SafeRestraintEntityLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.parent = parent;
        this.context = context;
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight,
                        @Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTick,
                        float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof IRestrainableEntity restrainable)) {
            return;
        }
        // Nothing equipped means nothing for Cuffed's layer to draw, so there is
        // no reason to have paid for building it.
        if (!restrainable.isRestrained()) {
            return;
        }
        if (delegate == null) {
            delegate = new RestraintEntityLayer<>(parent, context);
        }
        delegate.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick,
                ageInTicks, netHeadYaw, headPitch);
    }
}

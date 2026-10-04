package com.example.cuffedaddon.client;

import com.example.cuffedaddon.entity.AbstractReinforcedArrow;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws this addon's arrows in flight.
 *
 * <p>Vanilla's {@code ArrowRenderer} already contains the entire arrow render -
 * the crossed-quad geometry, the pitch/yaw from motion, the shake wobble - and
 * asks its subclass for nothing but a texture, exactly as
 * {@code TippableArrowRenderer} does for vanilla arrows. So this is a texture
 * and nothing else, and one parameterised class covers both arrows rather than
 * two near-identical ones.
 *
 * <p>The texture is a full {@link ResourceLocation} rather than a name under
 * this mod's namespace, because at 1.6.1 only ONE of the two arrows wants an
 * addon texture. [stated] asked for "a custom in flight texture for the
 * restraint arrows only": the Arrow of Restraint gets
 * {@code cuffedaddon:textures/entity/projectiles/arrow_of_restraint.png}, a
 * copy of vanilla's arrow for them to repaint, while the Arrow of
 * Electrization points straight at vanilla's own
 * {@code minecraft:textures/entity/projectiles/tipped_arrow.png} and ships no
 * file of its own - it is meant to read as a tipped arrow, and an unused copy
 * would only be something to keep in sync.
 *
 * <p>One texture per arrow TYPE, never per payload: every Arrow of Restraint
 * looks the same whatever restraint it holds, per [stated] ("so all types will
 * use the same texture"). That is also why the payload never needs syncing to
 * the client - the renderer never consults it.
 */
public class ReinforcedArrowRenderer<T extends AbstractReinforcedArrow> extends ArrowRenderer<T> {

    private final ResourceLocation texture;

    public ReinforcedArrowRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}

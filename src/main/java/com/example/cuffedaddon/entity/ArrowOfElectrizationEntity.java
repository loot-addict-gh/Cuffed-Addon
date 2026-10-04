package com.example.cuffedaddon.entity;

import com.example.cuffedaddon.collar.ShockCollarUtil;
import com.example.cuffedaddon.init.ModEntityTypes;
import com.example.cuffedaddon.init.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Arrow of Electrization in flight.
 *
 * <p>Carries no state at all - every one of these does the same thing - so
 * unlike {@link ArrowOfRestraintEntity} there is no payload to save, and
 * {@link #getPickupItem()} is a plain new item. Everything else about it is
 * {@link AbstractReinforcedArrow}'s and {@code AbstractArrow}'s.
 *
 * <p><b>Not restricted to players.</b> The restraint arrow has to be, because
 * Cuffed's restraint system is {@code ServerPlayer}-typed end to end; this one
 * does not, because Electrization is an ordinary {@code MobEffect} and
 * {@code ElectrizationEffect} is written against {@code LivingEntity}
 * throughout - its damage, its slowness, its weakness and its mining fatigue
 * all work on a mob. So this shocks whatever it hits. The one part that IS
 * player-only is the delayed nausea and hunger, which needs the collar
 * capability; see {@link ShockCollarUtil#electrify}.
 */
public class ArrowOfElectrizationEntity extends AbstractReinforcedArrow {

    /** Registry/reload constructor. Required by {@code EntityType.Builder.of}. */
    public ArrowOfElectrizationEntity(EntityType<? extends ArrowOfElectrizationEntity> type, Level level) {
        super(type, level);
    }

    public ArrowOfElectrizationEntity(Level level, LivingEntity shooter) {
        super(ModEntityTypes.ARROW_OF_ELECTRIZATION.get(), level, shooter);
    }

    /**
     * The colour of the swirl trail, as RGB.
     *
     * <p>{@code 0x7CAFC6} is vanilla's Swiftness potion colour, per [stated]:
     * the particles should be "the same ones as an arrow of swiftness,
     * specifically". It is also the colour their placeholder item art is tinted
     * with, so the trail and the icon match. Deliberately NOT
     * {@code ModEffects}' own electric yellow - that is the colour of the
     * status effect in the HUD, and a different decision.
     */
    private static final int PARTICLE_COLOR = 0x7CAFC6;

    /** Last tick's position, used to tell flying from landed. See {@link #tick()}. */
    private double lastX;
    private double lastY;
    private double lastZ;

    /**
     * The trailing swirl a tipped arrow leaves, reproduced.
     *
     * <p>Vanilla's {@code Arrow#tick} spawns two {@code ENTITY_EFFECT}
     * particles per tick in flight and one every fifth tick once the arrow has
     * landed, passing the potion colour's R, G and B (0-1) in place of the
     * usual velocity arguments - that particle type reads its tint from them.
     * All of that is copied here; only where the colour comes from differs.
     *
     * <h2>How "landed" is decided, and the 1.6.2 bug that is</h2>
     * Vanilla reads {@code AbstractArrow#inGround}. That field is not reachable
     * from outside {@code net.minecraft.world.entity.projectile} - vanilla's own
     * {@code Arrow} only reads it because it lives in that package - so this
     * class has to answer the question some other way.
     *
     * <p>1.6.2 used the arrow's own velocity, on the reasoning that a landed
     * arrow has none. <b>That was wrong, and it was the whole of [stated]'s
     * "much more frequently than any other tipped arrow does".</b>
     * {@code AbstractArrow#onHitBlock} does <i>not</i> zero the delta movement -
     * it sets it to {@code hitLocation.subtract(position())}, the leftover
     * vector from where the arrow was to where it struck - and nothing clears
     * that afterwards, because the whole movement block of
     * {@code AbstractArrow#tick} is skipped while the arrow is in the ground. So
     * a stuck arrow reports a small but non-zero velocity forever, the test said
     * "flying", and it emitted 2 particles every tick instead of 1 every 5.
     * Exactly ten times too many, which is also why the colour looked wrong:
     * these particles draw at alpha 0.15, so ten times the overlap accumulates
     * into a much darker blue and reads as Slowness rather than Swiftness.
     *
     * <p>The replacement is whether the entity <b>actually moved</b> since last
     * tick, tracked here rather than read from anything. That is the real
     * question, it needs no Minecraft field at all, and it is correct in both
     * directions: an arrow in flight always moves, a stuck one never does, and
     * one that gets knocked loose starts moving again on its own. The threshold
     * absorbs the occasional position resync from the server without being
     * anywhere near a real arrow's travel in one tick.
     *
     * <p>Client-side only, like vanilla's. The particles are cosmetic and are
     * drawn by whoever can see the arrow, so nothing is sent over the network
     * for them - which also means they appear whatever bow fired the arrow,
     * since {@code firedFromReinforcedBow} is server-side state. An arrow
     * loosed from a foreign bow therefore trails and does nothing, which is
     * the honest rendering of an arrow that IS an Arrow of Electrization.
     */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            return;
        }
        double travelled = Math.abs(getX() - lastX) + Math.abs(getY() - lastY) + Math.abs(getZ() - lastZ);
        lastX = getX();
        lastY = getY();
        lastZ = getZ();
        if (travelled > 1.0E-4D) {
            makeParticles(2);
        } else if (tickCount % 5 == 0) {
            makeParticles(1);
        }
    }

    private void makeParticles(int count) {
        double red = (double) (PARTICLE_COLOR >> 16 & 255) / 255.0D;
        double green = (double) (PARTICLE_COLOR >> 8 & 255) / 255.0D;
        double blue = (double) (PARTICLE_COLOR & 255) / 255.0D;
        for (int i = 0; i < count; i++) {
            level().addParticle(ParticleTypes.ENTITY_EFFECT,
                    getRandomX(0.5D), getRandomY(), getRandomZ(0.5D),
                    red, green, blue);
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ModItems.ARROW_OF_ELECTRIZATION.get());
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (level().isClientSide || !firedFromReinforcedBow()) {
            return;
        }
        ShockCollarUtil.electrify(target);
    }
}

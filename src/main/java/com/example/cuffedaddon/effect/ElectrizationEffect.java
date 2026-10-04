package com.example.cuffedaddon.effect;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.init.ModDamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;

/**
 * Electrization - what the Shock Collar actually does to its wearer.
 *
 * <p>[stated]'s spec: "a combination of mining fatigue, weakness and slowness
 * effects. itll also continuously cause damage to the player with the same
 * speed as fire damage, but it will stop at .5 hearts just like poison."
 *
 * <h2>Why the three vanilla effects are re-applied rather than reimplemented</h2>
 * Slowness and Weakness are plain attribute modifiers and could have been
 * declared on this MobEffect directly via {@code addAttributeModifier}. Mining
 * fatigue cannot: in 1.20.1 there is no dig-speed attribute, the slowdown is
 * hardcoded inside {@code Player#getDestroySpeed}, which checks for
 * {@code MobEffects.DIG_SLOWDOWN} specifically. So the only way to get real
 * mining fatigue is for the player to actually have that effect. Rather than
 * split the implementation - two effects faked via attributes, one real - all
 * three are applied for real, which also guarantees the numbers match vanilla
 * exactly at every amplifier.
 *
 * <p>They're applied with {@code showIcon = false} so the player sees only the
 * single Electrization icon in their HUD rather than a row of four, and with
 * {@code showParticles = false} so the wearer isn't wrapped in three colours of
 * swirl.
 *
 * <p>The short refresh duration ({@link #SUB_EFFECT_TICKS}) is deliberate: it's
 * re-applied every tick while Electrization runs, so the sub-effects trail off
 * on their own moments after the shock ends instead of needing explicit
 * cleanup. It's also short enough that it can never out-duration (and therefore
 * never override) a real Slowness/Weakness potion the player drank - vanilla's
 * {@code MobEffectInstance#update} only replaces an existing effect with a
 * stronger amplifier or an equal-amplifier-but-longer duration.
 *
 * <h2>Damage cadence</h2>
 * One half-heart per second - vanilla fire's rate - gated on
 * {@code getHealth() > 1.0F}, which is exactly how vanilla poison declines to
 * land a killing blow.
 *
 * <p>The cadence is keyed off {@code entity.tickCount}, NOT off this effect's
 * own remaining duration. That matters: a held remote re-applies the effect
 * every single tick, pinning its duration at a constant 40, so any
 * {@code duration % 20} test would fire on every tick instead of once a second
 * and shred the wearer roughly twenty times too fast.
 */
public class ElectrizationEffect extends MobEffect {

    /** Half a heart, matching vanilla fire and poison per-hit damage. */
    private static final float DAMAGE_PER_HIT = 1.0F;

    /**
     * Ticks between damage hits. 20 would be once a second, vanilla fire's rate;
     * [stated] asked after the first in-game test for "the damage to be twice as
     * fast", so it's 10 - a half heart every half second.
     *
     * <p>Note this stays comfortably above the 10-tick invulnerability window
     * {@code LivingEntity#hurt} enforces. Going faster than 10 would start
     * silently dropping hits rather than dealing more damage.
     */
    private static final int DAMAGE_INTERVAL_TICKS = 10;

    /** Below this the effect stops damaging, same floor vanilla poison uses. */
    private static final float HEALTH_FLOOR = 1.0F;

    /** How long each re-applied sub-effect is given. See this class's doc. */
    private static final int SUB_EFFECT_TICKS = 10;

    public ElectrizationEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Every tick: the sub-effects need continuous refreshing, and the
        // damage cadence is gated separately inside applyEffectTick.
        return true;
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        applySubEffect(entity, MobEffects.MOVEMENT_SLOWDOWN,
                CuffedAddonServerConfig.ELECTRIZATION_SLOWNESS_AMPLIFIER.get());
        applySubEffect(entity, MobEffects.WEAKNESS,
                CuffedAddonServerConfig.ELECTRIZATION_WEAKNESS_AMPLIFIER.get());
        // Mining Fatigue is deliberately not configurable - per [stated] it
        // doesn't need an amplifier, so it's always level I.
        applySubEffect(entity, MobEffects.DIG_SLOWDOWN, 0);

        if (entity.level().isClientSide()) {
            return;
        }
        if (entity.tickCount % DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }
        if (entity.getHealth() > HEALTH_FLOOR) {
            entity.hurt(ModDamageTypes.electrization(entity), DAMAGE_PER_HIT);
        }
    }

    private static void applySubEffect(LivingEntity entity, MobEffect effect, int amplifier) {
        if (amplifier < 0) {
            // Negative amplifier in config = "don't apply this one at all",
            // so each of the three can be switched off independently.
            return;
        }
        entity.addEffect(new MobEffectInstance(effect, SUB_EFFECT_TICKS, amplifier,
                false, false, false));
    }
}

package com.example.cuffedaddon.enchantment;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.fakeplayer.FakePlayerRestraintUtil;
import com.example.cuffedaddon.fakeplayer.FakePlayerSupport;
import com.example.cuffedaddon.init.ModEnchantments;
import com.lazrproductions.cuffed.api.CuffedAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Repellent (1.5.15): hostile mobs are shoved away from a restrained wearer.
 *
 * <h2>The spec</h2>
 * Range by effective tier - 4, 12 and 20 blocks - with tier 3 additionally
 * repelling the four bosses. Mobs still SPAWN inside the field, by [stated]'s
 * explicit choice ("mobs can still spawn within the range, but they will also be
 * pushed back"), so nothing here touches spawning; it only pushes what is
 * already there. Which tier is active is
 * {@code RestraintEnchantmentUtil#effectiveTier}'s business, not this class's.
 *
 * <h2>What counts as hostile</h2>
 * A {@code LivingEntity} implementing {@code Enemy} - the vanilla marker
 * interface every monster carries, directly or through {@code Monster}, and which
 * also covers the non-Monster hostiles (Ghast, Slime, Phantom, Shulker) that a
 * naive {@code instanceof Monster} would have missed. Passive and neutral mobs
 * are never pushed, so a wolf, an iron golem or an angered piglin walks straight
 * through the field.
 *
 * <p>The query is deliberately over {@code LivingEntity} rather than
 * {@code Mob}, and {@link #isBoss} is ORed into the filter rather than relying on
 * {@code Enemy} alone. Both are guards against the Ender Dragon specifically: it
 * is the one hostile in the game whose place in the class hierarchy is easy to
 * get wrong, and either assumption failing would have made tier 3 silently skip
 * it with nothing in the log to say so.
 *
 * <h2>Bosses</h2>
 * The four [stated] named are matched by class rather than by a tag, because
 * 1.20.1 has no vanilla "is a boss" flag and the {@code forge:bosses} tag is not
 * something every pack populates. The honest limit of that: <b>a modded boss is
 * not treated as a boss</b> unless it extends one of these, so it gets repelled
 * from tier 1 like any other hostile. Say the word if that should become a tag
 * instead.
 *
 * <h2>How the push works, and the one number to tune</h2>
 * A per-application impulse added to the mob's existing velocity, strongest at
 * the wearer's feet and weakest at the edge of the field ({@link #PUSH_AT_EDGE}
 * to {@link #PUSH_AT_CENTRE}), plus a small hop when the mob is on the ground so
 * that ground friction does not simply eat the shove before it moves anything.
 *
 * <p>Adding to velocity rather than overwriting it is deliberate: it leaves the
 * mob's own movement, gravity and fall behaviour intact, so a repelled mob looks
 * like something being blown backwards rather than something being dragged on a
 * rail. It also means the push and the mob's pathing genuinely compete, which is
 * why {@link #PUSH_AT_CENTRE} is set well above walking speed.
 *
 * <p><b>If the feel is wrong, those two constants are the whole knob.</b> They
 * are not config values on purpose - [stated] asked for one config toggle for
 * this enchantment (fake players) and nothing else, and a force-field strength is
 * a balance decision better made once here than left to every world.
 *
 * <h2>Known limit: the Ender Dragon</h2>
 * The dragon's flight is scripted by its own phase system, which recomputes its
 * movement every tick from a flight target, so an added impulse moves it far less
 * than it moves a walking mob. It is pushed - the code does not skip it - but do
 * not expect it to be held off at 20 blocks the way a Wither or a Warden is.
 * Fixing that properly would mean moving the dragon by position rather than by
 * velocity, which risks its flight logic, so it is not done unasked.
 *
 * <h2>Cost</h2>
 * The whole thing is skipped in one field read for any player with no Repellent
 * at all. Beyond that it runs every {@link #INTERVAL_TICKS} ticks rather than
 * every tick, because the expensive part is the area query at tier 3 (a 40-block
 * cube), not the pushing.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class RepellentEvents {

    /** Radius in blocks per effective tier, indexed 1..3. Index 0 is unused. */
    private static final double[] RANGE = {0.0d, 4.0d, 12.0d, 20.0d};

    /** The tier at which bosses start being repelled too. */
    private static final int BOSS_TIER = 3;

    private static final int INTERVAL_TICKS = 2;

    /** Impulse added to a mob standing on top of the wearer. */
    private static final double PUSH_AT_CENTRE = 0.30d;

    /** Impulse added to a mob at the very edge of the field. */
    private static final double PUSH_AT_EDGE = 0.10d;

    /**
     * Small upward nudge, and simultaneously the ceiling on it.
     *
     * <p>Without some lift, ground friction cancels most of a horizontal impulse
     * in the same tick it is applied and mobs grind forwards through the field
     * instead of being pushed out of it. Vanilla knockback solves the same problem
     * the same way.
     *
     * <p>It is applied as "raise upward velocity TOWARDS this value, never past
     * it" rather than "add this much if the mob is standing on the ground". Same
     * effect for a mob on the ground, but it cannot accumulate into flight for a
     * mob already rising, it gently slows a falling one instead of yanking it, and
     * - the reason it is written this way - it needs nothing but
     * {@code getDeltaMovement}, so it does not depend on
     * {@code Entity#onGround()}, whose name changed in 1.20 and could not be
     * verified from any source available in this sandbox.
     */
    private static final double PUSH_UP = 0.12d;

    /**
     * Phantoms are pushed this much harder than everything else, at every tier.
     *
     * <p>[stated], testing on their server: <i>"I want phantoms to be pushed away
     * more on all enchantments, because even at level 1 they can still come close
     * enough and damage the restrained player"</i>.
     *
     * <p>They are a genuinely different problem from a walking mob, for two
     * reasons, and the multiplier alone does not solve either. A phantom attacks
     * by <b>diving</b>, so it arrives with a large velocity of its own that a
     * modest impulse only dents; and its movement is driven by
     * {@code Phantom.PhantomMoveControl}, which recomputes its velocity every tick
     * from its own flight target, so an added impulse is partly overwritten before
     * it can move anything. The dive is why a level 1 field, which comfortably
     * holds off a zombie at the same 4 blocks, does not hold off a phantom.
     *
     * <p>So {@link #push} also cancels a phantom's INBOUND velocity - the part of
     * its motion pointing at the wearer - before adding the push, leaving any
     * sideways motion alone. That is what actually stops the dive: it can still
     * circle, it just cannot convert that into closing distance. Doing it only for
     * phantoms is deliberate; applied to everything it would change the feel of
     * the field for ordinary mobs, which [stated] has already said is fine as it
     * is.
     */
    private static final double PHANTOM_PUSH_MULTIPLIER = 3.0d;

    private RepellentEvents() {
    }

    // ------------------------------------------------------------ real players

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % INTERVAL_TICKS != 0) {
            return;
        }
        int tier = RestraintEnchantmentUtil.repellentTier(
                CuffedAPI.Capabilities.getRestrainableCapability(player), ModEnchantments.REPELLENT.get());
        repel(player, tier);
    }

    // ------------------------------------------------------------ fake players

    /**
     * Fake Players support, behind
     * {@code CuffedAddonServerConfig.ENCHANTMENTS_AFFECT_FAKE_PLAYERS} ("Works On
     * Fake Players", default TRUE as of 1.5.17). That toggle governs Repellent and
     * nothing else now - Restraint Gaze never applies to fake players at all.
     *
     * <p>{@code LivingTickEvent} rather than a Goal, for the same reason
     * {@code FakePlayerStationaryEvents} uses it: it fires for the entity whoever
     * owns it, and nothing about it can be outranked by the fake player's own AI.
     * The mod-absent case costs one boolean - {@code isFakePlayer} short-circuits
     * on {@code isModLoaded}.
     */
    @SubscribeEvent
    public static void onFakePlayerTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.tickCount % INTERVAL_TICKS != 0) {
            return;
        }
        if (!CuffedAddonServerConfig.ENCHANTMENTS_AFFECT_FAKE_PLAYERS.get()) {
            return;
        }
        if (!FakePlayerSupport.isFakePlayer(entity)) {
            return;
        }
        int tier = RestraintEnchantmentUtil.repellentTier(
                FakePlayerRestraintUtil.get(entity), ModEnchantments.REPELLENT.get());
        repel(entity, tier);
    }

    // ------------------------------------------------------------------ shared

    private static void repel(LivingEntity wearer, int tier) {
        if (tier <= 0) {
            return;
        }
        double radius = RANGE[Math.min(tier, RANGE.length - 1)];
        double radiusSqr = radius * radius;
        boolean repelBosses = tier >= BOSS_TIER;

        AABB box = wearer.getBoundingBox().inflate(radius);
        List<LivingEntity> mobs = wearer.level().getEntitiesOfClass(
                LivingEntity.class, box, mob -> mob instanceof Enemy || isBoss(mob));

        for (LivingEntity mob : mobs) {
            if (!mob.isAlive()) {
                continue;
            }
            if (!repelBosses && isBoss(mob)) {
                continue;
            }
            // The query box is a cube; the field is a sphere.
            double distSqr = mob.distanceToSqr(wearer);
            if (distSqr > radiusSqr) {
                continue;
            }
            push(wearer, mob, Math.sqrt(distSqr), radius);
        }
    }

    private static void push(LivingEntity wearer, LivingEntity mob, double distance, double radius) {
        double dx = mob.getX() - wearer.getX();
        double dz = mob.getZ() - wearer.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-4d) {
            // Standing exactly on the wearer: no direction to push along, so pick
            // one rather than dividing by zero or leaving it unpushed.
            dx = 1.0d;
            dz = 0.0d;
            horizontal = 1.0d;
        }

        double falloff = radius <= 0.0d ? 1.0d : Math.max(0.0d, 1.0d - (distance / radius));
        double strength = PUSH_AT_EDGE + (PUSH_AT_CENTRE - PUSH_AT_EDGE) * falloff;

        // Unit vector pointing from the wearer out towards the mob.
        double outX = dx / horizontal;
        double outZ = dz / horizontal;

        Vec3 current = mob.getDeltaMovement();

        if (mob instanceof Phantom) {
            strength *= PHANTOM_PUSH_MULTIPLIER;
            // Kill the part of its velocity aimed at the wearer, keeping the part
            // across. A negative radial component means it is closing in - that is
            // the dive - so subtract exactly that much along the outward axis.
            double radial = current.x * outX + current.z * outZ;
            if (radial < 0.0d) {
                current = new Vec3(current.x - radial * outX, current.y, current.z - radial * outZ);
            }
        }

        double liftedY = current.y < PUSH_UP ? Math.min(current.y + PUSH_UP, PUSH_UP) : current.y;
        mob.setDeltaMovement(
                current.x + outX * strength,
                liftedY,
                current.z + outZ * strength);
        // Entities are position-synced by the server every tick, so unlike a
        // player there is no velocity packet to force here.
    }

    /**
     * The four bosses [stated] named, and nothing else. See this class's doc for
     * why this is a class check rather than an entity-type tag.
     */
    private static boolean isBoss(LivingEntity mob) {
        return mob instanceof Warden
                || mob instanceof WitherBoss
                || mob instanceof EnderDragon
                || mob instanceof ElderGuardian;
    }
}

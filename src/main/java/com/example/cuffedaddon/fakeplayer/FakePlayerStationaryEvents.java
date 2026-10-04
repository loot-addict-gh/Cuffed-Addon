package com.example.cuffedaddon.fakeplayer;

import com.example.cuffedaddon.CuffedAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The per-tick half of the stationary restraints on fake players - holding them
 * where they belong and letting them go when the block holding them is gone. The
 * decisions all live in {@link FakeStationaryUtil}; this is only the wiring.
 *
 * <h2>Why LivingTickEvent and not a Goal</h2>
 * {@code FakePlayerImmobilizeGoal} already stops a stationary fake player walking
 * anywhere, and a goal ticks every tick, so putting the position lock there was the
 * obvious first thought. It is the wrong place: a goal only ticks while it is the
 * one holding its flag, so the lock would silently stop being applied the moment
 * anything outranked it, and goals do not tick at all on an entity whose AI is off
 * - which is precisely what Fake Players' own LAYING pose does
 * ({@code isNoAi()} returns true for it). A restraint that quietly stops holding
 * under either of those is not a restraint. {@code LivingTickEvent} fires from
 * {@code LivingEntity#tick} regardless of both.
 *
 * <h2>Why the whole-level cost is acceptable</h2>
 * This event fires for every living entity in the world every tick, which is why
 * the first thing it does is the cheapest possible test. When Fake Players is not
 * installed, {@code FakePlayerSupport#isFakePlayer} is a single cached boolean read
 * and returns immediately; when it is, it is one registry-key lookup. Both are
 * noise next to what ticking an entity already costs, and it buys a hold that
 * cannot be outranked or switched off.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class FakePlayerStationaryEvents {

    @SubscribeEvent
    public static void onFakePlayerTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !FakePlayerSupport.isFakePlayer(entity)) {
            return;
        }
        FakeStationaryUtil.tick(entity);
    }

    /**
     * A stationary fake player is not knocked off its spot.
     *
     * <p>The pose events do this for a posed player already (see
     * {@code LiePoseEvents#onKnockback}), but that listener is gated on
     * {@code Player} and covers only the lie pose. One listener here covers all
     * three stationary restraints for fake players without touching either of those
     * confirmed-working classes.
     *
     * <p>{@code EntityPushableLiePoseMixin}/{@code EntityPushableWallPoseMixin}
     * (both widened to {@code LivingEntity} this round) already cover entity-vs-
     * entity SHOVING, which is a different mechanism from knockback - shoving comes
     * from {@code Entity#pushEntities}, knockback from an attack or an explosion.
     * The per-tick position correction would put the entity back either way; this
     * just means it never visibly moves in the first place.
     */
    @SubscribeEvent
    public static void onFakePlayerKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!FakePlayerSupport.isFakePlayer(entity)) {
            return;
        }
        if (FakeStationaryUtil.isStationary(entity)) {
            event.setCanceled(true);
        }
    }
}

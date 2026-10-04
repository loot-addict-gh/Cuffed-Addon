package com.example.cuffedaddon.fakeplayer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Everything this addon needs to recognise a Fake Players entity, WITHOUT ever
 * referencing a single class from that mod.
 *
 * <h2>Why it is done by registry name</h2>
 * Fake Players ({@code players}, by duzo) is an OPTIONAL dependency, exactly like
 * Curios. The hard-won rule from the Curios integration applies here too: merely
 * having a class that names an absent mod's types is fine, but <b>executing</b> a
 * code path that resolves one of those types - even a method reference - throws
 * {@code NoClassDefFoundError}. The cheapest way never to trip that is never to
 * name their types at all.
 *
 * <p>So identification is a registry-key comparison against
 * {@code players:fake_player} rather than an {@code instanceof FakePlayerEntity},
 * and the entity is handled through vanilla supertypes ({@code Entity},
 * {@code Mob}, {@code LivingEntity}) everywhere in this package. The one place
 * that genuinely cannot avoid their code is the mixin, and that targets them by
 * STRING (see {@code FakePlayerEntityMixin}) and lives in its own
 * {@code required: false} mixin config so a missing mod is a no-op rather than a
 * crash.
 *
 * <p><b>Consequence worth knowing:</b> this addon never needs Fake Players on its
 * compile classpath, so no build.gradle dependency has to be added for it. The
 * only build change 1.5.0 needs is registering the second mixin config.
 */
public final class FakePlayerSupport {

    public static final String MODID = "players";

    /** Their entity's registry id. Stable across 2.x; verified against 2.2.0. */
    public static final ResourceLocation FAKE_PLAYER_TYPE =
            ResourceLocation.fromNamespaceAndPath(MODID, "fake_player");

    /**
     * Cached because this is consulted from interaction, tick and render paths.
     * {@link ModList} is only safe to query after mod loading, and every caller
     * here runs well after that.
     */
    private static Boolean loaded;

    private FakePlayerSupport() {
    }

    public static boolean isModLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MODID);
        }
        return loaded;
    }

    /**
     * Their {@code EntityType} instance, resolved once from the registry and then
     * compared by reference.
     *
     * <h2>Why this replaced the reverse key lookup (1.5.11)</h2>
     * This used to be {@code FAKE_PLAYER_TYPE.equals(ForgeRegistries.ENTITY_TYPES
     * .getKey(entity.getType()))} - a hash map hit per call, which was fine for the
     * interaction, tick and render paths it had. 1.5.11 widened
     * {@code LiePoseUtil#isPosed}/{@code WallPoseUtil#isPosed} from {@code Player}
     * to {@code LivingEntity} so fake players could reuse those poses, and those two
     * are reached from {@code Entity#isPushable} - which {@code Entity#pushEntities}
     * calls for every nearby entity of every entity, every tick. A hash lookup there
     * is the wrong order of magnitude.
     *
     * <p>Comparing the type instance instead is exactly equivalent: the registry is a
     * bijection between key and value, so "its key is players:fake_player" and "it is
     * the value registered under players:fake_player" are the same statement. It
     * reduces the test to a field read and a reference compare.
     *
     * <p>Resolved lazily, and a null result is NOT cached - every caller runs in a
     * world, long after registries are frozen, but caching a miss would be
     * unrecoverable if one ever somehow ran earlier.
     */
    @Nullable
    private static EntityType<?> cachedType;

    /**
     * True if this entity is a Fake Players fake player.
     *
     * <p>Deliberately a registry comparison, not an instanceof - see this class's
     * doc for why their types are never named here.
     */
    public static boolean isFakePlayer(@Nullable Entity entity) {
        if (entity == null || !isModLoaded()) {
            return false;
        }
        EntityType<?> type = cachedType;
        if (type == null) {
            type = ForgeRegistries.ENTITY_TYPES.getValue(FAKE_PLAYER_TYPE);
            if (type == null) {
                return false;
            }
            cachedType = type;
        }
        return entity.getType() == type;
    }
}

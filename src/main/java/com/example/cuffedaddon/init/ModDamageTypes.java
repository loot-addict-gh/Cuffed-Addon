package com.example.cuffedaddon.init;

import com.example.cuffedaddon.CuffedAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * Damage types are datapack-driven in 1.20.1, so the actual definition lives in
 * {@code data/cuffedaddon/damage_type/electrization.json} and this class only
 * holds the {@link ResourceKey} to look it up with.
 *
 * <p>Armor bypass is likewise data-driven - it comes from membership in the
 * vanilla {@code minecraft:bypasses_armor} damage type TAG, not from a field on
 * the type itself. This addon ships
 * {@code data/minecraft/tags/damage_type/bypasses_armor.json} with
 * {@code "replace": false} to append to it without owning the namespace; Cuffed
 * itself does exactly the same thing for its own {@code cuffed:hang} type, and
 * both entries merge cleanly.
 */
public final class ModDamageTypes {

    public static final ResourceKey<DamageType> ELECTRIZATION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "electrization"));

    private ModDamageTypes() {
    }

    /**
     * Builds a live DamageSource for the given entity's level.
     *
     * <p>Looked up through the level's own {@code registryAccess()} every time
     * rather than cached in a static: damage type registries are per-server and
     * are rebuilt on datapack reload, so a cached Holder would go stale across a
     * {@code /reload} or a world switch.
     */
    public static DamageSource electrization(Entity entity) {
        return new DamageSource(entity.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ELECTRIZATION));
    }
}

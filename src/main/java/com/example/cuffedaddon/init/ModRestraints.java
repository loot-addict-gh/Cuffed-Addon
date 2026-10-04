package com.example.cuffedaddon.init;

import com.example.cuffedaddon.restraints.BundleDuctTapeRestraint;
import com.example.cuffedaddon.restraints.BundleRopeRestraint;
import com.example.cuffedaddon.restraints.RopeArmsRestraint;
import com.example.cuffedaddon.restraints.RopeHeadRestraint;
import com.example.cuffedaddon.restraints.RopeLegsRestraint;
import com.example.cuffedaddon.restraints.SleepMaskDuctTapeRestraint;
import com.example.cuffedaddon.restraints.SleepMaskRestraint;
import com.example.cuffedaddon.restraints.SleepMaskRopeRestraint;
import com.example.cuffedaddon.restraints.StraitjacketArmsRestraint;
import com.example.cuffedaddon.restraints.StraitjacketHeadRestraint;
import com.example.cuffedaddon.restraints.StraitjacketLegsRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * This is deliberately OUR OWN Forge registry, separate from Cuffed's own
 * "cuffed:restraints" registry (com.lazrproductions.cuffed.init.ModRestraints).
 *
 * Cuffed CAN auto-detect foreign restraint registries (see CuffedMod's
 * registerSounds method - misleadingly named, it also handles this) by
 * peeking at every registry while Forge's RegisterEvent is being broadcast
 * and checking whether its values are AbstractRestraint instances. In
 * practice this is a race: Forge posts that broadcast to each mod's event
 * bus in turn, "cuffed" gets visited before "cuffedaddon" (it's a dependency,
 * so it loads first), and Cuffed's peek runs BEFORE our own DeferredRegister
 * has actually populated this registry - so it always sees zero entries and
 * never adopts it automatically.
 *
 * So instead of relying on that auto-detection, we capture the registry
 * Supplier that makeRegistry() hands back and register it with Cuffed's
 * RestraintAPI ourselves during FMLCommonSetupEvent (see CuffedAddon), which
 * only fires after every mod's registries are guaranteed fully populated.
 */
public class ModRestraints {
    private static boolean isInitialized = false;
    private static Supplier<IForgeRegistry<AbstractRestraint>> registrySupplier;

    public static final DeferredRegister<AbstractRestraint> RESTRAINTS =
            DeferredRegister.create(ResourceLocation.fromNamespaceAndPath("cuffedaddon", "restraints"), "cuffedaddon");

    public static final RegistryObject<AbstractRestraint> ROPE_ARMS = RESTRAINTS.register("rope_arms", RopeArmsRestraint::new);
    public static final RegistryObject<AbstractRestraint> ROPE_LEGS = RESTRAINTS.register("rope_legs", RopeLegsRestraint::new);
    public static final RegistryObject<AbstractRestraint> ROPE_HEAD = RESTRAINTS.register("rope_head", RopeHeadRestraint::new);
    public static final RegistryObject<AbstractRestraint> SLEEP_MASK = RESTRAINTS.register("sleep_mask", SleepMaskRestraint::new);

    public static final RegistryObject<AbstractRestraint> BUNDLE_DUCT_TAPE = RESTRAINTS.register("bundle_duct_tape", BundleDuctTapeRestraint::new);
    public static final RegistryObject<AbstractRestraint> BUNDLE_ROPE = RESTRAINTS.register("bundle_rope", BundleRopeRestraint::new);
    public static final RegistryObject<AbstractRestraint> SLEEP_MASK_DUCT_TAPE = RESTRAINTS.register("sleep_mask_duct_tape", SleepMaskDuctTapeRestraint::new);
    public static final RegistryObject<AbstractRestraint> SLEEP_MASK_ROPE = RESTRAINTS.register("sleep_mask_rope", SleepMaskRopeRestraint::new);

    public static final RegistryObject<AbstractRestraint> STRAITJACKET_ARMS = RESTRAINTS.register("straitjacket_arms", StraitjacketArmsRestraint::new);
    public static final RegistryObject<AbstractRestraint> STRAITJACKET_LEGS = RESTRAINTS.register("straitjacket_legs", StraitjacketLegsRestraint::new);
    public static final RegistryObject<AbstractRestraint> STRAITJACKET_HEAD = RESTRAINTS.register("straitjacket_head", StraitjacketHeadRestraint::new);

    public static void register(IEventBus modEventBus) {
        if (isInitialized) {
            throw new IllegalStateException("cuffedaddon restraints already initialized");
        }
        registrySupplier = RESTRAINTS.makeRegistry(RegistryBuilder::new);
        RESTRAINTS.register(modEventBus);
        isInitialized = true;
    }

    public static IForgeRegistry<AbstractRestraint> getRegistry() {
        return registrySupplier.get();
    }
}


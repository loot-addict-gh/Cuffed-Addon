package com.example.cuffedaddon;

import com.example.cuffedaddon.client.CuffedAddonClientConfig;
import com.example.cuffedaddon.collar.ShockCollarEvents;
import com.example.cuffedaddon.fakeplayer.FakePlayerEvents;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.curios.ModCurioMenuTypes;
import com.example.cuffedaddon.gamerule.ModGameRules;
import com.example.cuffedaddon.init.ModBlocks;
import com.example.cuffedaddon.init.ModCreativeTabContent;
import com.example.cuffedaddon.init.ModEffects;
import com.example.cuffedaddon.init.ModEnchantments;
import com.example.cuffedaddon.init.ModEntityTypes;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRecipeSerializers;
import com.example.cuffedaddon.init.ModRestraints;
import com.example.cuffedaddon.necklace.NecklaceEvents;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.picker.PlayerPickerEvents;
import com.example.cuffedaddon.pose.LiePoseCapabilityEvents;
import com.example.cuffedaddon.pose.WallPoseCapabilityEvents;
import com.example.cuffedaddon.trap.RestraintTraps;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(value = "cuffedaddon")
public class CuffedAddon {
    public static final String MODID = "cuffedaddon";

    /**
     * Added in 1.5.1. This addon had no logger of its own until then, which was
     * fine while every feature either worked or threw - but the Fake Players
     * compat can fail silently and invisibly (see
     * {@code FakePlayerEvents#reportOnce}), and "nothing happens" is not
     * something anyone can debug from in-game alone.
     */
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MODID);

    public CuffedAddon(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModGameRules.init();
        NetworkHandler.register();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModRestraints.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModCreativeTabContent.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        ModEffects.register(modEventBus);
        ModEnchantments.register(modEventBus);
        ModCurioMenuTypes.register(modEventBus);
        modEventBus.addListener(LiePoseCapabilityEvents::registerCapabilities);
        modEventBus.addListener(WallPoseCapabilityEvents::registerCapabilities);
        modEventBus.addListener(PlayerPickerEvents::registerCapabilities);
        modEventBus.addListener(ShockCollarEvents::registerCapabilities);
        modEventBus.addListener(NecklaceEvents::registerCapabilities);
        modEventBus.addListener(FakePlayerEvents::registerCapabilities);

        // All server-side config for this addon lives in this ONE
        // cuffedaddon-server.toml (CuffedAddonServerConfig.SPEC) - don't register a
        // second server toml for a new restraint/feature, add its config to
        // CuffedAddonServerConfig instead (see that class's own doc comment).
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CuffedAddonServerConfig.SPEC, "cuffedaddon-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CuffedAddonClientConfig.SPEC, "cuffedaddon-client.toml");

        modEventBus.addListener(this::commonSetup);
        // Dispenser trap behaviours go on at LOAD COMPLETE, not common setup -
        // Cuffed writes the same unsynchronised vanilla map from its own
        // parallel setup handler, and we also need to register after it to win
        // for the four items it claims. Full reasoning in RestraintTraps.
        modEventBus.addListener(RestraintTraps::registerDispenserBehaviours);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Cuffed CAN auto-adopt foreign restraint registries during RegisterEvent,
        // but that path races against mod-bus broadcast order and misses ours
        // (see ModRestraints for the full explanation). So instead we hand our
        // now-fully-populated registry to Cuffed directly here - commonSetup only
        // fires once every mod's registries are guaranteed done.
        event.enqueueWork(() -> RestraintAPI.Registries.register(ModRestraints.getRegistry()));
    }
}


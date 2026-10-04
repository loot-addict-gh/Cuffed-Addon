package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.curios.CurioFriskingScreen;
import com.example.cuffedaddon.curios.ModCurioMenuTypes;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only, mod-bus (same split as ClientModEvents). Registers
 * CurioFriskingScreen for CURIO_FRISKING_MENU only when Curios is loaded and
 * ModCurioMenuTypes actually registered that menu type - matches the same
 * guard used everywhere else in the curios package so a setup without
 * Curios installed never touches any curios-referencing class.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CurioFriskingClientEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("curios") && ModCurioMenuTypes.CURIO_FRISKING_MENU != null) {
            event.enqueueWork(() -> MenuScreens.register(ModCurioMenuTypes.CURIO_FRISKING_MENU.get(),
                    CurioFriskingScreen::new));
        }
    }
}

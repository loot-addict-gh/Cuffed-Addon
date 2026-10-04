package com.example.cuffedaddon.curios;

import com.example.cuffedaddon.CuffedAddon;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deliberately NOT final and NOT initialized inline - the actual
 * MENU_TYPES.register(...) call (and therefore the CurioFriskingMenu::new
 * method reference it needs to build) only ever runs inside the
 * isLoaded("curios") guard in #register below. On a setup without Curios,
 * that call never executes, so CurioFriskingMenu (and everything it
 * transitively references) never gets resolved by the JVM at all, and
 * CURIO_FRISKING_MENU is simply left null and never used - see
 * PossessionsBoxCurioEvents, which is the only thing that would ever reach
 * for it, and which is itself gated behind the same check.
 */
public class ModCurioMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister
            .create(ForgeRegistries.MENU_TYPES, CuffedAddon.MODID);

    public static RegistryObject<MenuType<CurioFriskingMenu>> CURIO_FRISKING_MENU;

    public static void register(IEventBus eventBus) {
        if (ModList.get().isLoaded("curios")) {
            CURIO_FRISKING_MENU = MENU_TYPES.register("curio_frisking_menu",
                    () -> IForgeMenuType.create(CurioFriskingMenu::new));
        }
        MENU_TYPES.register(eventBus);
    }
}

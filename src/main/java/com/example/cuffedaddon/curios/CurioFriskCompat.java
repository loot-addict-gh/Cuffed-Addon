package com.example.cuffedaddon.curios;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * The only class PossessionsBoxCurioEvents calls into. Everything
 * Curios-specific (CurioFriskingContainer, CurioFriskingMenu,
 * CurioFriskUtil, and transitively every top.theillusivec4.curios.* type
 * they use) is only ever touched from inside this method, which is itself
 * only ever called after PossessionsBoxCurioEvents has already confirmed
 * ModList.get().isLoaded("curios") - so this class, and everything it
 * references, only gets loaded on setups that actually have Curios
 * installed.
 *
 * Uses NetworkHooks#openScreen with an extra-data writer rather than plain
 * ServerPlayer#openMenu, because the curio slot count is dynamic per
 * target - the client needs to know how many curio slots (and their
 * labels) to build BEFORE real item contents arrive via normal slot-sync
 * packets, or its slot list would never match the server's.
 */
public class CurioFriskCompat {

    public static void frisk(@Nonnull ServerPlayer frisker, @Nonnull ServerPlayer target,
            @Nonnull ItemStack boxStack) {
        CurioFriskUtil.Result resolved = CurioFriskUtil.resolve(target);

        NetworkHooks.openScreen(frisker, new MenuProvider() {
            @Nonnull
            @Override
            public Component getDisplayName() {
                return target.getDisplayName();
            }

            @Nonnull
            @Override
            public AbstractContainerMenu createMenu(int id, @Nonnull Inventory playerInventory,
                    @Nonnull Player p) {
                return new CurioFriskingMenu(ModCurioMenuTypes.CURIO_FRISKING_MENU.get(), id, playerInventory,
                        target.getId(), new CurioFriskingContainer(target, boxStack), 5);
            }
        }, buf -> {
            buf.writeVarInt(target.getId());
            buf.writeVarInt(resolved.labels.size());
            for (String label : resolved.labels) {
                buf.writeUtf(label);
            }
        });
    }
}

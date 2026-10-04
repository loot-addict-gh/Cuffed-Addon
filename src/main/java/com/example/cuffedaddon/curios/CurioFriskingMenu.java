package com.example.cuffedaddon.curios;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.lazrproductions.cuffed.inventory.FriskingSlot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Same 45-slot layout as Cuffed's own FriskingMenu (target's armor, offhand,
 * main inventory, hotbar, then the frisker's own inventory/hotbar) - that
 * part is copied rather than reused, since FriskingMenu's constructor is
 * what builds those slots and there's no clean extension point to reuse just
 * that piece - plus one extra column of curio slots.
 *
 * The curio slot count varies per target, so unlike Cuffed's own FriskingMenu
 * (which the client always reconstructs at a hardcoded 5 rows / 45 slots),
 * this menu is registered via IForgeMenuType.create(...) and reconstructed
 * client-side from an extra-data buffer (see ModCurioMenuTypes and
 * CurioFriskCompat) telling it exactly how many curio slots - and their
 * labels - to expect, so the client's slot list always matches the server's.
 */
public class CurioFriskingMenu extends AbstractContainerMenu {

    private static final int CURIO_COLUMN_X = 184;
    private static final int CURIO_COLUMN_TOP_Y = 8;
    private static final int CURIO_ROWS_PER_COLUMN = 8;
    private static final int CURIO_ROW_HEIGHT = 18;

    private final Container container;
    private final List<String> curioLabels;
    private final int containerRows;
    private final int otherPlayerId;

    /** Server-side: real target inventory + real curio data. */
    public CurioFriskingMenu(MenuType<?> type, int containerId, @Nonnull Inventory playerInv, int otherPlayerId,
            @Nonnull CurioFriskingContainer container, int rows) {
        this(type, containerId, playerInv, otherPlayerId, container, buildLabels(container), rows);
    }

    /** Client-side: reconstructed from the extra-data buffer written in CurioFriskCompat#frisk. */
    public CurioFriskingMenu(int containerId, @Nonnull Inventory inv, @Nonnull FriendlyByteBuf buf) {
        this(ModCurioMenuTypes.CURIO_FRISKING_MENU.get(), containerId, inv, buf.readVarInt(), readLabels(buf), 5);
    }

    private CurioFriskingMenu(MenuType<?> type, int containerId, Inventory otherPlayerInv, int otherPlayerId,
            List<String> curioLabels, int rows) {
        this(type, containerId, otherPlayerInv, otherPlayerId, new SimpleContainer(9 * rows + curioLabels.size()),
                curioLabels, rows);
    }

    private CurioFriskingMenu(MenuType<?> type, int containerId, Inventory playerInv, int otherPlayerId,
            Container container, List<String> curioLabels, int rows) {
        super(type, containerId);
        checkContainerSize(container, rows * 9 + curioLabels.size());
        this.otherPlayerId = otherPlayerId;
        this.container = container;
        this.curioLabels = curioLabels;
        this.containerRows = rows;
        container.startOpen(playerInv.player);

        // target's armor slots
        this.addSlot(new FriskingSlot(container, 0, 8, 8 + (18 * 0)));
        this.addSlot(new FriskingSlot(container, 1, 8, 8 + (18 * 1)));
        this.addSlot(new FriskingSlot(container, 2, 8, 8 + (18 * 2)));
        this.addSlot(new FriskingSlot(container, 3, 8, 8 + (18 * 3)));

        // target's offhand
        this.addSlot(new FriskingSlot(container, 8, 5 + (18 * 4), 8 + (18 * 3)));

        // target's main inventory
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new FriskingSlot(container, column + row * 9 + 9,
                        8 + column * 18,
                        84 + row * 18));
            }
        }

        // target's hotbar
        for (int column = 0; column < 9; column++) {
            this.addSlot(new FriskingSlot(container, 36 + column,
                    8 + column * 18,
                    142));
        }

        // target's curio slots - one column per CURIO_ROWS_PER_COLUMN slots
        for (int i = 0; i < curioLabels.size(); i++) {
            int col = i / CURIO_ROWS_PER_COLUMN;
            int row = i % CURIO_ROWS_PER_COLUMN;
            this.addSlot(new FriskingSlot(container, 45 + i,
                    CURIO_COLUMN_X + col * 18,
                    CURIO_COLUMN_TOP_Y + row * CURIO_ROW_HEIGHT));
        }

        // frisker's own inventory
        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(playerInv, j1 + l * 9 + 9,
                        8 + j1 * 18,
                        174 + l * 18));
            }
        }

        // frisker's own hotbar
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInv, i1, 8 + i1 * 18, 232));
        }
    }

    private static List<String> buildLabels(CurioFriskingContainer container) {
        List<String> labels = new ArrayList<>(container.getCurioSlotCount());
        for (int i = 0; i < container.getCurioSlotCount(); i++) {
            labels.add(container.getCurioSlotLabel(i));
        }
        return labels;
    }

    private static List<String> readLabels(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            labels.add(buf.readUtf());
        }
        return labels;
    }

    public int getCurioSlotCount() {
        return curioLabels.size();
    }

    @Nonnull
    public String getCurioSlotLabel(int curioIndex) {
        return curioIndex >= 0 && curioIndex < curioLabels.size() ? curioLabels.get(curioIndex) : "";
    }

    public int getCurioColumns() {
        return curioLabels.isEmpty() ? 0 : (curioLabels.size() - 1) / CURIO_ROWS_PER_COLUMN + 1;
    }

    public boolean stillValid(@Nonnull Player player) {
        Level level = player.level();
        if (level != null) {
            if (level.getEntity(otherPlayerId) instanceof Player other) {
                if (other.isRemoved()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return this.container.stillValid(player);
    }

    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int count) {
        return ItemStack.EMPTY;
    }

    public boolean canDragTo(@Nonnull Slot slot) {
        return false;
    }

    public void removed(@Nonnull Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    public Container getContainer() {
        return this.container;
    }

    public int getRowCount() {
        return this.containerRows;
    }
}

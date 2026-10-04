package com.example.cuffedaddon.curios;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.lazrproductions.cuffed.CuffedMod;
import com.lazrproductions.cuffed.client.gui.screen.FriskingScreen;
import com.lazrproductions.cuffed.inventory.FriskingSlot;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Same base panel/layout as Cuffed's own FriskingScreen (indices 0-44), with
 * an extra curio column drawn to the right whose width grows with however
 * many visible curio slots the target has. No new texture asset - the panel
 * background and each slot's sunken bevel are drawn with plain GuiGraphics
 * fills using the same colors vanilla's own inventory screens use, at the
 * same 18px slot spacing, so it reads as a natural extension of the existing
 * panel rather than a bolted-on box.
 *
 * Reuses Cuffed's own public FriskingScreen.renderEntityInInventoryFollowsMouse
 * for the little rotating entity preview rather than re-deriving that math.
 * Does NOT reuse lazrslib's ScreenUtilities for the "taking..." progress
 * overlay (not on this addon's compile classpath - see project notes) -
 * that part is reimplemented here with plain GuiGraphics calls instead.
 */
public class CurioFriskingScreen extends AbstractContainerScreen<CurioFriskingMenu> {

    private static final ResourceLocation FRISKING_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            CuffedMod.MODID, "textures/gui/container/frisking.png");

    private static final int BASE_WIDTH = 176;
    private static final int CURIO_PANEL_GAP = 8;
    private static final int CURIO_PANEL_PADDING = 4;

    /** Same light-grey/dark-bevel palette vanilla itself uses for inventory panels and slots. */
    private static final int PANEL_BG_COLOR = 0xFFC6C6C6;
    private static final int SLOT_SHADOW_COLOR = 0xFF373737;
    private static final int SLOT_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int SLOT_FLOOR_COLOR = 0xFF8B8B8B;

    /**
     * Ticks to hold a curio slot before it's taken. Same constant Cuffed's own
     * FriskingScreen uses for the vanilla-inventory slots (kept identical so
     * frisking feels consistent everywhere). A possessions-box enchantment
     * that shortens this is planned for later - if/when that's added, swap
     * this constant for a lookup against the held box's enchantment level.
     */
    private static final int TICKS_TO_TAKE = 40;

    public CurioFriskingScreen(CurioFriskingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BASE_WIDTH + (menu.getCurioSlotCount() > 0
                ? CURIO_PANEL_GAP + menu.getCurioColumns() * 18
                : 0);
        this.imageHeight = 256;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (mouseHeld) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            renderTakeProgress(graphics, mouseX - 8, mouseY - 4, (float) mouseHeldTicks / (float) TICKS_TO_TAKE);
            graphics.drawString(this.font, Component.translatable("info.cuffed.frisk.taking"),
                    mouseX - 8, mouseY + 6, 0xFFFFFF, true);
            graphics.pose().popPose();
        }

        if (!mouseHeld) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * Draws a panel background behind the curio column(s), sized to the
     * target's actual slot count/column count, using the same light-grey
     * (0xC6C6C6) vanilla inventory screens use for their own background -
     * no new texture asset, just the same flat color vanilla already uses,
     * so it reads as part of the same GUI rather than a bolted-on box.
     */
    private void drawCurioPanelBackground(GuiGraphics graphics, int i, int j) {
        if (this.menu.getCurioSlotCount() == 0) {
            return;
        }
        int rows = Math.min(this.menu.getCurioSlotCount(), 8);
        // The -1 on both X and Y matches drawSlotBevel's own -1 inset (bevels are
        // drawn starting 1px up-left of each item's draw position) - without it
        // the panel sat 1px further right/down than the bevels, giving a visibly
        // uneven left/right margin around the column ([stated] caught this).
        int panelX = i + BASE_WIDTH + CURIO_PANEL_GAP - 1 - CURIO_PANEL_PADDING;
        int panelY = j + 8 - 1 - CURIO_PANEL_PADDING;
        int panelWidth = this.imageWidth - BASE_WIDTH - CURIO_PANEL_GAP + CURIO_PANEL_PADDING * 2;
        int panelHeight = rows * 18 + CURIO_PANEL_PADDING * 2;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG_COLOR);
    }

    /**
     * Same sunken-slot bevel vanilla itself draws for every inventory slot:
     * a 1px darker shadow border on the top/left, a 1px lighter highlight
     * border on the bottom/right, and a mid-grey floor in between - drawn at
     * the exact same 18x18 spacing (1px inset from the item's own draw
     * position) vanilla slots use, so items line up identically to any
     * other inventory screen.
     */
    private void drawSlotBevel(GuiGraphics graphics, int itemX, int itemY) {
        int x = itemX - 1;
        int y = itemY - 1;
        int size = 18;
        graphics.fill(x, y, x + size, y + size, SLOT_HIGHLIGHT_COLOR);
        graphics.fill(x, y, x + size - 1, y + size - 1, SLOT_SHADOW_COLOR);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, SLOT_FLOOR_COLOR);
    }

    private void renderTakeProgress(GuiGraphics graphics, int x, int y, float progress) {
        int width = 16;
        int height = 4;
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        int filled = Math.round(width * Math.min(1.0F, Math.max(0.0F, progress)));
        graphics.fill(x, y, x + filled, y + height, 0xFF00FF00);
    }

    @SuppressWarnings("null")
    @Override
    protected void renderTooltip(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack itemstack = this.hoveredSlot.getItem();
            ArrayList<Component> tooltips = new ArrayList<>(this.getTooltipFromContainerItem(itemstack));
            if (hoveredSlot instanceof FriskingSlot) {
                tooltips.add(Component.translatable("info.cuffed.frisk.tooltip").withStyle(ChatFormatting.GRAY));
                String label = this.menu.getCurioSlotLabel(hoveredSlot.getSlotIndex() - 45);
                if (!label.isEmpty()) {
                    tooltips.add(Component.literal(label).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            graphics.renderTooltip(this.font, tooltips, itemstack.getTooltipImage(), itemstack, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        graphics.blit(FRISKING_BACKGROUND, i, j, 0, 0, BASE_WIDTH, this.imageHeight);

        drawCurioPanelBackground(graphics, i, j);
        for (Slot slot : this.menu.slots) {
            // Curio FriskingSlots are always index 45+ (see CurioFriskingContainer) -
            // the target's own 0-44 slots and the frisker's own inventory/hotbar
            // (a different container entirely) never reach that index, so this
            // check alone is enough to single out just the curio slots.
            if (slot.getSlotIndex() >= 45) {
                drawSlotBevel(graphics, i + slot.x, j + slot.y);
            }
        }

        Minecraft instance = this.minecraft;
        if (instance != null) {
            ClientLevel level = instance.level;
            if (level != null) {
                LocalPlayer player = instance.player;
                if (player != null) {
                    Entity entity = null;
                    List<? extends Player> players = level.players();
                    for (LivingEntity p : players) {
                        if (p.getDisplayName().getString().equals(this.title.getString())) {
                            entity = p;
                        }
                    }
                    if (entity instanceof LivingEntity actualEntity) {
                        FriskingScreen.renderEntityInInventoryFollowsMouse(graphics, i + 51, j + 71, 30,
                                (float) (i + 51) - mouseX, (float) (j + 75 - 50) - mouseY, actualEntity);
                    }
                }
            }
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                4210752, false);
        graphics.drawString(this.font, this.title, this.titleLabelX + 110 - (this.font.width(this.title) / 2),
                this.titleLabelY + 20, 4210752, false);
    }

    private boolean isHovering(Slot slot, double mouseX, double mouseY) {
        return this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
    }

    @Nullable
    private Slot findSlot(double x, double y) {
        for (int i = 0; i < this.menu.slots.size(); ++i) {
            Slot slot = this.menu.slots.get(i);
            if (this.isHovering(slot, x, y) && slot.isActive()) {
                return slot;
            }
        }
        return null;
    }

    boolean mouseHeld;
    int mouseHeldTicks;
    double mouseHeldX, mouseHeldY;
    Slot heldSlot;

    @Override
    protected void containerTick() {
        if (mouseHeld) {
            mouseHeldTicks++;
            if (mouseHeldTicks >= TICKS_TO_TAKE) {
                mouseClicked(mouseHeldX, mouseHeldY, 0);
                mouseHeld = false;
                mouseHeldX = 0;
                mouseHeldY = 0;
                mouseHeldTicks = 0;
                heldSlot = null;
            }
        }
        super.containerTick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int keyCode) {
        if (mouseHeld) {
            super.mouseClicked(mouseX, mouseY, keyCode);
        }

        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(keyCode);
        Slot slot = this.findSlot(mouseX, mouseY);

        if (slot instanceof FriskingSlot) {
            if (mouseKey.getValue() == InputConstants.MOUSE_BUTTON_LEFT) {
                if (!slot.getItem().isEmpty()) {
                    mouseHeld = true;
                    mouseHeldX = mouseX;
                    mouseHeldY = mouseY;
                    mouseHeldTicks = 0;
                    heldSlot = slot;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, keyCode);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int keyCode) {
        mouseHeld = false;
        mouseHeldX = 0;
        mouseHeldY = 0;
        mouseHeldTicks = 0;
        heldSlot = null;
        return super.mouseReleased(mouseX, mouseY, keyCode);
    }
}

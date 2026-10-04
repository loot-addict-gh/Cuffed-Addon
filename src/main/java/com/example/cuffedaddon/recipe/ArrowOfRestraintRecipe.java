package com.example.cuffedaddon.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import com.example.cuffedaddon.items.ArrowOfRestraintItem;

/**
 * Eight vanilla arrows around <b>one named restraint item</b>, producing an
 * Arrow of Restraint that remembers that exact restraint.
 *
 * <pre>
 *   A A A
 *   A R A      A = minecraft:arrow      R = this recipe's restraint
 *   A A A
 * </pre>
 *
 * <h2>One recipe per restraint, and why that changed at 1.6.1</h2>
 * 1.6.0 shipped a single recipe whose centre accepted <i>any</i> restraint in
 * Cuffed's live registries. Elegant, and wrong for the player: [stated] pressed
 * the JEI recipe key on an arrow and got nothing.
 *
 * <p>Two separate reasons, and splitting into one recipe per restraint fixes
 * both:
 *
 * <ol>
 *   <li><b>{@code isSpecial()}.</b> {@code CustomRecipe} returns true, and both
 *       JEI and the vanilla recipe book skip every recipe that says so - that is
 *       what "special" means to them. It is overridden to <b>false</b> below.
 *       This is also the real explanation for the older note that JEI ignores
 *       the four combination-restraint recipes; those can be fixed the same way
 *       if [stated] ever wants them listed.</li>
 *   <li><b>Lookup matches by exact ItemStack.</b> Pressing the recipe key on a
 *       loaded handcuffs arrow looks for a recipe whose RESULT is that stack,
 *       NBT and all. A one-size-fits-all recipe can only advertise one result,
 *       so it could never match any real arrow. Now each recipe's result is the
 *       specific loaded arrow it produces, so the lookup lands.</li>
 * </ol>
 *
 * <p>Exactly nine ingredients in reading order means JEI and the recipe book
 * lay them out across the 3x3 grid as the ring they are, even though neither is
 * told this is a shaped recipe.
 *
 * <p>The trade is that the restraint list is now explicit - one small JSON per
 * restraint under {@code data/cuffedaddon/recipes/} - rather than read from the
 * registries. That is what [stated] asked for in practice: the pillory had been
 * swept in automatically and they wanted it gone ("I asked for the 3 slot
 * restraints and not the stationary ones"). An explicit list is the thing that
 * can leave something out. {@code ArrowRestraintUtil.EXCLUDED} keeps the
 * creative tab in step.
 *
 * <h2>Still a CustomRecipe, not a ShapedRecipe</h2>
 * The result depends on the input's NBT - the whole centre stack is copied into
 * the arrow so an enchanted restraint stays enchanted ([stated] confirmed that
 * working at 1.6.0) - and a data-driven shaped recipe's result is a constant.
 * {@link #assemble} is where that copy happens.
 *
 * <p>A non-empty Bundle is refused outright: a Bundle only counts as a head
 * restraint while empty, and crafting a full one into an arrow would smuggle its
 * contents into a restraint, where they would be lost. The same hazard this
 * addon's combination recipes and dispenser traps both already guard against,
 * and it is checked again when the arrow lands.
 */
public class ArrowOfRestraintRecipe extends CustomRecipe {

    private final Item restraint;

    public ArrowOfRestraintRecipe(ResourceLocation id, CraftingBookCategory category, Item restraint) {
        super(id, category);
        this.restraint = restraint;
    }

    Item getRestraint() {
        return restraint;
    }

    /**
     * Shown by JEI and the recipe book. {@code CustomRecipe} says true, which is
     * precisely why the 1.6.0 recipe was invisible to both. Nothing about this
     * recipe is dynamic enough to warrant hiding it: the shape is fixed and the
     * result is known up front.
     */
    @Override
    public boolean isSpecial() {
        return false;
    }

    private boolean isValidCentre(ItemStack stack) {
        if (!stack.is(restraint)) {
            return false;
        }
        return !(stack.is(Items.BUNDLE) && BundleItem.getFullnessDisplay(stack) > 0);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return findCentre(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack centre = findCentre(container);
        if (centre == null) {
            return ItemStack.EMPTY;
        }
        // The WHOLE centre stack, so enchantments and damage ride along.
        return ArrowOfRestraintItem.withRestraint(centre);
    }

    /**
     * The centre stack of the single valid 3x3 window, or null if the grid does
     * not hold this recipe.
     *
     * <p>Searched as a window rather than assumed to sit at slot 4, so this
     * still works on a crafting grid larger than 3x3 while refusing any grid
     * where something is left over outside the window.
     */
    private ItemStack findCentre(CraftingContainer container) {
        int width = container.getWidth();
        int height = container.getHeight();
        for (int originX = 0; originX + 3 <= width; originX++) {
            for (int originY = 0; originY + 3 <= height; originY++) {
                ItemStack centre = matchWindow(container, width, height, originX, originY);
                if (centre != null) {
                    return centre;
                }
            }
        }
        return null;
    }

    private ItemStack matchWindow(CraftingContainer container, int width, int height,
                                  int originX, int originY) {
        ItemStack centre = ItemStack.EMPTY;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                ItemStack stack = container.getItem(x + y * width);
                boolean inWindow = x >= originX && x < originX + 3 && y >= originY && y < originY + 3;
                if (!inWindow) {
                    // Anything outside the 3x3 window makes this placement wrong.
                    if (!stack.isEmpty()) {
                        return null;
                    }
                    continue;
                }
                if (x == originX + 1 && y == originY + 1) {
                    if (!isValidCentre(stack)) {
                        return null;
                    }
                    centre = stack;
                } else if (!stack.is(Items.ARROW)) {
                    return null;
                }
            }
        }
        return centre.isEmpty() ? null : centre;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    /**
     * Nine ingredients in reading order - arrows everywhere, the restraint in
     * the middle. Order matters: neither JEI nor the recipe book is told this is
     * shaped, so both place the ingredients into the grid in sequence, and this
     * sequence happens to BE the shape.
     */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        Ingredient arrow = Ingredient.of(Items.ARROW);
        Ingredient centre = Ingredient.of(restraint);
        for (int slot = 0; slot < 9; slot++) {
            ingredients.add(slot == 4 ? centre : arrow);
        }
        return ingredients;
    }

    /**
     * The loaded arrow for a plain, unenchanted restraint.
     *
     * <p>This is the stack JEI matches against when the recipe key is pressed on
     * an arrow, so it goes through {@code ArrowOfRestraintItem.withRestraint}
     * exactly like a crafted one and a creative-tab one do - three call sites,
     * one builder, byte-identical NBT.
     */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ArrowOfRestraintItem.withRestraint(new ItemStack(restraint));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ArrowOfRestraintRecipeSerializer.INSTANCE;
    }
}

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

/**
 * A "combination" recipe: any 2 of the crafting grid's slots hold exactly
 * one visionItem and one speechItem (order/position doesn't matter, same
 * as a normal shapeless recipe), every other slot is empty, and the result
 * is a single combo item. This can't be a plain data-driven
 * crafting_shapeless recipe because of the empty-bundle requirement below
 * - vanilla 1.20.1 has no ingredient predicate for NBT/content state, so
 * this is a CustomRecipe (same mechanism vanilla itself uses for e.g.
 * repairing/duplicating items) with the check done in matches() instead.
 *
 * requireEmptyVisionBundle: when true AND visionItem is the vanilla Bundle,
 * a bundle with anything inside it (BundleItem.getFullnessDisplay() > 0)
 * fails to match at all - [stated] explicitly didn't want a bundle's
 * contents silently discarded by using it as a combo ingredient. Doesn't
 * apply to Sleep Mask (not a container, nothing to check).
 *
 * No recipe remainder is produced (e.g. an emptied-out item like vanilla's
 * milk bucket -> bucket) - everything in the grid is fully consumed into
 * the one result item, same as this addon's other simple recipes.
 */
public class CombinationRestraintRecipe extends CustomRecipe {
    private final Item visionItem;
    private final boolean requireEmptyVisionBundle;
    private final Item speechItem;
    private final ItemStack result;

    public CombinationRestraintRecipe(ResourceLocation id, CraftingBookCategory category,
                                       Item visionItem, boolean requireEmptyVisionBundle,
                                       Item speechItem, Item result) {
        super(id, category);
        this.visionItem = visionItem;
        this.requireEmptyVisionBundle = requireEmptyVisionBundle;
        this.speechItem = speechItem;
        this.result = new ItemStack(result);
    }

    Item getVisionItem() {
        return visionItem;
    }
    boolean getRequireEmptyVisionBundle() {
        return requireEmptyVisionBundle;
    }
    Item getSpeechItem() {
        return speechItem;
    }
    Item getResultRaw() {
        return result.getItem();
    }

    /**
     * CustomRecipe's default returns an empty list, which is why vanilla's
     * own similar "special" recipes (item repair, banner duplication, etc)
     * don't show up in JEI or the recipe book either - JEI's generic
     * crafting display reads this to know what to draw. This is the
     * missing piece for JEI to pick these 4 combos up (not yet confirmed
     * in-game). Doesn't attempt to express the empty-bundle requirement
     * (Ingredient has no NBT/content-state predicate to do that with) -
     * this is purely for display, matches() still does the real check.
     */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(visionItem));
        ingredients.add(Ingredient.of(speechItem));
        return ingredients;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        boolean foundVision = false;
        boolean foundSpeech = false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (!foundVision && stack.is(visionItem)) {
                if (requireEmptyVisionBundle && stack.is(Items.BUNDLE) && BundleItem.getFullnessDisplay(stack) > 0) {
                    return false;
                }
                foundVision = true;
            } else if (!foundSpeech && stack.is(speechItem)) {
                foundSpeech = true;
            } else {
                // Either a duplicate of an ingredient already matched, or
                // an item that isn't part of this recipe at all - either
                // way the grid must contain exactly these 2 items.
                return false;
            }
        }

        return foundVision && foundSpeech;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return (long) width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CombinationRestraintRecipeSerializer.INSTANCE;
    }
}

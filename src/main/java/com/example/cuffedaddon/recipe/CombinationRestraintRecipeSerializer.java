package com.example.cuffedaddon.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;

/**
 * Data-driven, same convention as this addon's other recipes (a plain JSON
 * file, no hardcoded item pairs in Java) - only the actual matching logic
 * needed a custom Recipe class (see CombinationRestraintRecipe) since
 * vanilla ingredients can't express the empty-bundle requirement. One
 * shared serializer instance for all 4 combo recipes; each JSON supplies
 * its own vision_item / speech_item / result.
 *
 * Expected JSON shape:
 * {
 *   "type": "cuffedaddon:combination_restraint",
 *   "vision_item": "minecraft:bundle",
 *   "vision_item_must_be_empty_bundle": true,
 *   "speech_item": "cuffed:duck_tape",
 *   "result": "cuffedaddon:bundle_duct_tape"
 * }
 */
public class CombinationRestraintRecipeSerializer implements RecipeSerializer<CombinationRestraintRecipe> {
    public static final CombinationRestraintRecipeSerializer INSTANCE = new CombinationRestraintRecipeSerializer();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cuffedaddon", "combination_restraint");

    private static Item itemFromId(String id) {
        // Matches the rest of this addon's convention of using
        // ResourceLocation.fromNamespaceAndPath over the (deprecated in
        // this Forge version) single-string constructor.
        int colon = id.indexOf(':');
        ResourceLocation location = colon < 0
                ? ResourceLocation.fromNamespaceAndPath("minecraft", id)
                : ResourceLocation.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1));
        return ForgeRegistries.ITEMS.getValue(location);
    }

    // Recipe-book category isn't exposed as a JSON field here - every combo
    // recipe just uses MISC, same as most modded crafting-table recipes
    // that don't need a specific vanilla tab (equipment/building/misc).
    private static final CraftingBookCategory CATEGORY = CraftingBookCategory.MISC;

    @Nonnull
    @Override
    public CombinationRestraintRecipe fromJson(@Nonnull ResourceLocation recipeId, @Nonnull JsonObject json) {
        Item visionItem = itemFromId(GsonHelper.getAsString(json, "vision_item"));
        boolean requireEmptyVisionBundle = GsonHelper.getAsBoolean(json, "vision_item_must_be_empty_bundle", false);
        Item speechItem = itemFromId(GsonHelper.getAsString(json, "speech_item"));
        Item result = itemFromId(GsonHelper.getAsString(json, "result"));

        return new CombinationRestraintRecipe(recipeId, CATEGORY, visionItem, requireEmptyVisionBundle, speechItem, result);
    }

    @Override
    public CombinationRestraintRecipe fromNetwork(@Nonnull ResourceLocation recipeId, @Nonnull FriendlyByteBuf buf) {
        Item visionItem = itemFromId(buf.readUtf());
        boolean requireEmptyVisionBundle = buf.readBoolean();
        Item speechItem = itemFromId(buf.readUtf());
        Item result = itemFromId(buf.readUtf());

        return new CombinationRestraintRecipe(recipeId, CATEGORY, visionItem, requireEmptyVisionBundle, speechItem, result);
    }

    @Override
    public void toNetwork(@Nonnull FriendlyByteBuf buf, @Nonnull CombinationRestraintRecipe recipe) {
        buf.writeUtf(itemId(recipe.getVisionItem()));
        buf.writeBoolean(recipe.getRequireEmptyVisionBundle());
        buf.writeUtf(itemId(recipe.getSpeechItem()));
        buf.writeUtf(itemId(recipe.getResultRaw()));
    }

    private static String itemId(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "" : id.toString();
    }
}

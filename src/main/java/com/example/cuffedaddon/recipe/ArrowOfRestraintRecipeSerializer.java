package com.example.cuffedaddon.recipe;

import javax.annotation.Nonnull;

import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Serializer for {@link ArrowOfRestraintRecipe}. One field - which restraint
 * goes in the middle:
 *
 * <pre>
 * {
 *   "type": "cuffedaddon:arrow_of_restraint",
 *   "restraint": "cuffed:handcuffs"
 * }
 * </pre>
 *
 * <p>The shape is not in the JSON because it is the same for every one of them,
 * and putting it there would only create a way for the twelve files to disagree.
 *
 * <p>Recipe-book category is MISC, same choice as
 * {@code CombinationRestraintRecipeSerializer} makes and for the same reason.
 */
public class ArrowOfRestraintRecipeSerializer implements RecipeSerializer<ArrowOfRestraintRecipe> {

    public static final ArrowOfRestraintRecipeSerializer INSTANCE = new ArrowOfRestraintRecipeSerializer();
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("cuffedaddon", "arrow_of_restraint");

    private static final CraftingBookCategory CATEGORY = CraftingBookCategory.MISC;

    /**
     * Matches the rest of this addon's convention of using
     * {@code ResourceLocation.fromNamespaceAndPath} over the single-string
     * constructor, which is deprecated in this Forge version.
     */
    private static Item itemFromId(String id) {
        int colon = id.indexOf(':');
        ResourceLocation location = colon < 0
                ? ResourceLocation.fromNamespaceAndPath("minecraft", id)
                : ResourceLocation.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1));
        return ForgeRegistries.ITEMS.getValue(location);
    }

    @Nonnull
    @Override
    public ArrowOfRestraintRecipe fromJson(@Nonnull ResourceLocation recipeId, @Nonnull JsonObject json) {
        Item restraint = itemFromId(GsonHelper.getAsString(json, "restraint"));
        return new ArrowOfRestraintRecipe(recipeId, CATEGORY, restraint);
    }

    @Override
    public ArrowOfRestraintRecipe fromNetwork(@Nonnull ResourceLocation recipeId, @Nonnull FriendlyByteBuf buf) {
        return new ArrowOfRestraintRecipe(recipeId, CATEGORY, itemFromId(buf.readUtf()));
    }

    @Override
    public void toNetwork(@Nonnull FriendlyByteBuf buf, @Nonnull ArrowOfRestraintRecipe recipe) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(recipe.getRestraint());
        buf.writeUtf(id == null ? "" : id.toString());
    }
}

package com.example.cuffedaddon.init;

import com.example.cuffedaddon.recipe.CombinationRestraintRecipeSerializer;
import com.example.cuffedaddon.recipe.ArrowOfRestraintRecipeSerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "cuffedaddon");

    public static final RegistryObject<RecipeSerializer<?>> COMBINATION_RESTRAINT =
            RECIPE_SERIALIZERS.register("combination_restraint", () -> CombinationRestraintRecipeSerializer.INSTANCE);

    public static final RegistryObject<RecipeSerializer<?>> ARROW_OF_RESTRAINT =
            RECIPE_SERIALIZERS.register("arrow_of_restraint", () -> ArrowOfRestraintRecipeSerializer.INSTANCE);

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}

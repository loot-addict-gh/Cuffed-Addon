package com.example.cuffedaddon.init;

import com.example.cuffedaddon.blocks.ReinforcedBedBlock;
import com.example.cuffedaddon.blocks.WallRestraintBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1.3.x - all 16 dye-color variants of the "Reinforced Bed" (Cuffed's own
 * `cuffed:bunk`). Each is a separate registered Block (matching how
 * vanilla itself does colored beds - 16 separate BedBlock instances, not
 * one block with a color property), using ReinforcedBedBlock (see its own
 * doc for why it isn't BunkBlock directly). Properties mirror Cuffed's own
 * BUNK registration exactly (same sound/strength/occlusion) - only
 * mapColor was left as COLOR_GRAY uniformly across all 16 rather than
 * guessing a matching MapColor enum constant per dye, to avoid a
 * compile-time risk that can't be tested here.
 *
 * Deliberately a single loop over one shared array of color names instead
 * of 16 hand-written fields - keeps this file short and makes adding/
 * removing a color trivial. REINFORCED_BEDS is public so ModItems/
 * ModCreativeTabContent can iterate it without duplicating the color list.
 */
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "cuffedaddon");

    public static final String[] BED_COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    public static final Map<String, RegistryObject<Block>> REINFORCED_BEDS = new LinkedHashMap<>();

    static {
        for (String color : BED_COLORS) {
            REINFORCED_BEDS.put(color, BLOCKS.register("bunk_" + color,
                    () -> new ReinforcedBedBlock(BlockBehaviour.Properties.of()
                            .sound(SoundType.NETHERITE_BLOCK)
                            .mapColor(MapColor.COLOR_GRAY)
                            .noOcclusion()
                            .strength(6.0F, 18.0F))));
        }
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    // Wall Restraint - merged in from the temporary standalone wallrestraint
    // project (see /areas/wallrestraint.md for its build history). 1-wide,
    // 2-tall door-shaped standing restraint panel - see WallRestraintBlock's
    // own doc for the full design.
    public static final RegistryObject<Block> WALL_RESTRAINT = BLOCKS.register("wall_restraint",
            () -> new WallRestraintBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(5.0F, 6.0F)
                    .noOcclusion()));
}

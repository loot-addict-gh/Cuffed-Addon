package com.example.cuffedaddon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathPackResources;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Registers each folder under resourcepacks/ in this mod's jar as its own
 * separate, off-by-default built-in resource pack -- the same mechanism
 * vanilla uses for "Programmer Art" -- instead of letting Forge fold their
 * assets into the single catch-all "Mod resources" entry every mod's own
 * assets/ folder gets merged into.
 *
 * Merely having the folders present in the jar is not, on its own, enough
 * for Forge to treat them this way; this listener is what actually does it.
 *
 * Note: RepositorySource#loadPacks and Pack's factory methods changed shape
 * across Forge/MC versions (some later versions take a second
 * Pack.PackConstructor argument; 1.20.1 does not) -- this is written and
 * verified specifically against 1.20.1's mappings.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BuiltinResourcePacks {

    private static final String[] PACK_FOLDERS = {"Black_Fuzzy", "Clear_Bars", "Duct_Tape_Fix"};

    private BuiltinResourcePacks() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        IModFile modFile = ModList.get().getModFileById(CuffedAddon.MODID).getFile();
        for (String folder : PACK_FOLDERS) {
            try {
                registerPack(event, modFile, folder);
            } catch (IOException e) {
                throw new RuntimeException("Failed to register bundled resource pack " + folder, e);
            }
        }
    }

    private static void registerPack(AddPackFindersEvent event, IModFile modFile, String folder) throws IOException {
        Path resourcePath = modFile.findResource("resourcepacks/" + folder);
        PathPackResources resources = new PathPackResources(modFile.getFileName() + ":" + folder, true, resourcePath);

        event.addRepositorySource(packConsumer -> packConsumer.accept(
                Pack.readMetaAndCreate(
                        "builtin/cuffedaddon/" + folder,
                        Component.literal(folder.replace('_', ' ')),
                        false,
                        name -> resources,
                        PackType.CLIENT_RESOURCES,
                        Pack.Position.TOP,
                        PackSource.BUILT_IN
                )
        ));
    }
}

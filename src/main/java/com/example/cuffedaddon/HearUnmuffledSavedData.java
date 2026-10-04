package com.example.cuffedaddon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HearUnmuffledSavedData extends SavedData {
    private static final String NAME = "cuffedaddon_hearunmuffled";

    private final Set<UUID> enabled = new HashSet<>();

    public static HearUnmuffledSavedData create() {
        return new HearUnmuffledSavedData();
    }

    public static HearUnmuffledSavedData load(CompoundTag tag) {
        HearUnmuffledSavedData data = new HearUnmuffledSavedData();
        ListTag list = tag.getList("enabled", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.enabled.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : enabled) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("enabled", list);
        return tag;
    }

    public boolean isEnabled(UUID playerId) {
        return enabled.contains(playerId);
    }

    public void set(UUID playerId, boolean value) {
        boolean changed = value ? enabled.add(playerId) : enabled.remove(playerId);
        if (changed) {
            setDirty();
        }
    }

    public static HearUnmuffledSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(
                HearUnmuffledSavedData::load,
                HearUnmuffledSavedData::create,
                NAME
        );
    }
}

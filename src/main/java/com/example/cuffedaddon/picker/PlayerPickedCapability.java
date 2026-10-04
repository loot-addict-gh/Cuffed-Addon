package com.example.cuffedaddon.picker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public class PlayerPickedCapability implements IPlayerPicked {

    private boolean picked = false;
    private int previousGameModeId = -1;
    private AnchorType anchorType = AnchorType.NONE;
    @Nullable
    private UUID anchorPlayerUUID;
    @Nullable
    private BlockPos anchorContainerPos;
    @Nullable
    private String anchorDimension;
    @Nullable
    private UUID anchorContainerEntityUUID;
    private int lostAnchorTicks = 0;

    @Override
    public boolean isPicked() {
        return picked;
    }

    @Override
    public void setPicked(boolean picked) {
        this.picked = picked;
    }

    @Override
    public int getPreviousGameModeId() {
        return previousGameModeId;
    }

    @Override
    public void setPreviousGameModeId(int id) {
        this.previousGameModeId = id;
    }

    @Override
    public AnchorType getAnchorType() {
        return anchorType;
    }

    @Override
    public void setAnchorType(AnchorType type) {
        this.anchorType = type;
    }

    @Override
    @Nullable
    public UUID getAnchorPlayerUUID() {
        return anchorPlayerUUID;
    }

    @Override
    public void setAnchorPlayerUUID(@Nullable UUID uuid) {
        this.anchorPlayerUUID = uuid;
    }

    @Override
    @Nullable
    public BlockPos getAnchorContainerPos() {
        return anchorContainerPos;
    }

    @Override
    public void setAnchorContainerPos(@Nullable BlockPos pos) {
        this.anchorContainerPos = pos;
    }

    @Override
    @Nullable
    public String getAnchorDimension() {
        return anchorDimension;
    }

    @Override
    public void setAnchorDimension(@Nullable String dimension) {
        this.anchorDimension = dimension;
    }

    @Override
    @Nullable
    public UUID getAnchorContainerEntityUUID() {
        return anchorContainerEntityUUID;
    }

    @Override
    public void setAnchorContainerEntityUUID(@Nullable UUID uuid) {
        this.anchorContainerEntityUUID = uuid;
    }

    @Override
    public int getLostAnchorTicks() {
        return lostAnchorTicks;
    }

    @Override
    public void setLostAnchorTicks(int ticks) {
        this.lostAnchorTicks = ticks;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Picked", picked);
        tag.putInt("PreviousGameModeId", previousGameModeId);
        tag.putString("AnchorType", anchorType.name());
        if (anchorPlayerUUID != null) {
            tag.putUUID("AnchorPlayerUUID", anchorPlayerUUID);
        }
        if (anchorContainerPos != null) {
            tag.putInt("AnchorX", anchorContainerPos.getX());
            tag.putInt("AnchorY", anchorContainerPos.getY());
            tag.putInt("AnchorZ", anchorContainerPos.getZ());
        }
        if (anchorDimension != null) {
            tag.putString("AnchorDimension", anchorDimension);
        }
        if (anchorContainerEntityUUID != null) {
            tag.putUUID("AnchorContainerEntityUUID", anchorContainerEntityUUID);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.picked = tag.getBoolean("Picked");
        this.previousGameModeId = tag.contains("PreviousGameModeId") ? tag.getInt("PreviousGameModeId") : -1;
        try {
            this.anchorType = AnchorType.valueOf(tag.getString("AnchorType"));
        } catch (IllegalArgumentException ex) {
            this.anchorType = AnchorType.NONE;
        }
        this.anchorPlayerUUID = tag.hasUUID("AnchorPlayerUUID") ? tag.getUUID("AnchorPlayerUUID") : null;
        this.anchorContainerPos = tag.contains("AnchorX")
                ? new BlockPos(tag.getInt("AnchorX"), tag.getInt("AnchorY"), tag.getInt("AnchorZ"))
                : null;
        this.anchorDimension = tag.contains("AnchorDimension") ? tag.getString("AnchorDimension") : null;
        this.anchorContainerEntityUUID = tag.hasUUID("AnchorContainerEntityUUID") ? tag.getUUID("AnchorContainerEntityUUID") : null;
        // Deliberately NOT persisted (see serializeNBT - there is no
        // "LostAnchorTicks" tag). It is a transient runtime counter, and
        // starting a fresh grace period after a restart, respawn or dimension
        // change is the safe direction: the anchor is expected to be briefly
        // unresolvable right after one of those, and carrying a nearly-expired
        // counter across it could auto-release someone whose item is fine.
        this.lostAnchorTicks = 0;
    }
}

package com.example.cuffedaddon.collar;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Plain backing implementation of {@link ICollared} - same shape as
 * {@code PlayerPickedCapability}/{@code LiePoseCapability}.
 */
public class CollaredCapability implements ICollared {

    private boolean collared = false;
    private int durability = 0;
    @Nullable
    private UUID bindingId;
    @Nullable
    private UUID captorUUID;
    private int nauseaDelayTicks = -1;
    private int armingTicks = 0;
    private int struggleCooldownTicks = 0;

    @Override
    public boolean isCollared() {
        return collared;
    }

    @Override
    public void setCollared(boolean collared) {
        this.collared = collared;
    }

    @Override
    public int getDurability() {
        return durability;
    }

    @Override
    public void setDurability(int durability) {
        this.durability = durability;
    }

    @Override
    @Nullable
    public UUID getBindingId() {
        return bindingId;
    }

    @Override
    public void setBindingId(@Nullable UUID bindingId) {
        this.bindingId = bindingId;
    }

    @Override
    @Nullable
    public UUID getCaptorUUID() {
        return captorUUID;
    }

    @Override
    public void setCaptorUUID(@Nullable UUID captorUUID) {
        this.captorUUID = captorUUID;
    }

    @Override
    public int getNauseaDelayTicks() {
        return nauseaDelayTicks;
    }

    @Override
    public void setNauseaDelayTicks(int ticks) {
        this.nauseaDelayTicks = ticks;
    }

    @Override
    public int getArmingTicks() {
        return armingTicks;
    }

    @Override
    public void setArmingTicks(int ticks) {
        this.armingTicks = ticks;
    }

    @Override
    public int getStruggleCooldownTicks() {
        return struggleCooldownTicks;
    }

    @Override
    public void setStruggleCooldownTicks(int ticks) {
        this.struggleCooldownTicks = ticks;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Collared", collared);
        tag.putInt("Durability", durability);
        if (bindingId != null) {
            tag.putUUID("BindingId", bindingId);
        }
        if (captorUUID != null) {
            tag.putUUID("CaptorUUID", captorUUID);
        }
        tag.putInt("NauseaDelayTicks", nauseaDelayTicks);
        tag.putInt("ArmingTicks", armingTicks);
        tag.putInt("StruggleCooldown", struggleCooldownTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.collared = tag.getBoolean("Collared");
        this.durability = tag.getInt("Durability");
        this.bindingId = tag.hasUUID("BindingId") ? tag.getUUID("BindingId") : null;
        this.captorUUID = tag.hasUUID("CaptorUUID") ? tag.getUUID("CaptorUUID") : null;
        this.nauseaDelayTicks = tag.contains("NauseaDelayTicks") ? tag.getInt("NauseaDelayTicks") : -1;
        // Absent on a collar saved before 1.4.40 - an existing collar is
        // already long since armed, so 0 is the right default, not the window.
        this.armingTicks = tag.contains("ArmingTicks") ? tag.getInt("ArmingTicks") : 0;
        this.struggleCooldownTicks = tag.contains("StruggleCooldown") ? tag.getInt("StruggleCooldown") : 0;
    }
}

package com.example.cuffedaddon.entity;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.init.ModEntityTypes;
import com.example.cuffedaddon.util.CuffedConfigBridge;
import com.lazrproductions.cuffed.entity.base.IAnchorableEntity;
import com.lazrproductions.cuffed.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Addon-owned twin of Cuffed's own ChainKnotEntity, used for the five new
 * anchor-point blocks (Iron Bars, Reinforced Bars, Chain, Lightning Rod,
 * End Rod) that Cuffed itself doesn't support anchoring to. Deliberately a
 * SEPARATE entity type rather than trying to reuse/extend ChainKnotEntity -
 * survives() is compiled into Cuffed's jar hardcoded to Forge's
 * Tags.Blocks.FENCES + vanilla TRIPWIRE_HOOK, so a new class overriding
 * survives() with our own block set is the only additive way to do this.
 *
 * Everything else about anchoring (chain-length/suffocation distance
 * checks, the chain-line render between an anchored entity and its anchor)
 * is already fully generic-by-Entity in Cuffed - see AnchoringEvents for the
 * confirmation this needed before writing this class. This entity gets that
 * behavior for free with zero duplication just by implementing the same
 * shape ChainKnotEntity does (a HangingEntity tied to a BlockPos).
 */
public class AnchorKnotEntity extends HangingEntity {

    public AnchorKnotEntity(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    public AnchorKnotEntity(Level world, BlockPos pos) {
        super(ModEntityTypes.ANCHOR_KNOT.get(), world, pos);
        this.setPos((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D);
    }

    @Override
    public void dropItem(@Nullable Entity p_31837_) {
        this.playSound(SoundEvents.CHAIN_BREAK, 1.0F, 1.0F);
    }

    @Override
    public boolean hurt(@Nonnull DamageSource source, float f) {
        if (source.getEntity() instanceof IAnchorableEntity a)
            if (a.getAnchor() == this)
                return false;
        return super.hurt(source, f);
    }

    @Override
    public InteractionResult interact(@Nonnull Player interactor, @Nonnull InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            if (((IAnchorableEntity) interactor).isAnchored())
                return InteractionResult.PASS;

            boolean flag = false;
            double maxDist = CuffedConfigBridge.getAnchoringSuffocationLength() + 5;
            List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(this.getX() - maxDist - 2.0D, this.getY() - maxDist - 2.0D, this.getZ() - maxDist - 2.0D,
                            this.getX() + maxDist + 2.0D, this.getY() + maxDist + 2.0D, this.getZ() + maxDist + 2.0D));

            for (LivingEntity entity : list) {
                IAnchorableEntity anchorableEntity = (IAnchorableEntity) entity;
                if (anchorableEntity.getAnchor() == interactor) {
                    anchorableEntity.setAnchoredTo(this);
                    flag = true;
                }
            }

            boolean flag1 = false;
            if (!flag) {
                level().playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.7f, 1);
                for (LivingEntity entity : list) {
                    IAnchorableEntity anchorableEntity = (IAnchorableEntity) entity;
                    if (anchorableEntity.isAnchored() && anchorableEntity.getAnchor() == this) {
                        anchorableEntity.setAnchoredTo(null);
                        flag1 = true;
                    }
                }
                this.discard();
            }

            if (flag)
                level().playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7f, 1);
            if (flag1)
                level().playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.7f, 1);

            if (flag || flag1) {
                this.gameEvent(GameEvent.BLOCK_ATTACH, interactor);
            }

            return InteractionResult.CONSUME;
        }
    }

    public static AnchorKnotEntity getOrCreateKnot(Level level, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7f, 1);

        for (AnchorKnotEntity knot : level.getEntitiesOfClass(AnchorKnotEntity.class,
                new AABB((double) i - 1.0D, (double) j - 1.0D, (double) k - 1.0D, (double) i + 1.0D, (double) j + 1.0D,
                        (double) k + 1.0D))) {
            if (knot.getPos().equals(pos)) {
                return knot;
            }
        }

        AnchorKnotEntity newEntity = new AnchorKnotEntity(level, pos);
        level.addFreshEntity(newEntity);
        return newEntity;
    }

    public static AnchorKnotEntity bindEntityToNewOrExistingKnot(LivingEntity entity, Level level, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7f, 1);

        for (AnchorKnotEntity knot : level.getEntitiesOfClass(AnchorKnotEntity.class,
                new AABB((double) i - 1.0D, (double) j - 1.0D, (double) k - 1.0D, (double) i + 1.0D, (double) j + 1.0D,
                        (double) k + 1.0D))) {
            if (knot.getPos().equals(pos)) {
                ((IAnchorableEntity) entity).setAnchor(knot);
                return knot;
            }
        }

        AnchorKnotEntity newKnot = new AnchorKnotEntity(level, pos);
        ((IAnchorableEntity) entity).setAnchor(newKnot);
        level.addFreshEntity(newKnot);
        return newKnot;
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos((double) Mth.floor(x) + 0.5D, (double) Mth.floor(y) + 0.5D, (double) Mth.floor(z) + 0.5D);
    }

    @Override
    protected void setDirection(@Nonnull Direction pFacingDirection) {
    }

    @Override
    public int getWidth() {
        return 9;
    }

    @Override
    public int getHeight() {
        return 9;
    }

    @Override
    protected float getEyeHeight(@Nonnull Pose p_31839_, @Nonnull EntityDimensions p_31840_) {
        return 0.0625F;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0D;
    }

    /**
     * The whole point of this class: same shape as ChainKnotEntity.survives(),
     * but checking our own five new blocks/config instead of Cuffed's
     * fences/tripwire hook. Reinforced Bars and its gapped/windowed variant
     * are two independently-toggleable config options.
     */
    @Override
    public boolean survives() {
        BlockState state = this.level().getBlockState(this.pos);

        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_IRON_BARS.get() && state.is(Blocks.IRON_BARS))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS.get() && state.is(ModBlocks.REINFORCED_BARS.get()))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS_WINDOWS.get() && state.is(ModBlocks.REINFORCED_BARS_GAPPED.get()))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_CHAIN.get() && state.is(Blocks.CHAIN))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_LIGHTNING_ROD.get() && state.is(Blocks.LIGHTNING_ROD))
            return true;
        if (CuffedAddonServerConfig.ANCHORING_ALLOW_ANCHORING_TO_END_ROD.get() && state.is(Blocks.END_ROD))
            return true;

        return false;
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.CHAIN_PLACE, 1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        return this.getPosition(partialTick).add(getLeashOffset(partialTick));
    }

    @Override
    public Vec3 getLeashOffset(float partialTick) {
        return new Vec3(0.0D, (double) getEyeHeight(), 0.0D);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.CHAIN);
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.375D,
                (double) this.pos.getZ() + 0.5D);
        double d0 = (double) this.getType().getWidth() / 2.0D;
        double d1 = (double) this.getType().getHeight();
        this.setBoundingBox(new AABB(this.getX() - d0, this.getY(), this.getZ() - d0, this.getX() + d0,
                this.getY() + d1, this.getZ() + d0));
    }
}

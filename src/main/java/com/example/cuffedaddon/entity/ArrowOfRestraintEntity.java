package com.example.cuffedaddon.entity;

import com.example.cuffedaddon.init.ModEntityTypes;
import com.example.cuffedaddon.items.ArrowOfRestraintItem;
import com.example.cuffedaddon.util.ArrowRestraintUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Arrow of Restraint in flight.
 *
 * <h2>This is a plain arrow plus one line of payload</h2>
 * It changes nothing about flight, damage, blocks or pickup - see
 * {@link AbstractReinforcedArrow} for the full list of what is inherited and
 * why. The parts that matter for this arrow specifically:
 *
 * <ul>
 *   <li><b>Damage.</b> {@code AbstractArrow}'s {@code baseDamage} is 2.0 by
 *       default and vanilla's own {@code Arrow} class does not change it, so
 *       this hits for exactly what a vanilla arrow hits for, draw strength and
 *       Power included.</li>
 *   <li><b>Missing.</b> The arrow plants itself in the block the vanilla way,
 *       and picking it up returns an Arrow of Restraint <i>still carrying its
 *       restraint</i>, because {@link #getPickupItem()} rebuilds the item from
 *       the payload rather than handing back a blank arrow.</li>
 *   <li><b>Hitting the shooter.</b> Inherited, and confirmed in game -
 *       [stated] tested every arrow on themselves at 1.6.0.</li>
 * </ul>
 *
 * <h2>The one hook</h2>
 * {@link #doPostHurtEffects(LivingEntity)} is the same hook vanilla's
 * {@code Arrow} uses to apply its potion effects, and
 * {@code AbstractArrow#onHitEntity} only calls it after {@code entity.hurt(...)}
 * has actually succeeded. So the ordering [stated] specified comes for free:
 * damage first, restraint second, and a hit that was absorbed entirely applies
 * no restraint and leaves the arrow to bounce off with its payload intact.
 */
public class ArrowOfRestraintEntity extends AbstractReinforcedArrow {

    private static final String TAG_RESTRAINT = "Restraint";

    /** The restraint item this arrow will try to apply. May be empty. */
    private ItemStack restraint = ItemStack.EMPTY;

    /** Registry/reload constructor. Required by {@code EntityType.Builder.of}. */
    public ArrowOfRestraintEntity(EntityType<? extends ArrowOfRestraintEntity> type, Level level) {
        super(type, level);
    }

    public ArrowOfRestraintEntity(Level level, LivingEntity shooter, ItemStack restraint) {
        super(ModEntityTypes.ARROW_OF_RESTRAINT.get(), level, shooter);
        this.restraint = restraint.isEmpty() ? ItemStack.EMPTY : restraint.copyWithCount(1);
    }

    public ItemStack getRestraint() {
        return restraint;
    }

    @Override
    protected ItemStack getPickupItem() {
        return ArrowOfRestraintItem.withRestraint(restraint);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (level().isClientSide || !firedFromReinforcedBow() || restraint.isEmpty()) {
            return;
        }
        if (!(target instanceof ServerPlayer hit)) {
            // Players only. Cuffed's restraint system is ServerPlayer-typed end
            // to end, so mobs - and the Fake Players mod's PathfinderMob, which
            // has its own parallel representation in the fakeplayer package -
            // take the arrow's damage and nothing more.
            return;
        }
        Entity owner = getOwner();
        ServerPlayer shooter = owner instanceof ServerPlayer sp ? sp : null;
        ArrowRestraintUtil.apply(hit, restraint, shooter);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!restraint.isEmpty()) {
            tag.put(TAG_RESTRAINT, restraint.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_RESTRAINT, Tag.TAG_COMPOUND)) {
            // .copy() first - ItemStack.of does NOT copy the tag it reads, so
            // without this the stack would share NBT with the entity's own save
            // tag. Same rule as ArrowOfRestraintItem#getRestraint.
            restraint = ItemStack.of(tag.getCompound(TAG_RESTRAINT).copy());
        } else {
            restraint = ItemStack.EMPTY;
        }
    }
}

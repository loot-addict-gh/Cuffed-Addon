package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.fakeplayer.IFakeRestrained;
import com.lazrproductions.cuffed.entity.base.IRestrainableEntity;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

/**
 * Makes Fake Players' entity an {@link IRestrainableEntity}, backed by this
 * addon's own capability. That is all it does.
 *
 * <h2>Why this is split from the job gating (1.5.1)</h2>
 * 1.5.0 had this and {@code FakePlayerJobMixin} as one class, and that was a
 * mistake. A mixin is all-or-nothing: if any single member fails to apply, the
 * WHOLE mixin is dropped. The job-gating half needs a {@code @Shadow} of one of
 * their private static fields and an {@code @Inject} into one of their private
 * methods, both of which are far more fragile than simply adding an interface -
 * and because the config is {@code "required": false} (so that an absent mod is a
 * no-op rather than a crash), a failure there was <b>silent</b>. One brittle
 * shadow could therefore take rendering down with it and say nothing.
 *
 * <p>Split in two, the fragile part can fail without costing the part that has
 * nothing to fail at. This one shadows nothing and injects nothing.
 *
 * <h2>Why targets= and remap=false</h2>
 * The target is named by STRING so this addon never needs Fake Players on its
 * compile classpath - that mod is optional and must stay that way. {@code remap =
 * false} keeps anything referenced here out of this project's hand-maintained
 * refmap, which is right because nothing here is vanilla-derived. <b>Neither of
 * these two mixins needs a refmap entry</b> - worth preserving if anyone extends
 * them.
 *
 * <h2>Why implementing IRestrainableEntity is the whole rendering story</h2>
 * Cuffed's {@code RestraintEntityLayer} and this addon's own wrap layers all read
 * the entity purely through {@code IRestrainableEntity}, and their
 * {@code FakePlayerModel extends PlayerModel}, so once the entity answers that
 * interface every existing restraint layer works on it unchanged. No new rendering
 * code was needed for the restraints themselves.
 *
 * <p><b>Not covered, on purpose:</b> restrained arm and leg POSES. Cuffed's
 * {@code HumanoidModelMixin} gates its pose work on {@code entity instanceof
 * Player}, not on {@code IRestrainableEntity}, so poses do not come along for
 * free and would need a mixin of this addon's own. Deferred until [stated] has
 * seen how restraints sit against Fake Players' own Standing/Sitting/Laying
 * poses, since that is exactly where the two systems would collide.
 */
@Mixin(targets = "dev.duzo.players.entities.FakePlayerEntity", remap = false)
public abstract class FakePlayerEntityMixin implements IRestrainableEntity {

    @Nullable
    private IFakeRestrained cuffedaddon$restraints() {
        return ((Entity) (Object) this).getCapability(ModCapabilities.FAKE_RESTRAINED).orElse(null);
    }

    // ------------------------------------------------- IRestrainableEntity

    @Override
    public boolean isRestrained() {
        IFakeRestrained cap = cuffedaddon$restraints();
        return cap != null && cap.isRestrained();
    }

    /**
     * Cuffed packs "which slots are occupied" into a small bitfield. Rebuilt here
     * from the capability rather than stored, so it can never disagree with the
     * three ids.
     */
    @Override
    public int getRestraintCode() {
        IFakeRestrained cap = cuffedaddon$restraints();
        if (cap == null) {
            return 0;
        }
        int code = 0;
        if (cap.has(RestraintType.Arm)) {
            code |= 1;
        }
        if (cap.has(RestraintType.Leg)) {
            code |= 2;
        }
        if (cap.has(RestraintType.Head)) {
            code |= 4;
        }
        return code;
    }

    @Override
    public ResourceLocation getArmRestraintId() {
        return cuffedaddon$id(RestraintType.Arm);
    }

    @Override
    public ResourceLocation getLegRestraintId() {
        return cuffedaddon$id(RestraintType.Leg);
    }

    @Override
    public ResourceLocation getHeadRestraintId() {
        return cuffedaddon$id(RestraintType.Head);
    }

    /**
     * What Cuffed's three id getters return for an EMPTY slot, which is not null.
     *
     * <h2>Why null was the wrong answer</h2>
     * Cuffed's own implementation of this interface, on {@code PlayerMixin},
     * builds its answer with {@code ResourceLocation.bySeparator(<stored>, ':')}
     * over a string that is empty when nothing is equipped - so it hands back
     * {@code minecraft:} rather than null, and its callers are written for that.
     * {@code ModClientEvents}, {@code SimpleVoiceChatCompat} and
     * {@code PilloryEntityLayer} all call {@code .equals(...)} straight on the
     * result. None of those three can reach a fake player today, so returning
     * null was latent rather than live - but it is one added render layer or one
     * Cuffed update away from a NullPointerException thrown from the render
     * thread every frame, and matching their contract costs nothing.
     */
    @Unique
    private static final ResourceLocation CUFFEDADDON$NO_RESTRAINT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "");

    private ResourceLocation cuffedaddon$id(RestraintType type) {
        IFakeRestrained cap = cuffedaddon$restraints();
        if (cap == null) {
            return CUFFEDADDON$NO_RESTRAINT;
        }
        ResourceLocation id = cap.getRestraintId(type);
        return id == null ? CUFFEDADDON$NO_RESTRAINT : id;
    }

    @Override
    public boolean getArmsAreEnchanted() {
        IFakeRestrained cap = cuffedaddon$restraints();
        return cap != null && cap.isEnchanted(RestraintType.Arm);
    }

    @Override
    public boolean getLegsAreEnchanted() {
        IFakeRestrained cap = cuffedaddon$restraints();
        return cap != null && cap.isEnchanted(RestraintType.Leg);
    }

    @Override
    public boolean getHeadIsEnchanted() {
        IFakeRestrained cap = cuffedaddon$restraints();
        return cap != null && cap.isEnchanted(RestraintType.Head);
    }

    // The setters exist because IRestrainableEntity declares them. Nothing in
    // this addon drives a fake player's restraints through this interface -
    // FakePlayerRestraintUtil owns every transition and syncs it - so these write
    // the capability WITHOUT syncing, and are effectively unused. Left functional
    // rather than no-op so that any Cuffed code path that does reach for them
    // still behaves sanely.

    @Override
    public void setRestraintCode(int v) {
        // Derived from the ids, so there is nothing meaningful to set.
    }

    @Override
    public void setArmRestraintId(ResourceLocation v) {
        cuffedaddon$setId(RestraintType.Arm, v);
    }

    @Override
    public void setLegRestraintId(ResourceLocation v) {
        cuffedaddon$setId(RestraintType.Leg, v);
    }

    @Override
    public void setHeadRestraintId(ResourceLocation v) {
        cuffedaddon$setId(RestraintType.Head, v);
    }

    private void cuffedaddon$setId(RestraintType type, @Nullable ResourceLocation v) {
        IFakeRestrained cap = cuffedaddon$restraints();
        if (cap == null) {
            return;
        }
        // The other half of the contract above: Cuffed clears a slot by SETTING
        // the empty id, not by setting null, so an empty path has to mean empty
        // here too. Storing it verbatim would leave a slot that reads as occupied
        // by a restraint that does not exist.
        boolean empty = v == null || v.getPath().isEmpty();
        cap.setRestraintId(type, empty ? null : v);
        if (empty) {
            cap.setEnchanted(type, false);
            cap.setStack(type, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    @Override
    public void setArmsEnchanted(boolean v) {
        cuffedaddon$setEnchanted(RestraintType.Arm, v);
    }

    @Override
    public void setLegsEnchanted(boolean v) {
        cuffedaddon$setEnchanted(RestraintType.Leg, v);
    }

    @Override
    public void setHeadEnchanted(boolean v) {
        cuffedaddon$setEnchanted(RestraintType.Head, v);
    }

    private void cuffedaddon$setEnchanted(RestraintType type, boolean v) {
        IFakeRestrained cap = cuffedaddon$restraints();
        if (cap != null) {
            cap.setEnchanted(type, v);
        }
    }
}

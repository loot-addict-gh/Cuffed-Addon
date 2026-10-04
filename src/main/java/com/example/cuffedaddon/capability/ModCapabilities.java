package com.example.cuffedaddon.capability;

import com.example.cuffedaddon.collar.ICollared;
import com.example.cuffedaddon.fakeplayer.IFakeDetained;
import com.example.cuffedaddon.fakeplayer.IFakeRestrained;
import com.example.cuffedaddon.picker.IPlayerPicked;
import com.example.cuffedaddon.pose.ILiePose;
import com.example.cuffedaddon.pose.IWallPose;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class ModCapabilities {
    // Bed Restraint's IBedRestraint capability was removed with the rest of
    // that feature for the 1.2.x rebuild - LIE_POSE is its from-scratch
    // replacement foundation (see pose/ package).
    public static final Capability<ILiePose> LIE_POSE =
            CapabilityManager.get(new CapabilityToken<>() {});

    // Wall Restraint - merged in from the temporary standalone wallrestraint
    // project. Same shape as LIE_POSE, registered by WallPoseCapabilityEvents
    // (see pose/ package).
    public static final Capability<IWallPose> WALL_POSE =
            CapabilityManager.get(new CapabilityToken<>() {});

    // Player Picker - tracks a captured player's own state (previous
    // gamemode, current follow anchor). See picker/ package.
    public static final Capability<IPlayerPicked> PLAYER_PICKED =
            CapabilityManager.get(new CapabilityToken<>() {});

    // Shock Collar (1.4.32) - the addon's OWN fourth restraint slot, kept
    // separate from Cuffed's three (head/arms/legs) because
    // IRestrainableCapability is hardcoded to exactly those three and has no
    // fourth to register into. See collar/ICollared for the full write-up,
    // including why this is a custom capability rather than a Curios slot.
    public static final Capability<ICollared> COLLARED =
            CapabilityManager.get(new CapabilityToken<>() {});

    // Fake Players compatibility (1.5.0) - restraint state for one of that mod's
    // fake player entities. Separate from everything Cuffed has because its
    // entity is a PathfinderMob, not a Player, and Cuffed is Player/ServerPlayer
    // -typed from the capability attach all the way down to AbstractRestraint's
    // own method signatures. See fakeplayer/IFakeRestrained for the full
    // reasoning and for why three ids and three flags are all it has to hold.
    public static final Capability<IFakeRestrained> FAKE_RESTRAINED =
            CapabilityManager.get(new CapabilityToken<>() {});

    // Pillory state for a fake player (1.5.11). The two OTHER stationary
    // restraints - Bed and Wall - need no new capability at all: they are this
    // addon's own features and reuse LIE_POSE/WALL_POSE above, which are simply
    // attached to their entity too. Cuffed's pillory could not be reused because
    // its detain state is four EntityDataAccessors keyed to Player.class inside
    // Cuffed's own PlayerMixin - see fakeplayer/IFakeDetained.
    public static final Capability<IFakeDetained> FAKE_DETAINED =
            CapabilityManager.get(new CapabilityToken<>() {});
}

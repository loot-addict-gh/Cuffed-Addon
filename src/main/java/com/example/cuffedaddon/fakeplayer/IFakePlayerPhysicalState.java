package com.example.cuffedaddon.fakeplayer;

/**
 * Read/write access to a Fake Players fake player's own STANDING/SITTING/LAYING
 * pose, grafted onto their entity by {@code FakePlayerPhysicalStateMixin}.
 *
 * <h2>Why an interface rather than just calling their setter</h2>
 * Same rule the whole {@code fakeplayer} package lives under: nothing outside the
 * mixins may name a type from that mod, because merely executing a code path that
 * resolves one of their types throws {@code NoClassDefFoundError} when the mod is
 * absent. Their setter takes a {@code FakePlayerEntity.PhysicalState}, so it cannot
 * be called from ordinary code at all. The pose is stored as that enum's ORDINAL in
 * a synched data slot ({@code entityData.set(PHYSICAL_STATE, state.ordinal())}), so
 * an int crosses the boundary perfectly well and names nothing.
 *
 * <h2>The ordinals, and the same fragility the job gating has</h2>
 * Verified against 2.2.0: <b>0 STANDING, 1 SITTING, 2 LAYING</b>. Like
 * {@code FakePlayerJobRules}' job ordinals, there is no name in the data to
 * validate against - they store only the ordinal. Appending a state is safe;
 * INSERTING one would silently shift these. <b>Re-verify whenever Fake Players
 * updates.</b>
 *
 * <p>Two of the three states leak through vanilla and could in principle be read
 * without this interface at all ({@code isNoAi()} and {@code isSleeping()} are both
 * overridden to return true for LAYING), but SITTING has no vanilla tell, and
 * nothing at all can be WRITTEN that way - which is what this is really for.
 */
public interface IFakePlayerPhysicalState {

    int STANDING = 0;
    int SITTING = 1;
    int LAYING = 2;

    int cuffedaddon$getPhysicalState();

    /**
     * Writes the pose directly to the synched data slot, bypassing their own setter
     * - which matters, because {@code FakePlayerPhysicalStateMixin} CANCELS that
     * setter while a stationary restraint is on. Forcing the pose and refusing the
     * pose have to be able to coexist.
     */
    void cuffedaddon$setPhysicalState(int ordinal);
}

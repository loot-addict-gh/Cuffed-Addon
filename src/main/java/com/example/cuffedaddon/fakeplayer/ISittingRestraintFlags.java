package com.example.cuffedaddon.fakeplayer;

/**
 * A scrap of state carried on the MODEL instance, so that Fake Players' own
 * {@code translateSitting} can know what the entity it is about to pose is wearing.
 *
 * <h2>Why the model and not the entity</h2>
 * {@code FakePlayerModel.translateSitting()} takes no arguments and has no field
 * pointing back at the entity - it is called from {@code setupAnim} purely for its
 * side effects on {@code this}. So the mixin that replaces it cannot ask the entity
 * anything. A static "entity currently being rendered" would work on the render
 * thread but goes stale the moment anything renders between the two calls; a field
 * on the model instance cannot, because {@code HumanoidModel#setupAnim} and
 * {@code translateSitting} are two steps of ONE call on ONE model object:
 * <pre>
 *   FakePlayerModel.setupAnim(entity, ...) {
 *       super.setupAnim(entity, ...);        // HumanoidModel - FakePlayerArmPoseMixin writes the flags here
 *       if (entity.isSitting()) translateSitting();   // FakePlayerSittingPoseMixin reads them here
 *   }
 * </pre>
 * Nothing can run in between. The model is shared across every fake player on
 * screen, which is exactly why the flags are rewritten on every single call rather
 * than only when something is restrained.
 *
 * <p>Implemented by {@code FakePlayerArmPoseMixin}, which targets
 * {@code HumanoidModel} - so every humanoid model carries the field, and
 * {@code FakePlayerModel} inherits it for free.
 */
public interface ISittingRestraintFlags {

    /** The arms are in one of Cuffed's tied poses and must not be disturbed. */
    int ARMS_TIED = 1;

    /** A leg restraint is on, so the legs sit closed rather than splayed. */
    int LEGS_BOUND = 2;

    int cuffedaddon$sittingRestraintFlags();

    void cuffedaddon$setSittingRestraintFlags(int flags);
}

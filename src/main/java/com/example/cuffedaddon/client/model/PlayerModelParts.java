package com.example.cuffedaddon.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

/**
 * Reflective access to the limb {@link ModelPart}s of a {@link PlayerModel}, and to
 * the four outer-skin-layer parts that belong to it rather than to
 * {@link HumanoidModel}.
 *
 * <h2>Why reflection and not {@code @Shadow}</h2>
 * Carried over verbatim from {@code HumanoidModelLiePoseMixin}, which found the hard
 * way that {@code @Shadow} field resolution through this project's hand-maintained
 * refmap kept failing despite correct SRG names, while reflection only needs the name
 * to be right. SRG names are tried first (a real installed Forge client, where vanilla
 * is obfuscated) and the official names second (a Gradle {@code runClient} dev run).
 *
 * <p>Extracted into its own class at 1.5.11 because a second place now needs it -
 * {@code FakePlayerSittingPoseMixin} - and because it needs the four PlayerModel
 * fields, which the existing mixins never did. The two existing mixins keep their own
 * private copies; they are confirmed working and not worth churning.
 *
 * <h2>The eight fields, and where the names came from</h2>
 * The four HumanoidModel ones match the existing mixins. The four PlayerModel ones
 * were read straight out of a CFR decompile of Fake Players' own
 * {@code FakePlayerModel.translateSitting}, which touches each limb next to its own
 * outer layer - so the pairing (arm with sleeve, leg with pants) is observed rather
 * than assumed.
 */
public final class PlayerModelParts {

    private static final Logger LOGGER = LoggerFactory.getLogger("cuffedaddon/modelparts");

    private static final Field RIGHT_ARM = find(HumanoidModel.class, "f_102811_", "rightArm");
    private static final Field LEFT_ARM = find(HumanoidModel.class, "f_102812_", "leftArm");
    private static final Field RIGHT_LEG = find(HumanoidModel.class, "f_102813_", "rightLeg");
    private static final Field LEFT_LEG = find(HumanoidModel.class, "f_102814_", "leftLeg");

    private static final Field RIGHT_SLEEVE = find(PlayerModel.class, "f_103375_", "rightSleeve");
    private static final Field LEFT_SLEEVE = find(PlayerModel.class, "f_103374_", "leftSleeve");
    private static final Field RIGHT_PANTS = find(PlayerModel.class, "f_103377_", "rightPants");
    private static final Field LEFT_PANTS = find(PlayerModel.class, "f_103376_", "leftPants");

    private PlayerModelParts() {
    }

    @Nullable
    private static Field find(Class<?> owner, String srgName, String officialName) {
        Field field;
        try {
            field = owner.getDeclaredField(srgName);
        } catch (NoSuchFieldException srgFailed) {
            try {
                field = owner.getDeclaredField(officialName);
            } catch (NoSuchFieldException officialFailed) {
                LOGGER.error("[cuffedaddon] Could not resolve {}#{} under either SRG or official names - "
                        + "restraint-aware sitting poses will not apply.", owner.getSimpleName(), officialName,
                        officialFailed);
                return null;
            }
        }
        field.setAccessible(true);
        return field;
    }

    @Nullable
    private static ModelPart get(@Nullable Field field, Object model) {
        try {
            return field != null ? (ModelPart) field.get(model) : null;
        } catch (IllegalAccessException | ClassCastException e) {
            return null;
        }
    }

    @Nullable
    public static ModelPart rightArm(Object model) {
        return get(RIGHT_ARM, model);
    }

    @Nullable
    public static ModelPart leftArm(Object model) {
        return get(LEFT_ARM, model);
    }

    @Nullable
    public static ModelPart rightLeg(Object model) {
        return get(RIGHT_LEG, model);
    }

    @Nullable
    public static ModelPart leftLeg(Object model) {
        return get(LEFT_LEG, model);
    }

    @Nullable
    public static ModelPart rightSleeve(Object model) {
        return get(RIGHT_SLEEVE, model);
    }

    @Nullable
    public static ModelPart leftSleeve(Object model) {
        return get(LEFT_SLEEVE, model);
    }

    @Nullable
    public static ModelPart rightPants(Object model) {
        return get(RIGHT_PANTS, model);
    }

    @Nullable
    public static ModelPart leftPants(Object model) {
        return get(LEFT_PANTS, model);
    }

    /**
     * Copies just the three rotation fields from a limb onto its outer skin layer -
     * what {@code PlayerModel#setupAnim} does for every part with {@code copyFrom},
     * narrowed to rotations because nothing here moves a part's pivot.
     */
    public static void copyRotation(@Nullable ModelPart from, @Nullable ModelPart to) {
        if (from == null || to == null) {
            return;
        }
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
    }
}

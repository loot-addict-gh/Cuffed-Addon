package com.example.cuffedaddon.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * The worn Key Necklace, drawn by {@code KeyNecklaceLayer}.
 *
 * <p>[stated]: <i>"it should have the same height and texture detail as the
 * shock collar."</i> So this is {@code ShockCollarModel}'s geometry exactly -
 * one torso-only box, {@code texOffs(16,0)}, {@code addBox(-4,0,-2, 8,12,4)},
 * on a 64x32 UV canvas - which means the necklace sits at the same neck height,
 * its texture has the same pixel-for-pixel layout, and the body half of the
 * collar's texture can be opened side by side with this one as a reference.
 * Read {@code ShockCollarModel}'s own doc for why a neck item is a full torso
 * wrap with the visible part PAINTED into the top rows rather than cut into the
 * mesh, and for the 2-tall band box that was tried at 1.5.23 and reverted.
 *
 * <h2>Inflate is 0.45f - one step OUTSIDE the collar</h2>
 * The established ladder in this addon is Rope 0.25f, Straitjacket 0.35f, Shock
 * Collar 0.4f, and each rung exists because two wraps at the same inflate
 * z-fight when a player wears both. A collar and a necklace are explicitly
 * meant to be wearable together, so the necklace needs a rung of its own, and
 * outside is the right direction: a necklace hangs over a collar, not under it.
 *
 * <p><b>This is the one number in the feature worth eyeballing in game.</b>
 * [stated] has the collar tuned to 0.4f so that the Leashable Collars mod's own
 * collar still shows around it on their server; whether 0.45f clears that one
 * too is not something that can be checked from here. It is a single constant
 * below if it needs to move.
 */
public class KeyNecklaceModel<T extends LivingEntity> extends HumanoidModel<T> {

    /** See this class's doc - one rung outside the Shock Collar's 0.4f. */
    private static final float INFLATE = 0.45f;

    public KeyNecklaceModel(ModelPart root) {
        super(root);
    }

    /**
     * Texture layout (64x32 UV space; the shipped PNG is 256x128, i.e. 4x,
     * exactly like {@code textures/entity/shock_collar.png}).
     *
     * <p>One box, {@code texOffs(16,0)}, {@code addBox(-4,0,-2, 8,12,4)} - so
     * W=8, H=12, D=4. Unwrapped by vanilla's standard box layout
     * ({@code top = (u+D, v)} and so on, the formula recorded in the project
     * notes), that gives these six faces. Left and right are the PLAYER's own
     * left and right.
     *
     * <pre>
     *   face     UV texels (64x32)        PNG pixels (256x128)      size
     *   -------  -----------------------  ------------------------  -----------
     *   top      x[20,28)  y[0,4)         x[80,112)   y[0,16)       8 x 4  (32x16)
     *   bottom   x[28,36)  y[0,4)         x[112,144)  y[0,16)       8 x 4  (32x16)
     *   right    x[16,20)  y[4,16)        x[64,80)    y[16,64)      4 x 12 (16x48)
     *   front    x[20,28)  y[4,16)        x[80,112)   y[16,64)      8 x 12 (32x48)
     *   left     x[28,32)  y[4,16)        x[112,128)  y[16,64)      4 x 12 (16x48)
     *   back     x[32,40)  y[4,16)        x[128,160)  y[16,64)      8 x 12 (32x48)
     * </pre>
     *
     * <p>Everything outside those rectangles - all of x[0,16) and x[40,64) in
     * texel space, and the whole lower half of the canvas - is never sampled and
     * is shipped transparent, per [stated]'s standing rule that a placeholder
     * has its out-of-footprint pixels erased.
     *
     * <p><b>Where the necklace reads from.</b> The box runs from the neck
     * (local y=0, texture row 4) down to the waist (row 16), so one texel is
     * roughly one twelfth of a torso:
     *
     * <ul>
     *   <li><b>The cord</b> belongs on texel rows 4-5 of the four side faces,
     *       which is exactly where the Shock Collar's band is painted - that is
     *       what "the same height" means here.</li>
     *   <li><b>A pendant</b> can hang as far down the FRONT face as wanted,
     *       rows 6 and below, without any model change. This is the room the
     *       full-torso box was kept for.</li>
     *   <li><b>The TOP CAP is not wasted art.</b> The head cube rotates and
     *       reveals the pixels underneath it, so the strap where it passes over
     *       the shoulders and behind the neck reads from there - [stated]'s own
     *       correction, recorded against the collar.</li>
     * </ul>
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(INFLATE);

        // Unused parts - kept empty, same pattern as every other wrap model
        // here and as Cuffed's own DuckTape*Model classes.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        // PartPose.ZERO deliberately: KeyNecklaceLayer calls copyPropertiesTo
        // every frame, which overwrites each part's position and rotation from
        // the real player skeleton, so any pose baked in here is discarded.
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 0)
                        .addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, inflate),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }
}

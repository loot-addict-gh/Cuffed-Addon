package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.trap.TrapBlockStates;
import com.lazrproductions.cuffed.blocks.PilloryBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grafts this addon's {@code armed} property onto Cuffed's pillory, so a
 * redstone-powered pillory can latch after it fires and an observer can see it
 * do so.
 *
 * <h2>Why a new block state property is unavoidable here</h2>
 * [stated] asked for the trap's redstone state to be "detectable via observer".
 * An observer pulses on a block STATE change at the position it faces and on
 * nothing else - not a capability, not SavedData, not a block entity. So the one
 * bit the trap has to remember genuinely has to live in the block state, and the
 * pillory is Cuffed's block, so a mixin is the only way to put it there. Our own
 * Wall Restraint declares the identical property directly.
 *
 * <h2>Why this is safe, checked rather than assumed</h2>
 * <ul>
 *   <li><b>Models don't break.</b> Minecraft's {@code variants} matcher only
 *       tests the properties a variant key actually lists, and Cuffed's
 *       {@code pillory.json} already relies on that - its {@code half=lower}
 *       keys omit {@code closed} entirely. A key that never mentions
 *       {@code armed} therefore keeps matching every state. No replacement
 *       resource for Cuffed's namespace is needed, and none is shipped.</li>
 *   <li><b>Old worlds are fine.</b> A saved pillory with no {@code armed} tag
 *       deserialises to the block's default, and a saved one WITH the tag
 *       degrades gracefully if this addon is ever removed - the unknown property
 *       is dropped and the rest of the state is kept.</li>
 *   <li><b>The default value is not relied upon.</b> We do not inject into the
 *       constructor to force it, because {@code registerDefaultState} would have
 *       to be shadowed (another vanilla name to get right) for no real gain.
 *       Every read goes through {@code TrapBlockStates#isArmed}, and the reset
 *       path writes {@code armed = true} whenever the signal is low, so a fresh
 *       pillory settles into the right value on its first evaluation either
 *       way.</li>
 * </ul>
 *
 * <h2>The two method names, and why there is no refmap entry</h2>
 * {@code createBlockStateDefinition} is a VANILLA method that Cuffed overrides,
 * so unlike {@link PilloryBlockFakePlayerMixin} - which targets a method Cuffed
 * declares itself - this one IS remapped: in a built jar the method is called
 * {@code m_7926_}, verified by running {@code javap} over
 * {@code PilloryBlock.class} inside the shipped Cuffed-1.20.1-1.3.15.jar rather
 * than trusting a remembered mapping. Both names are listed with
 * {@code remap = false} and {@code require = 1}: the SRG name matches in a
 * production jar, the readable one matches in a dev run, exactly one of them
 * ever resolves, and this project's hand-maintained
 * {@code mixins.cuffedaddon.refmap.json} needs no new entry. That matters
 * because a wrong refmap entry here would be a mod-load crash, not a quiet
 * failure.
 */
@Mixin(value = PilloryBlock.class, remap = false)
public abstract class PilloryArmedMixin {

    @Inject(method = { "createBlockStateDefinition", "m_7926_" }, at = @At("TAIL"),
            remap = false, require = 1)
    private void cuffedaddon$addArmedProperty(StateDefinition.Builder<Block, BlockState> builder,
                                               CallbackInfo ci) {
        builder.add(TrapBlockStates.ARMED);
    }
}

package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.event.ModClientEvents;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ClientChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Cuffed muffling your chat when the gag is an Illusion.
 *
 * <p>Cuffed's own muffling is one {@code ClientChatEvent} listener that checks the
 * synced head-restraint id against duct tape's and rewrites the message in place.
 * There is no polite way to undo that from another listener - it does not cancel
 * the event, it edits it, so running afterwards would mean stashing the original
 * text at high priority and restoring it at low, across two handlers, for every
 * chat message anyone ever sends. Cancelling Cuffed's handler outright when the
 * head restraint is an Illusion is both smaller and exact.
 *
 * <p>This addon's OWN muffling (rope, the four combos, the Straitjacket) lives in
 * {@code ClientEvents#onChat} and is skipped there directly - no mixin needed for
 * the half we control.
 *
 * <p>The check reads the local player's capability rather than the synced
 * restraint id, because the id alone cannot say which enchantment is on it. That
 * is fine precisely here: a {@code ClientChatEvent} only ever fires for the player
 * doing the typing, and the local player's own capability IS populated on the
 * client by Cuffed's equip packet.
 *
 * <p>Cuffed's class, so no refmap entry.
 */
@Mixin(value = ModClientEvents.class, remap = false)
public class CuffedChatMuffleIllusionMixin {

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$dontMuffleAnIllusion(ClientChatEvent event, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (cap != null && IllusionUtil.isIllusion(cap.getRestraint(RestraintType.Head))) {
            ci.cancel();
        }
    }
}

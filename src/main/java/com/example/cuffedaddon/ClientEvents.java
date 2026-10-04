package com.example.cuffedaddon;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.OriginalChatPacket;
import com.example.cuffedaddon.restraints.BundleDuctTapeRestraint;
import com.example.cuffedaddon.restraints.BundleRopeRestraint;
import com.example.cuffedaddon.restraints.RopeHeadRestraint;
import com.example.cuffedaddon.restraints.SleepMaskDuctTapeRestraint;
import com.example.cuffedaddon.restraints.SleepMaskRopeRestraint;
import com.example.cuffedaddon.restraints.StraitjacketHeadRestraint;
import com.lazrproductions.cuffed.entity.base.IRestrainableEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * MODIFIED existing file - the change from your original version is the
 * addition of an unconditional check for RopeHeadRestraint.ID below.
 *
 * Why this exists at all: Cuffed's OWN chat-muffling (in its
 * ModClientEvents) is hardcoded to only check DuckTapeHeadRestraint.ID -
 * always on, no gamerule - and it's compiled into Cuffed's jar so we can't
 * extend it. This class is the addon's own parallel mechanism.
 *
 * Every restraint muffled here is muffled UNCONDITIONALLY. Rope is meant to
 * behave exactly like Duck Tape. The 4 combination restraints each pair a
 * vision item with a speech item on purpose, so there's no scenario where one
 * should not mute. Straitjacket blocks both speech and vision by design -
 * that's the whole reason it isn't split into a combo. None of them can reuse
 * Cuffed's own hardcoded Duck-Tape-ID check (their IDs are not that one), so
 * each needs its own check here.
 *
 * 1.4.41: there used to be one CONDITIONAL entry - Bundle, gated behind the
 * bundleMuffle gamerule, since Bundle doesn't mute chat in vanilla Cuffed.
 * [stated] had it removed as unused. It was the only thing in the addon that
 * needed a gamerule value mirrored to clients; see ModGameRules for what went
 * with it.
 */
@Mod.EventBusSubscriber(modid = "cuffedaddon", value = {Dist.CLIENT})
public class ClientEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ClientChatEvent event) {
        String original = event.getMessage();
        NetworkHandler.CHANNEL.sendToServer(new OriginalChatPacket(original));
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer instanceof IRestrainableEntity) {
            IRestrainableEntity restrainable = (IRestrainableEntity) localPlayer;
            ResourceLocation headRestraintId = restrainable.getHeadRestraintId();
            boolean ropeMuffled = RopeHeadRestraint.ID.equals(headRestraintId);
            boolean comboMuffled = BundleDuctTapeRestraint.ID.equals(headRestraintId)
                    || BundleRopeRestraint.ID.equals(headRestraintId)
                    || SleepMaskDuctTapeRestraint.ID.equals(headRestraintId)
                    || SleepMaskRopeRestraint.ID.equals(headRestraintId);
            // Straitjacket: unconditional too, same reasoning as the 4
            // combos - it always blocks both speech and vision by design
            // (that's the whole point of not splitting it into a combo),
            // so there's no scenario where it shouldn't mute.
            boolean straitjacketMuffled = StraitjacketHeadRestraint.ID.equals(headRestraintId);
            // An Illusion gag looks like a gag and does nothing, so it does not
            // muffle either. Read from the capability rather than the synced
            // restraint id, because the id cannot say which enchantment is on it;
            // that is safe here because ClientChatEvent only ever fires for the
            // player doing the typing, whose own capability IS populated client
            // side. Cuffed's own duct-tape muffling is cancelled separately - see
            // CuffedChatMuffleIllusionMixin.
            IRestrainableCapability cap =
                    CuffedAPI.Capabilities.getRestrainableCapability(localPlayer);
            boolean illusion = cap != null
                    && IllusionUtil.isIllusion(cap.getRestraint(RestraintType.Head));

            if (!illusion && (ropeMuffled || comboMuffled || straitjacketMuffled)) {
                event.setMessage(Muffler.muffle(original));
            }
        }
    }
}

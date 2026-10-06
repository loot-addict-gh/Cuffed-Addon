package com.example.cuffedaddon.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * The Key Necklace - a Handcuffs Key on a length of rope, worn round the neck
 * in a slot of its own.
 *
 * <p>[stated]'s brief: <i>"the point would be to give players a key that they
 * wont be able to get when arm restrained."</i> That is the whole item. Worn, it
 * is a key you are carrying in plain sight that your own bound hands cannot
 * reach - and that anyone else can lift off you with an empty-handed crouching
 * right-click, ahead of any restraint they might also be taking off.
 *
 * <h2>Why this class has almost nothing in it</h2>
 * Deliberate, and worth stating so nobody goes looking in the wrong place:
 *
 * <ul>
 *   <li><b>No {@code use}</b> override. Both self gestures are LEFT clicks, read
 *       from the arm swing in {@code interact.SelfGestureEvents}, so there is
 *       nothing for a right-click-on-air to do. Inheriting {@code Item}'s PASS
 *       is exactly right: the click falls through as if you were holding a
 *       stick.</li>
 *   <li><b>No {@code interactLivingEntity}</b> override. Applying it to another
 *       player is a HIGHEST-priority {@code EntityInteract} listener in
 *       {@code necklace.NecklaceEvents}, for the same reason every other
 *       player-targeting item in this addon does it that way: Cuffed's own
 *       interaction dispatch competes for that very click, and winning the race
 *       explicitly beats finding out the hard way whether
 *       {@code interactLivingEntity} is even reached.</li>
 *   <li><b>No {@code AbstractRestraintKeyItem}</b>, and this one is
 *       load-bearing. If the necklace were itself a key item, right-clicking a
 *       handcuffed player while holding it would unlock them - and the entire
 *       point is that the key inside is not usable until the necklace has been
 *       taken apart in a crafting grid. So it is a plain {@code Item} that
 *       Cuffed's restraint dispatch does not recognise at all.</li>
 *   <li><b>No {@code AbstractRestraint}</b> registration. It restrains nothing;
 *       see {@code necklace.INecklaced} for the full note on why it is a worn
 *       item rather than a fifth restraint.</li>
 * </ul>
 *
 * <p>It stacks normally. There is no per-instance NBT to keep two of them apart
 * (unlike the Shock Collar and the Player Picker, both of which are
 * {@code stacksTo(1)} precisely because there is), and the worn one is stored as
 * a real {@link ItemStack} in the capability, so even a renamed necklace comes
 * back off exactly as it went on.
 */
public class KeyNecklaceItem extends Item {

    public KeyNecklaceItem(Properties properties) {
        super(properties);
    }

    /**
     * One line, in Cuffed's own "removed with" shape - the grey label followed by
     * the method in white.
     *
     * <p><b>The label is OUR key, not Cuffed's</b> ({@code info.cuffedaddon.removed_with}
     * rather than {@code info.cuffed.restraint_type.my_key}). Cuffed's reads
     * "Restriant is removed with:", and [stated] asked for that typo gone from
     * every instance. Our own key is the only way to fix it unconditionally for
     * the items this addon draws - a lang override can only reach Cuffed's own
     * items, and only when its resource pack is enabled. The four items in this
     * addon that show the line were all switched together, so they cannot drift
     * apart. The VALUE stays Cuffed's key ({@code info.cuffed.empty_hand}) - that
     * one has no typo and tracking Cuffed's wording for it is still right.
     *
     * <p>No restraint-type lines above it, because this is not a restraint and
     * has no slot in Cuffed's sense - which is also why there is no leading
     * {@code Component.empty()}: that blank line exists to separate the type
     * lines from this one, and there are none to separate from.
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
            @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        tooltip.add(Component.translatable("info.cuffedaddon.removed_with")
                .withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(Component.translatable("info.cuffed.empty_hand")
                        .withStyle(ChatFormatting.WHITE)));
    }
}

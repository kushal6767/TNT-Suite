package com.tntsuite.addon.util;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/** Hotbar switching on top of Meteor's InvUtils. Remembers the original slot and restores it. */
public final class Hotbar {
    private Hotbar() {}

    /**
     * Makes sure one of the given items is usable.
     * @return the hand that holds it, or null if it is missing (or auto-switch is off and it is not held).
     */
    public static InteractionHand equip(boolean autoSwitch, Item... items) {
        FindItemResult result = InvUtils.findInHotbar(items);
        if (!result.found()) return null;
        if (result.isOffhand()) return InteractionHand.OFF_HAND;
        if (result.isMainHand()) return InteractionHand.MAIN_HAND;
        if (!autoSwitch) return null;
        return InvUtils.swap(result.slot(), true) ? InteractionHand.MAIN_HAND : null;
    }

    public static boolean has(Item... items) {
        return InvUtils.findInHotbar(items).found();
    }

    /** Switches back to the slot that was selected before the first equip(). */
    public static void restore() {
        InvUtils.swapBack();
    }
}

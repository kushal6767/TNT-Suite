package com.tntsuite.addon.util;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Every call that touches Minecraft's interaction API or Meteor's Rotations lives in this
 * one class. If a Minecraft/Meteor update renames something, this is the only file to fix.
 */
public final class Interact {
    private Interact() {}

    public static boolean inReach(Vec3 point, double range) {
        return mc.player.getEyePosition().distanceToSqr(point) <= range * range;
    }

    /** Right-click a block face (place a block / use an item on it). */
    public static void useOnBlock(BlockPos pos, Direction face, InteractionHand hand,
                                  boolean rotate, int priority, Runnable after) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        run(hit, rotate, priority, () -> {
            mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(hit, face, pos, false));
            mc.player.swing(hand);
            if (after != null) after.run();
        });
    }

    /** Right-click an entity (e.g. flint and steel on a TNT minecart). */
    public static void useOnEntity(Entity entity, InteractionHand hand,
                                   boolean rotate, int priority, Runnable after) {
        Vec3 center = entity.getBoundingBox().getCenter();
        run(center, rotate, priority, () -> {
            mc.gameMode.interact(mc.player, entity, hand);
            mc.player.swing(hand);
            if (after != null) after.run();
        });
    }

    private static void run(Vec3 target, boolean rotate, int priority, Runnable action) {
        if (rotate) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), priority, action);
        } else {
            action.run();
        }
    }
}

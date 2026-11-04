package com.tntsuite.addon.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Finds valid rail positions for TNT minecarts around a (predicted) target position. */
public final class CartSpots {
    private CartSpots() {}

    public static boolean isRail(BlockPos pos) {
        return mc.level.getBlockState(pos).is(BlockTags.RAILS);
    }

    private static boolean isReplaceable(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced();
    }

    private static boolean sturdyTop(BlockPos floor) {
        BlockState state = mc.level.getBlockState(floor);
        return state.isFaceSturdy(mc.level, floor, Direction.UP);
    }

    /**
     * The block position at the target's feet where a rail can exist (or already does),
     * scanning down at most `maxDrop` blocks. Returns null when the target is airborne
     * or standing on something a rail cannot go on. That is the "unreachable target" check.
     */
    public static BlockPos findGround(Vec3 pos, int maxDrop) {
        BlockPos feet = BlockPos.containing(pos.x, pos.y + 0.1, pos.z);
        for (int i = 0; i <= maxDrop; i++) {
            BlockPos p = feet.below(i);
            if (isRail(p)) return p;
            if (!isReplaceable(p)) return null;
            if (sturdyTop(p.below())) return p;
        }
        return null;
    }

    /**
     * Candidate rail positions around `ground`, nearest to the target first.
     * @param reach max eye distance to the cart position (use the ignite range when auto-igniting)
     */
    public static List<BlockPos> candidates(BlockPos ground, int radius, Vec3 targetPos, double reach,
                                            boolean useExistingRails, Set<BlockPos> occupied) {
        List<BlockPos> out = new ArrayList<>();
        Vec3 eye = mc.player.getEyePosition();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = ground.offset(dx, 0, dz);
                if (occupied.contains(p)) continue;

                if (isRail(p)) {
                    if (!useExistingRails) continue;
                } else if (!(isReplaceable(p) && sturdyTop(p.below()))) {
                    continue;
                }

                Vec3 cartPoint = new Vec3(p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5);
                if (eye.distanceToSqr(cartPoint) > reach * reach) continue;

                out.add(p.immutable());
            }
        }

        out.sort(Comparator.comparingDouble((BlockPos p) -> Vec3.atCenterOf(p).distanceToSqr(targetPos)));
        return out;
    }
}

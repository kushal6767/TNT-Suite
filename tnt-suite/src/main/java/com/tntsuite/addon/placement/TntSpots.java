package com.tntsuite.addon.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Finds valid TNT block positions around a (predicted) target position. */
public final class TntSpots {
    private TntSpots() {}

    /** A free position, plus the existing block + face to click to place into it. */
    public record Spot(BlockPos pos, BlockPos support, Direction face) {}

    /** True when a solid block is within `maxDrop` blocks under the target (not airborne). */
    public static boolean isGrounded(Vec3 pos, int maxDrop) {
        BlockPos feet = BlockPos.containing(pos.x, pos.y + 0.1, pos.z);
        for (int i = 1; i <= maxDrop + 1; i++) {
            BlockPos floor = feet.below(i);
            if (mc.level.getBlockState(floor).isFaceSturdy(mc.level, floor, Direction.UP)) return true;
        }
        return false;
    }

    public static List<Spot> candidates(Vec3 targetPos, int radius, boolean aboveHead, double reach,
                                        double minSelfDistance, Set<BlockPos> occupied) {
        List<Spot> out = new ArrayList<>();
        BlockPos base = BlockPos.containing(targetPos.x, targetPos.y + 0.1, targetPos.z);
        Vec3 eye = mc.player.getEyePosition();
        int maxDy = aboveHead ? 2 : 1;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= maxDy; dy++) {
                    // The target's own column is only usable above their head.
                    if (dx == 0 && dz == 0 && dy < 2) continue;

                    BlockPos p = base.offset(dx, dy, dz);
                    if (occupied.contains(p)) continue;
                    if (!mc.level.getBlockState(p).canBeReplaced()) continue;
                    if (Vec3.atCenterOf(p).distanceTo(mc.player.position()) < minSelfDistance) continue;
                    if (entityBlocks(p)) continue;

                    Spot spot = supportFor(p, eye, reach);
                    if (spot != null) out.add(spot);
                }
            }
        }

        out.sort(Comparator.comparingDouble((Spot s) -> Vec3.atCenterOf(s.pos()).distanceToSqr(targetPos)));
        return out;
    }

    /** Placing a full block fails when an entity (player, mob...) stands in the way. */
    private static boolean entityBlocks(BlockPos p) {
        List<Entity> hits = mc.level.getEntities((Entity) null, new AABB(p),
            e -> !e.isSpectator() && !e.isRemoved() && e.getType() != EntityType.ITEM);
        return !hits.isEmpty();
    }

    private static Spot supportFor(BlockPos p, Vec3 eye, double reach) {
        for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            BlockState st = mc.level.getBlockState(n);
            if (st.isAir() || st.canBeReplaced()) continue;

            Direction face = d.getOpposite();
            if (!st.isFaceSturdy(mc.level, n, face)) continue;

            Vec3 hit = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (eye.distanceToSqr(hit) > reach * reach) continue;

            return new Spot(p.immutable(), n.immutable(), face);
        }
        return null;
    }
}

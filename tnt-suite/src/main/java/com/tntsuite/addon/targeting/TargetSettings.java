package com.tntsuite.addon.targeting;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Shared targeting settings + target selection, reused by every module in the suite. */
public class TargetSettings {
    private final Setting<Boolean> targetPlayers;
    private final Setting<Double> range;
    private final Setting<TargetPriority> priority;
    private final Setting<Integer> maxTargets;
    private final Setting<Boolean> ignoreFriends;
    private final Setting<Boolean> ignoreTeammates;
    private final Setting<Boolean> ignoreCreative;
    private final Setting<Boolean> ignoreSpectators;

    public TargetSettings(SettingGroup sg, double defaultRange) {
        targetPlayers = sg.add(new BoolSetting.Builder()
            .name("target-players").description("Target other players.")
            .defaultValue(true).build());

        range = sg.add(new DoubleSetting.Builder()
            .name("target-range").description("Maximum distance to a player for them to be targeted.")
            .defaultValue(defaultRange).min(1).sliderMax(30).build());

        priority = sg.add(new EnumSetting.Builder<TargetPriority>()
            .name("target-priority").description("How to order valid targets.")
            .defaultValue(TargetPriority.Closest).build());

        maxTargets = sg.add(new IntSetting.Builder()
            .name("max-targets").description("How many targets to work on at the same time.")
            .defaultValue(1).min(1).sliderMax(5).build());

        ignoreFriends = sg.add(new BoolSetting.Builder()
            .name("ignore-friends").description("Never target players on your Meteor friends list.")
            .defaultValue(true).build());

        ignoreTeammates = sg.add(new BoolSetting.Builder()
            .name("ignore-teammates").description("Never target players on your scoreboard team.")
            .defaultValue(true).build());

        ignoreCreative = sg.add(new BoolSetting.Builder()
            .name("ignore-creative").description("Never target players in creative mode.")
            .defaultValue(true).build());

        ignoreSpectators = sg.add(new BoolSetting.Builder()
            .name("ignore-spectators").description("Never target players in spectator mode.")
            .defaultValue(true).build());
    }

    public List<Player> find() {
        List<Player> out = new ArrayList<>();
        if (!targetPlayers.get() || mc.player == null || mc.level == null) return out;

        double max = range.get();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isRemoved()) continue;
            if (mc.player.distanceTo(p) > max) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;
            if (ignoreTeammates.get() && mc.player.isAlliedTo(p)) continue;
            if (ignoreCreative.get() && p.isCreative()) continue;
            if (ignoreSpectators.get() && p.isSpectator()) continue;
            out.add(p);
        }

        out.sort(comparator());
        int limit = maxTargets.get();
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private Comparator<Player> comparator() {
        return switch (priority.get()) {
            case Closest -> Comparator.comparingDouble((Player p) -> mc.player.distanceToSqr(p));
            case LowestHealth -> Comparator.comparingDouble((Player p) -> health(p));
            case HighestHealth -> Comparator.comparingDouble((Player p) -> -health(p));
            case LookingAt -> Comparator.comparingDouble((Player p) -> lookCost(p));
        };
    }

    private static double health(Player p) {
        return p.getHealth() + p.getAbsorptionAmount();
    }

    /** Lower is better: the player closest to the crosshair has the most negative cost. */
    private static double lookCost(Player p) {
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 toTarget = p.getBoundingBox().getCenter().subtract(mc.player.getEyePosition()).normalize();
        return -look.dot(toTarget);
    }
}

package com.tntsuite.addon.modules;

import com.tntsuite.addon.placement.TntSpots;
import com.tntsuite.addon.targeting.MotionTracker;
import com.tntsuite.addon.targeting.TargetSettings;
import com.tntsuite.addon.util.Hotbar;
import com.tntsuite.addon.util.Interact;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Auto TNT: places TNT blocks around targets and lights them with flint and steel. */
public class AutoTnt extends SuiteModule {
    private static final Item[] IGNITERS = {Items.FLINT_AND_STEEL, Items.FIRE_CHARGE};
    private static final int ROTATE_PRIORITY = 55;

    private enum Stage { PLACING, PLACED, IGNITED }

    private static final class Job {
        final UUID target;
        final BlockPos pos;
        Stage stage = Stage.PLACING;
        final int created;
        int stageTick;

        Job(UUID target, BlockPos pos, int tick) {
            this.target = target;
            this.pos = pos;
            this.created = tick;
            this.stageTick = tick;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgIgnite = settings.createGroup("Ignition");
    private final SettingGroup sgSmart = settings.createGroup("Smart");

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch").description("Switches to TNT and the igniter in your hotbar, then back to your original slot.")
        .defaultValue(true).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Looks at the block before interacting with it.")
        .defaultValue(true).build());

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use").description("Pauses while you are eating, drinking or using an item.")
        .defaultValue(true).build());

    private final TargetSettings targeting = new TargetSettings(sgTarget, 8.0);

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Maximum distance to the block you place TNT against.")
        .defaultValue(4.5).min(1).sliderMax(6).build());

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks to wait after each TNT placement.")
        .defaultValue(2).min(0).sliderMax(20).build());

    private final Setting<Boolean> multipleTnt = sgPlace.add(new BoolSetting.Builder()
        .name("multiple-tnt").description("Allows more than one TNT block per target at a time.")
        .defaultValue(true).build());

    private final Setting<Integer> maxTnt = sgPlace.add(new IntSetting.Builder()
        .name("max-tnt-per-target").description("How many TNT blocks can be active on one target.")
        .defaultValue(3).min(1).sliderMax(8).visible(multipleTnt::get).build());

    private final Setting<Integer> radius = sgPlace.add(new IntSetting.Builder()
        .name("tnt-radius").description("How far from the target TNT may be placed (needs smart-placement).")
        .defaultValue(1).min(1).sliderMax(3).build());

    private final Setting<Boolean> aboveHead = sgPlace.add(new BoolSetting.Builder()
        .name("above-head").description("Also allows TNT directly above the target's head.")
        .defaultValue(true).build());

    private final Setting<Double> minSelfDistance = sgPlace.add(new DoubleSetting.Builder()
        .name("min-self-distance").description("Never places TNT closer than this to you.")
        .defaultValue(3).min(0).sliderMax(8).build());

    private final Setting<Boolean> autoIgnite = sgIgnite.add(new BoolSetting.Builder()
        .name("auto-ignite").description("Lights placed TNT with flint and steel or a fire charge.")
        .defaultValue(true).build());

    private final Setting<Integer> igniteDelay = sgIgnite.add(new IntSetting.Builder()
        .name("ignite-delay").description("Ticks to wait after each ignition.")
        .defaultValue(2).min(0).sliderMax(20).build());

    private final Setting<Double> igniteRange = sgIgnite.add(new DoubleSetting.Builder()
        .name("ignite-range").description("Maximum distance to a TNT block for lighting it.")
        .defaultValue(4.5).min(1).sliderMax(6).build());

    private final Setting<Double> minHealth = sgIgnite.add(new DoubleSetting.Builder()
        .name("min-health").description("Will not light TNT while your health (with absorption) is below this.")
        .defaultValue(8).min(0).sliderMax(20).build());

    private final Setting<Boolean> smartPlacement = sgSmart.add(new BoolSetting.Builder()
        .name("smart-placement").description("Considers every valid spot around the target and picks the closest. Off = adjacent spots only.")
        .defaultValue(true).build());

    private final Setting<Boolean> prediction = sgSmart.add(new BoolSetting.Builder()
        .name("prediction").description("Places where a moving target is heading instead of where they stand now.")
        .defaultValue(true).build());

    private final Setting<Integer> predictionTicks = sgSmart.add(new IntSetting.Builder()
        .name("prediction-ticks").description("How many ticks ahead to predict.")
        .defaultValue(4).min(1).sliderMax(15).visible(prediction::get).build());

    private final Setting<Boolean> antiWaste = sgSmart.add(new BoolSetting.Builder()
        .name("anti-waste").description("Skips targets that are airborne, too fast, or out of reach so TNT is not wasted.")
        .defaultValue(true).build());

    private final Setting<Double> maxSpeed = sgSmart.add(new DoubleSetting.Builder()
        .name("max-target-speed").description("Targets moving faster than this (blocks per tick) are skipped.")
        .defaultValue(0.35).min(0.05).sliderMax(1).visible(antiWaste::get).build());

    private final List<Job> jobs = new ArrayList<>();
    private final MotionTracker motion = new MotionTracker();
    private String lastTargetName;

    public AutoTnt() {
        super("auto-tnt", "Places TNT around nearby players and lights it.");
    }

    @Override
    protected void reset() {
        jobs.clear();
        motion.clear();
        lastTargetName = null;
    }

    @Override
    public String getInfoString() {
        return lastTargetName;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        safely(this::process);
    }

    private void process() {
        tick++;
        motion.update();
        updateJobs();

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (pauseOnUse.get() && mc.player.isUsingItem()) return;

        List<Player> targets = targeting.find();
        lastTargetName = targets.isEmpty() ? null : targets.get(0).getName().getString();

        if (autoIgnite.get() && igniteOne()) return;
        if (!targets.isEmpty()) placeOne(targets);
    }

    private void updateJobs() {
        Iterator<Job> it = jobs.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            int age = tick - j.stageTick;
            boolean isTnt = mc.level.getBlockState(j.pos).is(Blocks.TNT);

            if (tick - j.created > 300) {
                it.remove();
                continue;
            }

            switch (j.stage) {
                case PLACING -> {
                    if (isTnt) {
                        j.stage = Stage.PLACED;
                        j.stageTick = tick;
                    } else if (age > 10) {
                        it.remove();
                    }
                }
                case PLACED -> {
                    if (!isTnt) it.remove(); // exploded, mined or lit by something else
                }
                case IGNITED -> {
                    if (!isTnt || age > 20) it.remove();
                }
            }
        }
    }

    private int jobCount(Player target) {
        int n = 0;
        for (Job j : jobs) if (j.target.equals(target.getUUID())) n++;
        return n;
    }

    private Set<BlockPos> occupied() {
        Set<BlockPos> set = new HashSet<>();
        for (Job j : jobs) set.add(j.pos);
        return set;
    }

    private boolean igniteOne() {
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < minHealth.get()) return false;

        for (Job j : jobs) {
            if (j.stage != Stage.PLACED) continue;

            Vec3 center = Vec3.atCenterOf(j.pos);
            if (!Interact.inReach(center, igniteRange.get())) continue;

            InteractionHand hand = Hotbar.equip(autoSwitch.get(), IGNITERS);
            if (hand == null) {
                warnOnce("igniter", "No flint and steel or fire charge in your hotbar.");
                return false;
            }

            Vec3 eye = mc.player.getEyePosition();
            Direction face = Direction.getApproximateNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);

            Interact.useOnBlock(j.pos, face, hand, rotate.get(), ROTATE_PRIORITY, Hotbar::restore);
            j.stage = Stage.IGNITED;
            j.stageTick = tick;
            cooldown = igniteDelay.get();
            return true;
        }
        return false;
    }

    private void placeOne(List<Player> targets) {
        int cap = multipleTnt.get() ? maxTnt.get() : 1;
        int r = smartPlacement.get() ? radius.get() : 1;
        // When auto-igniting, the TNT must also be within lighting reach.
        double reach = autoIgnite.get() ? Math.min(placeRange.get(), igniteRange.get()) : placeRange.get();

        for (Player target : targets) {
            if (jobCount(target) >= cap) continue;
            if (antiWaste.get() && motion.speed(target) > maxSpeed.get()) continue;

            Vec3 pos = prediction.get() ? motion.predict(target, predictionTicks.get()) : target.position();
            if (antiWaste.get() && !TntSpots.isGrounded(pos, 2)) continue;

            List<TntSpots.Spot> spots = TntSpots.candidates(pos, r, aboveHead.get(), reach, minSelfDistance.get(), occupied());
            if (spots.isEmpty()) continue;

            if (!Hotbar.has(Items.TNT)) {
                warnOnce("tnt", "No TNT in your hotbar.");
                return;
            }
            if (autoIgnite.get() && !Hotbar.has(IGNITERS)) {
                warnOnce("igniter", "No flint and steel or fire charge in your hotbar.");
                return;
            }

            TntSpots.Spot spot = spots.get(0);
            InteractionHand hand = Hotbar.equip(autoSwitch.get(), Items.TNT);
            if (hand == null) {
                warnOnce("tnt", "No TNT in your hotbar.");
                return;
            }

            Interact.useOnBlock(spot.support(), spot.face(), hand, rotate.get(), ROTATE_PRIORITY, Hotbar::restore);
            jobs.add(new Job(target.getUUID(), spot.pos(), tick));
            cooldown = placeDelay.get();
            return;
        }
    }
}

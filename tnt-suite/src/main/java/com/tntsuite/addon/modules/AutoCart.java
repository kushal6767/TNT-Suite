package com.tntsuite.addon.modules;

import com.tntsuite.addon.placement.CartSpots;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Auto Cart: places a rail near each target, puts a TNT minecart on it, then lights it
 * with flint and steel (or a fire charge). Each cart is a small "job" that moves through stages,
 * and every step is verified against the world before the next one runs.
 */
public class AutoCart extends SuiteModule {
    private static final Item[] RAILS = {Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
    private static final Item[] IGNITERS = {Items.FLINT_AND_STEEL, Items.FIRE_CHARGE};
    private static final int ROTATE_PRIORITY = 55;

    private enum Stage { NEED_RAIL, NEED_CART, AWAIT_CART, READY, IGNITED }

    private static final class Job {
        final UUID target;
        final BlockPos rail;
        Stage stage;
        final int created;
        int stageTick;
        int entityId = -1;
        int attempts;

        Job(UUID target, BlockPos rail, Stage stage, int tick) {
            this.target = target;
            this.rail = rail;
            this.stage = stage;
            this.created = tick;
            this.stageTick = tick;
        }
    }

    // ---- Settings ----
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgIgnite = settings.createGroup("Ignition");
    private final SettingGroup sgSmart = settings.createGroup("Smart");

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch").description("Switches to rails, TNT minecarts and the igniter in your hotbar, then back to your original slot.")
        .defaultValue(true).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Looks at the block or cart before interacting with it.")
        .defaultValue(true).build());

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use").description("Pauses while you are eating, drinking or using an item.")
        .defaultValue(true).build());

    private final TargetSettings targeting = new TargetSettings(sgTarget, 6.0);

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Maximum distance to the block you place a rail or cart on.")
        .defaultValue(4.5).min(1).sliderMax(6).build());

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks to wait after each rail or cart placement.")
        .defaultValue(2).min(0).sliderMax(20).build());

    private final Setting<Boolean> multipleCarts = sgPlace.add(new BoolSetting.Builder()
        .name("multiple-carts").description("Allows more than one TNT minecart per target at a time.")
        .defaultValue(false).build());

    private final Setting<Integer> maxCarts = sgPlace.add(new IntSetting.Builder()
        .name("max-carts-per-target").description("How many carts can be active on one target (needs multiple-carts).")
        .defaultValue(2).min(1).sliderMax(6).visible(multipleCarts::get).build());

    private final Setting<Integer> cartRadius = sgPlace.add(new IntSetting.Builder()
        .name("cart-radius").description("How far from the target's feet carts may be placed (needs smart-positioning).")
        .defaultValue(1).min(0).sliderMax(3).build());

    private final Setting<Boolean> useExistingRails = sgPlace.add(new BoolSetting.Builder()
        .name("use-existing-rails").description("Puts carts on rails that are already there instead of placing new ones.")
        .defaultValue(true).build());

    private final Setting<Boolean> autoIgnite = sgIgnite.add(new BoolSetting.Builder()
        .name("auto-ignite").description("Lights placed TNT minecarts with flint and steel or a fire charge.")
        .defaultValue(true).build());

    private final Setting<Integer> igniteDelay = sgIgnite.add(new IntSetting.Builder()
        .name("ignite-delay").description("Ticks to wait after each ignition.")
        .defaultValue(1).min(0).sliderMax(20).build());

    private final Setting<Double> igniteRange = sgIgnite.add(new DoubleSetting.Builder()
        .name("ignite-range").description("Maximum distance to a cart for lighting it. Survival entity reach is about 3 blocks.")
        .defaultValue(2.9).min(1).sliderMax(4.5).build());

    private final Setting<Double> minHealth = sgIgnite.add(new DoubleSetting.Builder()
        .name("min-health").description("Will not light carts while your health (with absorption) is below this. You are close to the blast.")
        .defaultValue(8).min(0).sliderMax(20).build());

    private final Setting<Boolean> smartPositioning = sgSmart.add(new BoolSetting.Builder()
        .name("smart-positioning").description("Considers spots around the target and picks the closest valid one. Off = only the target's own feet position.")
        .defaultValue(true).build());

    private final Setting<Boolean> prediction = sgSmart.add(new BoolSetting.Builder()
        .name("prediction").description("Places where a moving target is heading instead of where they stand now.")
        .defaultValue(true).build());

    private final Setting<Integer> predictionTicks = sgSmart.add(new IntSetting.Builder()
        .name("prediction-ticks").description("How many ticks ahead to predict.")
        .defaultValue(4).min(1).sliderMax(15).visible(prediction::get).build());

    private final Setting<Boolean> antiWaste = sgSmart.add(new BoolSetting.Builder()
        .name("anti-waste").description("Skips targets that are airborne, too fast, or out of reach so carts are not wasted.")
        .defaultValue(true).build());

    private final Setting<Double> maxSpeed = sgSmart.add(new DoubleSetting.Builder()
        .name("max-target-speed").description("Targets moving faster than this (blocks per tick) are skipped. Walking is about 0.22, sprinting 0.28.")
        .defaultValue(0.35).min(0.05).sliderMax(1).visible(antiWaste::get).build());

    private final Setting<Integer> maxDrop = sgSmart.add(new IntSetting.Builder()
        .name("max-drop").description("A target more than this many blocks above solid ground counts as airborne.")
        .defaultValue(2).min(0).sliderMax(5).visible(antiWaste::get).build());

    // ---- State ----
    private final List<Job> jobs = new ArrayList<>();
    private final MotionTracker motion = new MotionTracker();
    private String lastTargetName;

    public AutoCart() {
        super("auto-cart", "Places a rail and a TNT minecart near nearby players and lights it.");
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

        // Priority: light ready carts > place carts on rails > place new rails.
        if (autoIgnite.get() && igniteOne()) return;
        if (placeCartOne()) return;
        if (!targets.isEmpty()) placeRailOne(targets);
    }

    // ---------- job bookkeeping ----------

    private void updateJobs() {
        Iterator<Job> it = jobs.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            int age = tick - j.stageTick;

            if (tick - j.created > 400) {
                it.remove();
                continue;
            }

            switch (j.stage) {
                case NEED_RAIL -> {
                    if (CartSpots.isRail(j.rail)) {
                        j.stage = Stage.NEED_CART;
                        j.stageTick = tick;
                    } else if (age > 15) {
                        it.remove();
                    }
                }
                case NEED_CART -> {
                    if (!CartSpots.isRail(j.rail)) it.remove();
                }
                case AWAIT_CART -> {
                    Entity cart = findCart(j.rail);
                    if (cart != null) {
                        j.entityId = cart.getId();
                        j.stage = Stage.READY;
                        j.stageTick = tick;
                    } else if (age > 12) {
                        if (j.attempts >= 3) {
                            it.remove();
                        } else {
                            j.stage = Stage.NEED_CART;
                            j.stageTick = tick;
                        }
                    }
                }
                case READY -> {
                    Entity cart = mc.level.getEntity(j.entityId);
                    if (cart == null || cart.isRemoved()) it.remove();
                }
                case IGNITED -> {
                    Entity cart = mc.level.getEntity(j.entityId);
                    if (cart == null || cart.isRemoved() || age > 120) it.remove();
                }
            }
        }
    }

    private Entity findCart(BlockPos rail) {
        AABB box = new AABB(rail).inflate(0.4);
        for (Entity e : mc.level.getEntities((Entity) null, box)) {
            if (e.getType() == EntityType.TNT_MINECART && !e.isRemoved()) return e;
        }
        return null;
    }

    private int jobCount(Player target) {
        int n = 0;
        for (Job j : jobs) if (j.target.equals(target.getUUID())) n++;
        return n;
    }

    private Set<BlockPos> occupied() {
        Set<BlockPos> set = new HashSet<>();
        for (Job j : jobs) set.add(j.rail);
        return set;
    }

    // ---------- actions ----------

    private boolean igniteOne() {
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < minHealth.get()) return false;

        for (Job j : jobs) {
            if (j.stage != Stage.READY) continue;

            Entity cart = mc.level.getEntity(j.entityId);
            if (cart == null || cart.isRemoved()) continue;
            if (!Interact.inReach(cart.getBoundingBox().getCenter(), igniteRange.get())) continue;

            InteractionHand hand = Hotbar.equip(autoSwitch.get(), IGNITERS);
            if (hand == null) {
                warnOnce("igniter", "No flint and steel or fire charge in your hotbar.");
                return false;
            }

            Interact.useOnEntity(cart, hand, rotate.get(), ROTATE_PRIORITY, Hotbar::restore);
            j.stage = Stage.IGNITED;
            j.stageTick = tick;
            cooldown = igniteDelay.get();
            return true;
        }
        return false;
    }

    private boolean placeCartOne() {
        for (Job j : jobs) {
            if (j.stage != Stage.NEED_CART) continue;
            if (!CartSpots.isRail(j.rail)) continue;
            if (!Interact.inReach(Vec3.atCenterOf(j.rail), placeRange.get())) continue;

            InteractionHand hand = Hotbar.equip(autoSwitch.get(), Items.TNT_MINECART);
            if (hand == null) {
                warnOnce("cart", "No TNT minecart in your hotbar.");
                return false;
            }

            Interact.useOnBlock(j.rail, Direction.UP, hand, rotate.get(), ROTATE_PRIORITY, Hotbar::restore);
            j.stage = Stage.AWAIT_CART;
            j.stageTick = tick;
            j.attempts++;
            cooldown = placeDelay.get();
            return true;
        }
        return false;
    }

    private void placeRailOne(List<Player> targets) {
        int cap = multipleCarts.get() ? maxCarts.get() : 1;
        int radius = smartPositioning.get() ? cartRadius.get() : 0;
        // When auto-igniting, the cart must also be within igniting reach, or the TNT is wasted.
        double reach = autoIgnite.get() ? Math.min(placeRange.get(), igniteRange.get()) : placeRange.get();

        for (Player target : targets) {
            if (jobCount(target) >= cap) continue;
            if (antiWaste.get() && motion.speed(target) > maxSpeed.get()) continue;

            Vec3 pos = prediction.get() ? motion.predict(target, predictionTicks.get()) : target.position();
            BlockPos ground = CartSpots.findGround(pos, antiWaste.get() ? maxDrop.get() : 1);
            if (ground == null) continue;

            List<BlockPos> spots = CartSpots.candidates(ground, radius, pos, reach, useExistingRails.get(), occupied());
            if (spots.isEmpty()) continue;

            // Inventory check before spending anything.
            if (!Hotbar.has(Items.TNT_MINECART)) {
                warnOnce("cart", "No TNT minecart in your hotbar.");
                return;
            }
            if (autoIgnite.get() && !Hotbar.has(IGNITERS)) {
                warnOnce("igniter", "No flint and steel or fire charge in your hotbar.");
                return;
            }

            BlockPos spot = spots.get(0);

            if (CartSpots.isRail(spot)) {
                jobs.add(new Job(target.getUUID(), spot, Stage.NEED_CART, tick));
                return;
            }

            InteractionHand hand = Hotbar.equip(autoSwitch.get(), RAILS);
            if (hand == null) {
                warnOnce("rail", "No rails in your hotbar.");
                return;
            }

            Interact.useOnBlock(spot.below(), Direction.UP, hand, rotate.get(), ROTATE_PRIORITY, Hotbar::restore);
            jobs.add(new Job(target.getUUID(), spot, Stage.NEED_RAIL, tick));
            cooldown = placeDelay.get();
            return;
        }
    }
}

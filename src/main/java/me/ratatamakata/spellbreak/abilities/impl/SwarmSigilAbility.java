package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SwarmSigilAbility implements Ability {

    private int cooldown = 25;
    private int manaCost = 60;
    private String requiredClass = "runesmith";
    private int minDrones = 2;
    private int maxDrones = 4;
    private double droneSpeed = 0.9;
    private double wanderRadius = 4.0;
    private double seekRadius = 8.0;
    private double stickRadius = 1.5;
    private int stickDuration = 60;
    private double explosionDamage = 3.0;
    private double explosionRadius = 4.0;
    private int droneDuration = 300; // ticks
    private int updateInterval = 1; // Changed to 1 tick for smooth movement

    private static final Map<UUID, List<SwarmDrone>> activeDrones = new HashMap<>();

    @Override public String getName() { return "SwarmSigil"; }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.swarmsigil.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        minDrones = cfg.getInt(base + "min-drones", minDrones);
        maxDrones = cfg.getInt(base + "max-drones", maxDrones);
        droneSpeed = cfg.getDouble(base + "drone-speed", droneSpeed);
        wanderRadius = cfg.getDouble(base + "wander-radius", wanderRadius);
        seekRadius = cfg.getDouble(base + "seek-radius", seekRadius);
        stickRadius = cfg.getDouble(base + "stick-radius", stickRadius);
        stickDuration = cfg.getInt(base + "stick-duration", stickDuration);
        explosionDamage = cfg.getDouble(base + "explosion-damage", explosionDamage);
        explosionRadius = cfg.getDouble(base + "explosion-radius", explosionRadius);
        droneDuration = cfg.getInt(base + "drone-duration", droneDuration);
        updateInterval = cfg.getInt(base + "update-interval", updateInterval);
    }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        removeDrones(uuid);

        int count = ThreadLocalRandom.current().nextInt(minDrones, maxDrones + 1);
        List<SwarmDrone> drones = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SwarmDrone d = new SwarmDrone(player);
            drones.add(d);
            d.start();
        }
        activeDrones.put(uuid, drones);

        // Fixed ActionBar duration countdown
        new BukkitRunnable() {
            int ticksLeft = droneDuration;
            @Override public void run() {
                if (ticksLeft <= 0 || !player.isOnline()) {
                    cancel();
                    removeDrones(uuid); // Ensure drones are removed when duration ends
                    return;
                }
                player.sendActionBar(ChatColor.GREEN + "SwarmSigil: " + (ticksLeft/20) + "s remaining");
                ticksLeft -= 20;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 20);

        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1, 0.8f);
        w.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
    }

    public class SwarmDrone extends BukkitRunnable {
        private final Player owner;
        private Location location;
        private Vector velocity = new Vector();
        private LivingEntity target;
        private DroneState state = DroneState.WANDERING;
        private int ticksAlive = 0;
        private int stickTicks = 0;
        private Location wanderTarget;
        private final ItemDisplay display;
        private boolean exploded = false;

        public SwarmDrone(Player owner) {
            this.owner = owner;
            Location c = owner.getLocation().add(0, 1.5, 0);
            double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
            double r = ThreadLocalRandom.current().nextDouble(0, wanderRadius);
            this.location = c.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            this.wanderTarget = this.location;

            this.display = owner.getWorld().spawn(location, ItemDisplay.class);
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta(); meta.setCustomModelData(1002); paper.setItemMeta(meta);
            display.setItemStack(paper);
            display.setGravity(false);
            display.setBillboard(Display.Billboard.FIXED);
            display.teleport(location);
        }

        @Override
        public void run() {
            if (!owner.isOnline() || owner.isDead() || exploded) { cleanup(); return; }
            ticksAlive++;
            if (ticksAlive >= droneDuration) { explode(); return; }

            switch (state) {
                case WANDERING: updateWander(); break;
                case SEEKING: updateSeek(); break;
                case STUCK: updateStuck(); break;
            }

            display.teleport(location);
            spawnEffects();
        }

        private void updateWander() {
            LivingEntity enemy = findEnemy();
            if (enemy != null) { target = enemy; state = DroneState.SEEKING; return; }

            if (location.distance(wanderTarget) < 0.5) {
                Location c = owner.getLocation().add(0, 1.5, 0);
                double ang = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                double rad = ThreadLocalRandom.current().nextDouble(0, wanderRadius);
                wanderTarget = c.clone().add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
            }
            // Adjusted speed for 1-tick updates (reduced by factor of 3)
            smoothMoveTowards(wanderTarget, droneSpeed * 0.5 / 3.0);
        }

        private void updateSeek() {
            if (target == null || target.isDead()) { state = DroneState.WANDERING; target = null; return; }
            double d = location.distance(target.getLocation());
            if (d <= stickRadius) {
                state = DroneState.STUCK;
                stickTicks = 0;
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.8f, 1.5f);
                return;
            }
            // Adjusted speed for 1-tick updates (reduced by factor of 3)
            smoothMoveTowards(target.getLocation().add(0, 1, 0), droneSpeed / 3.0);
        }

        private void updateStuck() {
            if (target == null || target.isDead()) { explode(); return; }
            stickTicks++;
            location = target.getLocation().add(0, 1.5, 0);
            if (stickTicks >= stickDuration) explode();
        }

        private void smoothMoveTowards(Location dest, double speed) {
            Vector dir = dest.toVector().subtract(location.toVector());
            if (dir.length() > 0) {
                dir.normalize().multiply(speed);
                velocity.multiply(0.8).add(dir.multiply(0.2)); // Smoother interpolation
                location.add(velocity);
            }
        }

        private LivingEntity findEnemy() {
            LivingEntity nearest = null;
            double min = seekRadius;
            for (Entity e : owner.getWorld().getNearbyEntities(location, seekRadius, seekRadius, seekRadius)) {
                if (!(e instanceof LivingEntity) || e.equals(owner)) continue;
                double dist = location.distance(((LivingEntity) e).getLocation());
                if (dist < min) { min = dist; nearest = (LivingEntity) e; }
            }
            return nearest;
        }

        private void explode() {
            if (exploded) return;
            exploded = true;
            World w = location.getWorld();
            w.spawnParticle(Particle.EXPLOSION, location, 1);
            w.spawnParticle(Particle.FLAME, location, 15, 0.5, 0.5, 0.5, 0.1);
            w.spawnParticle(Particle.SMOKE, location, 10, 0.3, 0.3, 0.3, 0.05);
            w.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1, 1.2f);
            for (Entity en : w.getNearbyEntities(location, explosionRadius, explosionRadius, explosionRadius)) {
                if (!(en instanceof LivingEntity) || en.equals(owner)) continue;
                LivingEntity le = (LivingEntity) en;
                double d = location.distance(le.getLocation());
                double mul = 1.0 - (d / explosionRadius);
                double dmg = explosionDamage * Math.max(0.2, mul);
                Spellbreak.getInstance().getAbilityDamage().damage(le, dmg, owner, SwarmSigilAbility.this, null);
            }
            cleanup();
        }

        private void spawnEffects() {
            World w = location.getWorld();
            w.spawnParticle(Particle.END_ROD, location, 1, 0.1, 0.1, 0.1, 0.01);
            if (ticksAlive % 5 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, location, 2, 0.2, 0.2, 0.2, 0.02);
            if (state == DroneState.SEEKING && ticksAlive % 10 == 0) {
                w.playSound(location, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 0.3f, 1.5f);
            }
        }

        private void cleanup() {
            cancel();
            if (display != null && !display.isDead()) {
                display.remove();
            }
            List<SwarmDrone> list = activeDrones.get(owner.getUniqueId());
            if (list != null) {
                list.remove(this);
                if (list.isEmpty()) activeDrones.remove(owner.getUniqueId());
            }
        }

        public void start() {
            runTaskTimer(Spellbreak.getInstance(), 0, updateInterval);
        }
    }

    private enum DroneState { WANDERING, SEEKING, STUCK }

    public static void removeDrones(UUID pid) {
        List<SwarmDrone> d = activeDrones.remove(pid);
        if (d != null) {
            for (SwarmDrone drone : d) {
                drone.exploded = true; // Prevent double cleanup
                drone.cancel();
                if (drone.display != null && !drone.display.isDead()) {
                    drone.display.remove();
                }
            }
        }
    }

    public static boolean hasActiveDrones(UUID pid) {
        List<SwarmDrone> d = activeDrones.get(pid);
        return d != null && !d.isEmpty();
    }

    @Override public String getDescription() { return "Summons swarm drones that wander, seek, and explode on enemies"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }
    @Override public boolean isSuccessful() { return true; }
    @Override public String getDeathMessage(String v, String c, String s) { return String.format("§c%s §4was swarmed by §c%s§4's drones!", v, c); }
}
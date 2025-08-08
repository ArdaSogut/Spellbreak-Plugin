package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BlackHoleAbility implements Ability {
    private int cooldown = 15;
    private int manaCost = 25;
    private String requiredClass = "starcaller";

    // Travel parameters
    private double initialSpeed = 0.6;
    private double minSpeed = 0.2;
    private double decel = 0.002;
    private double maxRange = 20;
    private double maxRadius = 4;
    private double growthRate = 0.07;

    // Anchored parameters
    private int anchorDuration = 60;
    private double basePullStrength = 0.9;
    private double travelPullMultiplier = 2.4;

    private final Map<UUID, TravelingHole> activeHoles = new HashMap<>();
    private final Random random = new Random();

    @Override public String getName() { return "BlackHole"; }
    @Override public String getDescription() {
        return "Shift+Left-click to send a growing black hole forward; Shift+Left-click again to anchor it.";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();

        if (activeHoles.containsKey(uuid)) {
            activeHoles.get(uuid).anchor();
            return;
        }

        // Get ground position below player
        Location groundLoc = findGroundLocation(player.getLocation());
        Vector dir = player.getLocation().getDirection().setY(0).normalize();

        TravelingHole hole = new TravelingHole(player.getWorld(), groundLoc, dir, uuid);
        hole.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        activeHoles.put(uuid, hole);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
        player.sendMessage(ChatColor.DARK_PURPLE + "Black Hole launched!");
    }

    private Location findGroundLocation(Location start) {
        Location ground = start.clone();
        ground.setY(ground.getWorld().getMaxHeight());

        // Raytrace to find ground
        RayTraceResult result = ground.getWorld().rayTraceBlocks(
                ground,
                new Vector(0, -1, 0),
                ground.getWorld().getMaxHeight(),
                FluidCollisionMode.NEVER,
                true
        );

        if (result != null && result.getHitBlock() != null) {
            Block hitBlock = result.getHitBlock();
            ground = hitBlock.getLocation().add(0.5, 1.2, 0.5);
        } else {
            ground.setY(start.getY());
        }
        return ground;
    }

    @Override public boolean isSuccessful() { return true; }

    @Override
    public String getDeathMessage(String victim, String caster, String unused) {
        return String.format("§6%s §ewas consumed by §6%s§e's BlackHole!", victim, caster);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String b = "abilities.blackhole.";
        cooldown       = cfg.getInt(b + "cooldown", cooldown);
        manaCost       = cfg.getInt(b + "mana-cost", manaCost);
        initialSpeed   = cfg.getDouble(b + "speed", initialSpeed);
        maxRange       = cfg.getDouble(b + "max-range", maxRange);
        maxRadius      = cfg.getDouble(b + "max-radius", maxRadius);
        growthRate     = cfg.getDouble(b + "growth-rate", growthRate);
        anchorDuration = cfg.getInt(b + "anchor-duration", anchorDuration);
        basePullStrength = cfg.getDouble(b + "base-pull-strength", basePullStrength);
        travelPullMultiplier = cfg.getDouble(b + "travel-pull-multiplier", travelPullMultiplier);
        requiredClass  = cfg.getString(b + "required-class", requiredClass);
    }

    public boolean isTraveling(UUID uuid) {
        return activeHoles.containsKey(uuid);
    }

    private class TravelingHole extends BukkitRunnable {
        private final World world;
        private Location loc;
        private final Vector dir;
        private final UUID owner;
        private double traveled = 0;
        private double radius = 1.0;
        private int tickCount = 0;
        private boolean anchored = false;
        private double speed = initialSpeed;

        TravelingHole(World w, Location start, Vector direction, UUID owner) {
            this.world = w;
            this.loc = start.clone();
            this.dir = direction.clone();
            this.owner = owner;
        }

        @Override
        public void run() {
            if (anchored || world == null) {
                cancel();
                return;
            }

            tickCount++;
            // Slow down gradually
            speed = Math.max(minSpeed, speed - decel);

            // Move and grow
            loc.add(dir.clone().multiply(speed));
            traveled += speed;
            radius = Math.min(maxRadius, radius + growthRate);

            // Create 3D sphere effect with multi-axis rings
            createMultiAxisSphere();

            // Create accretion disk effect
            createAccretionDisk();

            // Pull with larger radius and stronger force while traveling
            pullNearby(radius * 2.5, loc, true);

            // Auto-anchor at max range
            if (traveled >= maxRange) {
                anchor();
            }
        }

        private void createMultiAxisSphere() {
            double rotationSpeed = 3.0;
            double globalAngle = Math.toRadians(tickCount * rotationSpeed);

            // XY Plane Ring (Vertical)
            createRing(0, globalAngle, radius, 25, 0.7f);

            // XZ Plane Ring (Horizontal)
            createRing(1, globalAngle, radius, 25, 0.7f);

            // YZ Plane Ring (Vertical)
            createRing(2, globalAngle, radius, 25, 0.7f);

            // Additional rings at 45-degree angles
            double offsetAngle = Math.PI/4;
            createTiltedRing(globalAngle + offsetAngle, radius, 20, 0.6f);
            createTiltedRing(globalAngle - offsetAngle, radius, 20, 0.6f);

            // Dark core particles
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 8, 0.15, 0.15, 0.15, 0.05);
        }

        private void createRing(int plane, double angle, double radius, int points, float hueBase) {
            for (int i = 0; i < points; i++) {
                double theta = 2 * Math.PI * i / points;
                double x = 0, y = 0, z = 0;

                switch (plane) {
                    case 0: // XY plane (vertical)
                        x = radius * Math.cos(theta);
                        y = radius * Math.sin(theta);
                        break;
                    case 1: // XZ plane (horizontal)
                        x = radius * Math.cos(theta);
                        z = radius * Math.sin(theta);
                        break;
                    case 2: // YZ plane (vertical)
                        y = radius * Math.cos(theta);
                        z = radius * Math.sin(theta);
                        break;
                }

                // Rotate around Y axis
                double newX = x * Math.cos(angle) - z * Math.sin(angle);
                double newZ = x * Math.sin(angle) + z * Math.cos(angle);

                Location particleLoc = loc.clone().add(newX, y, newZ);

                // Color gradient
                float hue = (float) (hueBase + (i / (float) points) * 0.15f);
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.9f, 0.9f);
                Color color = Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue());

                world.spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.5f)
                );
            }
        }

        private void createTiltedRing(double angle, double radius, int points, float hueBase) {
            double tiltAngle = Math.PI/4; // 45-degree tilt

            for (int i = 0; i < points; i++) {
                double theta = 2 * Math.PI * i / points;
                double x = radius * Math.cos(theta);
                double y = radius * Math.sin(theta) * Math.sin(tiltAngle);
                double z = radius * Math.sin(theta) * Math.cos(tiltAngle);

                // Rotate around Y axis
                double newX = x * Math.cos(angle) - z * Math.sin(angle);
                double newZ = x * Math.sin(angle) + z * Math.cos(angle);

                Location particleLoc = loc.clone().add(newX, y, newZ);

                // Color gradient
                float hue = (float) (hueBase + (i / (float) points) * 0.15f);
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.9f, 0.9f);
                Color color = Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue());

                world.spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.2f)
                );
            }
        }

        private void createAccretionDisk() {
            int diskPoints = 40;
            double diskThickness = 0.6;

            for (int i = 0; i < diskPoints; i++) {
                double angle = 2 * Math.PI * i / diskPoints;
                double diskRadius = radius * 0.6;
                double x = Math.cos(angle) * diskRadius;
                double z = Math.sin(angle) * diskRadius;

                // Random height within disk
                double y = (random.nextDouble() - 0.5) * diskThickness;

                Location particleLoc = loc.clone().add(x, y, z);

                // Orange to yellow colors for accretion disk
                float hue = (float) (0.1f + random.nextFloat() * 0.1f);
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.9f, 1.0f);
                Color color = Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue());

                world.spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.2f)
                );
            }
        }

        public void anchor() {
            if (anchored) return;
            anchored = true;
            cancel();

            final Location anchorLoc = loc.clone();
            final double anchoredRadius = radius;

            // Play anchoring sound
            anchorLoc.getWorld().playSound(anchorLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);

            new BukkitRunnable() {
                int tick = 0;
                @Override public void run() {
                    if (tick++ >= anchorDuration) {
                        this.cancel();
                        activeHoles.remove(owner);
                        return;
                    }

                    // Create anchored sphere effect with multi-axis rings
                    createAnchoredSphere(anchorLoc, anchoredRadius, tick);

                    // Pull with larger radius
                    pullNearby(anchoredRadius * 3.0, anchorLoc, false);
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0, 1);

            Player p = Bukkit.getPlayer(owner);
            if (p != null && p.isOnline()) {
                p.sendMessage(ChatColor.DARK_PURPLE + "Black Hole anchored!");
            }
        }

        private void createAnchoredSphere(Location center, double radius, int time) {
            double rotationSpeed = 8.0;
            double globalAngle = Math.toRadians(time * rotationSpeed);

            // XY Plane Ring (Vertical)
            createRing(center, 0, globalAngle, radius, 30, 0.8f);

            // XZ Plane Ring (Horizontal)
            createRing(center, 1, globalAngle, radius, 30, 0.8f);

            // YZ Plane Ring (Vertical)
            createRing(center, 2, globalAngle, radius, 30, 0.8f);

            // Additional rings at 45-degree angles
            double offsetAngle = Math.PI/3;
            createTiltedRing(center, globalAngle + offsetAngle, radius, 25, 0.75f);
            createTiltedRing(center, globalAngle - offsetAngle, radius, 25, 0.75f);

            // Equatorial ring
            createEquatorialRing(center, radius, time);

            // Dark core particles
            world.spawnParticle(Particle.SMOKE, center, 15, 0.2, 0.2, 0.2, 0.05);
        }

        private void createRing(Location center, int plane, double angle, double radius, int points, float hueBase) {
            for (int i = 0; i < points; i++) {
                double theta = 2 * Math.PI * i / points;
                double x = 0, y = 0, z = 0;

                switch (plane) {
                    case 0: // XY plane (vertical)
                        x = radius * Math.cos(theta);
                        y = radius * Math.sin(theta);
                        break;
                    case 1: // XZ plane (horizontal)
                        x = radius * Math.cos(theta);
                        z = radius * Math.sin(theta);
                        break;
                    case 2: // YZ plane (vertical)
                        y = radius * Math.cos(theta);
                        z = radius * Math.sin(theta);
                        break;
                }

                // Rotate around Y axis
                double newX = x * Math.cos(angle) - z * Math.sin(angle);
                double newZ = x * Math.sin(angle) + z * Math.cos(angle);

                Location particleLoc = center.clone().add(newX, y, newZ);

                // Color gradient (purple to pink)
                float hue = (float) (hueBase + (i / (float) points) * 0.15f);
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.9f, 0.9f);
                Color color = Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue());

                world.spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(color, 2.0f)
                );
            }
        }

        private void createTiltedRing(Location center, double angle, double radius, int points, float hueBase) {
            double tiltAngle = Math.PI/3; // 60-degree tilt

            for (int i = 0; i < points; i++) {
                double theta = 2 * Math.PI * i / points;
                double x = radius * Math.cos(theta);
                double y = radius * Math.sin(theta) * Math.sin(tiltAngle);
                double z = radius * Math.sin(theta) * Math.cos(tiltAngle);

                // Rotate around Y axis
                double newX = x * Math.cos(angle) - z * Math.sin(angle);
                double newZ = x * Math.sin(angle) + z * Math.cos(angle);

                Location particleLoc = center.clone().add(newX, y, newZ);

                // Color gradient
                float hue = (float) (hueBase + (i / (float) points) * 0.15f);
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.9f, 0.9f);
                Color color = Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue());

                world.spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.8f)
                );
            }
        }

        private void createEquatorialRing(Location center, double radius, int time) {
            int points = 50;
            double rotationSpeed = 5.0;
            double angle = Math.toRadians(time * rotationSpeed);

            for (int i = 0; i < points; i++) {
                double theta = 2 * Math.PI * i / points;
                double x = radius * Math.cos(theta + angle);
                double z = radius * Math.sin(theta + angle);

                Location particleLoc = center.clone().add(x, 0, z);


                float hue = (float) (0.12f + (i / (float) points) * 0.05f);
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.9f, 1.0f);
                Color color = Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue());

                world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 2.0f)
                );
            }
        }

        private void pullNearby(double pullRadius, Location center, boolean isTraveling) {
            for (Entity e : world.getNearbyEntities(center, pullRadius, pullRadius, pullRadius)) {
                if (!(e instanceof LivingEntity)) continue;
                if (e.getUniqueId().equals(owner)) continue;

                Location entityLoc = e.getLocation();
                Vector toCenter = center.clone().subtract(entityLoc).toVector();
                double distance = toCenter.length();

                // Skip if very close to center
                if (distance < 0.5) continue;

                // Normalize direction
                toCenter.normalize();

                // Calculate pull strength using inverse square law
                double strength = basePullStrength * (1 / (distance * distance + 0.1));

                // Apply travel multiplier if needed
                if (isTraveling) {
                    strength *= travelPullMultiplier;
                }

                // Apply gravitational pull
                Vector currentVelocity = e.getVelocity();
                Vector pullVector = toCenter.multiply(strength);

                // Create smooth pull effect
                Vector newVelocity = currentVelocity.multiply(0.5).add(pullVector.multiply(0.5));

                // Cap velocity to prevent flinging
                if (newVelocity.length() > 0.8) {
                    newVelocity = newVelocity.normalize().multiply(0.8);
                }

                e.setVelocity(newVelocity);

                // Show pull effect
                if (tickCount % 3 == 0) {
                    world.spawnParticle(
                            Particle.CRIT,
                            entityLoc.add(0, 0.5, 0),
                            2,
                            0.1, 0.1, 0.1, 0.05
                    );
                }
            }
        }
    }
}
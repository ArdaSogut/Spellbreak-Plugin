package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class PhotonBeamAbility implements Ability {
    private int cooldown = 13;
    private int manaCost = 30;
    private String requiredClass = "starcaller";

    // Charging parameters
    private int maxChargeTime = 50; // 3 seconds (20 ticks/sec)
    private int focusTime = 30; // Time when beams start focusing
    private int minChargeTime = 30; // Minimum charge time before release is allowed
    private int beamCount = 6;
    private double initialSpread = 2.0; // Initial spread radius
    private double finalSpread = 0.1;
    private double minDamage = 1.0;
    private double maxDamage = 4.0;
    private double convergenceDistance = 10.0; // Distance where beams converge
    private double beamRange = 25.0; // Maximum beam range
    private double projectileSpeed = 1.5; // Speed of beam particles
    private double finalBeamThickness = 1.2; // Thickness of final beam
    private int finalBeamDuration = 5; // Duration of final beam effect

    private final Map<UUID, ChargingState> chargingPlayers = new HashMap<>();
    private final Random random = new Random();

    @Override public String getName() { return "PhotonBeam"; }
    @Override public String getDescription() {
        return "Hold shift to charge scattered photon beams. Release to fire a focused beam.";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; } // No click trigger

    @Override
    public void activate(Player player) {
        // This will be called when player releases shift
        UUID uuid = player.getUniqueId();
        if (chargingPlayers.containsKey(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "Releasing Photon Beam...");
            chargingPlayers.get(uuid).releaseBeam();
            chargingPlayers.remove(uuid);
        }
    }

    public void startCharging(Player player) {
        UUID uuid = player.getUniqueId();
        if (chargingPlayers.containsKey(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "Already charging PhotonBeam!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Starting Photon Beam charge...");
        ChargingState state = new ChargingState(player);
        chargingPlayers.put(uuid, state);
        state.start();
    }

    public void cancelCharging(Player player) {
        UUID uuid = player.getUniqueId();
        if (chargingPlayers.containsKey(uuid)) {
            chargingPlayers.get(uuid).cancel();
            chargingPlayers.remove(uuid);
        }
    }

    public boolean isPlayerCharging(Player player) {
        return chargingPlayers.containsKey(player.getUniqueId());
    }

    @Override public boolean isSuccessful() { return true; }

    @Override
    public String getDeathMessage(String victim, String caster, String unused) {
        return String.format("§6%s §ewas obliterated by §6%s§e's Photon Beam!", victim, caster);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String b = "abilities.photonbeam.";
        cooldown = cfg.getInt(b + "cooldown", cooldown);
        manaCost = cfg.getInt(b + "mana-cost", manaCost);
        maxChargeTime = cfg.getInt(b + "max-charge-time", maxChargeTime);
        focusTime = cfg.getInt(b + "focus-time", focusTime);
        minChargeTime = cfg.getInt(b + "min-charge-time", minChargeTime);
        beamCount = cfg.getInt(b + "beam-count", beamCount);
        initialSpread = cfg.getDouble(b + "initial-spread", initialSpread);
        finalSpread = cfg.getDouble(b + "final-spread", finalSpread);
        minDamage = cfg.getDouble(b + "min-damage", minDamage);
        maxDamage = cfg.getDouble(b + "max-damage", maxDamage);
        convergenceDistance = cfg.getDouble(b + "convergence-distance", convergenceDistance);
        beamRange = cfg.getDouble(b + "beam-range", beamRange);
        projectileSpeed = cfg.getDouble(b + "projectile-speed", projectileSpeed);
        finalBeamThickness = cfg.getDouble(b + "final-beam-thickness", finalBeamThickness);
        finalBeamDuration = cfg.getInt(b + "final-beam-duration", finalBeamDuration);
        requiredClass = cfg.getString(b + "required-class", requiredClass);
    }

    private class ChargingState {
        private final Player player;
        private BukkitTask task;
        private int chargeTicks = 0;
        private final List<Vector> initialBeamOffsets = new ArrayList<>();
        private final Set<UUID> damagedEntities = new HashSet<>();
        private int lastDamageTick = 0;

        public ChargingState(Player player) {
            this.player = player;
            // Initialize random beam offsets (not absolute directions)
            for (int i = 0; i < beamCount; i++) {
                initialBeamOffsets.add(generateRandomOffset());
            }
        }

        public void start() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    // Check if player is still online
                    if (!player.isOnline()) {
                        cancel();
                        chargingPlayers.remove(player.getUniqueId());
                        return;
                    }

                    // Check if player is still sneaking
                    if (!player.isSneaking()) {
                        // Player released shift - release beam
                        releaseBeam();
                        cancel();
                        chargingPlayers.remove(player.getUniqueId());
                        return;
                    }

                    // Auto-release if max charge time reached
                    if (chargeTicks++ >= maxChargeTime) {
                        player.sendMessage(ChatColor.GOLD + "Maximum charge reached! Auto-releasing beam...");
                        releaseBeam();
                        // Set cooldown here for auto-release
                        Spellbreak.getInstance().getCooldownManager().setCooldown(player, getName(), getCooldown());
                        cancel();
                        chargingPlayers.remove(player.getUniqueId());
                        return;
                    }

                    // Show charging effect
                    showChargingBeams();

                    // Apply damage from beams during charging (less frequently)
                    if (chargeTicks - lastDamageTick >= 20) { // Only every second instead of every 10 ticks
                        applyBeamDamage();
                        lastDamageTick = chargeTicks;
                    }
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        }

        private Vector generateRandomOffset() {
            // Create a more structured spread pattern - circular with some randomness
            double baseAngle = (2.0 * Math.PI * initialBeamOffsets.size()) / beamCount; // Evenly distribute base angles
            double angleVariation = 0.3; // Small random variation to avoid perfect symmetry
            double theta = baseAngle + (random.nextDouble() - 0.5) * angleVariation;

            // Two rings of beams - inner and outer for more structure
            double radius;
            if (initialBeamOffsets.size() < beamCount / 2) {
                radius = 0.7 + random.nextDouble() * 0.4; // Inner ring (0.7 to 1.1)
            } else {
                radius = 1.2 + random.nextDouble() * 0.6; // Outer ring (1.2 to 1.8)
            }

            // Create offset in a plane perpendicular to the look direction
            double x = Math.cos(theta) * radius;
            double y = Math.sin(theta) * radius;
            double z = 0; // No forward/backward offset, just spread

            return new Vector(x, y, z);
        }

        private void showChargingBeams() {
            Location eyeLoc = player.getEyeLocation();
            Vector playerDirection = player.getLocation().getDirection();
            double convergenceProgress = Math.min(1.0, Math.pow((double) chargeTicks / focusTime, 0.6));

            // Don't use a fixed convergence point - let calculateConvergingBeam handle targeting
            Location dummyConvergencePoint = eyeLoc.clone().add(playerDirection.clone().multiply(convergenceDistance));

            for (int i = 0; i < beamCount; i++) {
                BeamInfo beamInfo = calculateConvergingBeam(eyeLoc, dummyConvergencePoint, i, convergenceProgress);
                drawPhotonBeam(beamInfo.startLocation, beamInfo.direction, beamRange, 0.2, convergenceProgress, 0.8f);
            }
        }

        private BeamInfo calculateConvergingBeam(Location playerEye, Location convergencePoint, int beamIndex, double progress) {
            // Get the original offset for this beam
            Vector offset = initialBeamOffsets.get(beamIndex).clone();

            // Create a coordinate system based on the player's CURRENT look direction
            Vector forward = player.getLocation().getDirection().normalize();
            Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();

            // Handle edge case when looking straight up/down
            if (right.length() < 0.1) {
                right = new Vector(1, 0, 0);
            }

            Vector up = right.clone().crossProduct(forward).normalize();

            // Calculate current spread based on progress - slower convergence with minimum spread
            double minSpreadMultiplier = 0.2;
            double currentSpread = initialSpread * (1.0 - progress * (1.0 - minSpreadMultiplier)) + finalSpread * progress;

            // Calculate the starting point of this individual beam (spread out from player's eye)
            Vector spreadOffset = right.clone().multiply(offset.getX() * currentSpread)
                    .add(up.clone().multiply(offset.getY() * currentSpread));
            Location beamStart = playerEye.clone().add(spreadOffset);

            // FIX: Instead of always aiming at a fixed convergence point,
            // use raytrace to find where the player is actually looking
            RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(
                    playerEye,
                    forward,
                    beamRange,
                    FluidCollisionMode.NEVER,
                    true
            );

            Location actualTarget;
            if (rayTrace != null && rayTrace.getHitBlock() != null) {
                // Player is looking at a block - converge there
                actualTarget = rayTrace.getHitPosition().toLocation(player.getWorld());
            } else {
                // No block hit - use maximum range in look direction
                actualTarget = playerEye.clone().add(forward.clone().multiply(beamRange));
            }

            // During early charging, beams spread out from start position
            // As charging progresses, they converge toward the actual target
            Vector beamDirection;
            if (progress < 0.3) {
                // Early charging: beams go in spread directions
                beamDirection = forward.clone().add(spreadOffset.clone().multiply(0.3)).normalize();
            } else {
                // Later charging: beams converge toward actual target
                double convergenceFactor = (progress - 0.3) / 0.7; // 0 to 1 as progress goes from 0.3 to 1.0

                // Interpolate between spread direction and target direction
                Vector spreadDirection = forward.clone().add(spreadOffset.clone().multiply(0.3)).normalize();
                Vector targetDirection = actualTarget.toVector().subtract(beamStart.toVector()).normalize();

                beamDirection = spreadDirection.clone()
                        .multiply(1.0 - convergenceFactor)
                        .add(targetDirection.clone().multiply(convergenceFactor))
                        .normalize();
            }

            return new BeamInfo(beamStart, beamDirection);
        }

        // Helper class to store beam information
        private class BeamInfo {
            final Location startLocation;
            final Vector direction;

            BeamInfo(Location start, Vector dir) {
                this.startLocation = start;
                this.direction = dir;
            }
        }

        private void drawPhotonBeam(Location start, Vector direction, double maxRange, double step, double progress, float particleSize) {
            World world = start.getWorld();
            Color color = getBeamColor(progress);
            Particle.DustOptions dust = new Particle.DustOptions(color, particleSize);

            for (double d = 0.5; d < maxRange; d += step) {
                Location point = start.clone().add(direction.clone().multiply(d));

                // Main beam particles - more dense for continuous line
                world.spawnParticle(Particle.DUST, point, 2, 0.02, 0.02, 0.02, 0, dust);

                // Occasional brighter photon effect
                if (random.nextDouble() < 0.2) {
                    Color brightColor = Color.fromRGB(255, 255, 200); // Bright yellowish-white
                    Particle.DustOptions brightDust = new Particle.DustOptions(brightColor, particleSize * 1.2f);
                    world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0, brightDust);
                }

                // Add electric sparks occasionally for energy effect
                if (random.nextDouble() < 0.1) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }

        private Color getBeamColor(double progress) {
            // Photon color progression: yellow -> bright yellow -> white
            if (progress < 0.4) {
                // Starting yellow
                return Color.fromRGB(255, 255, 100);
            } else if (progress < 0.8) {
                // Bright yellow
                return Color.fromRGB(255, 255, 150);
            } else {
                // Pure white (focused photons)
                return Color.fromRGB(255, 255, 255);
            }
        }

        private void applyBeamDamage() {
            Location eyeLoc = player.getEyeLocation();
            Vector playerDirection = player.getLocation().getDirection();
            double convergenceProgress = Math.min(1.0, Math.pow((double) chargeTicks / focusTime, 0.6));

            // Don't use a fixed convergence point - let calculateConvergingBeam handle targeting
            Location dummyConvergencePoint = eyeLoc.clone().add(playerDirection.clone().multiply(convergenceDistance));

            for (int i = 0; i < initialBeamOffsets.size(); i++) {
                BeamInfo beamInfo = calculateConvergingBeam(eyeLoc, dummyConvergencePoint, i, convergenceProgress);

                RayTraceResult result = player.getWorld().rayTraceEntities(
                        beamInfo.startLocation,
                        beamInfo.direction,
                        beamRange,
                        0.3, // Smaller hitbox
                        entity -> entity != player && entity instanceof LivingEntity && !damagedEntities.contains(entity.getUniqueId())
                );

                if (result != null && result.getHitEntity() != null) {
                    LivingEntity target = (LivingEntity) result.getHitEntity();

                    // Much reduced damage during charging and add to damaged set
                    Spellbreak.getInstance().getAbilityDamage().damage(
                            target,
                            minDamage * 0.1, // Very reduced damage during charging
                            player,
                            PhotonBeamAbility.this,
                            "PhotonBeam"
                    );

                    damagedEntities.add(target.getUniqueId());

                    // Subtle hit effect
                    target.getWorld().spawnParticle(
                            Particle.CRIT,
                            target.getLocation().add(0, 1, 0),
                            3, 0.2, 0.2, 0.2, 0
                    );
                }
            }
        }

        public void releaseBeam() {
            if (chargeTicks < minChargeTime) {
                player.sendMessage(ChatColor.RED + "Beam released too early! Need at least " + (minChargeTime/20.0) + " seconds to charge.");
                // Cancel the beam entirely if released too early
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);
                return;
            }

            if (chargeTicks < focusTime) {
                player.sendMessage(ChatColor.RED + "Beam not fully focused yet, but firing anyway...");
                // Still fire but with reduced damage
                shootFocusedBeam(minDamage * 0.7);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.2f);
                return;
            }

            // Calculate damage based on charge time
            double damage = minDamage + (maxDamage - minDamage) *
                    Math.min(1.0, (double) (chargeTicks - focusTime) / (maxChargeTime - focusTime));

            player.sendMessage(ChatColor.GREEN + "Releasing focused beam with " + String.format("%.1f", damage) + " damage!");

            shootFocusedBeam(damage);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 1.8f);
        }

        private void shootFocusedBeam(double damage) {
            // Use raytrace to find where player is actually looking for the final beam
            Location eyeLoc = player.getEyeLocation();
            Vector direction = player.getLocation().getDirection();

            // Find actual target point
            RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(
                    eyeLoc,
                    direction,
                    25, // Extended range for final beam
                    FluidCollisionMode.NEVER,
                    true
            );

            Location targetPoint;
            if (rayTrace != null && rayTrace.getHitBlock() != null) {
                targetPoint = rayTrace.getHitPosition().toLocation(player.getWorld());
            } else {
                targetPoint = eyeLoc.clone().add(direction.clone().multiply(25));
            }

            // Start the final beam from a point closer to the player but still in front
            Location beamStart = eyeLoc.clone().add(direction.clone().multiply(2));

            World world = player.getWorld();
            Set<UUID> finalDamagedEntities = new HashSet<>();

            // Enhanced final beam effect
            new BukkitRunnable() {
                final int duration = 8;
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks++ > duration) {
                        cancel();
                        return;
                    }

                    // Calculate beam path from start to target
                    Vector beamDirection = targetPoint.toVector().subtract(beamStart.toVector()).normalize();
                    double maxDistance = beamStart.distance(targetPoint);

                    // Create a sweeping beam effect
                    for (double d = 0; d <= maxDistance; d += 0.3) {
                        Location point = beamStart.clone().add(beamDirection.clone().multiply(d));

                        // Multi-layered beam effect
                        drawEnhancedBeamSegment(point, ticks, duration);

                        // Damage entities
                        for (Entity e : world.getNearbyEntities(point, 1.2, 1.2, 1.2)) {
                            if (e instanceof LivingEntity && e != player && !finalDamagedEntities.contains(e.getUniqueId())) {
                                Spellbreak.getInstance().getAbilityDamage().damage(
                                        (LivingEntity) e,
                                        damage,
                                        player,
                                        PhotonBeamAbility.this,
                                        "PhotonBeam"
                                );
                                finalDamagedEntities.add(e.getUniqueId());

                                // Enhanced hit effects
                                world.spawnParticle(Particle.FLASH, e.getLocation().add(0, 1, 0), 5);
                                world.spawnParticle(Particle.EXPLOSION, e.getLocation(), 3, 0.3, 0.3, 0.3, 0);
                                world.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.1);
                            }
                        }
                    }
                }

                private void drawEnhancedBeamSegment(Location center, int tick, int maxTicks) {
                    double intensity = 1.0 - (double) tick / maxTicks; // Fade out over time

                    // Core beam - bright white/yellow
                    Color coreColor = Color.fromRGB(255, 255, (int)(200 + 55 * intensity));
                    Particle.DustOptions coreDust = new Particle.DustOptions(coreColor, 2.5f);
                    world.spawnParticle(Particle.DUST, center, 15, 0.05, 0.05, 0.05, 0, coreDust);

                    // Outer glow - yellow
                    Color glowColor = Color.fromRGB(255, (int)(255 * intensity), (int)(100 * intensity));
                    Particle.DustOptions glowDust = new Particle.DustOptions(glowColor, 3.5f);
                    world.spawnParticle(Particle.DUST, center, 10, 0.15, 0.15, 0.15, 0, glowDust);

                    // Additional bright photon particles
                    Color photonColor = Color.fromRGB(255, 255, 255);
                    Particle.DustOptions photonDust = new Particle.DustOptions(photonColor, 1.8f);
                    world.spawnParticle(Particle.DUST, center, 20, 0.1, 0.1, 0.1, 0, photonDust);

                    // Add electric sparks for more energy
                    world.spawnParticle(Particle.ELECTRIC_SPARK, center, 8, 0.2, 0.2, 0.2, 0.05);
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        }

        public void cancel() {
            if (task != null) {
                task.cancel();
            }
            player.sendMessage(ChatColor.RED + "Photon Beam charging cancelled!");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);
        }
    }
}
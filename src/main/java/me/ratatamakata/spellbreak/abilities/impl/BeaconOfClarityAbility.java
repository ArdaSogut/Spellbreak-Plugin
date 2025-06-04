package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BeaconOfClarityAbility implements Ability {

    private String name = "BeaconOfClarity";
    private String description = "Deploys a beacon that periodically heals nearby allies.";
    private int cooldown = 30;
    private int manaCost = 25;
    private String requiredClass = "lightbringer";
    private double beaconRadius = 7.0;
    private int durationTicks = 200; // 10 seconds (total beacon lifetime)

    // Healing Aura Parameters
    private double healAmount = 1.0; // 0.5 hearts per pulse
    private double maxTotalHealPerAlly = 4.0; // Max 2 hearts total per ally from one beacon
    private int healIntervalTicks = 20; // Heal pulse every 1 second

    private double beaconVisualCubeSize = 0.8;
    private int radiusVisualParticles = 30;
    private double radiusVisualYOffset = 0.75;

    // Runtime state - not saved in config
    private transient Location beaconLocation = null;
    private transient UUID beaconEntityId = null;
    private transient BukkitTask mainTask = null; // Will become the heal task
    private transient BukkitTask visualTask = null;
    private transient BukkitTask radiusVisualTask = null;
    private boolean successfulActivation = false;
    private transient Map<UUID, Double> allyHealProgress; // Tracks healing per ally for this beacon instance

    private final Particle.DustOptions beaconParticleOptions = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.0f);
    private final Particle.DustOptions radiusParticleOptions = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.0f);

    @Override
    public String getName() { return name; }
    @Override
    public String getDescription() { return description; }
    @Override
    public int getCooldown() { return cooldown; }
    @Override
    public int getManaCost() { return manaCost; }
    @Override
    public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) {
        return false; // Assuming dedicated activation
    }

    @Override
    public void activate(Player player) {
        successfulActivation = false;

        if (beaconLocation != null && mainTask != null && !mainTask.isCancelled()) {
            removeBeacon(player.getWorld());
            successfulActivation = true;
            return;
        }

        // Initialize/reset heal progress map for the new beacon
        this.allyHealProgress = new HashMap<>();

        World world = player.getWorld();
        // Raycast to find target block face -- REMOVED
        // RayTraceResult rayTraceResult = world.rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 12, FluidCollisionMode.NEVER, true);

        // if (rayTraceResult == null || rayTraceResult.getHitBlock() == null) {
        //     player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_FALL, 1f, 0.8f); // Thud sound
        //     return;
        // }

        // Block hitBlock = rayTraceResult.getHitBlock(); 
        // beaconLocation = rayTraceResult.getHitPosition().toLocation(world);
        // Adjust to be on top of the block, centered
        // beaconLocation.setX(hitBlock.getX() + 0.5);
        // beaconLocation.setY(hitBlock.getY() + 1.0); // Place on top of the hit block
        // beaconLocation.setZ(hitBlock.getZ() + 0.5);

        // Set beacon location to player's feet, adjusted to ground surface
        beaconLocation = player.getLocation().clone();
        // Attempt to find solid ground below the player up to 3 blocks down
        boolean groundFound = false;
        for (int i = 0; i <= 3; i++) {
            Block blockBeneath = beaconLocation.clone().subtract(0, i, 0).getBlock();
            if (blockBeneath.getType().isSolid()) {
                beaconLocation.setY(blockBeneath.getY() + 1.0);
                groundFound = true;
                break;
            }
        }
        if (!groundFound) {
            // If no solid ground directly below (e.g. player is flying high), place it at player's exact Y or a default offset
            // For now, let's just use player's Y. Could also play a fizzle sound if no suitable ground.
            // beaconLocation.setY(player.getLocation().getY()); // Or slightly below
        } 
        // Center it on the block grid for aesthetics if desired, though player location is often not grid-aligned
        beaconLocation.setX(Math.floor(beaconLocation.getX()) + 0.5);
        beaconLocation.setZ(Math.floor(beaconLocation.getZ()) + 0.5);

        // Spawn invisible ArmorStand as beacon marker
        ArmorStand beaconMarker = world.spawn(beaconLocation, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setMarker(true);
            armorStand.setInvulnerable(true);
            armorStand.setCustomName("BeaconOfClarityMarker"); // For identification if needed
        });
        beaconEntityId = beaconMarker.getUniqueId();

        world.playSound(beaconLocation, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);

        // Healing Aura Task (repurposed mainTask)
        mainTask = new BukkitRunnable() {
            int ticksRunThisInstance = 0; // Renamed from ticksElapsed for clarity, tracks ticks for *this* beacon instance

            @Override
            public void run() {
                Spellbreak.getInstance().getLogger().info(String.format(
                    "[BeaconOfClarity DEBUG] Heal Task Running. Beacon Active Ticks: %d / %d. Heal Interval: %d ticks.",
                    ticksRunThisInstance, durationTicks, healIntervalTicks
                ));

                if (beaconLocation == null) {
                    Spellbreak.getInstance().getLogger().warning("[BeaconOfClarity DEBUG] Heal task cancelling: beaconLocation is null. Beacon may have been removed externally.");
                    this.cancel(); // Cancel this task
                    return;
                }

                // Check for beacon expiration *before* healing logic
                if (ticksRunThisInstance >= durationTicks) {
                    Spellbreak.getInstance().getLogger().info(String.format(
                        "[BeaconOfClarity DEBUG] Heal task reached max duration (%d >= %d). Removing beacon.",
                        ticksRunThisInstance, durationTicks
                    ));
                    removeBeacon(world); // This will also cancel other associated tasks
                    this.cancel(); // Crucial: Cancel this BukkitRunnable instance
                    return;
                }

                List<Entity> nearbyEntities = new ArrayList<>(world.getNearbyEntities(beaconLocation, beaconRadius, beaconRadius, beaconRadius));

                for (Entity entity : nearbyEntities) {
                    if (entity instanceof Player) {
                        Player ally = (Player) entity;
                        UUID targetPlayerId = ally.getUniqueId();
                        double currentHealth = ally.getHealth();
                        double maxVanillaHealth = ally.getAttribute(Attribute.MAX_HEALTH).getValue();
                        double currentHealedByThisBeacon = allyHealProgress.getOrDefault(targetPlayerId, 0.0);

                        Spellbreak.getInstance().getLogger().info(String.format(
                            "[BeaconOfClarity DEBUG] Checking target %s: HP=%.1f/%.1f, HealedByBeacon=%.1f/%.1f",
                            ally.getName(), currentHealth, maxVanillaHealth, currentHealedByThisBeacon / 2.0, maxTotalHealPerAlly / 2.0
                        ));

                        if (currentHealedByThisBeacon < maxTotalHealPerAlly && currentHealth < maxVanillaHealth) {
                            double potentialHeal = healAmount;
                            double remainingHealBudgetForBeacon = maxTotalHealPerAlly - currentHealedByThisBeacon;
                            double healthNeededToMax = maxVanillaHealth - currentHealth;

                            double healToApply = Math.min(potentialHeal, Math.min(remainingHealBudgetForBeacon, healthNeededToMax));
                            
                            Spellbreak.getInstance().getLogger().info(String.format(
                                "[BeaconOfClarity DEBUG] Target %s: PotentialHeal=%.2f, BudgetLeft=%.2f, HealthToMax=%.2f, CalculatedHealToApply=%.2f",
                                ally.getName(), potentialHeal, remainingHealBudgetForBeacon, healthNeededToMax, healToApply
                            ));

                            if (healToApply > 0.001) { // Use a small epsilon for double comparison
                                ally.setHealth(currentHealth + healToApply);
                                allyHealProgress.put(targetPlayerId, currentHealedByThisBeacon + healToApply);

                                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, ally.getHeight() * 0.75, 0), 3, 0.3, 0.3, 0.3, 0);
                                ally.playSound(ally.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                                
                                Spellbreak.getInstance().getLogger().info(String.format(
                                    "[BeaconOfClarity DEBUG] HEALED %s for %.2f (%.1f hearts). New HP: %.1f. Total healed by this beacon: %.1f/%.1f hearts.",
                                    ally.getName(), healToApply, healToApply / 2.0, ally.getHealth(), allyHealProgress.get(targetPlayerId) / 2.0, maxTotalHealPerAlly / 2.0
                                ));
                            } else {
                                Spellbreak.getInstance().getLogger().info(String.format(
                                    "[BeaconOfClarity DEBUG] Target %s: No heal applied (healToApply=%.2f is too small or zero).", ally.getName(), healToApply));    
                            }
                        } else {
                            String reason = "";
                            if (!(currentHealedByThisBeacon < maxTotalHealPerAlly)) reason += "Already reached max heal from beacon. ";
                            if (!(currentHealth < maxVanillaHealth)) reason += "Target at max vanilla health. ";
                            Spellbreak.getInstance().getLogger().info(String.format(
                                "[BeaconOfClarity DEBUG] Target %s: Conditions for healing not met. Reason: %s", ally.getName(), reason.isEmpty() ? "Unknown" : reason
                            ));
                        }
                    }
                }
                ticksRunThisInstance += healIntervalTicks; // Increment by the interval the task runs at
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, healIntervalTicks);

        // Visuals: Rotating Dust Cube
        visualTask = new BukkitRunnable() {
            double angle = 0;
            final double cubeRadius = beaconVisualCubeSize / 2.0;

            @Override
            public void run() {
                if (beaconLocation == null) {
                    this.cancel();
                    return;
                }
                angle += Math.PI / 32; // Rotation speed

                // Define 8 corners of a cube
                Vector[] corners = {
                    new Vector(-cubeRadius, -cubeRadius, -cubeRadius), new Vector(cubeRadius, -cubeRadius, -cubeRadius),
                    new Vector(cubeRadius, cubeRadius, -cubeRadius),   new Vector(-cubeRadius, cubeRadius, -cubeRadius),
                    new Vector(-cubeRadius, -cubeRadius, cubeRadius),  new Vector(cubeRadius, -cubeRadius, cubeRadius),
                    new Vector(cubeRadius, cubeRadius, cubeRadius),    new Vector(-cubeRadius, cubeRadius, cubeRadius)
                };

                // Rotate and spawn particles at corners (can be expanded to edges/faces for more density)
                for (Vector corner : corners) {
                    // Apply rotation (around Y axis for simplicity, can be 3D)
                    double rotatedX = corner.getX() * Math.cos(angle) - corner.getZ() * Math.sin(angle);
                    double rotatedZ = corner.getX() * Math.sin(angle) + corner.getZ() * Math.cos(angle);
                    Location particleLoc = beaconLocation.clone().add(rotatedX, corner.getY() + cubeRadius, rotatedZ); // Centered cube
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, beaconParticleOptions);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 2L); // Fast for smooth visuals

        // Radius Visual: Hollow Circle
        radiusVisualTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (beaconLocation == null) {
                    this.cancel();
                    return;
                }
                double fixedY = beaconLocation.getY() + radiusVisualYOffset;
                for (int i = 0; i < radiusVisualParticles; i++) {
                    double angle = (2 * Math.PI * i) / radiusVisualParticles;
                    double x = beaconRadius * Math.cos(angle);
                    double z = beaconRadius * Math.sin(angle);
                    
                    Location particleLoc = new Location(world, beaconLocation.getX() + x, fixedY, beaconLocation.getZ() + z);
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, radiusParticleOptions);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 10L); // Update radius visual every 0.5 seconds

        successfulActivation = true;
    }

    // Helper method to find a suitable Y level for ground particles (optional, can be made more robust)
    private double findGroundY(Location loc) {
        Location checkLoc = loc.clone();
        // Search downwards from beacon's Y level or a bit higher
        for (double y = loc.getY() + 2; y > loc.getY() - 5; y -= 0.5) {
            checkLoc.setY(y);
            if (checkLoc.getBlock().getType().isSolid() && !checkLoc.clone().add(0,1,0).getBlock().getType().isSolid()) {
                return y + 0.1; // Slightly above the detected solid block
            }
        }
        return loc.getY(); // Default to original Y if no suitable ground found nearby
    }

    private void removeBeacon(World world) {
        Spellbreak.getInstance().getLogger().info("[BeaconOfClarity DEBUG] removeBeacon called. Cancelling tasks.");
        if (mainTask != null && !mainTask.isCancelled()) {
            mainTask.cancel();
        }
        if (visualTask != null && !visualTask.isCancelled()) {
            visualTask.cancel();
        }
        if (radiusVisualTask != null && !radiusVisualTask.isCancelled()) {
            radiusVisualTask.cancel();
        }
        if (beaconEntityId != null && world != null) {
            Entity beaconEntity = Bukkit.getEntity(beaconEntityId);
            if (beaconEntity instanceof ArmorStand) {
                beaconEntity.remove();
            }
        }
        if (beaconLocation != null && world != null) { // Play deactivate sound at old location
             world.playSound(beaconLocation, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        }

        if (allyHealProgress != null) {
            allyHealProgress.clear(); // Clear heal progress when beacon is removed
        }
        beaconLocation = null;
        beaconEntityId = null;
        mainTask = null;
        visualTask = null;
        radiusVisualTask = null; // Nullify radius visual task
    }

    @Override
    public boolean isSuccessful() {
        boolean success = successfulActivation;
        // successfulActivation = false; // Reset if one-time check
        return success;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.beaconofclarity.";
        name = cfg.getString(base + "name", name);
        description = cfg.getString(base + "description", description);
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        requiredClass = cfg.getString(base + "required-class", requiredClass);
        beaconRadius = cfg.getDouble(base + "beacon-radius", beaconRadius);
        durationTicks = cfg.getInt(base + "duration-ticks", durationTicks);
        
        // Load new healing parameters
        healAmount = cfg.getDouble(base + "heal-amount", healAmount);
        maxTotalHealPerAlly = cfg.getDouble(base + "max-total-heal-per-ally", maxTotalHealPerAlly);
        healIntervalTicks = cfg.getInt(base + "heal-interval-ticks", healIntervalTicks);

        beaconVisualCubeSize = cfg.getDouble(base + "beacon-visual-cube-size", beaconVisualCubeSize);
        radiusVisualParticles = cfg.getInt(base + "radius-visual-particles", radiusVisualParticles);
        radiusVisualYOffset = cfg.getDouble(base + "radius-visual-y-offset", radiusVisualYOffset);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return null; // This ability doesn't directly cause death
    }
} 
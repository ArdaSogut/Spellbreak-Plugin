package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors; // Added for orb detonation nearby entities

public class NatureStepAbility implements Ability {

    // Configurable values
    private String name = "NatureStep";
    private String description = "Dash forward instantly, consuming a charge. Charges regenerate over time.";
    private int manaCost = 15;
    private String requiredClass = "archdruid";
    private int maxCharges = 3;
    private int chargeRegenSeconds = 5; // Cooldown per charge
    private double dashDistance = 16.0; // Increased default range again
    private long internalCooldownMillis = 200; // 0.2 seconds between dashes
    // Orb Config
    private boolean orbEnabled = true;
    private double orbDamage = 2.0;
    private double orbRadius = 2.0; // Small radius
    private String orbParticleName = "END_ROD"; // More visible particle
    private String orbExplosionSoundName = "BLOCK_GRASS_BREAK";
    private int orbDurationTicks = 100; // 5 seconds (20 ticks/second)
    private long orbInternalCooldownMillis = 1000; // 1 second cooldown between orb generations
    private final Map<UUID, Long> lastOrbTime = new HashMap<>(); // Track last orb generation time

    // Runtime data
    private final Map<UUID, Integer> playerCharges = new HashMap<>();
    private final Map<UUID, BukkitTask> chargeRegenTasks = new HashMap<>();
    private final Map<UUID, Long> lastUsedTime = new HashMap<>(); // To prevent spamming too fast
    private boolean successfulActivation = false;
    // Orb Runtime Data
    private final Map<UUID, Location> activeOrbLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> activeOrbTasks = new HashMap<>(); // To store the visual task

    private static final Random random = new Random();
    private static final List<Material> SUITABLE_GROUND = Arrays.asList(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL, Material.MYCELIUM);
    private static final List<Material> BLOOM_OPTIONS = Arrays.asList(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.TALL_GRASS);

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public int getCooldown() {
        return 0; // Uses charge system
    }

    @Override
    public int getMaxCharges() {
        return maxCharges;
    }

    @Override
    public int getCurrentCharges(Player player) {
        return getCharges(player.getUniqueId());
    }

    @Override
    public int getChargeRegenTime() {
        return chargeRegenSeconds;
    }

    @Override
    public int getManaCost() { return manaCost; }

    @Override
    public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) {
        // Trigger on left click (air or block)
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public boolean isSuccessful() {
        return successfulActivation;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String path = "abilities.naturestep.";
        manaCost = cfg.getInt(path + "mana-cost", manaCost);
        requiredClass = cfg.getString(path + "required-class", requiredClass);
        maxCharges = cfg.getInt(path + "max-charges", maxCharges);
        chargeRegenSeconds = cfg.getInt(path + "charge-regen-seconds", chargeRegenSeconds);
        // Ensure config loading respects the new default
        dashDistance = cfg.getDouble(path + "dash-distance", 16.0); 
        internalCooldownMillis = cfg.getLong(path + "internal-cooldown-millis", 200); // Allow config override
        
        // Load Orb Config
        orbEnabled = cfg.getBoolean(path + "orb.enabled", orbEnabled);
        orbDamage = cfg.getDouble(path + "orb.damage", orbDamage);
        orbRadius = cfg.getDouble(path + "orb.radius", orbRadius);
        orbParticleName = cfg.getString(path + "orb.particle", orbParticleName);
        orbExplosionSoundName = cfg.getString(path + "orb.explosion-sound", orbExplosionSoundName);
        orbDurationTicks = cfg.getInt(path + "orb.duration-ticks", orbDurationTicks);
        orbInternalCooldownMillis = cfg.getLong(path + "orb.internal-cooldown-millis", orbInternalCooldownMillis);

        // Update description string to reflect new default range
        description = String.format("Dash %.1fm instantly (%d charges, %ds regen).",
                dashDistance, maxCharges, chargeRegenSeconds);
    }

    @Override
    public void activate(Player player) {
        successfulActivation = false; // Reset flag
        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();
        Location originLocation = player.getLocation().clone(); // Store start location safely

        // 1. Detonate existing orb if present
        if (orbEnabled && activeOrbLocations.containsKey(playerUUID)) {
            Location orbLocation = activeOrbLocations.remove(playerUUID);
            BukkitTask visualTask = activeOrbTasks.remove(playerUUID);
            if (visualTask != null && !visualTask.isCancelled()) {
                visualTask.cancel();
            }
            if (orbLocation != null) { // Ensure location exists
               detonateOrb(orbLocation, player); // Detonate BEFORE checks
            }
        }

        // 2. Check internal cooldown
        if (now - lastUsedTime.getOrDefault(playerUUID, 0L) < internalCooldownMillis) {
            return; // Silently fail if too fast, orb still detonates
        }

        // 3. Check charges
        int currentCharges = getCharges(playerUUID);
        if (currentCharges <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            // Maybe send action bar message: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cNatureStep has no charges!"));
            return;
        }

        // 4. Check mana & Consume if possible
        ManaSystem manaSystem = Spellbreak.getInstance().getManaSystem();
        if (!manaSystem.consumeMana(player, manaCost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f); // Out of mana sound
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cNot enough mana for NatureStep!"));
            return;
        }

        // --- Checks passed & Mana consumed ---

        // 5. Consume charge & Record time
        consumeCharge(playerUUID);
        lastUsedTime.put(playerUUID, now);

        // 6. Calculate target location
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();
        Location targetLocation = eyeLocation.add(direction.multiply(dashDistance));

        // 7. Check for safe teleport & Adjust if needed
        // Pass originLocation to check from where the player *was*
        Location safeLocation = findSafeLocation(originLocation, targetLocation); 
        if (safeLocation == null) {
             player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.5f);
             addCharge(playerUUID); // Refund charge if teleport fails
             manaSystem.restoreMana(player, manaCost); // Refund mana
             // Reset internal cooldown timer only if activation fails completely
             lastUsedTime.put(playerUUID, 0L); 
            return;
        }

        safeLocation.setPitch(player.getLocation().getPitch());
        safeLocation.setYaw(player.getLocation().getYaw());

        // 8. Effects at original location BEFORE teleport
        playEffects(originLocation, true); // Use the stored origin

        // 9. Teleport
        player.teleport(safeLocation);

        // 10. Play effects at new location AFTER teleport
        playEffects(player.getLocation(), false); // Use current location

        successfulActivation = true;

         // 11. Spawn new orb visual AFTER successful teleport
         if (orbEnabled) {
             // Check orb internal cooldown
             long lastOrb = lastOrbTime.getOrDefault(playerUUID, 0L);
             if (now - lastOrb >= orbInternalCooldownMillis) { // Only spawn if cooldown is over
                 // Ensure the world is not null before spawning
                 if (originLocation.getWorld() != null) {
                     spawnOrbVisual(originLocation, player); // Spawn visual at the place they LEFT FROM
                     activeOrbLocations.put(playerUUID, originLocation); // Store location for next time
                     lastOrbTime.put(playerUUID, now); // Record orb generation time
                 } else {
                     Spellbreak.getInstance().getLogger().warning("NatureStep: Could not spawn orb visual, origin location world was null for " + player.getName());
                 }
             } // else: Cooldown active, silently skip orb generation
         }
    }

    private void playEffects(Location loc, boolean isStart) {
        World world = loc.getWorld();
        if (world == null) return; // Safety check

        if (isStart) {
            // Start: Burst of green/pink particles
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f); // Slightly higher pitch
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
            world.spawnParticle(Particle.COMPOSTER, loc.add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.1);
        } else {
            // End: Gentle landing effect
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f); // Quieter, higher pitch
            // Corrected particle name
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1.2, 0), 25, 0.4, 0.6, 0.4, 0.1);
            world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, loc.add(0, 2.5, 0), 3, 0.3, 0.3, 0.3, 0);
            
            // --- Landing Bloom Effect --- 
            Block feetBlock = loc.getBlock(); // Block player is standing in
            Block groundBlock = feetBlock.getRelative(BlockFace.DOWN);

            // Check if ground is suitable and the space at feet is air/replaceable
            if (SUITABLE_GROUND.contains(groundBlock.getType()) && feetBlock.isPassable() && !feetBlock.isLiquid()) {
                final BlockData originalBlockData = feetBlock.getBlockData(); // Store original block data
                Material bloomType = BLOOM_OPTIONS.get(random.nextInt(BLOOM_OPTIONS.size()));
                feetBlock.setType(bloomType, false); // Set bloom type, no physics update

                // Schedule task to revert the block
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Check if the block is still the temporary bloom before reverting
                        if (feetBlock.getType() == bloomType) {
                            feetBlock.setBlockData(originalBlockData, false); // Restore original, no physics
                        }
                    }
                }.runTaskLater(Spellbreak.getInstance(), 60L); // Revert after 3 seconds (60 ticks)
            }
            // --- End Landing Bloom Effect --- 
        }
    }

    private void detonateOrb(Location location, Player caster) {
        World world = location.getWorld();
        if (world == null) return;

        // Play Effects
        try {
            Sound explosionSound = Sound.valueOf(orbExplosionSoundName.toUpperCase());
            world.playSound(location, explosionSound, 1.0f, 1.2f);
        } catch (IllegalArgumentException e) {
             Spellbreak.getInstance().getLogger().warning("NatureStep: Invalid orb explosion sound name in config: " + orbExplosionSoundName);
             world.playSound(location, Sound.BLOCK_GRASS_BREAK, 1.0f, 1.2f); // Nature-themed fallback sound
        }

        // Nature-themed particle effects
        Location effectLoc = location.clone().add(0, 0.5, 0);
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, effectLoc, 20, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, effectLoc, 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.COMPOSTER, effectLoc, 10, 0.4, 0.4, 0.4, 0.1);

        // Apply Damage
        if (orbDamage > 0 && orbRadius > 0) {
            // Get nearby living entities, excluding the caster
            List<LivingEntity> nearby = world.getNearbyEntities(location, orbRadius, orbRadius, orbRadius)
                    .stream()
                    .filter(e -> e instanceof LivingEntity && !e.getUniqueId().equals(caster.getUniqueId()))
                    .map(e -> (LivingEntity) e)
                    .collect(Collectors.toList());

            for (LivingEntity target : nearby) {
                 // Optional: Add team checks/PvP checks here if needed
                Spellbreak.getInstance().getAbilityDamage().damage(target, orbDamage, caster, this, null);
            }
        }
    }

    private void spawnOrbVisual(Location location, Player caster) {
        World world = location.getWorld();
        UUID playerUUID = caster.getUniqueId();
        if (world == null) return;

         // Cancel previous task for this player if somehow one still exists (safety check)
         BukkitTask existingTask = activeOrbTasks.remove(playerUUID);
         if (existingTask != null && !existingTask.isCancelled()) {
             existingTask.cancel();
         }

         Particle orbParticle;
         try {
             orbParticle = Particle.valueOf(orbParticleName.toUpperCase());
         } catch (IllegalArgumentException e) {
              Spellbreak.getInstance().getLogger().warning("NatureStep: Invalid orb particle name in config: " + orbParticleName);
              orbParticle = Particle.SPORE_BLOSSOM_AIR; // Nature-themed fallback particle
         }
         
         // Need final reference for Runnable
         final Particle particleType = orbParticle; 
         final Location orbLoc = location.clone().add(0, 1.0, 0); // Spawn slightly higher

         // Schedule auto-detonation
         new BukkitRunnable() {
             @Override
             public void run() {
                 // Check if an orb is still active for this player when the timer runs out
                 Location locationToDetonate = activeOrbLocations.remove(playerUUID); // Remove first to prevent race conditions
                 if (locationToDetonate != null) {
                     // An orb existed, cancel its visual task
                     BukkitTask visualTask = activeOrbTasks.remove(playerUUID);
                     if (visualTask != null && !visualTask.isCancelled()) {
                         visualTask.cancel();
                     }
                     // Now detonate at the stored location
                     detonateOrb(locationToDetonate, caster);
                 }
             }
         }.runTaskLater(Spellbreak.getInstance(), orbDurationTicks);

         BukkitTask task = new BukkitRunnable() {
             @Override
             public void run() {
                  // Check if player is still online and world is loaded
                  Player onlinePlayer = Spellbreak.getInstance().getServer().getPlayer(playerUUID);
                  if (onlinePlayer == null || !onlinePlayer.isOnline() || !orbLoc.isWorldLoaded()) {
                       activeOrbLocations.remove(playerUUID); // Remove location data
                       activeOrbTasks.remove(playerUUID); // Remove task entry
                       cancel(); // Stop the task
                       return;
                  }
                  // Spawn particle at the stored location
                 // Spawn 2 particles with slight spread every 4 ticks
                 world.spawnParticle(particleType, orbLoc, 2, 0.1, 0.1, 0.1, 0); 
             }
             // Run immediately, then repeat every 4 ticks (0.2 seconds)
         }.runTaskTimer(Spellbreak.getInstance(), 0L, 4L);

         // Store the new task
         activeOrbTasks.put(playerUUID, task);
    }

    private Location findSafeLocation(Location start, Location target) {
        Block blockAtTarget = target.getBlock();
        Block blockAboveTarget = blockAtTarget.getRelative(BlockFace.UP);

        // Check if the target head and feet locations are safe (not solid blocks)
        if (isSafe(blockAtTarget) && isSafe(blockAboveTarget)) {
            // Target space is clear, check ground below
            Block blockBelowTarget = blockAtTarget.getRelative(BlockFace.DOWN);
            if (blockBelowTarget.getType().isSolid()) {
                // Safe ground directly below, adjust Y slightly
                target.setY(blockAtTarget.getY() + 0.01);
                return target;
            } else {
                // Target space is clear, but ground isn't immediately below.
                // Allow teleporting into air, player will fall naturally.
                // We could add checks here to prevent extreme falls if desired.
                // For now, just return the target location in the air.
                return target; 
            }
        }

        // Target location (feet or head) is obstructed. 
        // Optional: Could add logic here to step back or find nearest safe spot.
        // For now, return null if the direct target space is blocked.
        return null;
    }

    private boolean isSafe(Block block) {
        Material type = block.getType();
        // Air, plants, water, lava, etc. are generally passable
        return !type.isSolid() || type.isInteractable() || type == Material.WATER || type == Material.LAVA;
         // Note: Might want to refine this. isSolid() can be tricky.
         // Material.isOccluding() might be better sometimes, or a custom list.
         // Also consider player height (need 2 blocks free usually).
    }

    private int getCharges(UUID playerUUID) {
        return playerCharges.computeIfAbsent(playerUUID, k -> maxCharges);
    }

    private void consumeCharge(UUID playerUUID) {
        int current = getCharges(playerUUID);
        if (current > 0) {
            playerCharges.put(playerUUID, current - 1);
            // If we just used a charge and were at max, start the regen timer
            if (current == maxCharges) {
                startRegenerationTask(playerUUID);
            }
        } 
        // Update HUD if possible (requires reference or event)
        // Player p = Bukkit.getPlayer(playerUUID); if (p != null) updateHUD(p);
    }
    
    private void addCharge(UUID playerUUID) {
         int current = getCharges(playerUUID);
         if (current < maxCharges) {
             playerCharges.put(playerUUID, current + 1);
         }
        // Update HUD if possible
        // Player p = Bukkit.getPlayer(playerUUID); if (p != null) updateHUD(p);
    }

    private void startRegenerationTask(UUID playerUUID) {
        // Cancel existing task for this player if any
        if (chargeRegenTasks.containsKey(playerUUID)) {
            chargeRegenTasks.get(playerUUID).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                int current = getCharges(playerUUID);
                if (current < maxCharges) {
                    addCharge(playerUUID);
                    // If not yet max charges, keep the timer running for the next charge
                    if (getCharges(playerUUID) < maxCharges) {
                        // Task will continue running
                    } else {
                        // Reached max charges, stop this regen task
                        chargeRegenTasks.remove(playerUUID);
                        cancel();
                    }
                } else {
                    // Already at max charges, stop task
                    chargeRegenTasks.remove(playerUUID);
                    cancel();
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), chargeRegenSeconds * 20L, chargeRegenSeconds * 20L);

        chargeRegenTasks.put(playerUUID, task);
    }

    // Placeholder for potential HUD update logic
    // public void updateHUD(Player player) { ... }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return null;
    }
} 
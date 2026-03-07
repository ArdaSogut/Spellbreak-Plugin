package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
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
import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;


public class NatureStepAbility implements Ability {

    // Configurable values
    private String name = "NatureStep";
    private String description = "Dash forward instantly, consuming a charge. Charges regenerate over time.";
    private int manaCost = 15;
    private String requiredClass = "archdruid";
    private int maxCharges = 3;
    private int chargeRegenSeconds = 4; // Cooldown per charge
    private double dashDistance = 10.0; // Increased default range again
    private long internalCooldownMillis = 200; // 0.2 seconds between dashes
    // Orb Config
    private boolean orbEnabled = true;
    private double orbDamage = 1.0;
    private double orbRadius = 1.4; // Small radius
    private String orbParticleName = "END_ROD"; // More visible particle
    private String orbExplosionSoundName = "BLOCK_GRASS_BREAK";
    private int orbDurationTicks = 60; // 5 seconds (20 ticks/second)
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
    public int getMaxCharges(Player player) {
        return getMaxCharges(player.getUniqueId());
    }
    
    private int getMaxCharges(UUID playerUUID) {
        SpellLevel sl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
                playerUUID, 
                Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(playerUUID), 
                getName());
        return Math.max(1, (int)Math.round(maxCharges * sl.getDamageMultiplier()));
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
        dashDistance = cfg.getDouble(path + "dash-distance", 10.0);
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
        successfulActivation = false;
        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();
        Location originLocation = player.getLocation().clone();

        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(playerUUID,
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(playerUUID),
                        getName());

        double scaledDashDistance = dashDistance * sl.getRangeMultiplier();
        double scaledOrbDamage = orbDamage * sl.getDamageMultiplier();
        double scaledOrbRadius = orbRadius * sl.getRangeMultiplier();

        // 1. Detonate existing orb if present
        if (orbEnabled && activeOrbLocations.containsKey(playerUUID)) {
            Location orbLocation = activeOrbLocations.remove(playerUUID);
            BukkitTask visualTask = activeOrbTasks.remove(playerUUID);
            if (visualTask != null && !visualTask.isCancelled()) visualTask.cancel();
            if (orbLocation != null) detonateOrb(orbLocation, player, scaledOrbDamage, scaledOrbRadius, sl);
        }

        // 2. Check internal cooldown
        if (now - lastUsedTime.getOrDefault(playerUUID, 0L) < internalCooldownMillis) return;

        // 3. Check charges
        int currentCharges = getCharges(playerUUID);
        if (currentCharges <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            return;
        }

        // 4. Check mana
        ManaSystem manaSystem = Spellbreak.getInstance().getManaSystem();
        if (!manaSystem.consumeMana(player, manaCost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent("§cNot enough mana for NatureStep!"));
            return;
        }

        // 5. Consume charge & record time
        consumeCharge(playerUUID);
        lastUsedTime.put(playerUUID, now);

        // 6. Calculate target location
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();
        Location targetLocation = eyeLocation.add(direction.multiply(scaledDashDistance));

        // 7. Check for safe teleport
        Location safeLocation = findSafeLocation(originLocation, targetLocation);
        if (safeLocation == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.5f);
            addCharge(playerUUID);
            manaSystem.restoreMana(player, manaCost);
            lastUsedTime.put(playerUUID, 0L);
            return;
        }

        safeLocation.setPitch(player.getLocation().getPitch());
        safeLocation.setYaw(player.getLocation().getYaw());

        // 8. Effects at origin
        playEffects(originLocation, true, sl);

        // 9. Teleport
        player.teleport(safeLocation);

        // 10. Effects at destination
        playEffects(player.getLocation(), false, sl);

        successfulActivation = true;

        // 11. Spawn new orb
        if (orbEnabled) {
            long lastOrb = lastOrbTime.getOrDefault(playerUUID, 0L);
            if (now - lastOrb >= orbInternalCooldownMillis) {
                if (originLocation.getWorld() != null) {
                    spawnOrbVisual(originLocation, player);
                    activeOrbLocations.put(playerUUID, originLocation);
                    lastOrbTime.put(playerUUID, now);
                }
            }
        }
    }

    private void playEffects(Location loc, boolean isStart, SpellLevel sl) {
        World world = loc.getWorld();
        if (world == null) return;

        if (isStart) {
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f + sl.getLevel() * 0.05f);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.clone().add(0, 1, 0), 30 + sl.getLevel() * 5, 0.5, 0.5, 0.5, 0.05);
            world.spawnParticle(Particle.COMPOSTER, loc.clone().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.1);
        } else {
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1.2, 0), 25 + sl.getLevel() * 3, 0.4, 0.6, 0.4, 0.1);

            // Level 3+: Cherry leaves blossom (instead of standard bloom)
            if (sl.getLevel() >= 3) {
                world.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 2.5, 0), 8, 0.5, 0.3, 0.5, 0.05);
            } else {
                world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, loc.clone().add(0, 2.5, 0), 3, 0.3, 0.3, 0.3, 0);
            }

            Block feetBlock = loc.getBlock();
            Block groundBlock = feetBlock.getRelative(BlockFace.DOWN);
            if (SUITABLE_GROUND.contains(groundBlock.getType()) && feetBlock.isPassable() && !feetBlock.isLiquid()) {
                final BlockData originalBlockData = feetBlock.getBlockData();
                // Level 3+: cherry blossom sapling
                Material bloomType = (sl.getLevel() >= 3)
                        ? Material.CHERRY_SAPLING
                        : BLOOM_OPTIONS.get(random.nextInt(BLOOM_OPTIONS.size()));
                feetBlock.setType(bloomType, false);
                new BukkitRunnable() {
                    @Override public void run() {
                        if (feetBlock.getType() == bloomType) feetBlock.setBlockData(originalBlockData, false);
                    }
                }.runTaskLater(Spellbreak.getInstance(), 60L);
            }
        }
    }

    // Overload for backward compat (no level info)
    private void playEffects(Location loc, boolean isStart) {
        playEffects(loc, isStart, Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(java.util.UUID.randomUUID(), "archdruid", getName()));
    }

    private void detonateOrb(Location location, Player caster, double scaledDamage, double scaledRadius, SpellLevel sl) {
        World world = location.getWorld();
        if (world == null) return;

        try {
            Sound explosionSound = Sound.valueOf(orbExplosionSoundName.toUpperCase());
            world.playSound(location, explosionSound, 1.0f, 1.2f + sl.getLevel() * 0.05f);
        } catch (IllegalArgumentException e) {
            world.playSound(location, Sound.BLOCK_GRASS_BREAK, 1.0f, 1.2f);
        }

        Location effectLoc = location.clone().add(0, 0.5, 0);
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, effectLoc, 20 + sl.getLevel() * 5, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, effectLoc, 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.COMPOSTER, effectLoc, 10, 0.4, 0.4, 0.4, 0.1);

        // Level 3+: cherry leaf burst on detonation
        if (sl.getLevel() >= 3) {
            world.spawnParticle(Particle.CHERRY_LEAVES, effectLoc, 12, 0.6, 0.6, 0.6, 0.1);
        }

        // Level 5: ring of totem particles
        if (sl.getLevel() >= 5) {
            int ringPoints = 24;
            for (int i = 0; i < ringPoints; i++) {
                double angle = 2 * Math.PI * i / ringPoints;
                Location ring = effectLoc.clone().add(
                        Math.cos(angle) * scaledRadius * 0.7, 0.2, Math.sin(angle) * scaledRadius * 0.7);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, ring, 2, 0.05, 0.1, 0.05, 0);
            }
        }

        if (scaledDamage > 0 && scaledRadius > 0) {
            world.getNearbyEntities(location, scaledRadius, scaledRadius, scaledRadius)
                    .stream()
                    .filter(e -> e instanceof LivingEntity && !e.getUniqueId().equals(caster.getUniqueId()))
                    .map(e -> (LivingEntity) e)
                    .collect(java.util.stream.Collectors.toList())
                    .forEach(target -> Spellbreak.getInstance().getAbilityDamage().damage(target, scaledDamage, caster, this, null));
        }
    }

    // Backward-compat overload (used during spawn orb auto-detonation)
    private void detonateOrb(Location location, Player caster) {
        detonateOrb(location, caster, orbDamage, orbRadius,
                Spellbreak.getInstance().getLevelManager()
                        .getSpellLevel(caster.getUniqueId(),
                                Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(caster.getUniqueId()),
                                getName()));
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
        return playerCharges.computeIfAbsent(playerUUID, k -> getMaxCharges(playerUUID));
    }

    private void consumeCharge(UUID playerUUID) {
        int current = getCharges(playerUUID);
        if (current > 0) {
            playerCharges.put(playerUUID, current - 1);
            // Fix: Start regen task if it's not already running and we are now below max
            if (chargeRegenTasks.get(playerUUID) == null || chargeRegenTasks.get(playerUUID).isCancelled()) {
                startRegenerationTask(playerUUID);
            }
        } 
        // Update HUD if possible (requires reference or event)
        // Player p = Bukkit.getPlayer(playerUUID); if (p != null) updateHUD(p);
    }
    
    private void addCharge(UUID playerUUID) {
         int current = getCharges(playerUUID);
         if (current < getMaxCharges(playerUUID)) {
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
                int mCharges = getMaxCharges(playerUUID);
                if (current < mCharges) {
                    addCharge(playerUUID);
                    // If not yet max charges, keep the timer running for the next charge
                    if (getCharges(playerUUID) < mCharges) {
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
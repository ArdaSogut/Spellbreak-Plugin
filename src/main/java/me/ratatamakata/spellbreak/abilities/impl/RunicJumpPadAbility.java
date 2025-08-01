package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class RunicJumpPadAbility implements Ability {

    // Configurable values
    private String name = "RunicJumpPad";
    private String description = "Create up to 2 runic pads that launch entities upward. Charges regenerate over time.";
    private int manaCost = 40;
    private String requiredClass = "runesmith";
    private int maxCharges = 2; // Maximum number of charges
    private int chargeRegenSeconds = 6; // Time to regenerate a charge
    private double radius = 4.0; // Radius of the pad
    private int buildTicks = 9; // Time to build the pad
    private int activeTicks = 80; // Duration of pad activation
    private double casterLaunchPower = 2.5; // Launch power for caster
    private double entityLaunchPower = 1.5; // Launch power for entities
    private double casterYBoost = 1.2; // Y velocity boost for caster
    private double entityYBoost = 1.5; // Y velocity boost for entities
    private double padHeight = 0.85; // Height of the pad
    private long internalCooldownMillis = 200; // 0.2 seconds between pads
    private double particleDensity = 0.8; // Lower value = fewer particles

    // Runtime data
    private final Map<UUID, Integer> playerCharges = new HashMap<>();
    private final Map<UUID, BukkitTask> chargeRegenTasks = new HashMap<>();
    private final Map<UUID, Long> lastUsedTime = new HashMap<>();
    private final Map<UUID, List<ActivePad>> playerPads = new HashMap<>();
    private boolean successfulActivation = false;

    // Visual settings
    private final Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(173, 216, 230), 1.2f);

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getCooldown() {
        return 0; // Uses charge system instead
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
    public int getManaCost() {
        return manaCost;
    }

    @Override
    public String getRequiredClass() {
        return requiredClass;
    }

    @Override
    public boolean isTriggerAction(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public boolean isSuccessful() {
        return successfulActivation;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.runicjumppad.";
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        requiredClass = cfg.getString(base + "required-class", requiredClass);
        maxCharges = cfg.getInt(base + "max-charges", maxCharges);
        chargeRegenSeconds = cfg.getInt(base + "charge-regen-seconds", chargeRegenSeconds);
        radius = cfg.getDouble(base + "radius", radius);
        buildTicks = cfg.getInt(base + "build-time", buildTicks);
        activeTicks = cfg.getInt(base + "duration", activeTicks);
        padHeight = cfg.getDouble(base + "height", padHeight);
        casterLaunchPower = cfg.getDouble(base + "caster-launch-power", casterLaunchPower);
        entityLaunchPower = cfg.getDouble(base + "entity-launch-power", entityLaunchPower);
        casterYBoost = cfg.getDouble(base + "caster-y-boost", casterYBoost);
        entityYBoost = cfg.getDouble(base + "entity-y-boost", entityYBoost);
        internalCooldownMillis = cfg.getLong(base + "internal-cooldown-millis", internalCooldownMillis);
        particleDensity = cfg.getDouble(base + "particle-density", particleDensity);

        // Update description to reflect settings
        description = String.format("Create up to %d runic pads that launch entities. Charges regenerate every %ds.",
                maxCharges, chargeRegenSeconds);
    }

    @Override
    public void activate(Player player) {
        successfulActivation = false; // Reset flag
        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Check internal cooldown
        if (now - lastUsedTime.getOrDefault(playerUUID, 0L) < internalCooldownMillis) {
            return; // Silently fail if too fast
        }

        // Check charges
        int currentCharges = getCharges(playerUUID);
        if (currentCharges <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§cNo charges left for " + name + "!"));
            return;
        }

        // Check mana
        ManaSystem manaSystem = Spellbreak.getInstance().getManaSystem();
        if (!manaSystem.consumeMana(player, manaCost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§cNot enough mana for " + name + "!"));
            return;
        }

        // Find target location - only allow targeting solid blocks
        Block targetBlock = getTargetBlock(player, 20);
        if (targetBlock == null) {
            manaSystem.restoreMana(player, manaCost); // Refund mana
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§cMust target a solid block!"));
            return;
        }

        // All checks passed, update state
        Location center = targetBlock.getLocation().add(0.5, 1.1, 0.5);
        consumeCharge(playerUUID);
        lastUsedTime.put(playerUUID, now);

        // Create the pad
        List<ActivePad> pads = playerPads.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        ActivePad pad = new ActivePad(player, center);
        pads.add(pad);
        pad.start();

        successfulActivation = true;
    }

    // Helper method to only find solid blocks (not air)
    private Block getTargetBlock(Player player, int maxDistance) {
        Block block = player.getTargetBlock(null, maxDistance);
        if (block != null && block.getType() != Material.AIR && block.getType().isSolid()) {
            return block;
        }
        return null;
    }

    private class ActivePad {
        private final Player player;
        private final Location center;
        private BukkitRunnable runnable;
        private long expireTime;
        private final int padId;
        private int tick = 0;

        ActivePad(Player player, Location center) {
            this.player = player;
            this.center = center;

            // Generate a unique pad ID (index in the list + 1)
            List<ActivePad> pads = playerPads.getOrDefault(player.getUniqueId(), Collections.emptyList());
            this.padId = pads.size() + 1;
        }

        void start() {
            runnable = new BukkitRunnable() {
                boolean ready = false;

                @Override
                public void run() {
                    // Action bar updates - only update every second
                    if (tick % 20 == 0) {
                        updateActionBar();
                    }

                    if (tick < buildTicks) {
                        // Build up effect
                        drawPadFilled(center, radius * ((double)tick/buildTicks));
                    } else if (tick < buildTicks + activeTicks) {
                        if (!ready) {
                            ready = true;
                            expireTime = System.currentTimeMillis() + (activeTicks * 50);
                            // Announce pad is ready
                            player.playSound(center, Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.2f);
                        }

                        // Draw full pad with reduced particle count
                        drawPadFilled(center, radius);
                        if (tick % 3 == 0) { // Only draw height particles every 3 ticks
                            for (double y = 0.5; y <= padHeight; y += 0.6) {
                                drawPadOutline(center.clone().add(0, y, 0), radius);
                            }
                        }

                        // Launch entities
                        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, padHeight, radius)) {
                            if (entity.isOnGround()) {
                                Vector velocity;
                                if (entity.getUniqueId().equals(player.getUniqueId())) {
                                    // Launch player in look direction with Y boost
                                    velocity = player.getLocation().getDirection().multiply(casterLaunchPower).setY(casterYBoost);
                                } else {
                                    // Launch other entities with different settings
                                    velocity = new Vector(0, entityYBoost, 0);

                                    // Add some horizontal movement for entities based on their position relative to center
                                    if (entityLaunchPower > 0) {
                                        Location entityLoc = entity.getLocation();
                                        double dx = entityLoc.getX() - center.getX();
                                        double dz = entityLoc.getZ() - center.getZ();

                                        // Normalize and scale
                                        double dist = Math.sqrt(dx*dx + dz*dz);
                                        if (dist > 0.1) {
                                            double factor = entityLaunchPower / dist;
                                            velocity.setX(dx * factor);
                                            velocity.setZ(dz * factor);
                                        }
                                    }
                                }
                                entity.setVelocity(velocity);
                                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.4f, 1.8f);
                            }
                        }
                    } else {
                        // Pad is expired, clean up
                        cleanup();
                        cancel();
                    }
                    tick++;
                }
            };
            runnable.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
        }

        void updateActionBar() {
            int charges = getCharges(player.getUniqueId());

            List<ActivePad> pads = playerPads.getOrDefault(player.getUniqueId(), Collections.emptyList());
            if (pads.isEmpty()) {
                return;
            }

            // If this is the most recently placed pad, update the action bar
            if (pads.indexOf(this) == pads.size() - 1) {
                String message;
                if (tick < buildTicks) {
                    message = ChatColor.AQUA + name + " #" + padId + ": " + ChatColor.WHITE +
                            charges + "/" + maxCharges + ChatColor.GRAY + " | " +
                            ChatColor.YELLOW + "Building...";
                } else {
                    message = ChatColor.AQUA + name + " #" + padId + ": " + ChatColor.WHITE +
                            charges + "/" + maxCharges + ChatColor.GRAY + " | " +
                            ChatColor.GREEN + "Active";
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            }
        }

        void cleanup() {
            List<ActivePad> pads = playerPads.get(player.getUniqueId());
            if (pads != null) {
                pads.remove(this);
                if (pads.isEmpty()) {
                    playerPads.remove(player.getUniqueId());
                }
            }
        }

        void forceCancel() {
            if (runnable != null && !runnable.isCancelled()) {
                runnable.cancel();
            }
            cleanup();
        }
    }

    private void drawPadFilled(Location loc, double r) {
        World world = loc.getWorld();
        if (world == null) return;

        // Reduce particle density
        double step = 0.5 / particleDensity;

        for (double x = -r; x <= r; x += step) {
            for (double z = -r; z <= r; z += step) {
                if (x*x + z*z <= r*r) {
                    world.spawnParticle(Particle.DUST, loc.clone().add(x, 0, z), 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private void drawPadOutline(Location loc, double r) {
        World world = loc.getWorld();
        if (world == null) return;

        // Just draw the outline with fewer particles
        double step = 0.8 / particleDensity;

        for (double theta = 0; theta < 2 * Math.PI; theta += step / r) {
            double x = r * Math.cos(theta);
            double z = r * Math.sin(theta);
            world.spawnParticle(Particle.DUST, loc.clone().add(x, 0, z), 1, 0, 0, 0, 0, dust);
        }
    }

    // Charge system methods
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
    }

    private void addCharge(UUID playerUUID) {
        int current = getCharges(playerUUID);
        if (current < maxCharges) {
            playerCharges.put(playerUUID, current + 1);
        }
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

    public void cancelPads(UUID uuid) {
        List<ActivePad> pads = playerPads.remove(uuid);
        if (pads != null) {
            pads.forEach(ActivePad::forceCancel);
        }
    }

    // Reset player state
    public void resetPlayer(UUID uuid) {
        cancelPads(uuid);
        playerCharges.put(uuid, maxCharges); // Reset to full charges
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("%s was launched to their doom by %s's Runic Jump Pad!", victim, caster);
    }
}
package me.ratatamakata.spellbreak.listeners;

import io.papermc.paper.event.entity.EntityMoveEvent;
import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class StunHandler implements Listener {
    private final HashMap<UUID, Long> stunMap = new HashMap<>();
    private final Spellbreak plugin;
    private boolean debugMode = true; // Set to true to enable debug messages

    public StunHandler(Spellbreak plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCleanupTask();

        // Log that the StunHandler has been initialized
        logDebug("StunHandler initialized");
    }

    private void logDebug(String message) {
        if (debugMode) {
            plugin.getLogger().log(Level.INFO, "[StunHandler Debug] " + message);
        }
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                stunMap.entrySet().removeIf(entry -> {
                    if (currentTime > entry.getValue()) {
                        Entity entity = plugin.getServer().getEntity(entry.getKey());
                        if (entity instanceof LivingEntity && entity.isValid()) {
                            removeStun((LivingEntity) entity);
                            logDebug("Auto-removed stun from " + entity.getName());
                        }
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    public void stun(LivingEntity entity, int durationTicks) {
        UUID uuid = entity.getUniqueId();
        long endTime = System.currentTimeMillis() + (durationTicks * 50L);
        stunMap.put(uuid, endTime);

        // Only disable AI for mobs, not players
        if(entity instanceof Mob) {
            ((Mob) entity).setAI(false);
        }

        // Store current attack speed in a tag if it's a player
        if (entity instanceof Player) {
            Player player = (Player) entity;
            // Reset cooldowns to ensure consistent behavior
            player.resetCooldown();
        }

        logDebug("Stunned " + entity.getName() + " for " + durationTicks + " ticks");

        // Make sure entity is not invulnerable when stunned
        if (entity.isInvulnerable()) {
            entity.setInvulnerable(false);
            logDebug("Removed invulnerability from " + entity.getName());
        }

        // Make sure the entity can be damaged
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (player.hasMetadata("NPC") || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                logDebug(player.getName() + " is in CREATIVE or is an NPC - may not be damageable");
            }
        }
    }

    public boolean isStunned(LivingEntity entity) {
        if (entity == null) return false;

        UUID uuid = entity.getUniqueId();
        boolean stunned = stunMap.containsKey(uuid) &&
                System.currentTimeMillis() < stunMap.get(uuid);

        if (stunned && debugMode) {
            // Only log if actually stunned to avoid spamming
            long remainingTime = (stunMap.get(uuid) - System.currentTimeMillis()) / 50L;
            if (remainingTime % 20 == 0) { // Log only every second
                logDebug(entity.getName() + " is stunned for " + remainingTime + " more ticks");
            }
        }

        return stunned;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent e) {
        if(isStunned(e.getPlayer())) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityMove(EntityMoveEvent e) {
        if(e.getEntity() instanceof LivingEntity && isStunned((LivingEntity) e.getEntity())) {
            e.setCancelled(true);
        }
    }

    // Listen for ALL damage events at lowest priority to ensure we see them first
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageLowest(EntityDamageEvent e) {
        if (e.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) e.getEntity();
            if (isStunned(entity)) {
                logDebug(entity.getName() + " is stunned and receiving damage: " + e.getDamage());

                // Make sure damage is not being blocked by invulnerability or similar
                if (entity.getNoDamageTicks() > 0) {
                    entity.setNoDamageTicks(0);
                    logDebug("Reset no damage ticks for " + entity.getName());
                }

                // Important: DO NOT cancel the event here - we want damage to go through
            }
        }
    }

    // Listen for entity damage by entity events at multiple priorities to better diagnose issues
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntityLowest(EntityDamageByEntityEvent e) {
        handleDamageEvent(e, "LOWEST");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamageByEntityLow(EntityDamageByEntityEvent e) {
        handleDamageEvent(e, "LOW");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntityNormal(EntityDamageByEntityEvent e) {
        handleDamageEvent(e, "NORMAL");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntityHigh(EntityDamageByEntityEvent e) {
        handleDamageEvent(e, "HIGH");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntityHighest(EntityDamageByEntityEvent e) {
        handleDamageEvent(e, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntityMonitor(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof LivingEntity) {
            LivingEntity victim = (LivingEntity) e.getEntity();
            if (isStunned(victim)) {
                logDebug("MONITOR: Damage to stunned " + victim.getName() +
                        " is proceeding: " + e.getDamage() + " (final)");
            }
        }
    }

    private void handleDamageEvent(EntityDamageByEntityEvent e, String priority) {
        if (e.getEntity() instanceof LivingEntity) {
            LivingEntity victim = (LivingEntity) e.getEntity();

            // Get the real damager (if it's a projectile, get the shooter)
            Entity damagerEntity = e.getDamager();
            if (damagerEntity instanceof Projectile) {
                Projectile projectile = (Projectile) damagerEntity;
                if (projectile.getShooter() instanceof Entity) {
                    damagerEntity = (Entity) projectile.getShooter();
                }
            }

            // If it's an area effect cloud or similar, try to get the source
            // This is a simplification - actual implementation may need more refinement
            if (damagerEntity instanceof AreaEffectCloud &&
                    ((AreaEffectCloud)damagerEntity).getSource() instanceof Entity) {
                damagerEntity = (Entity)((AreaEffectCloud)damagerEntity).getSource();
            }

            String damagerName = damagerEntity instanceof LivingEntity ?
                    ((LivingEntity)damagerEntity).getName() : damagerEntity.getType().toString();

            // If the victim is stunned, log but don't block damage
            if (isStunned(victim)) {
                logDebug(priority + ": " + damagerName + " damaging stunned " + victim.getName() +
                        " for " + e.getDamage() + (e.isCancelled() ? " (CANCELLED)" : ""));

                // CRITICAL: If the event was cancelled, we uncancelled it to allow damage
                if (e.isCancelled()) {
                    e.setCancelled(false);
                    logDebug(priority + ": Uncancelled damage event to allow damage to stunned " + victim.getName());
                }
            }

            // If the damager is stunned, explicitly allow them to deal damage (this is key)
            if (damagerEntity instanceof LivingEntity && isStunned((LivingEntity)damagerEntity)) {
                logDebug(priority + ": Stunned " + damagerName +
                        " dealing damage to " + victim.getName() +
                        " for " + e.getDamage() + (e.isCancelled() ? " (CANCELLED)" : ""));

                // IMPORTANT: Make sure the event is not cancelled
                if (e.isCancelled()) {
                    e.setCancelled(false);
                    logDebug(priority + ": Uncancelled damage event to allow stunned " +
                            damagerName + " to deal damage");
                }
            }
        }
    }

    public void removeStun(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        if(stunMap.containsKey(uuid)) {
            stunMap.remove(uuid);

            // Restore AI for mobs
            if (entity instanceof Mob && entity.isValid()) {
                ((Mob) entity).setAI(true);
            }

            // Reset attack cooldown for players to ensure they can attack immediately
            if (entity instanceof Player && entity.isValid()) {
                Player player = (Player) entity;
                player.resetCooldown();

                // Reset no damage ticks to ensure they can attack immediately
                player.setNoDamageTicks(0);

                // Schedule a task to reset again after 1 tick to handle edge cases
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isValid() && player.isOnline()) {
                            player.resetCooldown();
                            player.setNoDamageTicks(0);
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }

            logDebug("Removed stun from " + entity.getName());
        }
    }
}
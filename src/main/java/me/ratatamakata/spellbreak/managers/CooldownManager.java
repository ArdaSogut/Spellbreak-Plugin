package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private static CooldownManager instance; // Added for singleton pattern

    // Private constructor for singleton
    public CooldownManager() {}

    // Public method to get instance (Singleton pattern)
    public static synchronized CooldownManager getInstance() {
        if (instance == null) {
            instance = new CooldownManager();
        }
        return instance;
    }

    public void setCooldown(Player player, String abilityName, int seconds) {
        UUID uuid = player.getUniqueId();
        cooldowns.putIfAbsent(uuid, new HashMap<>());
        long expireTime = System.currentTimeMillis() + (seconds * 1000L);
        String abilityKey = abilityName.toLowerCase();
        cooldowns.get(uuid).put(abilityKey, expireTime);
        Spellbreak.getInstance().getLogger().info(
            String.format("[CM DEBUG] Set cooldown for %s - Ability: %s (Key: %s) - Seconds: %d (Expires: %d)", 
                          player.getName(), abilityName, abilityKey, seconds, expireTime));
    }

    public int getRemainingCooldown(Player player, String abilityName) {
        // Check for bypass first, though isOnCooldown is the primary gate
        if (Spellbreak.hasBypass(player.getUniqueId())) {
            // Spellbreak.getInstance().getLogger().info("[CM DEBUG] getRemainingCooldown: Player " + player.getName() + " has bypass. Returning 0.");
            return 0;
        }
        UUID uuid = player.getUniqueId();
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) {
            return 0;
        }
        String abilityKey = abilityName.toLowerCase();
        Long expireTime = playerCooldowns.get(abilityKey);
        if (expireTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long remaining = expireTime - currentTime;
        int remainingSeconds = (int) Math.ceil(remaining / 1000.0);
        return remainingSeconds > 0 ? remainingSeconds : 0; // Ensure it doesn't return negative
    }

    public boolean isOnCooldown(Player player, String abilityName) {
        // --- Add Bypass Check HERE ---
        if (Spellbreak.hasBypass(player.getUniqueId())) {
            Spellbreak.getInstance().getLogger().info("[CM DEBUG] isOnCooldown: Player " + player.getName() + " has bypass for ability " + abilityName + ". Returning false (not on cooldown).");
            return false; // Player has bypass, so they are never on cooldown
        }
        // --- End Bypass Check ---

        int remaining = getRemainingCooldown(player, abilityName);
        // if (remaining > 0) {
        //     Spellbreak.getInstance().getLogger().info("[CM DEBUG] isOnCooldown: Player " + player.getName() + " ability " + abilityName + " has " + remaining + "s left.");
        // } else {
        //     Spellbreak.getInstance().getLogger().info("[CM DEBUG] isOnCooldown: Player " + player.getName() + " ability " + abilityName + " is NOT on cooldown.");
        // }
        return remaining > 0;
    }

    public void clearAllCooldowns(UUID playerId) {
        Spellbreak.getInstance().getLogger().info(
            String.format("[CM DEBUG] Attempting to clear all cooldowns for player UUID: %s", playerId));
        if (cooldowns.containsKey(playerId)) {
            Map<String, Long> playerMap = cooldowns.get(playerId);
            Spellbreak.getInstance().getLogger().info(
                String.format("[CM DEBUG] Cooldowns for %s BEFORE clear: %s", playerId, playerMap.toString()));
            playerMap.clear();
            Spellbreak.getInstance().getLogger().info(
                String.format("[CM DEBUG] Cooldowns for %s AFTER clear: %s", playerId, playerMap.toString()));
            // if (cooldowns.get(playerId).isEmpty()) { // This check is fine, but removing the map itself might be cleaner
            //     cooldowns.remove(playerId);
            //     Spellbreak.getInstance().getLogger().info(String.format("[CM DEBUG] Removed empty cooldown map for %s", playerId));
            // }
        } else {
            Spellbreak.getInstance().getLogger().info(
                String.format("[CM DEBUG] No cooldown map found for %s to clear.", playerId));
        }
    }

    public void clearSpecificCooldown(UUID playerId, String abilityName) {
        if (abilityName == null || abilityName.equalsIgnoreCase("§8-")) return; // Ignore empty slots

        String abilityKey = abilityName.toLowerCase();
        Spellbreak.getInstance().getLogger().info(
            String.format("[CM DEBUG] Attempting to clear specific cooldown for player UUID: %s, Ability: %s (Key: %s)", 
                          playerId, abilityName, abilityKey));
        
        if (cooldowns.containsKey(playerId)) {
            Map<String, Long> playerMap = cooldowns.get(playerId);
            if (playerMap.containsKey(abilityKey)) {
                Long removedValue = playerMap.remove(abilityKey);
                Spellbreak.getInstance().getLogger().info(
                    String.format("[CM DEBUG] Removed cooldown for %s - Ability: %s. Old expiry: %d. Map after: %s", 
                                  playerId, abilityName, removedValue, playerMap.toString()));
            } else {
                Spellbreak.getInstance().getLogger().info(
                    String.format("[CM DEBUG] No specific cooldown found for %s - Ability: %s to clear.", 
                                  playerId, abilityName));
            }
        } else {
            Spellbreak.getInstance().getLogger().info(
                String.format("[CM DEBUG] No cooldown map found for %s when trying to clear specific ability %s.", 
                              playerId, abilityName));
        }
    }

    public void expireCooldownsForHUDAbilities(UUID playerId, String[] hudAbilityNames) {
        Spellbreak.getInstance().getLogger().info(
            String.format("[CM DEBUG] Attempting to EXPIRE cooldowns for player UUID: %s for HUD abilities.", playerId));
        
        if (!cooldowns.containsKey(playerId)) {
            Spellbreak.getInstance().getLogger().info(
                String.format("[CM DEBUG] No cooldown map found for %s. Cannot expire HUD cooldowns.", playerId));
            return;
        }

        Map<String, Long> playerMap = cooldowns.get(playerId);
        int expiredCount = 0;
        long newExpireTime = System.currentTimeMillis() - 2000L; // 2 seconds in the past

        for (String abilityName : hudAbilityNames) {
            if (abilityName == null || abilityName.equalsIgnoreCase("§8-")) {
                continue; // Skip empty slots
            }
            String abilityKey = abilityName.toLowerCase();
            if (playerMap.containsKey(abilityKey)) {
                long oldExpireTime = playerMap.get(abilityKey);
                playerMap.put(abilityKey, newExpireTime);
                Spellbreak.getInstance().getLogger().info(
                    String.format("[CM DEBUG] Expired cooldown for %s - Ability: %s (Key: %s). Old Expiry: %d, New Expiry: %d", 
                                  playerId, abilityName, abilityKey, oldExpireTime, newExpireTime));
                expiredCount++;
            } else {
                 Spellbreak.getInstance().getLogger().info(
                    String.format("[CM DEBUG] Ability %s (Key: %s) not found in cooldown map for %s during expire attempt.", 
                                  abilityName, abilityKey, playerId));
            }
        }
        Spellbreak.getInstance().getLogger().info(
            String.format("[CM DEBUG] Expired %d cooldowns for %s. Map after: %s", 
                          expiredCount, playerId, playerMap.toString()));
    }

    public long getCooldownRemaining(Player player, String abilityName) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            String key = abilityName.toLowerCase();
            if (playerCooldowns.containsKey(key)) {
                long cooldownUntil = playerCooldowns.get(key);
                long remaining = cooldownUntil - System.currentTimeMillis();
                return remaining > 0 ? remaining : 0;
            }
        }
        return 0;
    }

    public void removeCooldown(Player player, String abilityName) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            playerCooldowns.remove(abilityName.toLowerCase());
        }
    }

    public void reduceAllCooldownsForPlayer(Player player, int millisToReduce) {
        UUID playerId = player.getUniqueId();
        if (!cooldowns.containsKey(playerId)) {
            return; // No cooldowns to reduce
        }

        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns.isEmpty()) {
            return; // No active cooldowns
        }

        long currentTime = System.currentTimeMillis();
        int reducedCount = 0;

        // Iterate over a copy of keys to avoid ConcurrentModificationException if modifying the map directly
        // or use entrySet and update values carefully.
        for (Map.Entry<String, Long> entry : new HashMap<>(playerCooldowns).entrySet()) {
            String abilityKey = entry.getKey();
            Long currentExpireTime = entry.getValue();

            if (currentExpireTime > currentTime) { // If the cooldown is active
                long newExpireTime = currentExpireTime - millisToReduce;
                // Ensure cooldown doesn't go negative (become usable instantly beyond its original duration)
                // If it's meant to simply clear it if reduction is too high, then `Math.max(currentTime -1, newExpireTime)`
                // or simply set to currentTime-1 to make it immediately available.
                // For now, let's cap it at 'just expired'.
                playerCooldowns.put(abilityKey, Math.max(currentTime - 1, newExpireTime)); 
                reducedCount++;
            }
        }
        if (reducedCount > 0) {
            Spellbreak.getInstance().getLogger().info(
                String.format("[CM DEBUG] Reduced %d cooldowns for %s by %dms. New map: %s", 
                              reducedCount, player.getName(), millisToReduce, playerCooldowns.toString()));
        }
    }

    // Method to load cooldowns (e.g., from player data on join)
}
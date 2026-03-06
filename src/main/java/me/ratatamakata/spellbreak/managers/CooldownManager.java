package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {
    // Map of player UUID -> (abilityName -> expiry timestamp in millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public CooldownManager() {}

    public void setCooldown(Player player, String abilityName, int seconds) {
        UUID uuid = player.getUniqueId();
        cooldowns.putIfAbsent(uuid, new HashMap<>());

        // Apply SpellLevel cooldown reduction automatically
        double reduction = 1.0;
        try {
            String playerClass = Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(uuid);
            SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                    .getSpellLevel(uuid, playerClass, abilityName);
            reduction = sl.getCooldownReduction();
        } catch (Exception ignored) {
            // Safety: if level system fails for any reason, use base cooldown
        }

        long reducedMillis = (long)(seconds * 1000L * reduction);
        long expireTime = System.currentTimeMillis() + reducedMillis;
        cooldowns.get(uuid).put(abilityName.toLowerCase(), expireTime);
    }

    public int getRemainingCooldown(Player player, String abilityName) {
        if (Spellbreak.hasBypass(player.getUniqueId())) return 0;

        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        Long expireTime = playerCooldowns.get(abilityName.toLowerCase());
        if (expireTime == null) return 0;

        long remaining = expireTime - System.currentTimeMillis();
        int remainingSeconds = (int) Math.ceil(remaining / 1000.0);
        return remainingSeconds > 0 ? remainingSeconds : 0;
    }

    public boolean isOnCooldown(Player player, String abilityName) {
        if (Spellbreak.hasBypass(player.getUniqueId())) return false;
        return getRemainingCooldown(player, abilityName) > 0;
    }

    public void clearAllCooldowns(UUID playerId) {
        Map<String, Long> playerMap = cooldowns.get(playerId);
        if (playerMap != null) {
            playerMap.clear();
        }
    }

    public void clearSpecificCooldown(UUID playerId, String abilityName) {
        if (abilityName == null || abilityName.equalsIgnoreCase("§8-")) return;
        Map<String, Long> playerMap = cooldowns.get(playerId);
        if (playerMap != null) {
            playerMap.remove(abilityName.toLowerCase());
        }
    }

    public void expireCooldownsForHUDAbilities(UUID playerId, String[] hudAbilityNames) {
        Map<String, Long> playerMap = cooldowns.get(playerId);
        if (playerMap == null) return;

        long newExpireTime = System.currentTimeMillis() - 2000L; // 2 seconds in the past
        for (String abilityName : hudAbilityNames) {
            if (abilityName == null || abilityName.equalsIgnoreCase("§8-")) continue;
            String abilityKey = abilityName.toLowerCase();
            if (playerMap.containsKey(abilityKey)) {
                playerMap.put(abilityKey, newExpireTime);
            }
        }
    }

    /**
     * Returns remaining milliseconds for a cooldown (used for smooth HUD rendering).
     */
    public long getCooldownRemaining(Player player, String abilityName) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            Long cooldownUntil = playerCooldowns.get(abilityName.toLowerCase());
            if (cooldownUntil != null) {
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
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null || playerCooldowns.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : new HashMap<>(playerCooldowns).entrySet()) {
            if (entry.getValue() > currentTime) {
                long newExpireTime = entry.getValue() - millisToReduce;
                // Cap at "just expired" so it doesn't wrap around to a very long future time
                playerCooldowns.put(entry.getKey(), Math.max(currentTime - 1, newExpireTime));
            }
        }
    }
}
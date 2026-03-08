package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class PvPManager {

    public static class DamageRecord {
        public final String attackerName;
        public final String abilityName;
        public final double damage;
        public final long timestamp;

        public DamageRecord(String attackerName, String abilityName, double damage, long timestamp) {
            this.attackerName = attackerName;
            this.abilityName = abilityName;
            this.damage = damage;
            this.timestamp = timestamp;
        }
    }

    private final Spellbreak plugin;
    private final Map<UUID, Integer> killStreaks = new HashMap<>();
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();
    private final Map<UUID, List<DamageRecord>> recentDamage = new HashMap<>();

    private static final long COMBAT_DURATION_MILLIS = 30000; // 30 seconds

    public PvPManager(Spellbreak plugin) {
        this.plugin = plugin;
    }

    public void markInCombat(Player... players) {
        for (Player p : players) {
            if (p != null) lastCombatTime.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void addDamageRecord(Player victim, String attackerName, String abilityName, double damage) {
        recentDamage.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>())
                .add(new DamageRecord(attackerName, abilityName, damage, System.currentTimeMillis()));
        // Clean up old records (> 30s)
        cleanOldRecords(victim.getUniqueId());
    }

    public List<DamageRecord> getRecentDamage(Player victim) {
        cleanOldRecords(victim.getUniqueId());
        return recentDamage.getOrDefault(victim.getUniqueId(), new ArrayList<>());
    }

    private void cleanOldRecords(UUID victimId) {
        List<DamageRecord> records = recentDamage.get(victimId);
        if (records != null) {
            long now = System.currentTimeMillis();
            records.removeIf(record -> now - record.timestamp > COMBAT_DURATION_MILLIS);
        }
    }

    public boolean isInCombat(Player player) {
        Long lastCombat = lastCombatTime.get(player.getUniqueId());
        if (lastCombat == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastCombat) <= COMBAT_DURATION_MILLIS;
    }

    public void handleKill(Player killer, Player victim) {
        // Clear combat/damage status for victim on death
        lastCombatTime.remove(victim.getUniqueId());
        recentDamage.remove(victim.getUniqueId());

        // Process Victim's streak/bounty
        int victimStreak = killStreaks.getOrDefault(victim.getUniqueId(), 0);
        killStreaks.put(victim.getUniqueId(), 0);

        if (victimStreak >= 3) {
            // Bounty logic - Killer ended a high streak
            // XP rewards disabled for now, handled via commands
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Spellbreak] " + ChatColor.RED + killer.getName() + " ended " + victim.getName() + "'s " + victimStreak + " kill streak!");
        }

        // Process Killer's streak
        int killerStreak = killStreaks.getOrDefault(killer.getUniqueId(), 0) + 1;
        killStreaks.put(killer.getUniqueId(), killerStreak);

        if (killerStreak == 3 || killerStreak == 5 || killerStreak == 10 || killerStreak > 10 && killerStreak % 5 == 0) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Spellbreak] " + ChatColor.GREEN + killer.getName() + " is on a " + killerStreak + " kill streak!");
        }
    }

    public void resetStreak(Player player) {
        killStreaks.remove(player.getUniqueId());
        lastCombatTime.remove(player.getUniqueId());
        recentDamage.remove(player.getUniqueId());
    }

    // --- TESTING METHODS ---
    public void setStreakForTesting(Player player, int streak) {
        killStreaks.put(player.getUniqueId(), streak);
    }

    public void simulateKillForTesting(Player killer, String victimName, int victimStreak) {
        if (victimStreak >= 3) {
            // XP rewards disabled for now, handled via commands
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Spellbreak] " + ChatColor.RED + killer.getName() + " ended " + victimName + "'s " + victimStreak + " kill streak!");
        }

        int killerStreak = killStreaks.getOrDefault(killer.getUniqueId(), 0) + 1;
        killStreaks.put(killer.getUniqueId(), killerStreak);

        if (killerStreak == 3 || killerStreak == 5 || killerStreak == 10 || (killerStreak > 10 && killerStreak % 5 == 0)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Spellbreak] " + ChatColor.GREEN + killer.getName() + " is on a " + killerStreak + " kill streak!");
        }
        
        killer.sendMessage(net.kyori.adventure.text.Component.text("You eliminated " + victimName + "!").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
        killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
}

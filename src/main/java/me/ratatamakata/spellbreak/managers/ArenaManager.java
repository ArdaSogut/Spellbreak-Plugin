package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ArenaManager {

    private final Spellbreak plugin;
    private Location arenaSpawn;

    public ArenaManager(Spellbreak plugin) {
        this.plugin = plugin;
        loadSpawnLocation();
    }

    private void loadSpawnLocation() {
        FileConfiguration config = plugin.getConfig();
        if (config.contains("arena.spawn")) {
            arenaSpawn = config.getLocation("arena.spawn");
        }
    }

    public void setSpawnLocation(Location location) {
        this.arenaSpawn = location;
        plugin.getConfig().set("arena.spawn", location);
        plugin.saveConfig();
    }

    public boolean hasSpawn() {
        return arenaSpawn != null;
    }

    public void startArenaMatch(Player triggerPlayer) {
        if (!hasSpawn()) {
            triggerPlayer.sendMessage(ChatColor.RED + "Arena spawn is not set. Use /pvp setspawn");
            return;
        }

        // Instead of hardcoding to all players, if there are teams, we might want to teleport teams.
        // For now, let's teleport all online players for a free-for-all match.
        for (Player p : Bukkit.getOnlinePlayers()) {
            resetPlayer(p);
            p.teleport(arenaSpawn);
            p.sendMessage(ChatColor.GOLD + "[Spellbreak] " + ChatColor.GREEN + "The Arena Match has started! Fight!");
        }
    }

    private void resetPlayer(Player player) {
        // Heal them fully according to their Bukkit attribute which is updated by PlayerDataManager
        org.bukkit.attribute.AttributeInstance healthAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            player.setHealth(healthAttr.getValue());
        } else {
            // Fallback if somehow attribute is null
            player.setHealth(20.0);
        }

        // Reset cooldowns
        plugin.getCooldownManager().clearAllCooldowns(player.getUniqueId());
        
        // Reset Mana
        int maxMana = plugin.getManaSystem().getMaxMana(player);
        plugin.getManaSystem().restoreMana(player, maxMana);

        // Reset streaks and combat
        plugin.getPvpManager().resetStreak(player);

        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }
}

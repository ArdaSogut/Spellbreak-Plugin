// src/main/java/me/ratatamakata/spellbreak/managers/ManaSystem.java
package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

// In ManaSystem.java
public class ManaSystem {
    private final Spellbreak plugin;
    private final Map<UUID, Integer> mana = new HashMap<>();
    private final Map<UUID, Integer> maxMana = new HashMap<>(); // Changed to per-player
    private final int baseRegen;

    public ManaSystem(Spellbreak plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        baseRegen = cfg.getInt("mana.regen-amount", 500);
    }

    public void startRegenerationTask() {
        Bukkit.getScheduler().runTaskTimer(Spellbreak.getInstance(),
                () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        UUID u = p.getUniqueId();
                        int cur = mana.getOrDefault(u, getMaxMana(p));
                        int regen = (int) (baseRegen * plugin.getLevelManager()
                                .getPlayerLevel(u, Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId())).getManaRegenRate());
                        mana.put(u, Math.min(cur + regen, getMaxMana(p)));
                    }
                }, 0L, 20L * Spellbreak.getInstance().getConfig()
                        .getInt("mana.regen-interval", 1));
    }
    public void restoreMana(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = mana.getOrDefault(uuid, getMaxMana(player));
        mana.put(uuid, Math.min(current + amount, getMaxMana(player)));
    }
    public int getMana(Player p) { return mana.getOrDefault(p.getUniqueId(), getMaxMana(p.getPlayer())); }

    public void setMaxMana(Player player, int max) {
        maxMana.put(player.getUniqueId(), max);
        mana.putIfAbsent(player.getUniqueId(), max);
    }

    public int getMaxMana(Player player) {
        return maxMana.getOrDefault(player.getUniqueId(),
                plugin.getLevelManager().getPlayerLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId())).getMaxMana());
    }

    // Update other methods to use getMaxMana(player)
    public boolean consumeMana(Player p, int amt) {
        UUID u = p.getUniqueId();
        int cur = mana.getOrDefault(u, getMaxMana(p));
        if (cur < amt) return false;
        mana.put(u, cur - amt);
        return true;
    }
}
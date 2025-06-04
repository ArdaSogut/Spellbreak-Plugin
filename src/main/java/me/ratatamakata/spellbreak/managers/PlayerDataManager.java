// Updated PlayerDataManager.java with level system integration
package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.SpellSchool;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerDataManager implements Listener {
    private final Map<UUID, String[]> binds = new HashMap<>();
    private final Map<UUID, String> classes = new HashMap<>();
    private final Map<UUID, Map<SpellSchool, Set<String>>> knownAbilitiesBySchool = new HashMap<>();
    private final File folder;
    private final Spellbreak plugin;

    public PlayerDataManager(Spellbreak plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadData(player.getUniqueId());

        // Load level data when player joins
        plugin.getLevelManager().loadPlayerLevels(player.getUniqueId());
        plugin.getLevelManager().loadSpellLevels(player.getUniqueId());

        // Apply level-based health and mana
        applyLevelStats(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        saveData(player.getUniqueId());

        // Save level data when player leaves
        plugin.getLevelManager().savePlayerLevels(player.getUniqueId());
        plugin.getLevelManager().saveSpellLevels(player.getUniqueId());
    }

    void applyLevelStats(Player player) {
        String className = getPlayerClass(player.getUniqueId());
        if (className.equals("None")) return;

        PlayerLevel playerLevel = plugin.getLevelManager().getPlayerLevel(player.getUniqueId(), className);

        // Health
        player.setMaxHealth(playerLevel.getMaxHealth());
        player.setHealth(Math.min(player.getHealth(), playerLevel.getMaxHealth()));

        // Mana
        if (plugin.getManaSystem() != null) {
            plugin.getManaSystem().setMaxMana(player, playerLevel.getMaxMana());
        }
    }


    public void bindAbility(UUID u, int slot, String ability) {
        binds.computeIfAbsent(u, id -> new String[9])[slot] = ability;
        saveData(u);
    }

    public String getAbilityAtSlot(UUID u, int slot) {
        String[] arr = binds.get(u);
        return (arr != null ? arr[slot] : null);
    }

    public String[] getBindings(UUID u) {
        return binds.getOrDefault(u, new String[9]);
    }

    public void setClass(UUID u, String cls) {
        classes.put(u, cls);
        saveData(u);

        Player player = Bukkit.getPlayer(u);
        if (player != null) {
            applyLevelStats(player);
        }
    }

    public String getPlayerClass(UUID u) {
        return classes.getOrDefault(u, "None");
    }

    public void clearBindings(UUID playerId) {
        binds.put(playerId, new String[9]);
        saveData(playerId);
    }

    public void learnAbility(UUID playerId, SpellSchool school, String abilityName) {
        knownAbilitiesBySchool
                .computeIfAbsent(playerId, k -> new EnumMap<>(SpellSchool.class))
                .computeIfAbsent(school, k -> new HashSet<>())
                .add(abilityName);
        saveData(playerId);

        // Give XP for learning new ability
        //Player player = plugin.getServer().getPlayer(playerId);
        //if (player != null) {
          //  plugin.getLevelManager().givePlayerExperience(player, 25, "Learned " + abilityName);
        //}
    }

    public Set<String> getKnownAbilities(UUID playerId, SpellSchool school) {
        return knownAbilitiesBySchool
                .getOrDefault(playerId, Collections.emptyMap())
                .getOrDefault(school, Collections.emptySet());
    }

    public int countKnownAbilities(UUID playerId, SpellSchool school) {
        return getKnownAbilities(playerId, school).size();
    }

    public boolean knowsAbility(UUID playerId, SpellSchool school, String abilityName) {
        return getKnownAbilities(playerId, school).contains(abilityName);
    }

    public void saveData(UUID u) {
        File f = new File(folder, u + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.set("class", getPlayerClass(u));
            cfg.set("abilities", Arrays.asList(getBindings(u)));

            Map<SpellSchool, Set<String>> playerKnownAbilities = knownAbilitiesBySchool.get(u);
            if (playerKnownAbilities != null && !playerKnownAbilities.isEmpty()) {
                List<String> knownAbilitiesSerialized = new ArrayList<>();
                for (Map.Entry<SpellSchool, Set<String>> entry : playerKnownAbilities.entrySet()) {
                    SpellSchool school = entry.getKey();
                    for (String abilityName : entry.getValue()) {
                        knownAbilitiesSerialized.add(school.name() + ":" + abilityName);
                    }
                }
                cfg.set("known-abilities", knownAbilitiesSerialized);
            } else {
                cfg.set("known-abilities", Collections.emptyList());
            }

            cfg.save(f);
        } catch (IOException ex) {
            Spellbreak.getInstance().getLogger().warning("Could not save data for " + u);
        }
    }

    public void loadData(UUID u) {
        File f = new File(folder, u + ".yml");
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        classes.put(u, cfg.getString("class", "None"));
        List<String> list = cfg.getStringList("abilities");
        String[] arr = new String[9];
        for (int i = 0; i < Math.min(9, list.size()); i++) {
            arr[i] = "null".equalsIgnoreCase(list.get(i)) ? null : list.get(i);
        }
        binds.put(u, arr);

        List<String> knownAbilitiesSerialized = cfg.getStringList("known-abilities");
        if (!knownAbilitiesSerialized.isEmpty()) {
            Map<SpellSchool, Set<String>> playerKnownAbilities = new EnumMap<>(SpellSchool.class);
            for (String serialized : knownAbilitiesSerialized) {
                String[] parts = serialized.split(":", 2);
                if (parts.length == 2) {
                    try {
                        SpellSchool school = SpellSchool.valueOf(parts[0]);
                        String abilityName = parts[1];
                        playerKnownAbilities.computeIfAbsent(school, k -> new HashSet<>()).add(abilityName);
                    } catch (IllegalArgumentException e) {
                        Spellbreak.getInstance().getLogger().warning("Could not parse known ability: " + serialized + " for player " + u);
                    }
                }
            }
            knownAbilitiesBySchool.put(u, playerKnownAbilities);
        } else {
            knownAbilitiesBySchool.remove(u);
        }
    }

    // Helper method to refresh player stats (call when level changes)
    public void refreshPlayerStats(Player player) {
        applyLevelStats(player);
    }
}
package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LevelManager {
    private final Spellbreak plugin;
    private final Map<UUID, Map<String, PlayerLevel>> playerLevels = new HashMap<>();
    public final Map<UUID, Map<String, Map<String, SpellLevel>>> spellLevels = new HashMap<>();
    private final File levelFolder;

    public LevelManager(Spellbreak plugin) {
        this.plugin = plugin;
        this.levelFolder = new File(plugin.getDataFolder(), "levels");
        if (!levelFolder.exists()) levelFolder.mkdirs();
    }

    // Player Level Methods
    public PlayerLevel getPlayerLevel(UUID playerId, String className) {
        return playerLevels.computeIfAbsent(playerId, k -> new HashMap<>())
                .computeIfAbsent(className, k -> new PlayerLevel());
    }
    public Set<UUID> getAllPlayerLevelIds() {
        return Collections.unmodifiableSet(playerLevels.keySet());
    }

    public void givePlayerExperience(Player player, int experience, String reason) {
        String className = plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId());
        if (className.equals("None")) {
            player.sendMessage(ChatColor.RED + "Select a class to gain XP!");
            return;
        }

        PlayerLevel playerLevel = getPlayerLevel(player.getUniqueId(), className);
        int oldLevel = playerLevel.getLevel();

        boolean leveledUp = playerLevel.addExperience(experience);

        player.sendMessage(ChatColor.GREEN + "+" + experience + " XP (" + className + ")" +
                (reason != null ? " - " + reason : ""));

        if (leveledUp) {
            int newLevel = playerLevel.getLevel();
            player.sendMessage(ChatColor.GOLD + "LEVEL UP! " + ChatColor.YELLOW +
                    "Level " + oldLevel + " → " + newLevel);
            player.sendMessage(ChatColor.GRAY + "Health: " + playerLevel.getMaxHealth() +
                    " | Mana: " + playerLevel.getMaxMana());

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() +
                    ChatColor.YELLOW + " reached level " + newLevel + " in " + className + "!");
        }

        savePlayerLevels(player.getUniqueId());
    }

    // Spell Level Methods
    public SpellLevel getSpellLevel(UUID playerId, String className, String spellName) {
        return spellLevels.computeIfAbsent(playerId, k -> new HashMap<>())
                .computeIfAbsent(className, k -> new HashMap<>())
                .computeIfAbsent(spellName, SpellLevel::new);
    }

    public void giveSpellExperience(Player player, String spellName, int experience) {
        String className = plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId());
        if (className.equals("None")) return;

        SpellLevel spellLevel = getSpellLevel(player.getUniqueId(), className, spellName);
        int oldLevel = spellLevel.getLevel();

        boolean leveledUp = spellLevel.addExperience(experience);

        if (leveledUp) {
            int newLevel = spellLevel.getLevel();
            player.sendMessage(ChatColor.LIGHT_PURPLE + spellName + " (" + className + ") leveled up! " +
                    ChatColor.WHITE + "Level " + oldLevel + " → " + newLevel);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        }

        saveSpellLevels(player.getUniqueId());
    }

    // Save/Load Methods
    public void savePlayerLevels(UUID playerId) {
        Map<String, PlayerLevel> classLevels = playerLevels.get(playerId);
        if (classLevels == null) return;

        File file = new File(levelFolder, playerId + "_player.yml");
        YamlConfiguration config = new YamlConfiguration();

        ConfigurationSection classesSection = config.createSection("classes");
        for (Map.Entry<String, PlayerLevel> entry : classLevels.entrySet()) {
            ConfigurationSection classSection = classesSection.createSection(entry.getKey());
            classSection.set("level", entry.getValue().getLevel());
            classSection.set("experience", entry.getValue().getExperience());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player levels for " + playerId);
        }
    }

    public void saveSpellLevels(UUID playerId) {
        Map<String, Map<String, SpellLevel>> classSpells = spellLevels.get(playerId);
        if (classSpells == null) return;

        File file = new File(levelFolder, playerId + "_spells.yml");
        YamlConfiguration config = new YamlConfiguration();

        ConfigurationSection classesSection = config.createSection("classes");
        for (Map.Entry<String, Map<String, SpellLevel>> classEntry : classSpells.entrySet()) {
            ConfigurationSection classSection = classesSection.createSection(classEntry.getKey());
            ConfigurationSection spellsSection = classSection.createSection("spells");

            for (Map.Entry<String, SpellLevel> spellEntry : classEntry.getValue().entrySet()) {
                ConfigurationSection spellSection = spellsSection.createSection(spellEntry.getKey());
                spellSection.set("level", spellEntry.getValue().getLevel());
                spellSection.set("experience", spellEntry.getValue().getExperience());
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save spell levels for " + playerId);
        }
    }

    public void loadPlayerLevels(UUID playerId) {
        File file = new File(levelFolder, playerId + "_player.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, PlayerLevel> classLevels = new HashMap<>();

        ConfigurationSection classesSection = config.getConfigurationSection("classes");
        if (classesSection != null) {
            for (String className : classesSection.getKeys(false)) {
                ConfigurationSection classSection = classesSection.getConfigurationSection(className);
                int level = classSection.getInt("level", 1);
                int experience = classSection.getInt("experience", 0);
                classLevels.put(className, new PlayerLevel(level, experience));
            }
        }

        playerLevels.put(playerId, classLevels);
    }

    public void loadSpellLevels(UUID playerId) {
        File file = new File(levelFolder, playerId + "_spells.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, Map<String, SpellLevel>> classSpells = new HashMap<>();

        ConfigurationSection classesSection = config.getConfigurationSection("classes");
        if (classesSection != null) {
            for (String className : classesSection.getKeys(false)) {
                ConfigurationSection classSection = classesSection.getConfigurationSection(className);
                ConfigurationSection spellsSection = classSection.getConfigurationSection("spells");
                Map<String, SpellLevel> spells = new HashMap<>();

                if (spellsSection != null) {
                    for (String spellName : spellsSection.getKeys(false)) {
                        ConfigurationSection spellSection = spellsSection.getConfigurationSection(spellName);
                        int level = spellSection.getInt("level", 1);
                        int experience = spellSection.getInt("experience", 0);
                        spells.put(spellName, new SpellLevel(spellName, level, experience));
                    }
                }

                classSpells.put(className, spells);
            }
        }

        spellLevels.put(playerId, classSpells);
    }

    // Admin methods
    public void setPlayerLevel(UUID playerId, String className, int level) {
        PlayerLevel playerLevel = getPlayerLevel(playerId, className);
        playerLevel.setLevel(level);
        playerLevel.setExperience(0);
        savePlayerLevels(playerId);
    }

    public void setSpellLevel(UUID playerId, String className, String spellName, int level) {
        SpellLevel spellLevel = getSpellLevel(playerId, className, spellName);
        spellLevel.setLevel(level);
        spellLevel.setExperience(0);
        saveSpellLevels(playerId);
    }

    public void refreshPlayerStats(Player player) {
        plugin.getPlayerDataManager().applyLevelStats(player);
    }

    public void loadAllPlayerLevels() {
        playerLevels.clear();
        File[] files = levelFolder.listFiles((dir, name) -> name.endsWith("_player.yml"));
        if (files != null) {
            for (File file : files) {
                UUID playerId = UUID.fromString(file.getName().replace("_player.yml", ""));
                loadPlayerLevels(playerId);
            }
        }
    }

    public void loadAllSpellLevels() {
        spellLevels.clear();
        File[] files = levelFolder.listFiles((dir, name) -> name.endsWith("_spells.yml"));
        if (files != null) {
            for (File file : files) {
                UUID playerId = UUID.fromString(file.getName().replace("_spells.yml", ""));
                loadSpellLevels(playerId);
            }
        }
    }
}
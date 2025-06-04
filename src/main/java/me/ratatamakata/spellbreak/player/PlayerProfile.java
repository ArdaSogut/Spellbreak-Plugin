package me.ratatamakata.spellbreak.player;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PlayerProfile {
    private String[] bindings;
    private String playerClass;
    private final Map<String, ConfigurationSection> addonData; // Key: AddonName

    public PlayerProfile() {
        this.bindings = new String[9];
        this.playerClass = "None";
        this.addonData = new HashMap<>();
    }

    public String[] getBindings() {
        return bindings;
    }

    public void setBindings(String[] bindings) {
        this.bindings = bindings;
    }

    public String getPlayerClass() {
        return playerClass;
    }

    public void setPlayerClass(String playerClass) {
        this.playerClass = playerClass;
    }

    public Map<String, ConfigurationSection> getAddonData() {
        return addonData;
    }

    /**
     * Gets the ConfigurationSection for a specific addon.
     * If it doesn't exist, it will be created.
     * @param addonKey A unique key for the addon (e.g., plugin name).
     * @return The ConfigurationSection for the addon.
     */
    public ConfigurationSection getOrCreateAddonDataSection(String addonKey) {
        return addonData.computeIfAbsent(addonKey, k -> new YamlConfiguration());
    }

    public void removeAddonDataSection(String addonKey) {
        addonData.remove(addonKey);
    }
} 
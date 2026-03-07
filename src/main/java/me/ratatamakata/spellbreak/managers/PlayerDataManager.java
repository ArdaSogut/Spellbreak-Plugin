package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.SpellSchool;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import me.ratatamakata.spellbreak.player.CharacterSlot;
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

/**
 * Manages persistent player data including multi-slot character support.
 *
 * Each player has up to 14 character slots (2 per class × 7 classes).
 * The "active slot" determines which class / bindings are currently in use.
 *
 * Backward-compatible shim methods (getPlayerClass, getBindings, setClass,
 * bindAbility, clearBindings) delegate to the active slot so no existing
 * ability code needs to change.
 */
public class PlayerDataManager implements Listener {

    /** Total character slots available per player. */
    public static final int TOTAL_SLOTS = 14;
    /** Sentinel value when no slot is active. */
    private static final int NO_ACTIVE_SLOT = -1;

    // -------------------------------------------------------------------------
    // In-memory storage
    // -------------------------------------------------------------------------
    private final Map<UUID, CharacterSlot[]>              slots              = new HashMap<>();
    private final Map<UUID, Integer>                      activeSlot         = new HashMap<>();
    private final Map<UUID, Map<SpellSchool, Set<String>>> knownAbilitiesBySchool = new HashMap<>();

    private final File folder;
    private final Spellbreak plugin;

    public PlayerDataManager(Spellbreak plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
    }

    // -------------------------------------------------------------------------
    // Bukkit event hooks
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadData(player.getUniqueId());
        plugin.getLevelManager().loadPlayerLevels(player.getUniqueId());
        plugin.getLevelManager().loadSpellLevels(player.getUniqueId());
        applyLevelStats(player);

        // Open character select GUI on join (configurable)
        if (plugin.getConfig().getBoolean("open-gui-on-join", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    me.ratatamakata.spellbreak.gui.CharacterSelectGUI.open(player);
                }
            }, 10L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        saveData(player.getUniqueId());
        plugin.getLevelManager().savePlayerLevels(player.getUniqueId());
        plugin.getLevelManager().saveSpellLevels(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Character slot API
    // -------------------------------------------------------------------------

    /**
     * Returns the CharacterSlot array for a player (lazily initialised).
     */
    private CharacterSlot[] getSlots(UUID uuid) {
        return slots.computeIfAbsent(uuid, id -> {
            CharacterSlot[] arr = new CharacterSlot[TOTAL_SLOTS];
            for (int i = 0; i < TOTAL_SLOTS; i++) arr[i] = new CharacterSlot(i);
            return arr;
        });
    }

    public CharacterSlot getCharacterSlot(UUID uuid, int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return null;
        return getSlots(uuid)[index];
    }

    public void setCharacterSlot(UUID uuid, int index, CharacterSlot slot) {
        if (index < 0 || index >= TOTAL_SLOTS) return;
        getSlots(uuid)[index] = slot;
    }

    public int getActiveSlotIndex(UUID uuid) {
        return activeSlot.getOrDefault(uuid, NO_ACTIVE_SLOT);
    }

    public void setActiveSlotIndex(UUID uuid, int index) {
        activeSlot.put(uuid, index);
        saveData(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) applyLevelStats(player);
    }

    /** Returns the currently active CharacterSlot, or null if none. */
    public CharacterSlot getActiveSlot(UUID uuid) {
        int idx = getActiveSlotIndex(uuid);
        if (idx == NO_ACTIVE_SLOT) return null;
        return getCharacterSlot(uuid, idx);
    }

    // -------------------------------------------------------------------------
    // Backward-compatible shim methods (used by ability/listener code)
    // -------------------------------------------------------------------------

    public String getPlayerClass(UUID uuid) {
        CharacterSlot active = getActiveSlot(uuid);
        return (active == null || active.isEmpty()) ? "None" : active.getClassName();
    }

    public String[] getBindings(UUID uuid) {
        CharacterSlot active = getActiveSlot(uuid);
        return (active == null) ? new String[9] : active.getBindings();
    }

    public String getAbilityAtSlot(UUID uuid, int slot) {
        String[] bindings = getBindings(uuid);
        return (slot >= 0 && slot < 9) ? bindings[slot] : null;
    }

    public void bindAbility(UUID uuid, int slot, String ability) {
        CharacterSlot active = getActiveSlot(uuid);
        if (active == null) return;
        String[] bindings = active.getBindings();
        if (slot >= 0 && slot < 9) bindings[slot] = ability;
        saveData(uuid);
    }

    public void setClass(UUID uuid, String cls) {
        // When called directly (e.g., from old admin code), update the active slot's class
        CharacterSlot active = getActiveSlot(uuid);
        if (active != null) {
            active.setClassName(cls);
            saveData(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) applyLevelStats(player);
        }
    }

    public void clearBindings(UUID uuid) {
        CharacterSlot active = getActiveSlot(uuid);
        if (active != null) {
            active.clearBindings();
            saveData(uuid);
        }
    }

    // -------------------------------------------------------------------------
    // Known-abilities API (unchanged)
    // -------------------------------------------------------------------------

    public void learnAbility(UUID playerId, SpellSchool school, String abilityName) {
        knownAbilitiesBySchool
                .computeIfAbsent(playerId, k -> new EnumMap<>(SpellSchool.class))
                .computeIfAbsent(school, k -> new HashSet<>())
                .add(abilityName);
        saveData(playerId);
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

    // -------------------------------------------------------------------------
    // Stats application
    // -------------------------------------------------------------------------

    public void applyLevelStats(Player player) {
        String className = getPlayerClass(player.getUniqueId());
        if (className.equals("None")) return;

        PlayerLevel playerLevel = plugin.getLevelManager().getPlayerLevel(player.getUniqueId(), className);
        player.setMaxHealth(playerLevel.getMaxHealth());
        player.setHealth(Math.min(player.getHealth(), playerLevel.getMaxHealth()));

        if (plugin.getManaSystem() != null) {
            plugin.getManaSystem().setMaxMana(player, playerLevel.getMaxMana());
        }
    }

    public void refreshPlayerStats(Player player) {
        applyLevelStats(player);
    }

    // -------------------------------------------------------------------------
    // Save / Load
    // -------------------------------------------------------------------------

    public void saveData(UUID uuid) {
        File f = new File(folder, uuid + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("active-slot", getActiveSlotIndex(uuid));

        CharacterSlot[] arr = getSlots(uuid);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            CharacterSlot slot = arr[i];
            String path = "slots.slot-" + i;
            cfg.set(path + ".class", slot.isEmpty() ? "None" : slot.getClassName());
            cfg.set(path + ".bindings", Arrays.asList(slot.getBindings()));
        }

        // Known abilities
        Map<SpellSchool, Set<String>> playerKnownAbilities = knownAbilitiesBySchool.get(uuid);
        if (playerKnownAbilities != null && !playerKnownAbilities.isEmpty()) {
            List<String> serialized = new ArrayList<>();
            for (Map.Entry<SpellSchool, Set<String>> entry : playerKnownAbilities.entrySet()) {
                for (String name : entry.getValue()) {
                    serialized.add(entry.getKey().name() + ":" + name);
                }
            }
            cfg.set("known-abilities", serialized);
        } else {
            cfg.set("known-abilities", Collections.emptyList());
        }

        try {
            cfg.save(f);
        } catch (IOException ex) {
            Spellbreak.getInstance().getLogger().warning("Could not save data for " + uuid);
        }
    }

    public void loadData(UUID uuid) {
        File f = new File(folder, uuid + ".yml");
        CharacterSlot[] arr = new CharacterSlot[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) arr[i] = new CharacterSlot(i);

        if (f.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

            // Try new multi-slot format
            if (cfg.contains("slots")) {
                for (int i = 0; i < TOTAL_SLOTS; i++) {
                    String path = "slots.slot-" + i;
                    String cls = cfg.getString(path + ".class", "None");
                    List<String> bindList = cfg.getStringList(path + ".bindings");
                    String[] bindings = new String[9];
                    for (int j = 0; j < Math.min(9, bindList.size()); j++) {
                        bindings[j] = "null".equalsIgnoreCase(bindList.get(j)) ? null : bindList.get(j);
                    }
                    arr[i] = new CharacterSlot(i, "None".equalsIgnoreCase(cls) ? null : cls, bindings);
                }
                activeSlot.put(uuid, cfg.getInt("active-slot", NO_ACTIVE_SLOT));
            } else {
                // Migrate old single-slot format
                String oldClass = cfg.getString("class", "None");
                List<String> oldBindList = cfg.getStringList("abilities");
                String[] oldBindings = new String[9];
                for (int j = 0; j < Math.min(9, oldBindList.size()); j++) {
                    oldBindings[j] = "null".equalsIgnoreCase(oldBindList.get(j)) ? null : oldBindList.get(j);
                }
                if (!oldClass.equalsIgnoreCase("None")) {
                    arr[0] = new CharacterSlot(0, oldClass, oldBindings);
                    activeSlot.put(uuid, 0);
                } else {
                    activeSlot.put(uuid, NO_ACTIVE_SLOT);
                }
            }

            // Known abilities
            List<String> knownSerialized = cfg.getStringList("known-abilities");
            if (!knownSerialized.isEmpty()) {
                Map<SpellSchool, Set<String>> playerKnown = new EnumMap<>(SpellSchool.class);
                for (String s : knownSerialized) {
                    String[] parts = s.split(":", 2);
                    if (parts.length == 2) {
                        try {
                            SpellSchool school = SpellSchool.valueOf(parts[0]);
                            playerKnown.computeIfAbsent(school, k -> new HashSet<>()).add(parts[1]);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Could not parse known ability: " + s + " for " + uuid);
                        }
                    }
                }
                knownAbilitiesBySchool.put(uuid, playerKnown);
            }
        } else {
            activeSlot.put(uuid, NO_ACTIVE_SLOT);
        }

        slots.put(uuid, arr);
    }
}
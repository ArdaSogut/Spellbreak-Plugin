package me.ratatamakata.spellbreak.dailies;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DailyMissionManager {
    private final Spellbreak plugin;
    private final File missionsFile;
    private final Map<String, DailyMission> allMissions = new LinkedHashMap<>();
    private final Map<UUID, List<DailyMission>> playerMissions = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> progress = new HashMap<>();
    private final Map<UUID, Set<String>> rerolledMissions = new HashMap<>();

    public DailyMissionManager(Spellbreak plugin) {
        this.plugin = plugin;
        this.missionsFile = new File(plugin.getDataFolder(), "missions.yml");
        loadMissions();
        scheduleDailyReset();
    }

    private void loadMissions() {
        allMissions.clear();
        if (!missionsFile.exists()) plugin.saveResource("missions.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(missionsFile);
        ConfigurationSection sec = cfg.getConfigurationSection("missions");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                allMissions.put(key, DailyMission.fromConfig(key, sec.getConfigurationSection(key)));
            }
        }
        plugin.getLogger().info("Loaded " + allMissions.size() + " missions.");
    }

    public List<DailyMission> getMissions(UUID playerId) {
        return playerMissions.computeIfAbsent(playerId, id -> assignRandomMissions(3));
    }

    private List<DailyMission> assignRandomMissions(int count) {
        List<DailyMission> pool = new ArrayList<>(allMissions.values());
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    public boolean canReroll(UUID playerId, String missionKey) {
        return !rerolledMissions.computeIfAbsent(playerId, k -> new HashSet<>()).contains(missionKey);
    }

    public DailyMission rerollSingle(UUID playerId, String missionKey) {
        if (!canReroll(playerId, missionKey)) return null;
        List<DailyMission> list = getMissions(playerId);
        Set<String> existing = Set.copyOf(list.stream().map(DailyMission::getKey).toList());
        List<DailyMission> pool = new ArrayList<>(allMissions.values());
        pool.removeIf(m -> existing.contains(m.getKey()));
        if (pool.isEmpty()) return null;
        DailyMission newM = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equals(missionKey)) {
                list.set(i, newM);
                break;
            }
        }
        progress.computeIfAbsent(playerId, k -> new HashMap<>()).remove(missionKey);
        rerolledMissions.get(playerId).add(missionKey);
        return newM;
    }

    public void incrementProgress(UUID playerId, String missionKey) {
        Map<String, Integer> pmap = progress.computeIfAbsent(playerId, k -> new HashMap<>());
        int currentProgress = pmap.getOrDefault(missionKey, 0);
        DailyMission mission = allMissions.get(missionKey);

        if (mission == null) return; // mission doesn't exist, bail out

        int amount = mission.getAmount();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return; // player not online, bail out

        // If already completed, no need to increment further
        if (currentProgress >= amount) return;

        int newVal = currentProgress + 1;
        if (newVal > amount) newVal = amount; // cap progress

        pmap.put(missionKey, newVal);

        // Send progress update in chat
        player.sendMessage(ChatColor.AQUA + "[Daily Quest] " + mission.getDescription() + ": " + newVal + "/" + amount);

        // Check completion exactly on final increment
        if (newVal == amount) {
            // Notify player mission is complete
            player.sendMessage(ChatColor.GOLD + "Mission complete! You can now claim your reward.");

            // Play level-up sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Spawn particle effect at player's location (adjust as needed)
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        }
    }



    public int getProgress(UUID playerId, String missionKey) {
        return progress.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(missionKey, 0);
    }

    public boolean isComplete(UUID playerId, DailyMission m) {
        return getProgress(playerId, m.getKey()) >= m.getAmount();
    }

    public boolean claimSingle(UUID playerId, DailyMission m) {
        if (!isComplete(playerId, m)) {
            return false;
        }
        // Get the player object
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        // Award experience via LevelManager (RPG system)
        plugin.getLevelManager().givePlayerExperience(player, m.getXpReward(), "Daily Mission");
        // Remove this mission from the player's list so it can't be claimed again
        List<DailyMission> list = getMissions(playerId);
        list.removeIf(x -> x.getKey().equals(m.getKey()));
        return true;
    }

    private void scheduleDailyReset() {
        new BukkitRunnable() {
            long ticks = 0;
            @Override
            public void run() {
                ticks += 600;
                if (ticks >= 24000) {
                    playerMissions.clear();
                    progress.clear();
                    rerolledMissions.clear();
                    Bukkit.broadcastMessage(ChatColor.AQUA + "Daily missions refreshed!");
                    ticks = 0;
                }
            }
        }.runTaskTimer(plugin, 0, 600);
    }
}
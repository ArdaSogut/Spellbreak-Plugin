// src/main/java/me/ratatamakata/spellbreak/Spellbreak.java
package me.ratatamakata.spellbreak;

import me.ratatamakata.spellbreak.abilities.impl.BoneChoirAbility;
import me.ratatamakata.spellbreak.abilities.impl.EmberstormAbility;
import me.ratatamakata.spellbreak.abilities.impl.IronwoodShellAbility;
import me.ratatamakata.spellbreak.abilities.impl.ThunderSlamAbility;
import me.ratatamakata.spellbreak.commands.*;
import me.ratatamakata.spellbreak.dailies.DailiesListener;
import me.ratatamakata.spellbreak.dailies.DailyMissionManager;
import me.ratatamakata.spellbreak.dailies.DailyProgressListener;
import me.ratatamakata.spellbreak.listeners.*;
import me.ratatamakata.spellbreak.listeners.TidepoolListener;
import me.ratatamakata.spellbreak.listeners.EmberstormListener;
import me.ratatamakata.spellbreak.managers.*;
import me.ratatamakata.spellbreak.util.AbilityDamage;
import me.ratatamakata.spellbreak.util.AbilityDamageTracker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class Spellbreak extends JavaPlugin implements Listener {
    private static Spellbreak instance;
    private AbilityManager abilityManager;
    private PlayerDataManager playerDataManager;
    private SpellClassManager spellClassManager;
    private CooldownManager cooldownManager;
    private ManaSystem manaSystem;
    private StunHandler stunHandler;
    private BoneChoirAbility boneChoirAbility;
    private HUDManager hudManager;
    private IronwoodShellAbility ironwoodShellAbilityInstance;
    private TeamManager teamManager;
    private LevelManager levelManager;

    // Cooldown Bypass Feature
    public static Set<UUID> playersWithCooldownBypass = new HashSet<>();
    private AbilityDamageTracker damageTracker;
    private AbilityDamageListener damageListener;
    private AbilityDamage abilityDamage;
    private DailyMissionManager dailyMissionManager;



    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupDamageTracking();
        // Ensure config is saved before abilities load it
        getConfig().options().copyDefaults(true);
        saveConfig();

        abilityManager    = new AbilityManager(this);
        playerDataManager = new PlayerDataManager(this);
        spellClassManager = new SpellClassManager();
        cooldownManager   = new CooldownManager();
        manaSystem        = new ManaSystem(this);
        hudManager        = new HUDManager();
        this.stunHandler = new StunHandler(this);
        boneChoirAbility = new BoneChoirAbility();
        ironwoodShellAbilityInstance = new IronwoodShellAbility();
        teamManager = new TeamManager(this);
        levelManager = new LevelManager(this);
        abilityManager.loadAbilities();
        spellClassManager.loadClasses();
        getLevelManager().loadAllPlayerLevels();
        getLevelManager().loadAllSpellLevels();
        dailyMissionManager = new DailyMissionManager(this);


        // Load data for any players already online (e.g., after a /reload)
        for (Player player : getServer().getOnlinePlayers()) {

            if (playerDataManager != null) { // Ensure playerDataManager is initialized
                playerDataManager.loadData(player.getUniqueId());
            }
        }

        getServer().getPluginManager().registerEvents(new PlayerDataListener(), this);
        getServer().getPluginManager().registerEvents(new ChatClassTagListener(), this);
        getServer().getPluginManager().registerEvents(new MistDashListener(), this);
        getServer().getPluginManager().registerEvents(new LifeDrainListener(), this);
        getServer().getPluginManager().registerEvents(new PlagueCloudListener(), this);
        getServer().getPluginManager().registerEvents(new TentaclesListener(), this);
        getServer().getPluginManager().registerEvents(new HealingListener(), this);
        getServer().getPluginManager().registerEvents(stunHandler, this);
        getServer().getPluginManager().registerEvents(new BoneChoirListener(), this);
        getServer().getPluginManager().registerEvents(new CustomDeathMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new UndyingRageListener(this), this);
        getServer().getPluginManager().registerEvents(new NatureStepListener(), this);
        getServer().getPluginManager().registerEvents(new FallDamageListener(), this);
        getServer().getPluginManager().registerEvents(new AmbushSlashListener(this), this);
        getServer().getPluginManager().registerEvents(new SporeBlossomListener(),this);
        getServer().getPluginManager().registerEvents(new CanopyCrashListener(), this);
        getServer().getPluginManager().registerEvents(new QuillflareSurgeListener(this), this);
        getServer().getPluginManager().registerEvents(new IronwoodShellListener(ironwoodShellAbilityInstance), this);
        getServer().getPluginManager().registerEvents(new LightCageListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsecrationListener(this),this);
        getServer().getPluginManager().registerEvents(new BeaconOfClarityListener(), this);
        getServer().getPluginManager().registerEvents(new RadiantPhaseListener(), this);
        getServer().getPluginManager().registerEvents(new RadiantDashListener(), this);
        getServer().getPluginManager().registerEvents(new PurifyingPrismListener(this),this);
        getServer().getPluginManager().registerEvents(new EchoPulseListener(this),this);
        getServer().getPluginManager().registerEvents(new PhantomEchoListener(),this);
        getServer().getPluginManager().registerEvents(new NeuralTrapListener(), this);
        getServer().getPluginManager().registerEvents(new DreamwalkerListener(), this);
        getServer().getPluginManager().registerEvents(new ShadowCreaturesListener(), this);
        getServer().getPluginManager().registerEvents(new CloneSwarmListener(), this);
        getServer().getPluginManager().registerEvents(new TidepoolListener(this),this);
        getServer().getPluginManager().registerEvents(new EmberstormListener(), this);
        getServer().getPluginManager().registerEvents(new GaleVortexListener(), this);
        getServer().getPluginManager().registerEvents(new EarthShardsListener(this), this);
        getServer().getPluginManager().registerEvents(new AvalancheListener(), this);
        ThunderSlamAbility thunderSlam = new ThunderSlamAbility();
        getServer().getPluginManager().registerEvents(new ThunderSlamListener(this, thunderSlam), this);
        getServer().getPluginManager().registerEvents(new WardingSigilListener(), this);
        getServer().getPluginManager().registerEvents(new RunecarverListener(), this);
        getServer().getPluginManager().registerEvents(new RunicJumpPadListener(), this);
        getServer().getPluginManager().registerEvents(new RunicTurretListener(), this);
        getServer().getPluginManager().registerEvents(new BladeSpinListener(), this);
        getServer().getPluginManager().registerEvents(new SwarmSigilListener(), this);
        getServer().getPluginManager().registerEvents(getPlayerDataManager(), this);
        getServer().getPluginManager().registerEvents(new DailiesListener(this), this);
        getServer().getPluginManager().registerEvents(new DailyProgressListener(this), this);


        getCommand("bind").setExecutor(new BindCommand());
        getCommand("dailies").setExecutor(new DailiesCommand(this));
        getCommand("reload").setExecutor(new ReloadConfigCommand(this));
        getCommand("class").setExecutor(new ClassCommand());
        getCommand("bind").setTabCompleter(new TabComplete());
        getCommand("class").setTabCompleter(new TabComplete());
        getCommand("spellbreakcooldownreset").setExecutor(new CooldownResetCommand(this));
        getCommand("testdeath").setExecutor(new TestDeathMessageCommand());
        PresetCommand presetExecutor = new PresetCommand();
        getCommand("preset").setExecutor(presetExecutor);
        getCommand("preset").setTabCompleter(presetExecutor);
        this.getCommand("team").setExecutor(new TeamCommand(this));
        getCommand("level").setExecutor(new LevelCommand(this));

        // Scoreboard HUD Updater Task
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    hudManager.updateHUD(player);
                }
            }
        }.runTaskTimer(this, 0L, 2L); // Update scoreboard every 2 ticks for responsiveness

        // Commented out Action Bar HUD Updater
        // new AbilityHUDUpdater().runTaskTimer(this, 0L, 2L);
        manaSystem.startRegenerationTask();

        getLogger().info("Spellbreak v1.0 enabled!");
    }

    @Override
    public void onDisable() {
        // Clear bypass on disable just in case
        playersWithCooldownBypass.clear();
        for (Player p : getServer().getOnlinePlayers())
            playerDataManager.saveData(p.getUniqueId());
        for (UUID playerId : Spellbreak.getInstance().getLevelManager().getAllPlayerLevelIds()) {
            // (this ensures you only iterate over players who actually have a PlayerLevel map)
            Spellbreak.getInstance().getLevelManager().savePlayerLevels(playerId);
            Spellbreak.getInstance().getLevelManager().saveSpellLevels(playerId);
        }
        getLogger().info("Spellbreak v1.0 disabled!");

    }

    public static Spellbreak getInstance() { return instance; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public SpellClassManager getSpellClassManager() { return spellClassManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public ManaSystem getManaSystem() { return manaSystem; }
    public StunHandler getStunHandler() {return stunHandler;}
    public TeamManager getTeamManager() { return teamManager; }
    /**
     * Access the ability damage utility from abilities
     */
    public AbilityDamage getAbilityDamage() {
        return abilityDamage;
    }

    /**
     * Access damage tracker for more complex scenarios
     */
    public AbilityDamageTracker getDamageTracker() {
        return damageTracker;
    }


    private void setupDamageTracking() {
        // Create components in correct order
        CustomDeathMessageListener customListener = new CustomDeathMessageListener(this);
        damageTracker = new AbilityDamageTracker(this);

        damageListener = new AbilityDamageListener(this, damageTracker);
        abilityDamage = new AbilityDamage(this, damageTracker, damageListener);

        // Register death message listener (your existing one)
        getServer().getPluginManager().registerEvents(new CustomDeathMessageListener(this), this);

        // Register ability damage listener
        getServer().getPluginManager().registerEvents(damageListener, this);
    }

    // --- Cooldown Bypass Methods ---
    public static void addBypass(UUID playerId) {
        playersWithCooldownBypass.add(playerId);
        instance.getLogger().info("[Bypass] Added " + playerId + " to cooldown bypass set.");
    }

    public static void removeBypass(UUID playerId) {
        playersWithCooldownBypass.remove(playerId);
        instance.getLogger().info("[Bypass] Removed " + playerId + " from cooldown bypass set.");
    }

    public static boolean hasBypass(UUID playerId) {
        return playersWithCooldownBypass.contains(playerId);
    }
    // --- End Cooldown Bypass Methods ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (teamManager != null) {
            teamManager.handlePlayerQuit(event.getPlayer());
        }
    }
    public LevelManager getLevelManager() {
        return levelManager;
    }
    public DailyMissionManager getDailyMissionManager() { return dailyMissionManager; }
}

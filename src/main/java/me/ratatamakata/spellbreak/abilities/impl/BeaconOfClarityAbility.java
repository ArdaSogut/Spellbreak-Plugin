package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconOfClarityAbility implements Ability {
    private String name = "BeaconOfClarity";
    private String description = "Deploys a beacon that periodically heals nearby allies.";
    private int cooldown = 25;
    private int manaCost = 25;
    private String requiredClass = "lightbringer";

    // Configurable parameters
    private double beaconRadius = 5.0;
    private int durationTicks = 120;
    private double healAmount = 1.0;
    private double maxTotalHealPerAlly = 4.0;
    private int healIntervalTicks = 20;
    private double lightbringerHealMultiplier = 2;
    private double lightbringerMaxHealMultiplier = 2;
    private double beaconVisualCubeSize = 0.8;
    private int radiusVisualParticles = 30;
    private double radiusVisualYOffset = 0.75;

    private final Particle.DustOptions defaultBeaconParticles = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.0f);
    private final Particle.DustOptions lightbringerParticles   = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);

    // Per-player runtime state
    private final Map<UUID, Location> beaconLocations        = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> beaconEntityIds           = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> mainTasks           = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> visualTasks         = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> radiusVisualTasks   = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> healProgress = new ConcurrentHashMap<>();
    private final Map<UUID, String> casterClasses           = new ConcurrentHashMap<>();

    @Override public String getName()        { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown()       { return cooldown; }
    @Override public int getManaCost()       { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) {
        return false;
    }

    @Override
    public void activate(Player player) {
        // auto-fetch class
        PlayerDataManager pdm = Spellbreak.getInstance().getPlayerDataManager();
        String cls = pdm.getPlayerClass(player.getUniqueId());
        activate(player, cls);
    }

    public void activate(Player player, String playerClass) {
        UUID casterId = player.getUniqueId();

        // toggle off existing beacon for this player
        if (beaconLocations.containsKey(casterId)) {
            removeBeaconFor(casterId, player.getWorld());
            return;
        }

        casterClasses.put(casterId, playerClass);
        healProgress.put(casterId, new HashMap<>());

        // find ground
        Location loc = player.getLocation().clone();
        boolean groundFound = false;
        for (int i=0; i<=3; i++) {
            Block b = loc.clone().subtract(0,i,0).getBlock();
            if (b.getType().isSolid()) {
                loc.setY(b.getY()+1.0);
                groundFound = true; break;
            }
        }
        loc.setX(Math.floor(loc.getX())+0.5);
        loc.setZ(Math.floor(loc.getZ())+0.5);
        beaconLocations.put(casterId, loc);

        boolean isLB = "lightbringer".equalsIgnoreCase(playerClass);

        // spawn marker
        ArmorStand marker = player.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomName(isLB ? "LightbringerBeaconOfClarityMarker" : "BeaconOfClarityMarker");
        });
        beaconEntityIds.put(casterId, marker.getUniqueId());
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, isLB?1.4f:1.2f);
        if (isLB) player.sendMessage("§6✦ §eLightbringer's Beacon radiates with enhanced divine power! §6✦");

        // heal task
        BukkitTask healTask = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                Location bcLoc = beaconLocations.get(casterId);
                if (bcLoc==null || ticks>=durationTicks) {
                    removeBeaconFor(casterId, player.getWorld()); cancel(); return;
                }
                boolean lb = "lightbringer".equalsIgnoreCase(casterClasses.get(casterId));
                double effHeal = healAmount * (lb?lightbringerHealMultiplier:1);
                double effCap  = maxTotalHealPerAlly * (lb?lightbringerMaxHealMultiplier:1);

                for (Entity e: player.getWorld().getNearbyEntities(bcLoc, beaconRadius, beaconRadius, beaconRadius)) {
                    if (!(e instanceof Player)) continue;
                    Player ally = (Player)e;
                    UUID tid = ally.getUniqueId();
                    double cur = ally.getHealth();
                    double max = ally.getAttribute(Attribute.MAX_HEALTH).getValue();
                    double done = healProgress.get(casterId).getOrDefault(tid, 0.0);

                    if (done<effCap && cur<max) {
                        double toHeal = Math.min(effHeal, Math.min(effCap-done, max-cur));
                        if (toHeal>0.001) {
                            ally.setHealth(cur+toHeal);
                            healProgress.get(casterId).put(tid, done+toHeal);
                            if (lb) {
                                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0,ally.getHeight()*0.75,0),5,0.4,0.4,0.4);
                                ally.getWorld().spawnParticle(Particle.DUST, ally.getLocation().add(0,ally.getHeight()*0.75,0),8,0.5,0.5,0.5,0, lightbringerParticles);
                                ally.playSound(ally.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.7f,1.8f);
                            } else {
                                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0,ally.getHeight()*0.75,0),3,0.3,0.3,0.3);
                                ally.playSound(ally.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.5f,1.5f);
                            }
                        }
                    }
                }
                ticks += healIntervalTicks;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, healIntervalTicks);
        mainTasks.put(casterId, healTask);

        // visual cube
        BukkitTask cubeTask = new BukkitRunnable() {
            double angle=0;
            @Override public void run() {
                Location bcLoc = beaconLocations.get(casterId);
                if (bcLoc==null) { cancel(); return; }
                angle += Math.PI/32;
                double r = beaconVisualCubeSize/2;
                Vector[] pts = {
                        new Vector(-r,-r,-r), new Vector(r,-r,-r), new Vector(r,r,-r), new Vector(-r,r,-r),
                        new Vector(-r,-r,r),  new Vector(r,-r,r),  new Vector(r,r,r),  new Vector(-r,r,r)
                };
                boolean lb = "lightbringer".equalsIgnoreCase(casterClasses.get(casterId));
                for (Vector v: pts) {
                    double x = v.getX()*Math.cos(angle)-v.getZ()*Math.sin(angle);
                    double z = v.getX()*Math.sin(angle)+v.getZ()*Math.cos(angle);
                    Location p = bcLoc.clone().add(x, v.getY()+r, z);
                    p.getWorld().spawnParticle(Particle.DUST, p,1,0,0,0,0, lb?lightbringerParticles:defaultBeaconParticles);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(),0L,2L);
        visualTasks.put(casterId, cubeTask);

        // radius circle
        BukkitTask circTask = new BukkitRunnable() {
            @Override public void run() {
                Location bcLoc = beaconLocations.get(casterId);
                if (bcLoc==null) { cancel(); return; }
                boolean lb = "lightbringer".equalsIgnoreCase(casterClasses.get(casterId));
                int count = lb? radiusVisualParticles+10 : radiusVisualParticles;
                for (int i=0;i<count;i++) {
                    double a = 2*Math.PI*i/count;
                    double x = beaconRadius*Math.cos(a), z = beaconRadius*Math.sin(a);
                    Location p = bcLoc.clone().add(x, radiusVisualYOffset, z);
                    p.getWorld().spawnParticle(Particle.DUST, p,1,0,0,0,0, lb?lightbringerParticles:defaultBeaconParticles);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(),0L,10L);
        radiusVisualTasks.put(casterId, circTask);
    }

    private void removeBeaconFor(UUID casterId, World world) {
        if (mainTasks.containsKey(casterId)) { mainTasks.remove(casterId).cancel(); }
        if (visualTasks.containsKey(casterId)) { visualTasks.remove(casterId).cancel(); }
        if (radiusVisualTasks.containsKey(casterId)) { radiusVisualTasks.remove(casterId).cancel(); }

        UUID entId = beaconEntityIds.remove(casterId);
        if (entId!=null) {
            Entity e = Bukkit.getEntity(entId);
            if (e instanceof ArmorStand) e.remove();
        }
        Location loc = beaconLocations.remove(casterId);
        if (loc!=null) {
            world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE,1f,1f);
        }
        healProgress.remove(casterId);
        casterClasses.remove(casterId);
    }

    @Override
    public boolean isSuccessful() {
        return true; // always toggles
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.beaconofclarity.";
        name = cfg.getString(base+"name", name);
        description = cfg.getString(base+"description", description);
        cooldown = cfg.getInt(base+"cooldown", cooldown);
        manaCost = cfg.getInt(base+"mana-cost", manaCost);
        requiredClass = cfg.getString(base+"required-class", requiredClass);
        beaconRadius = cfg.getDouble(base+"beacon-radius", beaconRadius);
        durationTicks= cfg.getInt(base+"duration-ticks", durationTicks);
        healAmount = cfg.getDouble(base+"heal-amount", healAmount);
        maxTotalHealPerAlly = cfg.getDouble(base+"max-total-heal-per-ally", maxTotalHealPerAlly);
        healIntervalTicks = cfg.getInt(base+"heal-interval-ticks", healIntervalTicks);
        lightbringerHealMultiplier = cfg.getDouble(base+"lightbringer-heal-multiplier", lightbringerHealMultiplier);
        lightbringerMaxHealMultiplier= cfg.getDouble(base+"lightbringer-max-heal-multiplier", lightbringerMaxHealMultiplier);
        beaconVisualCubeSize= cfg.getDouble(base+"beacon-visual-cube-size", beaconVisualCubeSize);
        radiusVisualParticles= cfg.getInt(base+"radius-visual-particles", radiusVisualParticles);
        radiusVisualYOffset = cfg.getDouble(base+"radius-visual-y-offset", radiusVisualYOffset);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return null;
    }
}
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconOfClarityAbility implements Ability {
    private String name = "BeaconOfClarity";
    private String description = "Deploys a sacred beacon that bathes allies in healing light.";
    private int cooldown = 35;
    private int manaCost = 25;
    private String requiredClass = "lightbringer";

    // Configurable parameters
    private double beaconRadius = 10.0;
    private int durationTicks = 120;
    private double healAmount = 1.0;
    private double maxTotalHealPerAlly = 4.0;
    private int healIntervalTicks = 20;
    private double lightbringerHealMultiplier = 2;
    private double lightbringerMaxHealMultiplier = 2;
    private double beaconVisualCubeSize = 0.8;
    private int radiusVisualParticles = 30;
    private double radiusVisualYOffset = 0.75;

    // Enhanced visual parameters
    private double holyBeamHeight = 8.0;
    private double celestialRingRadius = 3.0;
    private double angelicOrbitSpeed = 0.05;
    private double haloParticles = 12;
    private double divineActivationHeight = 4.0;

    // Particle options
    private final Particle.DustOptions goldenLight = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.5f);
    private final Particle.DustOptions sacredWhite = new Particle.DustOptions(Color.fromRGB(255, 255, 230), 1.8f);
    private final Particle.DustOptions divineGlow = new Particle.DustOptions(Color.fromRGB(180, 230, 255), 2.0f);

    // Per-player runtime state
    private final Map<UUID, Location> beaconLocations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> beaconEntityIds = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> mainTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> visualTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> radiusVisualTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> healProgress = new ConcurrentHashMap<>();
    private final Map<UUID, String> casterClasses = new ConcurrentHashMap<>();
    private final Map<UUID, Double> orbitAngles = new ConcurrentHashMap<>();

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) {
        return false;
    }

    @Override
    public void activate(Player player) {
        PlayerDataManager pdm = Spellbreak.getInstance().getPlayerDataManager();
        String cls = pdm.getPlayerClass(player.getUniqueId());
        activate(player, cls);
    }

    public void activate(Player player, String playerClass) {
        UUID casterId = player.getUniqueId();

        // Toggle off existing beacon
        if (beaconLocations.containsKey(casterId)) {
            removeBeaconFor(casterId, player.getWorld());
            return;
        }

        casterClasses.put(casterId, playerClass);
        healProgress.put(casterId, new HashMap<>());
        orbitAngles.put(casterId, 0.0);

        // Find ground position
        Location loc = player.getLocation().clone();
        for (int i = 0; i <= 3; i++) {
            Block b = loc.clone().subtract(0, i, 0).getBlock();
            if (b.getType().isSolid()) {
                loc.setY(b.getY() + 1.0);
                break;
            }
        }
        loc.setX(Math.floor(loc.getX()) + 0.5);
        loc.setZ(Math.floor(loc.getZ()) + 0.5);
        beaconLocations.put(casterId, loc);

        boolean isLightbringer = "lightbringer".equalsIgnoreCase(playerClass);

        // Spawn marker
        ArmorStand marker = player.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomName("BeaconOfClarityMarker");
        });
        beaconEntityIds.put(casterId, marker.getUniqueId());

        // Divine activation sequence
        player.getWorld().playSound(loc, Sound.BLOCK_BELL_USE, 1.8f, 1.6f);
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
        player.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RETURN, 1.2f, 1.4f);
        player.getWorld().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.8f);

        // Grand celestial activation
        for (int i = 0; i < 3; i++) {
            final int ring = i;
            Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
                double radius = 1.0 + (ring * 1.5);
                for (int j = 0; j < 36; j++) {
                    double angle = 2 * Math.PI * j / 36;
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location particleLoc = loc.clone().add(x, 0.1, z);
                    particleLoc.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 2, 0, 0, 0, 0.05);
                    particleLoc.getWorld().spawnParticle(Particle.FIREWORK, particleLoc, 3, 0.1, 0, 0.1, 0.02);
                }
            }, i * 5L);
        }

        // Ascending light pillar
        for (int y = 0; y < 20; y++) {
            final int height = y;
            Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
                Location pillarLoc = loc.clone().add(0, height * 0.25, 0);
                pillarLoc.getWorld().spawnParticle(Particle.END_ROD, pillarLoc, 4, 0.15, 0.15, 0.15, 0.03);
                pillarLoc.getWorld().spawnParticle(Particle.CLOUD, pillarLoc, 2, 0.1, 0.1, 0.1, 0.01);
            }, y * 1L);
        }

        // Heal task
        BukkitTask healTask = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                Location bcLoc = beaconLocations.get(casterId);
                if (bcLoc == null || ticks >= durationTicks) {
                    removeBeaconFor(casterId, player.getWorld());
                    cancel();
                    return;
                }

                // Action bar timer
                int secs = (durationTicks - ticks)/20;
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§e✦ §6§lBEACON OF CLARITY §e✦ §7[§6" + secs + "s§7]"));

                PlayerDataManager pdm = Spellbreak.getInstance().getPlayerDataManager();
                boolean isLB = "lightbringer".equalsIgnoreCase(casterClasses.get(casterId));

                for (Entity e : player.getWorld().getNearbyEntities(bcLoc, beaconRadius, beaconRadius, beaconRadius)) {
                    if (!(e instanceof Player)) continue;
                    Player ally = (Player) e;
                    UUID tid = ally.getUniqueId();

                    // Get ally's class for multiplier
                    String allyClass = pdm.getPlayerClass(tid);
                    boolean allyIsLB = "lightbringer".equalsIgnoreCase(allyClass);

                    // Apply healing multipliers
                    double effHeal = healAmount * (allyIsLB ? lightbringerHealMultiplier : 1);
                    double effCap = maxTotalHealPerAlly * (allyIsLB ? lightbringerMaxHealMultiplier : 1);

                    double cur = ally.getHealth();
                    double max = ally.getAttribute(Attribute.MAX_HEALTH).getValue();
                    double done = healProgress.get(casterId).getOrDefault(tid, 0.0);

                    if (done < effCap && cur < max) {
                        double toHeal = Math.min(effHeal, Math.min(effCap - done, max - cur));
                        if (toHeal > 0.001) {
                            ally.setHealth(cur + toHeal);
                            healProgress.get(casterId).put(tid, done + toHeal);

                            // Divine healing effects
                            Location allyLoc = ally.getLocation().add(0, 1.5, 0);
                            if (allyIsLB) {
                                // Enhanced Lightbringer effects
                                allyLoc.getWorld().spawnParticle(Particle.HEART, allyLoc, 8, 0.4, 0.4, 0.4, 0);
                                allyLoc.getWorld().spawnParticle(Particle.END_ROD, allyLoc, 12, 0.5, 0.5, 0.5, 0.1);
                                allyLoc.getWorld().spawnParticle(Particle.FIREWORK, allyLoc, 15, 0.6, 0.6, 0.6, 0.08);

                                ally.playSound(allyLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 2.0f);
                                ally.playSound(allyLoc, Sound.BLOCK_BELL_USE, 0.6f, 2.0f);
                                ally.playSound(allyLoc, Sound.ITEM_TRIDENT_RETURN, 0.7f, 1.8f);


                            } else {
                                // Standard healing effects
                                allyLoc.getWorld().spawnParticle(Particle.HEART, allyLoc, 4, 0.3, 0.3, 0.3, 0);
                                allyLoc.getWorld().spawnParticle(Particle.FIREWORK, allyLoc, 8, 0.4, 0.4, 0.4, 0.05);

                                ally.playSound(allyLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
                                ally.playSound(allyLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.9f);

                            }
                        }
                    }
                }
                ticks += healIntervalTicks;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, healIntervalTicks);
        mainTasks.put(casterId, healTask);

        // Divine beacon visuals
        BukkitTask visualTask = new BukkitRunnable() {
            double rotation = 0;
            double pulse = 0;

            @Override public void run() {
                Location bcLoc = beaconLocations.get(casterId);
                if (bcLoc == null) {
                    cancel();
                    return;
                }

                rotation += Math.PI/48;
                pulse += Math.PI/20;
                double pulseSize = 0.1 * Math.sin(pulse);
                boolean isLB = "lightbringer".equalsIgnoreCase(casterClasses.get(casterId));

                // Core beacon - pulsating golden light
                double r = beaconVisualCubeSize/2 + pulseSize;
                for (int i = 0; i < 3; i++) {
                    double yOffset = i * 0.3;
                    for (int j = 0; j < 8; j++) {
                        double angle = rotation + (j * Math.PI/4);
                        double x = r * Math.cos(angle);
                        double z = r * Math.sin(angle);
                        Location p = bcLoc.clone().add(x, yOffset, z);
                        p.getWorld().spawnParticle(Particle.DUST, p, 1, 0,0,0,0, goldenLight);
                        if (isLB) {
                            p.getWorld().spawnParticle(Particle.DUST, p, 1, 0,0,0,0, sacredWhite);
                        }
                    }
                }

                // Celestial rings
                for (int ring = 0; ring < 3; ring++) {
                    double radius = celestialRingRadius * (1 - ring * 0.2);
                    double yOffset = 0.2 + ring * 0.3;
                    for (int i = 0; i < 24; i++) {
                        double angle = 2 * Math.PI * i / 24 + rotation;
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        Location p = bcLoc.clone().add(x, yOffset, z);
                        Particle.DustOptions dust = ring == 0 ? goldenLight :
                                ring == 1 ? sacredWhite : divineGlow;
                        p.getWorld().spawnParticle(Particle.DUST, p, 1, 0,0,0,0, dust);
                    }
                }

                // Angelic orbiting particles
                double orbitAngle = orbitAngles.get(casterId);
                orbitAngle += angelicOrbitSpeed;
                orbitAngles.put(casterId, orbitAngle);

                // Heavenly beam
                for (double y = 0; y < holyBeamHeight; y += 0.3) {
                    Location beamLoc = bcLoc.clone().add(0, y, 0);
                    beamLoc.getWorld().spawnParticle(Particle.END_ROD, beamLoc, 1, 0.2, 0.2, 0.2, 0.01);
                    if (y % 1 < 0.3) {
                        beamLoc.getWorld().spawnParticle(Particle.CLOUD, beamLoc, 1, 0.15, 0.15, 0.15, 0.005);
                    }
                }

                // Divine ground effect
                for (int i = 0; i < 12; i++) {
                    double angle = 2 * Math.PI * i / 12 + rotation;
                    double distance = beaconRadius * 0.7;
                    double x = distance * Math.cos(angle);
                    double z = distance * Math.sin(angle);
                    Location p = bcLoc.clone().add(x, 0.1, z);
                    p.getWorld().spawnParticle(Particle.FIREWORK, p, 1, 0, 0, 0, 0.03);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
        visualTasks.put(casterId, visualTask);
    }

    private void removeBeaconFor(UUID casterId, World world) {
        if (mainTasks.containsKey(casterId)) {
            mainTasks.remove(casterId).cancel();
        }
        if (visualTasks.containsKey(casterId)) {
            visualTasks.remove(casterId).cancel();
        }
        if (radiusVisualTasks.containsKey(casterId)) {
            radiusVisualTasks.remove(casterId).cancel();
        }

        UUID entId = beaconEntityIds.remove(casterId);
        if (entId != null) {
            Entity e = Bukkit.getEntity(entId);
            if (e instanceof ArmorStand) e.remove();
        }

        Location loc = beaconLocations.remove(casterId);
        if (loc != null) {
            // Final beam collapse
            for (double y = holyBeamHeight; y > 0; y -= 0.4) {
                final double height = y;
                Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
                    Location beamLoc = loc.clone().add(0, height, 0);
                    beamLoc.getWorld().spawnParticle(Particle.FIREWORK, beamLoc, 8, 0.3, 0.3, 0.3, 0.1);
                }, (long) ((holyBeamHeight - y) * 0.5));
            }

            world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.9f);
            world.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.8f);
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.6f);

            Player p = Bukkit.getPlayer(casterId);

        }

        healProgress.remove(casterId);
        casterClasses.remove(casterId);
        orbitAngles.remove(casterId);
    }

    @Override
    public boolean isSuccessful() {
        return true;
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

        // New visual parameters
        holyBeamHeight = cfg.getDouble(base+"holy-beam-height", holyBeamHeight);
        celestialRingRadius = cfg.getDouble(base+"celestial-ring-radius", celestialRingRadius);
        angelicOrbitSpeed = cfg.getDouble(base+"angelic-orbit-speed", angelicOrbitSpeed);
        haloParticles = cfg.getDouble(base+"halo-particles", haloParticles);
        divineActivationHeight = cfg.getDouble(base+"divine-activation-height", divineActivationHeight);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return null;
    }
}
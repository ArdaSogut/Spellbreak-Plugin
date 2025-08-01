package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DreamwalkerAbility implements Ability {
    private String name = "Dreamwalker";
    private String description = "Phase through reality while anchored to your starting elevation";
    private int cooldown = 25;
    private int manaCost = 50;
    private String requiredClass = "mindshaper";
    private int durationTicks = 20 * 4;
    private boolean successfulActivation = false;
    private double damage = 1.0;

    public static final Set<UUID> activePlayers = new HashSet<>();
    public static final Map<UUID, GameMode> previousGamemodes = new HashMap<>();
    public static final Map<UUID, Double> initialYLevels = new HashMap<>();
    public static final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private static final Map<UUID, Set<UUID>> affectedTargets = new HashMap<>();

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getCooldown() {
        return cooldown;
    }

    @Override
    public int getManaCost() {
        return manaCost;
    }

    @Override
    public String getRequiredClass() {
        return requiredClass;
    }

    @Override
    public boolean isTriggerAction(org.bukkit.event.block.Action action) {
        return false;
    }

    @Override
    public void activate(Player player) {
        successfulActivation = false;
        if (activePlayers.contains(player.getUniqueId())) return;

        activePlayers.add(player.getUniqueId());
        successfulActivation = true;

        initialYLevels.put(player.getUniqueId(), player.getLocation().getY());
        previousGamemodes.put(player.getUniqueId(), player.getGameMode());
        affectedTargets.put(player.getUniqueId(), new HashSet<>());

        player.setGameMode(GameMode.SPECTATOR);
        player.setInvulnerable(true);
        player.setCollidable(false);

        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "Dreamwalker");
        int adjustedDurationTicks = durationTicks + (spellLevel.getLevel() * 5); // Increase duration based on level

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= adjustedDurationTicks || !player.isOnline()) {
                    endAbility(player);
                    cancel();
                    return;
                }

                Double initialY = initialYLevels.get(player.getUniqueId());
                if (initialY != null) {
                    Location currentLoc = player.getLocation();
                    if (currentLoc.getY() < initialY) {
                        Location newLoc = currentLoc.clone();
                        newLoc.setY(initialY);
                        player.teleport(newLoc);
                    }
                }

                int remaining = (adjustedDurationTicks - ticks) / 20;
                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.LIGHT_PURPLE + "Dreamwalker: " + remaining + "s"));

                player.getWorld().spawnParticle(
                        Particle.DUST,
                        player.getLocation(),
                        25, 0.5, 0.5, 0.5, 0.15,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 182, 193), 1.0f));

                Set<UUID> affected = affectedTargets.get(player.getUniqueId());
                for (Entity entity : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity target && !target.equals(player)) {
                        if (!affected.contains(target.getUniqueId())) {
                            affected.add(target.getUniqueId());

                            // Only apply potion effects to players
                            if (target instanceof Player) {
                                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
                                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
                            }

                            player.setMetadata("DREAMWALKER_DAMAGE", new FixedMetadataValue(Spellbreak.getInstance(), true));
                            Spellbreak.getInstance().getAbilityDamage().damage(
                                    target,
                                    damage,
                                    player,
                                    DreamwalkerAbility.this,
                                    "phase_damage"
                            );
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.removeMetadata("DREAMWALKER_DAMAGE", Spellbreak.getInstance());
                                }
                            }.runTaskLater(Spellbreak.getInstance(), 1L);
                        }
                    }
                }
            }
        };
        activeTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    public static void endAbility(Player player) {
        UUID uuid = player.getUniqueId();
        activePlayers.remove(uuid);
        initialYLevels.remove(uuid);
        affectedTargets.remove(uuid);

        BukkitRunnable task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        GameMode previous = previousGamemodes.remove(uuid);
        if (previous != null && player.isOnline()) {
            player.setGameMode(previous);
        }
        player.setInvulnerable(false);
        player.setCollidable(true);

        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(""));
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.dreamwalker.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        durationTicks = cfg.getInt(base + "duration-seconds", 4) * 20; // Base duration
        damage = cfg.getDouble(base + "damage", damage);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        if ("phase_damage".equals(sub)) {
            return String.format("§d%s was phased out of existence by %s's Dreamwalker", victim, caster);
        }
        return String.format("§d%s succumbed to dream energy from %s", victim, caster);
    }
}
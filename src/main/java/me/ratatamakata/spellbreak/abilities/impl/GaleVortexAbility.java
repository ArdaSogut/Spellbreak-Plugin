package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class GaleVortexAbility implements Ability {
    // Configuration values
    private int cooldown = 9;
    private int manaCost = 30;
    private String requiredClass = "elementalist";
    private double radius = 1.7;
    private double damage = 2.0;
    private double basePullStrength = 1.7;
    private double forwardSpeed = 1.2;
    private double range = 16.0;

    // --- LEVEL-ADJUSTED (OYUNCUYA FAYDA SAĞLAYAN) DEĞİŞKENLER ---
    public int getAdjustedCooldown(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return (int) (cooldown * lvl.getCooldownReduction());
    }

    public int getAdjustedManaCost(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return (int) (manaCost * lvl.getManaCostReduction());
    }

    public double getAdjustedDamage(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return damage * lvl.getDamageMultiplier();
    }

    public double getAdjustedRange(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return range * lvl.getRangeMultiplier();
    }

    @Override
    public String getName() { return "GaleVortex"; }

    @Override
    public String getDescription() {
        return "Fire a spiraling wind tunnel that pulls enemies toward you";
    }

    @Override
    public int getCooldown() { return cooldown; }

    @Override
    public int getManaCost() { return manaCost; }

    @Override
    public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public void activate(Player player) {
        final Location startLoc = player.getEyeLocation().clone();
        final Vector dir = startLoc.getDirection().normalize();
        Vector up = new Vector(0, 1, 0);
        final Vector Utmp = dir.clone().crossProduct(up).normalize();
        final Vector U = (Utmp.lengthSquared() == 0) ? new Vector(1, 0, 0) : Utmp;
        final Vector V = dir.clone().crossProduct(U).normalize();

        // Seviye bazlı değerler
        double adjustedDamage = getAdjustedDamage(player);
        double adjustedRange = getAdjustedRange(player);

        new BukkitRunnable() {
            double angle = 0;
            Location movingLoc = startLoc.clone();
            final Set<UUID> hitEntities = new HashSet<>();

            @Override
            public void run() {
                if (!player.isValid() || movingLoc.distance(startLoc) >= adjustedRange) {
                    cancel();
                    return;
                }

                movingLoc.add(dir.clone().multiply(forwardSpeed));
                World world = player.getWorld();

                // Spiral particles (görsel parametreler sabit)
                int arms = 6;
                for (int i = 0; i < arms; i++) {
                    double currentAngle = angle + (i * 2 * Math.PI / arms);
                    for (double rScale : new double[]{radius, radius * 0.5}) {
                        double cos = Math.cos(currentAngle) * rScale;
                        double sin = Math.sin(currentAngle) * rScale;
                        Vector offset = U.clone().multiply(cos).add(V.clone().multiply(sin));
                        Location spawnLoc = movingLoc.clone().add(offset);

                        // Main particles
                        world.spawnParticle(Particle.CLOUD, spawnLoc, 3, 0.02, 0.02, 0.02, 0.02);
                        world.spawnParticle(Particle.ASH, spawnLoc.clone().add(0, 0.2, 0), 2, 0.05, 0.05, 0.05, 0.02);

                        // Entity handling
                        for (Entity e : world.getNearbyEntities(spawnLoc, 1, 1, 1)) {
                            if (!(e instanceof LivingEntity)) continue;
                            LivingEntity entity = (LivingEntity) e;
                            if (entity.equals(player) || hitEntities.contains(entity.getUniqueId())) continue;

                            // Register hit
                            hitEntities.add(entity.getUniqueId());

                            // Apply seviye bazlı damage
                            Spellbreak.getInstance().getAbilityDamage().damage(
                                    entity, adjustedDamage, player, GaleVortexAbility.this, "GaleVortex"
                            );

                            // Calculate natural pull
                            Location playerLoc = player.getLocation();
                            Vector toPlayer = playerLoc.toVector().subtract(entity.getLocation().toVector());
                            double distance = toPlayer.length();

                            if (distance < 3) return; // Too close, don't pull

                            toPlayer.normalize();
                            double strength = basePullStrength * (1 + (10 / (distance + 1)));
                            Vector velocity = toPlayer.multiply(Math.min(strength, distance * 0.5)) // prevents overshooting
                                    .add(new Vector(0, 0.3, 0));

                            entity.setVelocity(velocity);

                            // Effects
                            world.spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.02);
                            world.playSound(entity.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.7f, 1.5f);
                        }
                    }
                }
                angle += Math.PI / 4;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);

        // Initial effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.2f);
    }

    @Override
    public boolean isSuccessful() { return true; }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.galevortex.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        radius = cfg.getDouble(base + "radius", radius);
        damage = cfg.getDouble(base + "damage", damage);
        basePullStrength = cfg.getDouble(base + "pull-strength", basePullStrength);
        forwardSpeed = cfg.getDouble(base + "forward-speed", forwardSpeed);
        range = cfg.getDouble(base + "range", range);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§7%s §8couldn't stand their ground against §7%s§8's Gale Vortex!", victim, caster);
    }
}


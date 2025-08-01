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

public class AvalancheAbility implements Ability {
    private int cooldown = 11;
    private int manaCost = 40;
    private String requiredClass = "elementalist";
    private double baseRadius = 1.5;
    private double maxRadius = 2.2;
    private double initialSpeed = 0.5;
    private double speedGain = 1.0;
    private double maxSpeed = 0.9;
    private double damage = 1.0;
    private double knockbackStrength = 1.5;
    private int maxDuration = 30;
    private double growthRate = 0.05;
    private int hitCooldownTicks = 20;

    private final Map<UUID, AvalancheData> activeAvalanches = new HashMap<>();

    private class AvalancheData {
        double currentRadius;
        double currentSpeed;
        int ticksActive;
        Location lastLocation;
        Map<UUID, Integer> hitCooldowns = new HashMap<>();

        AvalancheData(Player player) {
            this.currentRadius = adjustedBaseRadius;
            this.currentSpeed = adjustedInitialSpeed;
            this.ticksActive = 0;
            this.lastLocation = player.getLocation().clone();
        }

        void tickCooldowns() {
            hitCooldowns.entrySet().removeIf(e -> e.getValue() <= 0);
            hitCooldowns.replaceAll((u, t) -> t - 1);
        }

        boolean canHit(Entity e) {
            return !hitCooldowns.containsKey(e.getUniqueId());
        }

        void markHit(Entity e) {
            hitCooldowns.put(e.getUniqueId(), hitCooldownTicks);
        }
    }

    // adjusted parameters per-cast
    private double adjustedBaseRadius;
    private double adjustedMaxRadius;
    private double adjustedInitialSpeed;
    private double adjustedSpeedGain;
    private double adjustedMaxSpeed;
    private double adjustedDamage;
    private double adjustedKnockback;
    private int adjustedMaxDuration;
    private double adjustedGrowthRate;

    @Override
    public String getName() { return "Avalanche"; }

    @Override
    public String getDescription() { return "Create a growing snowball that increases your speed and damages enemies."; }

    @Override
    public int getCooldown() { return cooldown; }

    @Override
    public int getManaCost() { return manaCost; }

    @Override
    public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeAvalanches.containsKey(uuid)) return;

        SpellLevel lvl = Spellbreak.getInstance()
                .getLevelManager()
                .getSpellLevel(uuid,
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(uuid),
                        getName());

        // Sadece oyuncuya fayda sağlayan değişkenler seviye ile artacak
        adjustedMaxRadius = maxRadius * lvl.getRangeMultiplier();
        adjustedDamage = damage * lvl.getDamageMultiplier();
        adjustedKnockback = knockbackStrength * (1 + lvl.getLevel() * 0.05);
        adjustedMaxDuration = (int)(maxDuration * (1 + lvl.getDurationMultiplier()));
        // Görsel/kozmetik değişkenler (baseRadius, initialSpeed, speedGain, maxSpeed, growthRate) sabit kalacak
        adjustedBaseRadius = baseRadius;
        adjustedInitialSpeed = initialSpeed;
        adjustedSpeedGain = speedGain;
        adjustedMaxSpeed = maxSpeed;
        adjustedGrowthRate = growthRate;

        AvalancheData data = new AvalancheData(player);
        activeAvalanches.put(uuid, data);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeAvalanches.containsKey(uuid)
                        || data.ticksActive++ >= adjustedMaxDuration
                        || !player.isValid()) {
                    cleanup(player);
                    cancel();
                    return;
                }
                data.tickCooldowns();

                double moved = player.getLocation().distance(data.lastLocation);
                data.currentRadius = Math.min(adjustedMaxRadius,
                        data.currentRadius + moved * adjustedGrowthRate);
                data.currentSpeed = Math.min(adjustedMaxSpeed,
                        adjustedInitialSpeed + (data.currentRadius - adjustedBaseRadius) * adjustedSpeedGain);
                data.lastLocation = player.getLocation().clone();

                // --- STAIR LOGIC ---
                Location loc = player.getLocation();
                Vector dir = loc.getDirection().normalize();
                Location front = loc.clone().add(dir.clone().setY(0).multiply(1.1));
                Location frontUp = front.clone().add(0, 1, 0);
                boolean blockInFront = !front.getBlock().isPassable();
                boolean canStepUp = blockInFront && frontUp.getBlock().isPassable();
                Vector vel = dir.multiply(data.currentSpeed);
                if (canStepUp) {
                    vel.setY(0.5); // Yükselme efekti
                } else {
                    vel.setY(player.isOnGround() ? 0.1 : -0.3);
                }
                player.setVelocity(vel);
                // ---

                player.setWalkSpeed((float)Math.min(0.7, 0.2 * data.currentSpeed));

                createSnowSphere(player, data.currentRadius);
                createFrontTrail(player);
                handleCollisions(player, data);
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.5f);
        player.sendMessage(ChatColor.AQUA + "Avalanche building momentum!");
    }

    private void createSnowSphere(Player player, double radius) {
        World w = player.getWorld();
        Location c = player.getLocation().add(0, 1, 0);
        for (double t = 0; t < 2 * Math.PI; t += Math.PI / 6) {
            for (double p = 0; p < Math.PI; p += Math.PI / 6) {
                double x = radius * Math.cos(t) * Math.sin(p);
                double y = radius * Math.cos(p);
                double z = radius * Math.sin(t) * Math.sin(p);
                w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(x, y, z), 1, 0, 0, 0, 0);
            }
        }
    }

    private void createFrontTrail(Player player) {
        World w = player.getWorld();
        Location f = player.getLocation().add(0, 1, 0)
                .add(player.getLocation().getDirection().normalize().multiply(1.5));
        w.spawnParticle(Particle.SNOWFLAKE, f, 2, 0.2, 0.2, 0.2, 0.01);
    }

    private void handleCollisions(Player player, AvalancheData data) {
        if (data.currentRadius < adjustedBaseRadius + 0.3) return;
        World w = player.getWorld();
        double r = data.currentRadius * 0.9;
        for (Entity e : w.getNearbyEntities(player.getLocation(), r, r/2, r,
                ent -> ent instanceof LivingEntity && !ent.equals(player))) {
            if (!data.canHit(e)) continue;
            data.markHit(e);
            Vector knock = e.getLocation().toVector()
                    .subtract(player.getLocation().toVector()).normalize()
                    .multiply(adjustedKnockback).setY(0.5);
            e.setVelocity(knock);
            Spellbreak.getInstance().getAbilityDamage()
                    .damage((LivingEntity)e, adjustedDamage, player, this, "Avalanche");
            w.spawnParticle(Particle.BLOCK_CRUMBLE, e.getLocation(), 20,
                    Material.ICE.createBlockData());
            w.playSound(e.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
        }
    }

    private void cleanup(Player player) {
        if (activeAvalanches.remove(player.getUniqueId()) != null) {
            player.setWalkSpeed(0.2f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SNOW_BREAK, 1f, 0.8f);
        }
    }

    @Override
    public boolean isSuccessful() { return true; }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String b = "abilities.avalanche.";
        cooldown = cfg.getInt(b + "cooldown", cooldown);
        manaCost = cfg.getInt(b + "mana-cost", manaCost);
        baseRadius = cfg.getDouble(b + "base-radius", baseRadius);
        maxRadius = cfg.getDouble(b + "max-radius", maxRadius);
        initialSpeed = cfg.getDouble(b + "initial-speed", initialSpeed);
        speedGain = cfg.getDouble(b + "speed-gain", speedGain);
        maxSpeed = cfg.getDouble(b + "max-speed", maxSpeed);
        damage = cfg.getDouble(b + "damage", damage);
        knockbackStrength = cfg.getDouble(b + "knockback-strength", knockbackStrength);
        maxDuration = cfg.getInt(b + "max-duration", maxDuration);
        growthRate = cfg.getDouble(b + "growth-rate", growthRate);
        hitCooldownTicks = cfg.getInt(b + "hit-cooldown-ticks", hitCooldownTicks);
        requiredClass = cfg.getString(b + "required-class", requiredClass);
    }

    public int getAdjustedCooldown(Player p) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
                p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), getName());
        return (int)(cooldown * lvl.getCooldownReduction());
    }

    public int getAdjustedManaCost(Player p) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
                p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), getName());
        return (int)(manaCost * lvl.getManaCostReduction());
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§b%s §3was crushed by §b%s§3's Avalanche!", victim, caster);
    }
}

// Enhanced EchoPulseAbility.java with level system integration
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.stream.Collectors;

public class EchoPulseAbility implements Ability {
    // Base values (Level 1)
    private int baseCooldown = 12;
    private int baseManaCost = 35;
    private String requiredClass = "mindshaper";
    private double baseRange = 10.0;
    private double baseConeAngle = 55.0;
    private double baseDamage = 3.0;
    private int baseDelayTicks = 15;

    private final Particle.DustOptions[] waveParticles = {
            new Particle.DustOptions(Color.fromRGB(255, 100, 220), 2.5f),
            new Particle.DustOptions(Color.fromRGB(255, 180, 250), 2.0f),
            new Particle.DustOptions(Color.fromRGB(255, 220, 240), 1.8f)
    };

    @Override
    public String getName() {
        return "EchoPulse";
    }

    @Override
    public String getDescription() {
        return "Projects psychokinetic rings in a cone pattern that expand with damage radius.";
    }

    @Override
    public int getCooldown() {
        return baseCooldown;
    }

    @Override
    public int getManaCost() {
        return baseManaCost;
    }

    @Override
    public String getRequiredClass() {
        return requiredClass;
    }

    @Override
    public boolean isTriggerAction(Action action) {
        return false;
    }

    @Override
    public void activate(Player player) {
        // Get level-adjusted values
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "EchoPulse");

        double range = baseRange * spellLevel.getRangeMultiplier();
        double damage = baseDamage * spellLevel.getDamageMultiplier();
        int delayTicks = (int) (baseDelayTicks * spellLevel.getCooldownReduction());

        Location origin = player.getEyeLocation().add(0, -0.2, 0);
        Vector direction = player.getLocation().getDirection().normalize();

        // Launch first wave immediately
        launchWaveSequence(origin, direction, player, false, spellLevel, range, damage);

        // Launch echo wave after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                launchWaveSequence(origin, direction, player, true, spellLevel, range, damage);
            }
        }.runTaskLater(Spellbreak.getInstance(), delayTicks);

        // Enhanced sound based on spell level
        float pitch = 0.7f + (spellLevel.getLevel() * 0.1f);
        player.getWorld().playSound(origin, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, pitch);

        // Give spell experience for using the ability
        // Spellbreak.getInstance().getLevelManager().giveSpellExperience(player, getName(), 2);
    }

    private void launchWaveSequence(Location origin, Vector direction, Player caster, boolean isEcho,
                                    SpellLevel spellLevel, double range, double damage) {
        double waveInterval = 1.5;
        double startDistance = 1.5;
        int totalWaves = (int) Math.ceil((range - startDistance) / waveInterval);

        // More waves at higher levels
        if (spellLevel.getLevel() >= 3) {
            totalWaves += 2;
        }
        if (spellLevel.getLevel() >= 5) {
            totalWaves += 3;
        }

        for (int i = 0; i < totalWaves; i++) {
            final double distance = startDistance + i * waveInterval;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (distance <= range) {
                        double currentRadius = distance * 0.4;

                        // Enhanced radius at higher levels
                        if (spellLevel.getLevel() >= 4) {
                            currentRadius *= 1.2;
                        }

                        createConeRing(origin, direction, distance, currentRadius, isEcho, spellLevel);
                        checkDamage(origin, direction, caster, distance, currentRadius, isEcho, damage, spellLevel);
                    }
                }
            }.runTaskLater(Spellbreak.getInstance(), i * 2L);
        }
    }

    private void createConeRing(Location origin, Vector dir, double distance, double radius,
                                boolean isEcho, SpellLevel spellLevel) {
        World world = origin.getWorld();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Vector up = right.getCrossProduct(dir).normalize();

        int points = (int) (20 + (radius * 2.5));

        // More particles at higher levels
        if (spellLevel.getLevel() >= 3) {
            points += 10;
        }

        double angleStep = (2 * Math.PI) / points;

        for (double angle = 0; angle < 2 * Math.PI; angle += angleStep) {
            Vector ringPoint = right.clone().multiply(Math.cos(angle) * radius)
                    .add(up.clone().multiply(Math.sin(angle) * radius));

            Location point = origin.clone()
                    .add(dir.clone().multiply(distance))
                    .add(ringPoint);

            // Enhanced particles at higher levels
            int particleCount = 3 + spellLevel.getLevel();
            double spread = 0.15 + (spellLevel.getLevel() * 0.05);

            world.spawnParticle(Particle.DUST, point, particleCount,
                    spread, spread, spread, 0,
                    waveParticles[isEcho ? 1 : 0]);

            world.spawnParticle(Particle.DUST, point, 2,
                    0.1, 0.1, 0.1, 0,
                    waveParticles[2]);
        }

        // Enhanced sparks at higher levels
        if (distance % 3 == 0 || spellLevel.getLevel() >= 4) {
            double sparkAngle = (System.currentTimeMillis() / 50.0) % (2 * Math.PI);
            Vector sparkVec = right.clone().multiply(Math.cos(sparkAngle) * radius)
                    .add(up.clone().multiply(Math.sin(sparkAngle) * radius));

            int sparkCount = 8 + (spellLevel.getLevel() * 2);
            world.spawnParticle(Particle.DUST,
                    origin.clone().add(dir.clone().multiply(distance)).add(sparkVec),
                    sparkCount, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.WHITE, 1.2f));

            // Level 5 special effect - purple sparks
            if (spellLevel.getLevel() >= 5) {
                world.spawnParticle(Particle.DUST,
                        origin.clone().add(dir.clone().multiply(distance)).add(sparkVec),
                        5, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(128, 0, 255), 1.5f));
            }
        }
    }

    private void checkDamage(Location origin, Vector direction, Player caster, double distance,
                             double currentRadius, boolean isEcho, double damage, SpellLevel spellLevel) {
        double checkDistance = distance + currentRadius;
        Collection<LivingEntity> targets = origin.getWorld().getNearbyEntities(
                        origin, checkDistance, checkDistance / 2, checkDistance,
                        e -> e instanceof LivingEntity && e != caster && !e.isDead())
                .stream()
                .map(e -> (LivingEntity) e)
                .collect(Collectors.toList());

        double coneAngle = baseConeAngle;

        // Wider cone at higher levels
        if (spellLevel.getLevel() >= 2) {
            coneAngle += 10;
        }
        if (spellLevel.getLevel() >= 4) {
            coneAngle += 15;
        }

        double cosAngle = Math.cos(Math.toRadians(coneAngle / 2));
        Vector dirNormalized = direction.clone().normalize();

        String subAbility = isEcho ? "Echo" : null;

        for (LivingEntity target : targets) {
            Vector toTarget = target.getLocation().toVector().subtract(origin.toVector());
            double targetDistance = toTarget.length();
            toTarget.normalize();

            if (dirNormalized.dot(toTarget) > cosAngle &&
                    targetDistance >= distance - 1.5 &&
                    targetDistance <= distance + 1.5) {

                // Apply level-modified damage
                double finalDamage = damage;

                // Bonus damage effects at higher levels
                if (spellLevel.getLevel() >= 3 && Math.random() < 0.2) {
                    finalDamage *= 1.5; // 20% chance for 50% more damage
                }

                if (spellLevel.getLevel() >= 5 && isEcho && Math.random() < 0.1) {
                    finalDamage *= 2.0; // 10% chance for double damage on echo
                }

                Spellbreak.getInstance().getAbilityDamage().damage(
                        target, finalDamage, caster, this, subAbility);

                spawnHitEffect(target.getLocation(), spellLevel);

                // Give extra XP for hitting targets
                //Spellbreak.getInstance().getLevelManager().giveSpellExperience(caster, getName(), 1);
            }
        }
    }

    private void spawnHitEffect(Location loc, SpellLevel spellLevel) {
        World world = loc.getWorld();

        int particleCount = 12 + (spellLevel.getLevel() * 3);
        double spread = 0.4 + (spellLevel.getLevel() * 0.1);

        world.spawnParticle(Particle.WITCH, loc, particleCount, spread, spread, spread, 0.15);

        // Enhanced hit effects at higher levels
        if (spellLevel.getLevel() >= 3) {
            world.spawnParticle(Particle.ENCHANT, loc, 8, 0.5, 0.5, 0.5, 1.0);
        }

        if (spellLevel.getLevel() >= 5) {
            world.spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.3, 0.3, 0.3, 0.1);
        }

        float pitch = 1.8f + (spellLevel.getLevel() * 0.1f);
        world.playSound(loc, Sound.BLOCK_BELL_USE, 0.8f, pitch);
    }

    // Modified methods to return level-adjusted values
    public int getAdjustedCooldown(Player player) {
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "EchoPulse");
        return (int) (baseCooldown * spellLevel.getCooldownReduction());
    }

    public int getAdjustedManaCost(Player player) {
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "EchoPulse");
        return (int) (baseManaCost * spellLevel.getManaCostReduction());
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.echopulse.";
        baseCooldown = cfg.getInt(base + "cooldown", baseCooldown);
        baseManaCost = cfg.getInt(base + "mana-cost", baseManaCost);
        baseRange = cfg.getDouble(base + "range", baseRange);
        baseConeAngle = cfg.getDouble(base + "cone-angle", baseConeAngle);
        baseDamage = cfg.getDouble(base + "damage", baseDamage);
        baseDelayTicks = cfg.getInt(base + "delay-ticks", baseDelayTicks);
        requiredClass = cfg.getString(base + "required-class", requiredClass);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        if (sub != null && sub.equals("Echo")) {
            return String.format("§d%s§5's echoing sonic waves overwhelmed §d%s§5's consciousness.", caster, victim);
        }
        return String.format("§d%s§5's sonic waves overwhelmed §d%s§5's consciousness.", caster, victim);
    }

    @Override
    public String getDefaultSubAbilityName() {
        return null;
    }

    // Helper method to get spell level info for tooltips/descriptions
    public String getLevelInfo(Player player) {
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "EchoPulse");
        StringBuilder info = new StringBuilder();

        info.append("§7Level ").append(spellLevel.getLevel()).append("/5\n");
        info.append("§7Damage: §c").append(String.format("%.1f", baseDamage * spellLevel.getDamageMultiplier())).append("\n");
        info.append("§7Range: §e").append(String.format("%.1f", baseRange * spellLevel.getRangeMultiplier())).append("\n");
        info.append("§7Cooldown: §b").append(getAdjustedCooldown(player)).append("s\n");
        info.append("§7Mana: §9").append(getAdjustedManaCost(player));

        if (spellLevel.getLevel() < 5) {
            info.append("\n§7Progress: ").append(String.format("%.1f", spellLevel.getExperiencePercentage())).append("%");
        } else {
            info.append("\n§6MAX LEVEL");
        }

        return info.toString();
    }
}
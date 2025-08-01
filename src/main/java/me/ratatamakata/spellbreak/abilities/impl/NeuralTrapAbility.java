package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class NeuralTrapAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 30;
    private String requiredClass = "mindshaper"; // Lowercase to match config

    private double damagePerThreshold = 1.0;
    private double blocksPerThreshold = 3.5; // Increased to reduce knockback spam
    private int tickInterval = 15; // Check every second (20 ticks)
    private int durationTicks = 100; // Duration in ticks (4 seconds)
    private double range = 15.0;
    private double arcHeight = 2.5;
    private double maxTotalDamage = 3.0;

    private boolean success = false;

    @Override
    public String getName() { return "NeuralTrap"; }

    @Override
    public String getDescription() {
        return "Ensnare target with a neural trap; deals damage the more they move while trapped.";
    }

    @Override
    public int getCooldown() { return cooldown; }

    @Override
    public int getManaCost() { return manaCost; }

    @Override
    public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) { return false; }

    @Override
    public boolean isSuccessful() { return success; }

    @Override
    public void activate(Player player) {
        success = false;

        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), getName(), "NeuralTrap");
        int adjustedCooldown = (int) (cooldown * spellLevel.getCooldownReduction());
        int adjustedManaCost = (int) (manaCost * spellLevel.getManaCostReduction());
        double adjustedDamagePerThreshold = damagePerThreshold + (spellLevel.getLevel() * 0.5); // Increase damage based on level

        // Check if the player has enough mana
        if (!Spellbreak.getInstance().getManaSystem().consumeMana(player, adjustedManaCost)) {
            return;
        }

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                1.0,
                entity -> entity instanceof LivingEntity && !entity.equals(player) && player.hasLineOfSight(entity)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity target = (LivingEntity) result.getHitEntity();

        if (target.isDead() || target.getHealth() <= 0) return;

        spawnParticles(player.getLocation(), target.getLocation(), player.getWorld());

        // Start the trap effect with adjusted damage
        startNeuralTrapEffect(player, target, adjustedDamagePerThreshold);

        success = true;
    }

    private void spawnParticles(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = start.distance(end);

        start = start.clone().add(0, 1.5, 0);
        end = end.clone().add(0, 1.5, 0);

        int steps = (int) (distance * 4);
        List<Location> path = new ArrayList<>();

        for (int i = 0; i <= steps; i++) {
            double ratio = (double) i / steps;
            Location point = start.clone().add(direction.clone().multiply(ratio));
            double x = ratio * 2 - 1;
            double yOffset = arcHeight * (1 - x * x);
            point.add(0, yOffset, 0);

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    3,
                    0.15, 0.15, 0.15,
                    new Particle.DustOptions(Color.fromRGB(255, 182, 193), 2.5f) // pastel pink
            );
            path.add(point);
        }

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= path.size()) {
                    cancel();
                    return;
                }

                Location point = path.get(index);
                world.spawnParticle(
                        Particle.DUST,
                        point,
                        1,
                        0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 182, 193), 2.5f) // pastel pink
                );
                index++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void startNeuralTrapEffect(Player caster, LivingEntity target, double adjustedDamagePerThreshold) {
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(caster.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(caster.getUniqueId()), "NeuralTrap");
        int adjustedDurationTicks = durationTicks + (spellLevel.getLevel() * 5);
        // Removed damageDone map - not needed for single-target tracking

        new BukkitRunnable() {
            Location lastLocation = target.getLocation().clone();
            double movedDistance = 0.0;
            int elapsedTicks = 0;
            double totalDamageDone = 0.0; // Track cumulative damage for THIS trap

            @Override
            public void run() {
                if (elapsedTicks >= adjustedDurationTicks || target.isDead() || target.getHealth() <= 0) {
                    if (target instanceof Player) {
                        ((Player) target).sendActionBar("");
                    }
                    cancel();
                    return;
                }

                double distance = target.getLocation().distance(lastLocation);
                movedDistance += distance;
                lastLocation = target.getLocation().clone();

                int hits = (int) (movedDistance / blocksPerThreshold);

                if (hits > 0) {
                    double damageThisTick = hits * adjustedDamagePerThreshold;

                    // Enforce max total damage cap
                    if (totalDamageDone + damageThisTick > maxTotalDamage) {
                        damageThisTick = maxTotalDamage - totalDamageDone;
                    }

                    // Apply damage if we haven't reached the cap
                    if (damageThisTick > 0) {
                        Spellbreak.getInstance().getAbilityDamage().damage(
                                target,
                                damageThisTick,
                                caster,
                                NeuralTrapAbility.this,
                                "movement"
                        );

                        totalDamageDone += damageThisTick; // Update cumulative damage

                        // Effects
                        target.getWorld().playSound(target.getLocation(),
                                Sound.ENTITY_PLAYER_HURT, 1f, 1f);
                        target.getWorld().spawnParticle(
                                Particle.DUST,
                                target.getLocation().add(0, 1, 0),
                                10,
                                0.3, 0.3, 0.3,
                                new Particle.DustOptions(Color.fromRGB(255, 182, 193), 1.5f)
                        );
                    }

                    movedDistance -= hits * blocksPerThreshold;

                    // Cancel if we reached damage cap
                    if (totalDamageDone >= maxTotalDamage) {
                        if (target instanceof Player) {
                            ((Player) target).sendActionBar("");
                        }
                        cancel();
                        return;
                    }
                }

                elapsedTicks += tickInterval;

                // Action bar update
                if (target instanceof Player) {
                    int secondsLeft = Math.max(0, (adjustedDurationTicks - elapsedTicks) / 20);
                    ((Player) target).sendActionBar(ChatColor.LIGHT_PURPLE + "Neural Trap: "
                            + ChatColor.WHITE + secondsLeft + "s");
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, tickInterval);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String sub) {
        return String.format("§d%s's §5mind collapsed from §d%s's §5neural trap", victimName, casterName);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.neuraltrap.cooldown", cooldown);
        manaCost = cfg.getInt("abilities.neuraltrap.mana-cost", manaCost);
        requiredClass = cfg.getString("abilities.neuraltrap.class", requiredClass);
        damagePerThreshold = cfg.getDouble("abilities.neuraltrap.damage-per-threshold", damagePerThreshold);
        blocksPerThreshold = cfg.getDouble("abilities.neuraltrap.blocks-per-threshold", blocksPerThreshold);
        tickInterval = cfg.getInt("abilities.neuraltrap.tick-interval", tickInterval);
        durationTicks = cfg.getInt("abilities.neuraltrap.duration-ticks", durationTicks);
        range = cfg.getDouble("abilities.neuraltrap.range", range);
        arcHeight = cfg.getDouble("abilities.neuraltrap.arc-height", arcHeight);
    }
}

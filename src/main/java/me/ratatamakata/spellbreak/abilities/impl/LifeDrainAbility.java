package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class LifeDrainAbility implements Ability {
    private int cooldown = 12;
    private int manaCost = 30;
    private String requiredClass = "necromancer"; // Lowercase to match config
    private double drainAmount = 1.0;
    private boolean success = false;
    private double rayWidth = 2.0; // Wider ray
    private double range = 10.0;

    @Override
    public String getName() { return "LifeDrain"; }

    @Override
    public String getDescription() { return "Drain life from targeted entity"; }

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

    public void resetCasts(Player player) {
        player.removeMetadata("drainCasts", Spellbreak.getInstance());
    }

    @Override
    public void activate(Player player) {
        success = false;

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                rayWidth,
                entity -> entity instanceof LivingEntity
                        && !entity.equals(player)
                        && player.hasLineOfSight(entity)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity target = (LivingEntity) result.getHitEntity();
        if (target.isDead() || target.getHealth() <= 0) {
            return;
        }


        // Apply damage using ability damage handler
        Spellbreak.getInstance()
                .getAbilityDamage()
                .damage(target, drainAmount, player, this, null);

        // Spawn particles and healing animation
        spawnParticles(target.getLocation(), player.getLocation(), player);

        success = true;
    }

    private void spawnParticles(Location start, Location end, Player player) {
        World world = start.getWorld();
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = start.distance(end);

        start = start.clone().add(0, 1.5, 0);
        end = end.clone().add(0, 1.5, 0);

        int steps = (int) (distance * 4);
        double arcHeight = 2.5;

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
                    new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.5f)
            );

            path.add(point);
        }

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= path.size()) {
                    double healAmount = drainAmount/2; // Heal for half the drain amount
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    cancel();
                    return;
                }

                Location point = path.get(index);
                world.spawnParticle(Particle.DAMAGE_INDICATOR, point, 1, 0, 0, 0, 0);
                index++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        final World finalWorld = world;
        final Location finalEnd = end;
        Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
            for (int i = 0; i < 5; i++) {
                finalWorld.spawnParticle(
                        Particle.DUST,
                        finalEnd,
                        10,
                        0.3, 0.3, 0.3,
                        new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.5f)
                );
            }
        }, 2L);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format("§e%s §fwas drained of life by §c%s§f.", victimName, casterName);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.lifedrain.cooldown", cooldown);
        manaCost = cfg.getInt("abilities.lifedrain.mana-cost", manaCost);
        requiredClass = cfg.getString("abilities.lifedrain.class", requiredClass);
        drainAmount = cfg.getDouble("abilities.lifedrain.amount", drainAmount);
    }
}

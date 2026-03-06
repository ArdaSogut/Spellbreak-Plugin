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

import java.util.ArrayList;
import java.util.List;

public class LifeDrainAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 30;
    private String requiredClass = "necromancer";
    private double drainAmount = 1.0;
    private boolean success = false;
    private double rayWidth = 2.0;
    private double range = 10.0;

    @Override public String getName() { return "LifeDrain"; }
    @Override public String getDescription() { return "Drain life from targeted entity, healing yourself"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }
    @Override public boolean isSuccessful() { return success; }

    public void resetCasts(Player player) {
        player.removeMetadata("drainCasts", Spellbreak.getInstance());
    }

    @Override
    public void activate(Player player) {
        success = false;

        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(player.getUniqueId(),
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
                        getName());

        double scaledDrain = drainAmount * sl.getDamageMultiplier();
        double scaledRange = range * sl.getRangeMultiplier();

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                scaledRange,
                rayWidth,
                entity -> entity instanceof LivingEntity
                        && !entity.equals(player)
                        && player.hasLineOfSight(entity)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity)) return;

        LivingEntity target = (LivingEntity) result.getHitEntity();
        if (target.isDead() || target.getHealth() <= 0) return;

        Spellbreak.getInstance().getAbilityDamage().damage(target, scaledDrain, player, this, null);

        spawnParticles(target.getLocation(), player.getLocation(), player, scaledDrain, sl);
        success = true;
    }

    private void spawnParticles(Location start, Location end, Player player, double drainAmt, SpellLevel sl) {
        World world = start.getWorld();
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = start.distance(end);

        start = start.clone().add(0, 1.5, 0);
        end = end.clone().add(0, 1.5, 0);

        int steps = (int) (distance * 4);
        double arcHeight = 2.5;

        List<Location> path = new ArrayList<>();

        // Level 3+: brighter crimson beam
        Color beamColor = (sl.getLevel() >= 3)
                ? Color.fromRGB(220, 0, 0)
                : Color.fromRGB(139, 0, 0);
        float particleSize = (sl.getLevel() >= 3) ? 3.2f : 2.5f;

        for (int i = 0; i <= steps; i++) {
            double ratio = (double) i / steps;
            Location point = start.clone().add(direction.clone().multiply(ratio));
            double x = ratio * 2 - 1;
            double yOffset = arcHeight * (1 - x * x);
            point.add(0, yOffset, 0);

            world.spawnParticle(Particle.DUST, point, 3,
                    0.15, 0.15, 0.15,
                    new Particle.DustOptions(beamColor, particleSize));

            // Level 5: extra soul fire along beam
            if (sl.getLevel() >= 5 && i % 4 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.05, 0.05, 0.05, 0.02);
            }

            path.add(point);
        }

        final Location finalEnd = end.clone();
        final World finalWorld = world;

        new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                if (index >= path.size()) {
                    double healAmount = drainAmt / 2;
                    // Level 3+: heal 60% instead of 50%
                    if (sl.getLevel() >= 3) healAmount = drainAmt * 0.6;
                    // Level 5: heal 80%
                    if (sl.getLevel() >= 5) healAmount = drainAmt * 0.8;
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f,
                            1.3f + (sl.getLevel() * 0.1f));
                    cancel();
                    return;
                }
                Location point = path.get(index);
                world.spawnParticle(Particle.DAMAGE_INDICATOR, point, 1, 0, 0, 0, 0);
                index++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
            int burstCount = 5 + sl.getLevel() * 2;
            for (int i = 0; i < burstCount; i++) {
                finalWorld.spawnParticle(Particle.DUST, finalEnd, 10,
                        0.3, 0.3, 0.3,
                        new Particle.DustOptions(beamColor, particleSize));
            }
            // Level 5: soul fire orb on arrival
            if (sl.getLevel() >= 5) {
                finalWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, finalEnd, 15, 0.4, 0.4, 0.4, 0.08);
                finalWorld.playSound(finalEnd, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 1.4f);
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

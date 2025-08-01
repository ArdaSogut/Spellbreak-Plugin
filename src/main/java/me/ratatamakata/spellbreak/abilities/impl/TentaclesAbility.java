package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.listeners.StunHandler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TentaclesAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 35;
    private String requiredClass = "necromancer";
    private double maxRange = 25.0;
    private double speed = 0.6;
    private int stunDuration = 25;
    private double damage = 2.0;

    private Color tendrilColor = Color.fromRGB(40, 0, 50);
    private Color tentacleColor = Color.fromRGB(80, 0, 100);
    private double particleSize = 2.4;
    private double tentacleHeight = 2;

    private final StunHandler stunHandler;

    public TentaclesAbility(StunHandler stunHandler) {
        this.stunHandler = stunHandler;
    }

    @Override public String getName() { return "Tentacles"; }
    @Override public String getDescription() { return "Control dark tendrils that trap enemies in writhing tentacles"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) { return false; }
    @Override public boolean isSuccessful() { return true; }

    @Override
    public void activate(Player player) {
        Location start = findGroundPosition(player.getLocation());
        if (start == null) return;
        start.add(0, 0.5, 0);

        new BukkitRunnable() {
            double distance = 0;
            Location currentPos = start.clone();
            boolean hasHit = false;
            Vector direction = player.getLocation().getDirection().setY(0).normalize();

            @Override
            public void run() {
                if (hasHit || distance >= maxRange || player.isDead()) {
                    cancel();
                    return;
                }

                direction = player.getLocation().getDirection().setY(0).normalize();

                RayTraceResult entityRay = player.getWorld().rayTraceEntities(
                        currentPos,
                        direction,
                        speed,
                        1.5,
                        e -> e instanceof LivingEntity && !e.equals(player)
                );

                if (entityRay != null && entityRay.getHitEntity() instanceof LivingEntity) {
                    LivingEntity target = (LivingEntity) entityRay.getHitEntity();
                    Player caster = player;
                    eruptTentacles(target.getLocation(), caster);
                    stunHandler.stun(target, stunDuration);
                    hasHit = true;
                    cancel();

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            stunHandler.removeStun(target);
                        }
                    }.runTaskLater(Spellbreak.getInstance(), stunDuration);
                    return;
                }

                Vector movement = direction.clone().multiply(speed);
                currentPos.add(movement);
                distance += speed;

                createTendrilEffect(currentPos.clone());
                for (int i = 0; i < 3; i++) {
                    createTendrilEffect(currentPos.clone().add(
                            (Math.random() - 0.5) * 0.5,
                            Math.random() * 0.7,
                            (Math.random() - 0.5) * 0.5
                    ));
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private Location findGroundPosition(Location loc) {
        for (int i = 0; i < 15; i++) {
            Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (block.getType().isSolid()) {
                return block.getLocation().add(0.5, 0.05, 0.5);
            }
        }
        return loc.clone().subtract(0, 1, 0);
    }

    private void createTendrilEffect(Location loc) {
        loc.getWorld().spawnParticle(Particle.DUST, loc, 2,
                new Particle.DustOptions(tendrilColor, (float) particleSize));
    }

    private void eruptTentacles(Location center, Player caster) {
        int tentacleCount = 6;
        List<TentacleWave> tentacleWaves = new ArrayList<>();
        double baseRadius = 2.0;
        Random random = new Random();

        Location groundCenter = findGroundPosition(center);
        groundCenter.add(0, 0.1, 0);

        Particle.DustOptions spikeDustOptions = new Particle.DustOptions(Color.fromRGB(240, 240, 240), 0.7f);
        for (int i = 0; i < tentacleCount; i++) {
            double angle = (2 * Math.PI / tentacleCount) * i;
            tentacleWaves.add(new TentacleWave(
                    angle,
                    baseRadius,
                    0.5 + random.nextDouble() * 0.2,
                    0.5 + random.nextDouble() * 0.2,
                    random.nextDouble() * Math.PI
            ));
        }

        new BukkitRunnable() {
            double height = 0;
            final double maxHeight = 3.0;
            final double convergeFactor = 0.85;
            boolean damageApplied = false;

            @Override
            public void run() {
                if (height >= maxHeight) {
                    if (!damageApplied) {
                        applyTrapDamage(groundCenter, caster);
                        damageApplied = true;
                    }
                    cancel();
                    return;
                }

                height += 0.08;
                for (TentacleWave wave : tentacleWaves) {
                    double currentRadius = wave.radius * (1 - (height / maxHeight) * convergeFactor);
                    double verticalOffset = Math.sin(height * wave.frequency + wave.phase) * wave.amplitude;

                    Location loc = groundCenter.clone().add(
                            Math.cos(wave.angle) * currentRadius,
                            height + verticalOffset,
                            Math.sin(wave.angle) * currentRadius
                    );
                    loc.add(
                            -Math.cos(wave.angle) * 0.2 * height,
                            0,
                            -Math.sin(wave.angle) * 0.2 * height
                    );

                    float progress = (float) (height / maxHeight);
                    Color color = blendColor(tentacleColor, Color.BLACK, progress);
                    float size = 4.0f - (2.5f * progress);

                    if (height < 0.5) {
                        loc.getWorld().spawnParticle(Particle.SQUID_INK,
                                loc.clone().add(0, 0.1, 0), 3, 0.2, 0.1, 0.2, 0.02);
                    }

                    loc.getWorld().spawnParticle(Particle.DUST,
                            loc,
                            4,
                            new Particle.DustOptions(color, size)
                    );

                    if (height > 0.3 && height < maxHeight * 0.8) {
                        Vector perp = new Vector(-Math.sin(wave.angle), 0, Math.cos(wave.angle)).normalize();
                        double spikeDist = 0.3 + (size / 10.0);
                        Location s1 = loc.clone().add(perp.clone().multiply(spikeDist));
                        Location s2 = loc.clone().subtract(perp.clone().multiply(spikeDist));
                        double yAdj = verticalOffset * 0.3;
                        s1.add(0, yAdj, 0);
                        s2.add(0, yAdj, 0);
                        if (random.nextInt(3) == 0) {
                            loc.getWorld().spawnParticle(Particle.DUST, s1, 1, 0, 0, 0, 0, spikeDustOptions);
                            loc.getWorld().spawnParticle(Particle.DUST, s2, 1, 0, 0, 0, 0, spikeDustOptions);
                        }
                    }
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void applyTrapDamage(Location center, Player caster) {
        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1f, 0.7f);

        // Get nearby entities and filter for LivingEntity
        for (Entity entity : center.getWorld().getNearbyEntities(center, 2.5, 2.5, 2.5)) {
            if (entity instanceof LivingEntity && !entity.equals(caster)) {
                LivingEntity livingEntity = (LivingEntity) entity;
                if (livingEntity.getLocation().distanceSquared(center) <= 6.25) { // 2.5^2
                    Spellbreak.getInstance().getAbilityDamage().damage(
                            livingEntity,
                            damage,
                            caster,
                            TentaclesAbility.this,
                            "tentacle_snap"
                    );
                    livingEntity.getWorld().spawnParticle(
                            Particle.DAMAGE_INDICATOR,
                            livingEntity.getLocation().add(0, 1, 0),
                            8
                    );
                }
            }
        }
    }

    private Color blendColor(Color from, Color to, float progress) {
        int r = Math.max(0, Math.min(255, (int) (from.getRed() + (to.getRed() - from.getRed()) * progress)));
        int g = Math.max(0, Math.min(255, (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * progress)));
        int b = Math.max(0, Math.min(255, (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * progress)));
        return Color.fromRGB(r, g, b);
    }

    private static class TentacleWave {
        final double angle, radius, amplitude, frequency, phase;
        TentacleWave(double a, double r, double amp, double freq, double ph) {
            angle = a; radius = r; amplitude = amp; frequency = freq; phase = ph;
        }
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.tentacles.cooldown", cooldown);
        manaCost = cfg.getInt("abilities.tentacles.mana-cost", manaCost);
        maxRange = cfg.getDouble("abilities.tentacles.range", maxRange);
        stunDuration = cfg.getInt("abilities.tentacles.stun-duration", stunDuration);
        tentacleHeight = cfg.getDouble("abilities.tentacles.tentacle-height", tentacleHeight);
        damage = cfg.getDouble("abilities.tentacles.damage", damage);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        if ("tentacle_snap".equals(subAbilityName)) {
            return String.format("§4%s §6was crushed by §c%s's §deldritch tentacles",
                    victimName, casterName);
        }
        return String.format("§4%s §6was consumed by §c%s's §dwrithing tentacles",
                victimName, casterName);
    }
}

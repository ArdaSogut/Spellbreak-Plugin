package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class EmberstormAbility implements Ability {
    // Configuration values
    private int cooldown = 12;
    private int manaCost = 40;
    private String requiredClass = "elementalist";
    private double radius = 5.0;
    private int duration = 5;
    private double damage = 1.0;
    private int burnDuration = 20;
    private int chargeTime = 20;
    private int hitCooldown = 25;

    // Charging tracking
    public static final Map<UUID, Integer> chargingPlayers = new HashMap<>();

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

    public double getAdjustedRadius(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return radius * lvl.getRangeMultiplier();
    }

    public int getAdjustedDuration(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return (int) (duration * lvl.getDurationMultiplier());
    }

    @Override
    public String getName() { return "Emberstorm"; }

    @Override
    public String getDescription() { return "Summon a swirling storm of fire around you"; }

    @Override
    public int getCooldown() { return cooldown; }

    @Override
    public int getManaCost() { return manaCost; }

    @Override
    public String getRequiredClass() { return requiredClass; }

    public int getChargeTime() { return chargeTime; }

    @Override
    public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.8f);

        // Seviye bazlı değerler
        double adjustedRadius = getAdjustedRadius(player);
        double adjustedDamage = getAdjustedDamage(player);
        int adjustedDuration = getAdjustedDuration(player);

        // Show visual radius indicator when ability activates
        Location center = player.getLocation();
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 32) {
            double x = Math.cos(i) * adjustedRadius;
            double z = Math.sin(i) * adjustedRadius;
            center.getWorld().spawnParticle(
                    Particle.FLAME,
                    center.clone().add(x, 0.1, z),
                    1, 0, 0, 0, 0
            );

            // Optional second layer for visibility
            center.getWorld().spawnParticle(
                    Particle.FLAME,
                    center.clone().add(x, 0.2, z),
                    1, 0, 0, 0, 0
            );
        }

        new BukkitRunnable() {
            int ticks = 0;
            final Map<UUID, Long> lastHits = new HashMap<>();
            final Random rand = new Random();

            // Effect pattern state
            private final List<Vector> embers = new ArrayList<>();
            private final List<Vector> emberVelocities = new ArrayList<>();
            private final List<Double> spiralAngles = new ArrayList<>();
            private boolean initialized = false;

            @Override
            public void run() {
                if (!initialized) {
                    initializeEffectPatterns();
                    initialized = true;
                }

                if (ticks++ >= adjustedDuration * 20 || !player.isValid()) {
                    cancel();
                    return;
                }

                // Update and spawn particle effects
                updateEffectPatterns();
                spawnEffectParticles(player.getLocation(), adjustedRadius);

                // Damage logic
                affectEntities(player, adjustedRadius, adjustedDamage);
            }

            private void initializeEffectPatterns() {
                // Create ember particles with trajectories
                for (int i = 0; i < 15; i++) {
                    // Random position within the sphere
                    embers.add(generateRandomSphereVector(radius * 0.8));

                    // Give each ember a random velocity
                    Vector velocity = new Vector(
                            rand.nextDouble() * 0.1 - 0.05,
                            rand.nextDouble() * 0.1 - 0.02, // Slight upward tendency
                            rand.nextDouble() * 0.1 - 0.05
                    );
                    emberVelocities.add(velocity);
                }

                // Create spiral patterns
                for (int i = 0; i < 3; i++) {
                    spiralAngles.add(rand.nextDouble() * Math.PI * 2);
                }
            }

            private void updateEffectPatterns() {
                // Update ember positions based on velocities
                for (int i = 0; i < embers.size(); i++) {
                    Vector ember = embers.get(i);
                    Vector velocity = emberVelocities.get(i);

                    // Move ember along its trajectory
                    ember.add(velocity);

                    // If ember leaves the radius, reset it to a new random position
                    if (ember.length() > radius * 0.9) {
                        if (rand.nextBoolean()) {
                            // Bounce by reflecting velocity (with dampening)
                            Vector normal = ember.clone().normalize();
                            Vector reflection = velocity.clone().subtract(normal.multiply(2 * velocity.dot(normal))).multiply(0.8);
                            velocity.setX(reflection.getX() + (rand.nextDouble() * 0.04 - 0.02));
                            velocity.setY(reflection.getY() + (rand.nextDouble() * 0.04 - 0.02));
                            velocity.setZ(reflection.getZ() + (rand.nextDouble() * 0.04 - 0.02));
                        } else {
                            // Reset position
                            embers.set(i, generateRandomSphereVector(radius * 0.5));
                            // And randomize velocity
                            emberVelocities.set(i, new Vector(
                                    rand.nextDouble() * 0.1 - 0.05,
                                    rand.nextDouble() * 0.1 - 0.02,
                                    rand.nextDouble() * 0.1 - 0.05
                            ));
                        }
                    }

                    // Occasionally change velocity slightly for unpredictability
                    if (rand.nextInt(20) == 0) {
                        Vector v = emberVelocities.get(i);
                        v.add(new Vector(
                                rand.nextDouble() * 0.06 - 0.03,
                                rand.nextDouble() * 0.06 - 0.03,
                                rand.nextDouble() * 0.06 - 0.03
                        ));
                        if (v.lengthSquared() > 0.02) {
                            v.normalize().multiply(0.14);
                        }
                    }
                }

                // Update spiral angles
                for (int i = 0; i < spiralAngles.size(); i++) {
                    double angle = spiralAngles.get(i);
                    angle += 0.1 + (i * 0.03);
                    spiralAngles.set(i, angle);
                }
            }

            private Vector generateRandomSphereVector(double r) {
                double u = rand.nextDouble();
                double v = rand.nextDouble();
                double theta = 2 * Math.PI * u;
                double phi = Math.acos(2 * v - 1);

                double x = r * Math.sin(phi) * Math.cos(theta);
                double y = r * Math.sin(phi) * Math.sin(theta);
                double z = r * Math.cos(phi);

                return new Vector(x, y, z);
            }

            private void spawnEffectParticles(Location center, double radius) {
                // 1. Main rotating fire spirals
                for (int arm = 0; arm < 4; arm++) {
                    double baseAngle = spiralAngles.get(arm % spiralAngles.size());
                    for (int i = 0; i < 6; i++) {
                        double spiralRadius = (radius * 0.7) * (1 - (i / 12.0));
                        double x = Math.cos(baseAngle + (i * 0.2)) * spiralRadius;
                        double z = Math.sin(baseAngle + (i * 0.2)) * spiralRadius;
                        double y = 1.0 + (i * 0.3);
                        Location loc = center.clone().add(x, y, z);
                        center.getWorld().spawnParticle(
                                Particle.FLAME,
                                loc,
                                2, 0.1, 0.1, 0.1, 0.01
                        );
                        center.getWorld().spawnParticle(
                                Particle.SMOKE,
                                loc.clone().subtract(0, 0.2, 0),
                                1, 0.05, 0.05, 0.05, 0.01
                        );
                    }
                }

                // 2. Random ember positions
                for (int i = 0; i < embers.size(); i++) {
                    Vector ember = embers.get(i);
                    Location loc = center.clone().add(ember);
                    double vx = rand.nextDouble() * 0.3 - 0.15;
                    double vy = rand.nextDouble() * 0.3 - 0.15;
                    double vz = rand.nextDouble() * 0.3 - 0.15;
                    center.getWorld().spawnParticle(
                            Particle.FLAME,
                            loc,
                            1, vx, vy, vz, 0.2
                    );
                    center.getWorld().spawnParticle(
                            Particle.SMOKE,
                            loc.clone().add(0, 0.1, 0),
                            1, 0.02, 0.02, 0.02, 0.01
                    );
                }

                // 3. Ground fire ring that pulses
                if (ticks % 5 == 0) {
                    double pulseRadius = radius * (0.8 + Math.sin(ticks * 0.1) * 0.2);
                    for (double i = 0; i < Math.PI * 2; i += Math.PI / 16) {
                        double x = Math.cos(i) * pulseRadius;
                        double z = Math.sin(i) * pulseRadius;
                        Location loc = center.clone().add(x, 0.1, z);
                        center.getWorld().spawnParticle(
                                Particle.FLAME,
                                loc,
                                1, 0.05, 0.05, 0.05, 0.01
                        );
                        center.getWorld().spawnParticle(
                                Particle.SMOKE,
                                loc.clone().add(0, 0.2, 0),
                                1, 0.03, 0.03, 0.03, 0.01
                        );
                    }
                }

                // 4. Occasional fire bursts
                if (rand.nextInt(8) == 0) {
                    double burstAngle = rand.nextDouble() * Math.PI * 2;
                    double burstDistance = rand.nextDouble() * radius * 0.8;
                    double burstX = Math.cos(burstAngle) * burstDistance;
                    double burstZ = Math.sin(burstAngle) * burstDistance;
                    double burstY = rand.nextDouble() * 2.5;
                    Location burstLoc = center.clone().add(burstX, burstY, burstZ);
                    center.getWorld().spawnParticle(
                            Particle.FLAME,
                            burstLoc,
                            8, 0.3, 0.3, 0.3, 0.05
                    );
                    center.getWorld().spawnParticle(
                            Particle.LAVA,
                            burstLoc,
                            3, 0.2, 0.2, 0.2, 0.02
                    );
                    center.getWorld().spawnParticle(
                            Particle.SMOKE,
                            burstLoc.clone().add(0, 0.5, 0),
                            4, 0.1, 0.1, 0.1, 0.02
                    );
                }

                // 5. Rising ember columns
                if (rand.nextInt(4) == 0) {
                    double emberAngle = rand.nextDouble() * Math.PI * 2;
                    double emberDistance = rand.nextDouble() * radius * 0.7;
                    double emberX = Math.cos(emberAngle) * emberDistance;
                    double emberZ = Math.sin(emberAngle) * emberDistance;
                    for (double h = 0; h < 3.0; h += 0.4) {
                        Location loc = center.clone().add(emberX, h, emberZ);
                        center.getWorld().spawnParticle(
                                Particle.FLAME,
                                loc,
                                1, 0.05, 0.05, 0.05, 0.01
                        );
                        center.getWorld().spawnParticle(
                                Particle.SMOKE,
                                loc.clone().add(0, 0.1, 0),
                                1, 0.03, 0.03, 0.03, 0.01
                        );
                    }
                }
            }

            private void affectEntities(Player caster, double radius, double damage) {
                Ability thisAbility = Spellbreak.getInstance().getAbilityManager().getAbilityByName("Emberstorm");

                for (LivingEntity entity : caster.getWorld().getEntitiesByClass(LivingEntity.class)) {
                    if (entity.equals(caster)) continue;
                    double distance = entity.getLocation().distance(caster.getLocation());
                    if (distance > radius) continue;
                    UUID entityId = entity.getUniqueId();
                    long lastHit = lastHits.getOrDefault(entityId, 0L);
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastHit > hitCooldown * 50) {
                        entity.getWorld().spawnParticle(
                                Particle.FLAME,
                                entity.getLocation().add(0, 1, 0),
                                5, 0.1, 0.1, 0.1, 0.1
                        );
                        Spellbreak.getInstance().getAbilityDamage().damage(
                                entity, damage, caster, thisAbility, "Emberstorm"
                        );
                        entity.setFireTicks(burnDuration);
                        entity.getWorld().playSound(
                                entity.getLocation(),
                                Sound.ENTITY_GENERIC_BURN,
                                0.5f,
                                1.2f
                        );
                        lastHits.put(entityId, currentTime);
                    }
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
    }

    @Override
    public boolean isSuccessful() { return true; }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.emberstorm.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        radius = cfg.getDouble(base + "radius", radius);
        duration = cfg.getInt(base + "duration", duration);
        damage = cfg.getDouble(base + "damage", damage);
        burnDuration = cfg.getInt(base + "burn-duration", burnDuration);
        chargeTime = cfg.getInt(base + "charge-time", chargeTime);
        hitCooldown = cfg.getInt(base + "hit-cooldown", hitCooldown);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§c%s §4was charred by §c%s§4's Emberstorm!", victim, caster);
    }
}


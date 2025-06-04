// LightCageAbility.java
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.listeners.StunHandler;
// import me.ratatamakata.spellbreak.util.ProjectileUtil; // Removed import
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
// import org.bukkit.util.BoundingBox; // No longer needed directly in runnable
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.function.Predicate; // Added for rayTraceEntities filter
import java.util.stream.Collectors;

public class LightCageAbility implements Ability {
    private int cooldown = 12;
    private int manaCost = 35;
    private String requiredClass = "lightbringer";
    private double projectileSpeed = 1.0; // Speed units per tick
    private double radius = 1.1; // Cage radius and projectile collision radius
    private double height = 3.0; // Cage height
    private int durationTicks = 60; // Cage duration
    private double range = 20.0; // Max projectile travel distance
    private double initialDamage = 5.0;
    private double tickDamage = 2.0;
    private final Particle.DustOptions cageParticleOptions = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.0f);
    private boolean successfulActivation = false;

    @Override
    public String getName() {
        return "LightCage";
    }

    @Override
    public String getDescription() {
        return "Shoots a cage of light that stuns and damages foes. Travels a fixed range.";
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
    public boolean isTriggerAction(Action action) {
        return false; // Activation handled by listener
    }

    @Override
    public void activate(Player player) {
        this.successfulActivation = false;
        Location spawnLoc = player.getEyeLocation().add(0, -0.2, 0); // Slightly lower origin
        Vector direction = player.getEyeLocation().getDirection().normalize();
        
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.FLASH, spawnLoc, 3, 0.1, 0.1, 0.1, 0.05); // Initial flash

        // Define the entity filter predicate here to pass to the runnable
        Predicate<Entity> entityFilter = entity -> 
            entity instanceof LivingEntity && 
            !entity.getUniqueId().equals(player.getUniqueId()) && 
            !entity.isDead() && 
            !(entity instanceof org.bukkit.entity.ArmorStand);

        new LightCageProjectileRunnable(player, spawnLoc, direction, entityFilter).runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
        // isSuccessful will be set by the runnable upon successful cage creation
    }

    public void createCage(Location center, Player caster) {
        this.successfulActivation = true; // Mark successful activation when cage is created
        World world = center.getWorld();
        StunHandler stunHandler = Spellbreak.getInstance().getStunHandler();

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        world.spawnParticle(Particle.FLASH, center, 5, radius * 0.7, height * 0.3, radius * 0.7, 0.1);


        Collection<LivingEntity> targets = world.getNearbyEntities(center, radius, height / 2, radius,
                        e -> e instanceof LivingEntity && e != caster && !(e instanceof org.bukkit.entity.ArmorStand) && !e.isDead())
                .stream()
                .map(e -> (LivingEntity) e)
                .collect(Collectors.toList());


        for (LivingEntity target : targets) {
            Spellbreak.getInstance().getAbilityDamage().damage(target, initialDamage, caster, this, null);
            stunHandler.stun(target, durationTicks);
            target.setVelocity(new Vector(0, 0.3, 0));
            world.spawnParticle(Particle.CRIT, target.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.1); // Linter error here if MAGIC_CRIT invalid
        }

        new BukkitRunnable() {
            int ticks = 0;
            final Location cageCenter = center.clone();

            @Override
            public void run() {
                if (ticks >= durationTicks) {
                    world.playSound(cageCenter, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
                    spawnStaticCageParticles(cageCenter, true);
                    cancel();
                    return;
                }

                spawnStaticCageParticles(cageCenter, false);

                if (ticks > 0 && ticks % 20 == 0) {
                    for (LivingEntity target : world.getNearbyEntities(cageCenter, radius, height / 2, radius,
                                    e -> e instanceof LivingEntity && e != caster && stunHandler.isStunned((LivingEntity)e) && !(e instanceof org.bukkit.entity.ArmorStand))
                            .stream()
                            .map(e -> (LivingEntity) e)
                            .collect(Collectors.toList())) {
                        Spellbreak.getInstance().getAbilityDamage().damage(target, tickDamage, caster, null, null);
                        world.spawnParticle(Particle.END_ROD, target.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
    }

    private void spawnStaticCageParticles(Location center, boolean isBreakingEffect) {
        World world = center.getWorld();
        double angleStep = Math.PI / (isBreakingEffect ? 6 : 10);
        double verticalStep = 0.4;

        for (double yOffset = -height / 2; yOffset <= height / 2; yOffset += verticalStep) {
            for (double angle = 0; angle < Math.PI * 2; angle += angleStep) {
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = center.clone().add(x, yOffset, z);
                if (isBreakingEffect) {
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.GRAY, 1.2f));
                    if (Math.random() < 0.2) world.spawnParticle(Particle.POOF, particleLoc, 1, 0,0,0,0.01);
                } else {
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0,0,0,0, cageParticleOptions);
                }
            }
        }
    }
    
    private class LightCageProjectileRunnable extends BukkitRunnable {
        private final Player caster;
        private final World world;
        private Location currentLocation;
        private final Vector direction;
        private final double speedPerTick;
        private final double maxTravelDistance;
        private double distanceTraveled;
        private final double collisionRadius; // For entity checks primarily
        // private final double collisionHeight; // Less critical with rayTraceEntities
        private final Predicate<Entity> entityFilter; // Added field for the filter

        public LightCageProjectileRunnable(Player caster, Location origin, Vector direction, Predicate<Entity> filter) {
            this.caster = caster;
            this.world = origin.getWorld();
            this.currentLocation = origin.clone();
            this.direction = direction.clone(); 
            this.speedPerTick = LightCageAbility.this.projectileSpeed;
            this.maxTravelDistance = LightCageAbility.this.range;
            this.distanceTraveled = 0;
            this.collisionRadius = LightCageAbility.this.radius;
            // this.collisionHeight = LightCageAbility.this.height;
            this.entityFilter = filter; // Store the filter
        }

        @Override
        public void run() {
            if (!caster.isOnline() || caster.isDead() || distanceTraveled >= maxTravelDistance) {
                if (distanceTraveled >= maxTravelDistance && caster.isOnline()) { // Fizzle at max range
                    world.playSound(currentLocation, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f);
                    world.spawnParticle(Particle.POOF, currentLocation, 15, collisionRadius * 0.5, LightCageAbility.this.height * 0.25, collisionRadius * 0.5, 0.03);
                }
                this.cancel();
                return;
            }

            spawnProjectileRingParticles(currentLocation);

            // Block Collision Check
            RayTraceResult blockHit = world.rayTraceBlocks(currentLocation, direction, speedPerTick, FluidCollisionMode.NEVER, true);
            if (blockHit != null && blockHit.getHitBlock() != null && blockHit.getHitBlock().getType().isSolid()) {
                // Don't create cage, just play sound and spawn fizzle particles
                world.playSound(blockHit.getHitPosition().toLocation(world), Sound.BLOCK_STONE_HIT, 0.8f, 0.8f); 
                world.spawnParticle(Particle.SMOKE, blockHit.getHitPosition().toLocation(world), 10, 0.1, 0.1, 0.1, 0.02); // Changed to SMOKE
                // LightCageAbility.this.successfulActivation = false; // Ensure it's not marked successful
                this.cancel();
                return;
            }

            // Entity Collision Check using rayTraceEntities
            RayTraceResult entityHitResult = world.rayTraceEntities(currentLocation, direction, speedPerTick, collisionRadius, this.entityFilter);
            if (entityHitResult != null && entityHitResult.getHitEntity() instanceof LivingEntity) {
                LivingEntity hitEntity = (LivingEntity) entityHitResult.getHitEntity();
                LightCageAbility.this.createCage(hitEntity.getLocation(), caster); 
                world.playSound(hitEntity.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
                this.cancel();
                return;
            }
            
            currentLocation.add(direction.clone().multiply(speedPerTick)); 
            distanceTraveled += speedPerTick;
        }
        
        private void spawnProjectileRingParticles(Location center) {
            // Re-implement the ring particle logic
            double angleStep = Math.PI / 10; // 20 points for the ring
            for (double angle = 0; angle < Math.PI * 2; angle += angleStep) {
                double x = Math.cos(angle) * LightCageAbility.this.radius; // Use ability's radius for the ring size
                double z = Math.sin(angle) * LightCageAbility.this.radius;
                world.spawnParticle(Particle.DUST, center.clone().add(x, 0, z), 1, 0, 0, 0, 0, LightCageAbility.this.cageParticleOptions);
            }
        }
    }


    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.lightcage.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        projectileSpeed = cfg.getDouble(base + "projectile-speed", projectileSpeed);
        radius = cfg.getDouble(base + "radius", radius);
        height = cfg.getDouble(base + "height", height);
        durationTicks = cfg.getInt(base + "duration", durationTicks);
        range = cfg.getDouble(base + "range", range);
        initialDamage = cfg.getDouble(base + "damage-initial", initialDamage);
        tickDamage = cfg.getDouble(base + "damage-tick", tickDamage);
        requiredClass = cfg.getString(base + "required-class", requiredClass);
    }

    @Override
    public boolean isSuccessful() {
        boolean S = this.successfulActivation;
        this.successfulActivation = false;
        return S;
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format("§e%s §6was ensnared and purified by §e%s§6's Light Cage.", victimName, casterName);
    }
}
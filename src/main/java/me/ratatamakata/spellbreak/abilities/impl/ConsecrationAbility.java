package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.stream.Collectors;

public class ConsecrationAbility implements Ability {

    private int cooldown = 13; // Adjusted default cooldown
    private int manaCost = 60; // Adjusted default mana cost
    private String requiredClass = "lightbringer";
    private double damageAmount = 3.0; // Damage on slam
    private double liftRadius = 5.0;
    private double liftHeight = 2.5; // Max height entities are lifted
    private int liftDurationTicks = 30; // How long they stay up before slam
    private double initialGroundParticleYOffset = 0.5; // Y-offset for initial ground particles
    private float liftUpwardVelocity = 0.9f; // Increased lift velocity

    // Holy dust particle options
    private final Particle.DustOptions holyDustOptions = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.6f); // Gold/Yellow
    private final Particle.DustOptions holyDustOptions2 = new Particle.DustOptions(Color.fromRGB(255, 223, 0), 1.0f); // Gold/Yellow
    private boolean successfulActivation = false; // Added flag

    @Override
    public String getName() {
        return "Consecration";
    }

    @Override
    public String getDescription() {
        return "Damages nearby enemies, lifts them, then slams them down.";
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
        return false; // Activated by sneak
    }

    @Override
    public void activate(Player player) {
        final Ability ability = this; // Add this line
        this.successfulActivation = false; // Reset at the start of activation
        this.successfulActivation = true; // Ensure ability is considered successful for cooldown/mana purposes

        World world = player.getWorld();
        Location center = player.getLocation();

        world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.8f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);

        // Initial ground particles (starburst with outer circle)
        new BukkitRunnable() {
            final int numberOfRays = 8;
            final double particleSpacingOnRay = 0.6; // Increased spacing for less dense rays
            final double rayExpansionSpeed = 0.25; // How much each ray grows per tick
            final int effectDurationTicks = 20; // Total duration of the effect
            double currentRayLength = 0.5; // Initial length of rays
            int ticksRun = 0;
            final int circleParticleDensity = 32; // Slightly reduced density for outer circle

            public void run() {
                if (ticksRun >= effectDurationTicks) {
                    this.cancel();
                    return;
                }

                // Draw Starburst Rays
                for (int i = 0; i < numberOfRays; i++) {
                    double angle = (Math.PI * 2 / numberOfRays) * i;
                    for (double length = particleSpacingOnRay; length <= currentRayLength; length += particleSpacingOnRay) {
                        double x = Math.cos(angle) * length;
                        double z = Math.sin(angle) * length;
                        Location particleLoc = center.clone().add(x, initialGroundParticleYOffset, z);
                        world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, holyDustOptions);
                    }
                    if (ticksRun % 2 == 0 && currentRayLength > 0.1) { // Ensure ray has some length for the tip
                        double tipX = Math.cos(angle) * currentRayLength;
                        double tipZ = Math.sin(angle) * currentRayLength;
                        Location tipLoc = center.clone().add(tipX, initialGroundParticleYOffset + 0.1, tipZ);
                        world.spawnParticle(Particle.DUST, tipLoc, 1, 0,0,0,0, new Particle.DustOptions(Color.WHITE, 1.5f));
                    }
                }

                // Draw Outer Circle connecting the tips of the rays
                if (currentRayLength > 0.1) { // Ensure rays have some length before drawing circle
                    for (int i = 0; i < circleParticleDensity; i++) {
                        double angle = (Math.PI * 2 / circleParticleDensity) * i;
                        double x = Math.cos(angle) * currentRayLength;
                        double z = Math.sin(angle) * currentRayLength;
                        Location circleParticleLoc = center.clone().add(x, initialGroundParticleYOffset, z);
                        world.spawnParticle(Particle.DUST, circleParticleLoc, 1, 0, 0, 0, 0, holyDustOptions2);
                    }
                }

                currentRayLength += rayExpansionSpeed;
                ticksRun++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        List<LivingEntity> targets = world.getNearbyEntities(center, liftRadius, liftRadius, liftRadius)
                .stream()
                .filter(e -> e instanceof LivingEntity && !e.equals(player) && !e.isDead() && !(e instanceof org.bukkit.entity.ArmorStand))
                .map(e -> (LivingEntity) e)
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            Spellbreak.getInstance().getLogger().info("[Consecration DEBUG] No targets found for Consecration by " + player.getName() + " at " + center);
            player.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
            // successfulActivation is true, so cooldown will apply, but the rest of the target-specific logic is skipped.
            return;
        }

        Spellbreak.getInstance().getLogger().info("[Consecration DEBUG] " + player.getName() + " found " + targets.size() + " target(s) for Consecration.");
        // this.successfulActivation = true; // Already set earlier
        player.getWorld().playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);


        for (LivingEntity target : targets) {
            Spellbreak.getInstance().getAbilityDamage().damage(target, damageAmount/2, player, this, null); // Initial damage on lift
            // The old setLastAttacker call is removed as the listener handles it via the tag on caster

            final Location originalLocation = target.getLocation();
            final Vector toCenter = center.toVector().subtract(originalLocation.toVector()).normalize().multiply(0.2);

            new BukkitRunnable() {
                int ticks = 0;
                double currentY = originalLocation.getY();

                @Override
                public void run() {
                    if (ticks >= liftDurationTicks || !target.isValid() || target.isDead()) {
                        // Slam effect
                        if (target.isValid() && !target.isDead()) {
                            Location groundLocation = target.getLocation().clone();
                            // Find actual ground
                            while(!groundLocation.getBlock().getType().isSolid() && groundLocation.getY() > world.getMinHeight()){
                                groundLocation.subtract(0,0.1,0);
                            }
                            groundLocation.setY(groundLocation.getBlock().getY() + 1); // land on top of block

                            target.setVelocity(new Vector(0, -1.5, 0)); // Slam down
                            target.setFallDistance(0); // Reset fall distance to prevent vanilla fall damage


                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if(target.isOnGround() || target.getLocation().getY() <= groundLocation.getY() + 0.5){
                                        Spellbreak.getInstance().getAbilityDamage().damage(target, damageAmount/2, player, ConsecrationAbility.this, null);
                                        world.playSound(groundLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
                                        world.playSound(groundLocation, Sound.BLOCK_STONE_BREAK, 1.2f, 0.8f);
                                        // Slam particles (Holy Dust)
                                        world.spawnParticle(Particle.DUST, groundLocation.clone().add(0, 0.2, 0), 80, 0.7, 0.2, 0.7, 0.05, holyDustOptions);
                                        world.spawnParticle(Particle.DUST, groundLocation.clone().add(0, 0.5, 0), 50, 0.5, 0.5, 0.5, 0.02, holyDustOptions);
                                        this.cancel();
                                    } else if (!target.isValid() || target.isDead()){
                                        this.cancel();
                                    }
                                }
                            }.runTaskTimer(Spellbreak.getInstance(),0L,1L);
                        }
                        this.cancel();
                        return;
                    }

                    // Lift logic
                    if (currentY < originalLocation.getY() + liftHeight) {
                        target.setVelocity(new Vector(toCenter.getX(), liftUpwardVelocity, toCenter.getZ())); // Increased Y velocity, gentle pull to center
                        currentY += liftUpwardVelocity * 0.5; // Approximate rise based on velocity
                    } else {
                        target.setVelocity(new Vector(toCenter.getX()*0.5, 0.05, toCenter.getZ()*0.5)); // Hover with slight pull
                    }

                    // Swirling particles during lift (Holy Dust)
                    world.spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 5, 0.4, 0.5, 0.4, 0.02, holyDustOptions);
                    if(ticks % 3 == 0)
                         world.spawnParticle(Particle.DUST, target.getEyeLocation(), 3, 0.3, 0.3, 0.3, 0.01, new Particle.DustOptions(Color.WHITE, 0.8f));


                    ticks++;
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
        }
    }

    @Override
    public boolean isSuccessful() {
        boolean success = this.successfulActivation;
        // this.successfulActivation = false; // Optional: Reset after check, depending on listener logic
        return success;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.consecration.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        damageAmount = cfg.getDouble(base + "damage", damageAmount);
        liftRadius = cfg.getDouble(base + "lift-radius", liftRadius);
        liftHeight = cfg.getDouble(base + "lift-height", liftHeight);
        liftDurationTicks = cfg.getInt(base + "lift-duration", liftDurationTicks);
        requiredClass = cfg.getString(base + "required-class", requiredClass);
        initialGroundParticleYOffset = cfg.getDouble(base + "ground-particle-y-offset", initialGroundParticleYOffset);
        liftUpwardVelocity = (float) cfg.getDouble(base + "lift-upward-velocity", liftUpwardVelocity);

        // Load holy dust color from config if available
        Color dustColor = Color.fromRGB(
                cfg.getInt(base + "dust-color.r", 255),
                cfg.getInt(base + "dust-color.g", 223),
                cfg.getInt(base + "dust-color.b", 0)
        );
        float dustSize = (float) cfg.getDouble(base + "dust-size", 1.2);
        // holyDustOptions = new Particle.DustOptions(dustColor, dustSize); // Cannot reassign final field
        // For now, dust options remain as defined. Configurable dust would require removing 'final'.
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        if ("Slam".equals(subAbilityName)) {
            return String.format("§e%s §6was slammed down by §e%s§6's Consecration.", victimName, casterName);
        }
        return String.format("§e%s §6was lifted by §e%s§6's Consecration and didn't survive.", victimName, casterName);
    }
}
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SolarLanceAbility implements Ability {
    private int cooldown = 11;
    private int manaCost = 25;
    private String requiredClass = "starcaller";
    private double damage = 2.0;
    private double knockbackForce = 2.5;
    private double dashVelocity = 4.0; // Initial velocity multiplier
    private double velocityDecay = 0.95; // How much velocity decreases per tick
    private int dashDuration = 30; // Duration in ticks (3 seconds)
    private double hitRadius = 2.5; // Increased hit range
    private boolean allowSteering = true; // Allow player to steer during dash
    private double steeringPower = 0.15; // How much steering influence (0.0 = none, 1.0 = full)
    private int fireTickDuration = 40; // Fire ticks to apply (3 seconds)
    private double lanceHitRange = 5.0; // Additional forward reach of the lance

    private final Map<UUID, Set<UUID>> hitEntities = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeDashes = new HashMap<>();
    private final Map<UUID, Vector> fixedLanceDirections = new HashMap<>();

    @Override public String getName() { return "SolarLance"; }
    @Override public String getDescription() {
        return "Launch forward with a blazing solar lance, dealing damage and knockback to enemies in your path.";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public void activate(Player player) {
        // Cancel any existing dash for this player
        BukkitRunnable existingDash = activeDashes.get(player.getUniqueId());
        if (existingDash != null) {
            existingDash.cancel();
        }

        // Calculate dash direction (keep some Y component for natural movement)
        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY(), 0.1)); // Minimum upward component
        direction.normalize();

        // Store the initial lance direction for hit detection - this stays fixed
        Vector lanceDirection = player.getLocation().getDirection();
        lanceDirection.setY(0); // Keep lance horizontal
        lanceDirection.normalize();
        fixedLanceDirections.put(player.getUniqueId(), lanceDirection);

        // Store hit entities for this cast to prevent multiple hits
        hitEntities.put(player.getUniqueId(), new HashSet<>());

        // Give initial velocity boost
        Vector initialVelocity = direction.clone().multiply(dashVelocity);
        player.setVelocity(initialVelocity);

        // Start the dash effects and monitoring
        startSolarLanceDash(player, direction);

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);
    }

    private void startSolarLanceDash(Player player, Vector initialDirection) {
        BukkitRunnable dashTask = new BukkitRunnable() {
            private int ticksElapsed = 0;
            private Vector currentDirection = initialDirection.clone();

            @Override
            public void run() {
                if (!player.isOnline() || ticksElapsed >= dashDuration) {
                    cleanup(player);
                    this.cancel();
                    return;
                }

                // Update direction based on player's current look direction if steering allowed
                if (allowSteering && steeringPower > 0) {
                    Vector playerDirection = player.getLocation().getDirection();
                    playerDirection.setY(Math.max(playerDirection.getY(), 0.1));
                    playerDirection.normalize();

                    // Blend current direction with player's look direction based on steering power
                    currentDirection = currentDirection.clone()
                            .multiply(1.0 - steeringPower)
                            .add(playerDirection.multiply(steeringPower))
                            .normalize();
                }

                // Maintain forward momentum with gradual decay
                Vector currentVelocity = player.getVelocity();
                Vector horizontalDirection = new Vector(currentDirection.getX(), 0, currentDirection.getZ()).normalize();

                // Apply continued forward force (decreasing over time)
                double forceMultiplier = Math.max(0.2, 1.0 - (ticksElapsed / (double) dashDuration));
                Vector forwardForce = horizontalDirection.multiply(dashVelocity * forceMultiplier * 0.5);

                // Preserve Y velocity for natural falling/jumping
                Vector newVelocity = new Vector(
                        forwardForce.getX(),
                        currentVelocity.getY(),
                        forwardForce.getZ()
                );

                player.setVelocity(newVelocity);

                // Get the lance direction for visuals - should follow horizontal look direction
                Vector lanceDirection = player.getLocation().getDirection();
                lanceDirection.setY(0); // Keep lance horizontal
                lanceDirection.normalize();

                // Spawn visual effects at player's current location
                Location playerLoc = player.getLocation();
                spawnSolarTrail(playerLoc, currentDirection);
                spawnSolarLance(playerLoc, lanceDirection); // Lance follows horizontal look

                // Check for entities to damage using fixed direction - if hit, end the dash
                if (checkForEnemies(player, playerLoc)) {
                    cleanup(player);
                    this.cancel();
                    return;
                }

                ticksElapsed++;
            }
        };

        activeDashes.put(player.getUniqueId(), dashTask);
        dashTask.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void cleanup(Player player) {
        hitEntities.remove(player.getUniqueId());
        activeDashes.remove(player.getUniqueId());
        fixedLanceDirections.remove(player.getUniqueId());

        // Optional: Add a small ground slam effect when dash ends
        if (player.isOnGround()) {
            Location groundLoc = player.getLocation();
            groundLoc.getWorld().spawnParticle(Particle.EXPLOSION, groundLoc, 3, 1, 0.1, 1, 0);
            groundLoc.getWorld().playSound(groundLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
        }
    }

    private void spawnSolarTrail(Location loc, Vector direction) {
        World world = loc.getWorld();
        if (world == null) return;

        // Reduced trail particles - only behind player
        Location trailLoc = loc.clone().subtract(direction.clone().multiply(1.0));

        // Fewer golden dust particles
        world.spawnParticle(Particle.DUST, trailLoc.add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f)); // Gold

        // Fewer flame particles
        world.spawnParticle(Particle.FLAME, trailLoc, 2, 0.1, 0.1, 0.1, 0.02);
    }

    private void spawnSolarLance(Location loc, Vector direction) {
        World world = loc.getWorld();
        if (world == null) return;

        // Get perpendicular vectors for lance construction
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        Vector up = new Vector(0, 1, 0);

        // Position lance at player's right arm (offset to the right and slightly forward)
        Location rightArmPos = loc.clone()
                .add(right.clone().multiply(0.6))  // Move to right side
                .add(0, 1.2, 0)                    // Chest/shoulder height
                .add(direction.clone().multiply(0.3)); // Slightly forward

        // Lance dimensions - longer but with original spearhead design
        double lanceLength = 4.5; // Keep the longer length
        double shaftWidth = 0.1; // Back to original thickness
        double spearheadLength = 1.0; // Reasonable spearhead length
        double spearheadBaseWidth = 0.4; // Back to original width for diamond shape
        double guardWidth = 0.6; // Back to original guard size

        // Position lance starting from right arm
        Location lanceStart = rightArmPos;

        // LANCE SHAFT - Back to original design
        for (double i = 0; i < lanceLength; i += 0.15) {
            Location shaftPoint = lanceStart.clone().add(direction.clone().multiply(i));

            // Create shaft thickness with multiple particles like original
            for (double offset = -shaftWidth; offset <= shaftWidth; offset += shaftWidth) {
                Location thickPoint = shaftPoint.clone().add(right.clone().multiply(offset));
                world.spawnParticle(Particle.DUST, thickPoint, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(139, 69, 19), 1.0f)); // Brown wood
            }
        }

        // SPEARHEAD - Back to original diamond shape design
        Location spearTip = lanceStart.clone().add(direction.clone().multiply(lanceLength));

        // Spearhead edges and center - original design
        for (double i = 0; i < spearheadLength; i += 0.1) {
            double widthAtPoint = spearheadBaseWidth * (1 - (i / spearheadLength)); // Linear taper

            Location basePoint = spearTip.clone().subtract(direction.clone().multiply(i));

            // Center line of spearhead
            world.spawnParticle(Particle.DUST, basePoint, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.3f)); // Gold

            // Spearhead edges
            for (double side = -1; side <= 1; side += 2) {
                Location edgePoint = basePoint.clone().add(right.clone().multiply(widthAtPoint * side));
                world.spawnParticle(Particle.DUST, edgePoint, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.2f)); // Gold

                // Fill in the spearhead
                for (double fill = 0; fill <= Math.abs(widthAtPoint * side); fill += 0.08) {
                    Location fillPoint = basePoint.clone().add(right.clone().multiply(fill * side));
                    world.spawnParticle(Particle.DUST, fillPoint, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 223, 0), 0.9f)); // Light gold
                }
            }
        }

        // CROSSGUARD - Back to original size
        Location guardCenter = spearTip.clone().subtract(direction.clone().multiply(spearheadLength * 0.3));
        for (double i = -guardWidth; i <= guardWidth; i += 0.1) {
            Location guardPoint = guardCenter.clone().add(right.clone().multiply(i));
            world.spawnParticle(Particle.DUST, guardPoint, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(192, 192, 192), 1.1f)); // Silver
        }

        // DECORATIVE ELEMENTS - Back to original design
        // Grip wrapping
        Location gripStart = lanceStart.clone().add(direction.clone().multiply(lanceLength * 0.2));
        for (double i = 0; i < 0.3; i += 0.08) {
            Location gripPoint = gripStart.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.DUST, gripPoint, 2, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(101, 67, 33), 1.0f)); // Dark brown leather
        }

        // Glowing runes along shaft
        for (double i = 0.5; i < lanceLength - 0.5; i += 0.8) {
            Location runePoint = lanceStart.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.DUST, runePoint, 3, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 69, 0), 1.4f)); // Red-orange glow
            world.spawnParticle(Particle.ENCHANT, runePoint, 2, 0.1, 0.1, 0.1, 0.1);
        }

        // Pommel at the back
        Location pommel = lanceStart.clone().subtract(direction.clone().multiply(0.2));
        world.spawnParticle(Particle.DUST, pommel, 4, 0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.3f)); // Gold pommel

        // Solar energy emanating from the lance
        for (int j = 0; j < 5; j++) {
            Vector randomOffset = new Vector(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3
            );
            Location energyPoint = spearTip.clone().add(randomOffset);
            world.spawnParticle(Particle.FLAME, energyPoint, 1, 0, 0, 0, 0.02);
        }
    }

    private boolean checkForEnemies(Player caster, Location loc) {
        Set<UUID> alreadyHit = hitEntities.get(caster.getUniqueId());
        if (alreadyHit == null) return false;

        boolean hitSomething = false;

        // Get the fixed lance direction for extended hit detection
        Vector lanceDirection = fixedLanceDirections.get(caster.getUniqueId());
        if (lanceDirection == null) lanceDirection = caster.getLocation().getDirection();

        // Check multiple points along the lance for hits (lance extends forward)
        for (double range = 0; range <= lanceHitRange; range += 0.5) {
            Location checkPoint = loc.clone().add(lanceDirection.clone().multiply(range));

            for (Entity entity : checkPoint.getWorld().getNearbyEntities(checkPoint, hitRadius, hitRadius, hitRadius)) {
                if (entity instanceof LivingEntity && entity != caster && !alreadyHit.contains(entity.getUniqueId())) {
                    LivingEntity target = (LivingEntity) entity;

                    // Add to hit list to prevent multiple hits
                    alreadyHit.add(target.getUniqueId());
                    hitSomething = true;

                    // Deal damage
                    Spellbreak.getInstance().getAbilityDamage().damage(
                            target, damage, caster, this, "SolarLance"
                    );

                    // Apply fire ticks
                    target.setFireTicks(fireTickDuration);

                    // Apply knockback
                    Vector knockback = target.getLocation().toVector().subtract(caster.getLocation().toVector());
                    knockback.setY(0.3); // Small upward component
                    knockback.normalize().multiply(knockbackForce);
                    target.setVelocity(knockback);

                    // Visual and sound effects
                    target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 5, 0.5, 0.5, 0.5, 0);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);

                }
            }
        }

        return hitSomething; // Return true if we hit something
    }

    @Override
    public String getDeathMessage(String victim, String caster, String unused) {
        return String.format("§6%s §ewas impaled by §6%s§e's blazing Solar Lance!", victim, caster);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String b = "abilities.solarlance.";
        cooldown = cfg.getInt(b + "cooldown", cooldown);
        manaCost = cfg.getInt(b + "mana-cost", manaCost);
        damage = cfg.getDouble(b + "damage", damage);
        knockbackForce = cfg.getDouble(b + "knockback-force", knockbackForce);
        dashVelocity = cfg.getDouble(b + "dash-velocity", dashVelocity);
        velocityDecay = cfg.getDouble(b + "velocity-decay", velocityDecay);
        dashDuration = cfg.getInt(b + "dash-duration", dashDuration);
        allowSteering = cfg.getBoolean(b + "allow-steering", allowSteering);
        steeringPower = cfg.getDouble(b + "steering-power", steeringPower);
        fireTickDuration = cfg.getInt(b + "fire-tick-duration", fireTickDuration);
        hitRadius = cfg.getDouble(b + "hit-radius", hitRadius);
        lanceHitRange = cfg.getDouble(b + "lance-hit-range", lanceHitRange);
        requiredClass = cfg.getString(b + "required-class", requiredClass);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }
}
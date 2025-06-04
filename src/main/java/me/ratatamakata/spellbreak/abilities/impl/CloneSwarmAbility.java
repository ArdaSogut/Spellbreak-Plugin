
package me.ratatamakata.spellbreak.abilities.impl;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class CloneSwarmAbility implements Ability {
    private int cooldown = 15;
    private int manaCost = 20;
    private String requiredClass = "mindshaper";
    private int cloneCount = 4;
    private int cloneDuration = 100; // ticks
    private double cloneMoveSpeed = 0.3;
    private double cloneJumpForce = 0.6;
    private double maxTargetDistance = 30.0;
    private double cloneCollisionDamage = 1.5;
    private double cloneCollisionRadius = 1.5;
    private double spawnRadius = 3.0; // NEW: radius to space clones

    private final Map<UUID, List<CloneData>> playerClones = new HashMap<>();

    @Override public String getName() { return "CloneSwarm"; }
    @Override public String getDescription() { return "Summon multiple clones that swarm towards your target"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) { return false; }
    @Override public boolean isSuccessful() { return false; }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return victim + " was swarmed by " + caster + "'s clones";
    }
    public int getAdjustedCooldown(Player player) {
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "CloneSwarm");
        return (int) (cooldown * spellLevel.getCooldownReduction());
    }

    public int getAdjustedManaCost(Player player) {
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "CloneSwarm");
        return (int) (manaCost * spellLevel.getManaCostReduction());
    }

    @Override
    public void activate(Player player) {
        // Get level-adjusted values
        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "CloneSwarm");

        int adjustedCooldown = (int) (cooldown * spellLevel.getCooldownReduction());
        int adjustedManaCost = (int) (manaCost * spellLevel.getManaCostReduction());
        int adjustedCloneCount = cloneCount + spellLevel.getLevel(); // Increase clone count based on level

        if (playerClones.containsKey(player.getUniqueId())) {
            clearClones(player, false);
        }
        if (!Spellbreak.getInstance().getManaSystem().consumeMana(player, adjustedManaCost)) return;
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§cNo target found within range"));
            Spellbreak.getInstance().getManaSystem().restoreMana(player, adjustedManaCost);
            return;
        }

        List<CloneData> clones = new ArrayList<>();
        // Spawn clones evenly spaced in a circle around the player
        Location origin = player.getLocation();
        for (int i = 0; i < adjustedCloneCount; i++) {
            double angle = 2 * Math.PI * i / adjustedCloneCount;
            double xOff = Math.cos(angle) * spawnRadius;
            double zOff = Math.sin(angle) * spawnRadius;
            Location spawnLoc = origin.clone().add(xOff, 0.5, zOff);
            CloneData clone = createCloneAt(player, target, spawnLoc);
            if (clone != null) clones.add(clone);
        }

        if (clones.isEmpty()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§cFailed to create clones"));
            Spellbreak.getInstance().getManaSystem().restoreMana(player, adjustedManaCost);
            return;
        }

        playerClones.put(player.getUniqueId(), clones);
        Spellbreak.getInstance().getCooldownManager().setCooldown(player, getName(), adjustedCooldown);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        spawnSwarmParticles(player.getLocation());

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || ticks++ > cloneDuration) cancel();
                double rem = Math.max(0, (cloneDuration - ticks) / 20.0);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(String.format("§dClone Swarm: %.1fs", rem)));
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);

        new BukkitRunnable() {
            @Override public void run() { clearClones(player, false); }
        }.runTaskLater(Spellbreak.getInstance(), cloneDuration);
    }

    private CloneData createCloneAt(Player player, LivingEntity target, Location spawnLoc) {
        ArmorStand stand = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setGravity(true);
        stand.setVisible(true);
        stand.setSmall(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.addScoreboardTag("clone_swarm");
        stand.setMetadata("clone_owner", new FixedMetadataValue(Spellbreak.getInstance(), player.getUniqueId().toString()));

        PlayerDisguise disguise = new PlayerDisguise(player.getName());
        disguise.getWatcher().setCustomName(player.getName());
        disguise.getWatcher().setCustomNameVisible(true);
        DisguiseAPI.disguiseToAll(stand, disguise);
        spawnParticles(spawnLoc);

        BukkitTask moveTask = new BukkitRunnable() {
            int ticksToJump = (int)(Math.random()*20)+10;
            @Override public void run() {
                if (stand.isDead() || !target.isValid() || !player.isOnline()) cancel();
                Vector dir = target.getLocation().toVector().subtract(stand.getLocation().toVector()).normalize();
                dir.add(new Vector((Math.random()-0.5)*0.3,0,(Math.random()-0.5)*0.3)).normalize();
                if (--ticksToJump<=0) { dir.setY(cloneJumpForce); ticksToJump = (int)(Math.random()*20)+10; }
                stand.setVelocity(dir.multiply(cloneMoveSpeed));
                spawnTinyParticles(stand.getLocation());
                if (stand.getLocation().distance(target.getLocation())<cloneCollisionRadius) handleCloneCollision(stand, target, player);
            }
        }.runTaskTimer(Spellbreak.getInstance(),5,2);

        return new CloneData(stand, moveTask);
    }

    private LivingEntity findTarget(Player player) {
        LivingEntity target = null;
        double closestDist = Double.MAX_VALUE;

        // Find the closest living entity in player's line of sight
        for (Entity entity : player.getNearbyEntities(maxTargetDistance, maxTargetDistance, maxTargetDistance)) {
            if (!(entity instanceof LivingEntity livingEntity) || entity.equals(player)) continue;
            if (isCloneEntity(entity)) continue;

            // Check if player is looking at entity (rough check)
            if (!isInLineOfSight(player, livingEntity)) continue;

            double dist = player.getLocation().distance(entity.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                target = livingEntity;
            }
        }

        return target;
    }

    private boolean isInLineOfSight(Player player, LivingEntity target) {
        Vector toTarget = target.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
        double dot = toTarget.dot(player.getLocation().getDirection());

        // Check if the target is in a 30-degree cone of player's view
        return dot > 0.866; // Cosine of 30 degrees is ~0.866
    }

    private CloneData createClone(Player player, LivingEntity target) {
        // Create spawn location with slight random offset from player
        Location spawnLoc = player.getLocation().clone().add(
                (Math.random() - 0.5) * 3,
                0.5,
                (Math.random() - 0.5) * 3
        );

        // Create armor stand for the clone
        ArmorStand stand = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setGravity(true);
        stand.setVisible(true);
        stand.setSmall(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.addScoreboardTag("clone_swarm");
        stand.setMetadata("clone_owner", new FixedMetadataValue(Spellbreak.getInstance(), player.getUniqueId().toString()));

        // Disguise the armor stand as the player
        PlayerDisguise disguise = new PlayerDisguise(player.getName());
        disguise.getWatcher().setCustomName(player.getName());
        disguise.getWatcher().setCustomNameVisible(true);
        DisguiseAPI.disguiseToAll(stand, disguise);

        spawnParticles(spawnLoc);

        // Create the movement task for this clone
        BukkitTask moveTask = new BukkitRunnable() {
            int jumps = 0;
            int ticksToNextJump = (int) (Math.random() * 20) + 10;

            @Override
            public void run() {
                if (stand.isDead() || !target.isValid() || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Calculate direction to target
                Vector toTarget = target.getLocation().toVector().subtract(stand.getLocation().toVector()).normalize();

                // Add some randomness to movement
                toTarget.add(new Vector(
                        (Math.random() - 0.5) * 0.3,
                        0,
                        (Math.random() - 0.5) * 0.3
                )).normalize();

                // Set velocity towards target
                Vector currentVel = stand.getVelocity();

                // Jump occasionally
                ticksToNextJump--;
                if (ticksToNextJump <= 0) {
                    ticksToNextJump = (int) (Math.random() * 20) + 10;
                    toTarget.setY(cloneJumpForce);
                    jumps++;
                }

                // Apply movement
                stand.setVelocity(toTarget.multiply(cloneMoveSpeed));

                // Create trail effect
                spawnTinyParticles(stand.getLocation());

                // Check for collision with target
                if (stand.getLocation().distance(target.getLocation()) < cloneCollisionRadius) {
                    handleCloneCollision(stand, target, player);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 5L, 2L);

        return new CloneData(stand, moveTask);
    }

    private void handleCloneCollision(ArmorStand clone, LivingEntity target, Player owner) {
        // Apply damage and knockback effect
        Spellbreak.getInstance().getAbilityDamage().damage(target, cloneCollisionDamage, owner, this, null);

        // Apply small knockback
        Vector knock = target.getLocation().toVector().subtract(clone.getLocation().toVector()).normalize().multiply(0.3);
        knock.setY(Math.max(0.1, knock.getY()));
        target.setVelocity(target.getVelocity().add(knock));

        // Visual and sound effects
        clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.5f);
        spawnHitParticles(clone.getLocation());

        // Remove the clone
        DisguiseAPI.undisguiseToAll(clone);
        clone.remove();
    }

    private void clearClones(Player player, boolean refundCooldown) {
        List<CloneData> clones = playerClones.remove(player.getUniqueId());
        if (clones == null) return;

        for (CloneData clone : clones) {
            if (clone.moveTask != null) {
                clone.moveTask.cancel();
            }

            if (clone.stand != null && !clone.stand.isDead()) {
                // Final burst effect
                spawnBurstParticles(clone.stand.getLocation());
                clone.stand.getWorld().playSound(clone.stand.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);

                // Remove the clone
                DisguiseAPI.undisguiseToAll(clone.stand);
                clone.stand.remove();
            }
        }

        if (refundCooldown) {
            Spellbreak.getInstance().getCooldownManager().removeCooldown(player, getName());
        }
    }

    private boolean isCloneEntity(Entity entity) {
        return entity.getScoreboardTags().contains("clone_swarm") || entity.getScoreboardTags().contains("phantom_clone");
    }

    private void spawnParticles(Location loc) {
        loc.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                30,
                0.5, 1.0, 0.5,
                0.5,
                new DustOptions(Color.fromRGB(255, 105, 180), 1.5f),
                true
        );
    }

    private void spawnTinyParticles(Location loc) {
        loc.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                5,
                0.2, 0.3, 0.2,
                0.01,
                new DustOptions(Color.fromRGB(255, 105, 180), 1.0f),
                true
        );
    }

    private void spawnHitParticles(Location loc) {
        loc.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                30,
                0.5, 0.5, 0.5,
                0.5,
                new DustOptions(Color.fromRGB(255, 0, 128), 1.5f),
                true
        );
    }

    private void spawnBurstParticles(Location loc) {
        loc.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                50,
                1.0, 1.0, 1.0,
                0.5,
                new DustOptions(Color.fromRGB(255, 105, 180), 2.0f),
                true
        );
    }

    private void spawnSwarmParticles(Location loc) {
        // Create a spiral effect around the player
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 12) {
            double radius = 1.5;
            double x = Math.cos(i) * radius;
            double z = Math.sin(i) * radius;

            for (double y = 0; y < 2.0; y += 0.25) {
                Location particleLoc = loc.clone().add(x, y, z);
                loc.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        0, 0, 0,
                        0,
                        new DustOptions(Color.fromRGB(255, 105, 180), 1.5f),
                        true
                );
            }
        }
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.cloneswarm.cooldown", cooldown);
        manaCost = cfg.getInt("abilities.cloneswarm.mana-cost", manaCost);
        cloneCount = cfg.getInt("abilities.cloneswarm.clone-count", cloneCount);
        cloneDuration = cfg.getInt("abilities.cloneswarm.clone-duration", cloneDuration);
        cloneMoveSpeed = cfg.getDouble("abilities.cloneswarm.clone-move-speed", cloneMoveSpeed);
        cloneJumpForce = cfg.getDouble("abilities.cloneswarm.clone-jump-force", cloneJumpForce);
        maxTargetDistance = cfg.getDouble("abilities.cloneswarm.max-target-distance", maxTargetDistance);
        cloneCollisionDamage = cfg.getDouble("abilities.cloneswarm.clone-collision-damage", cloneCollisionDamage);
        cloneCollisionRadius = cfg.getDouble("abilities.cloneswarm.clone-collision-radius", cloneCollisionRadius);
    }

    private static class CloneData {
        ArmorStand stand;
        BukkitTask moveTask;

        CloneData(ArmorStand stand, BukkitTask moveTask) {
            this.stand = stand;
            this.moveTask = moveTask;
        }
    }
}
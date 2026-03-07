package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CanopyCrashAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 25;
    private String requiredClass = "archdruid";
    private double damage = 2.0;
    private double radius = 7.0;
    private double verticalKnockback = 0.8;
    private double horizontalKnockback = 1.2;
    private int particleCount = 30;
    private double minHeight = 4.0;
    private final Set<UUID> activeSlams = new HashSet<>();

    @Override public String getName() { return "CanopyCrash"; }
    @Override public String getDescription() {
        return "Slam down from heights, creating a shockwave of nature energy";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    public double getMinHeight() {
        return minHeight;
    }

    @Override
    public void activate(Player player) {
        if (player.isOnGround()) return;
        double heightAboveGround = calculateHeightAboveGround(player.getLocation());
        if (heightAboveGround < minHeight) return;

        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(player.getUniqueId(),
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
                        getName());

        double scaledRadius = radius * sl.getRangeMultiplier();
        double scaledDamage = damage * sl.getDamageMultiplier();

        activeSlams.add(player.getUniqueId());
        World world = player.getWorld();

        float chargePitch = 0.6f + sl.getLevel() * 0.08f;
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_STEP, 0.5f, chargePitch);

        player.setVelocity(new Vector(0, -2.5, 0));
        player.setInvulnerable(true);

        Particle.DustOptions chargeDust = new Particle.DustOptions(
                sl.getLevel() >= 3 ? Color.fromRGB(0, 200, 80) : Color.fromRGB(50, 205, 50), 2.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (player.isOnGround() || ticks++ > 100) {
                    cancel();
                    resolveImpact(player, scaledRadius, scaledDamage, sl);
                    return;
                }
                double angle = Math.toRadians(ticks * 15);
                double x = Math.cos(angle) * 1.2;
                double z = Math.sin(angle) * 1.2;
                world.spawnParticle(Particle.DUST, player.getLocation().add(x, 0.5, z), 2, 0.1, 0.1, 0.1, chargeDust);
                world.spawnParticle(Particle.DUST, player.getLocation().add(-x, 0.5, -z), 2, 0.1, 0.1, 0.1, chargeDust);
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
    }
    private double calculateHeightAboveGround(Location location) {
        Block blockBelow = location.getBlock().getRelative(BlockFace.DOWN);
        int depth = 0;
        int maxDepth = 256;
        int worldMinHeight = location.getWorld().getMinHeight(); // This handles negative Y worlds

        // Search downward until we find solid ground or hit world bottom
        while (!blockBelow.getType().isSolid() &&
                blockBelow.getY() >= worldMinHeight &&
                depth++ < maxDepth) {
            blockBelow = blockBelow.getRelative(BlockFace.DOWN);
        }

        // If we found solid ground, calculate height
        if (blockBelow.getType().isSolid()) {
            return location.getY() - blockBelow.getY() - 1;
        }

        // If no solid ground found, return -1 to indicate failure
        return -1;
    }

    private void resolveImpact(Player player, double scaledRadius, double scaledDamage, SpellLevel sl) {
        if (!activeSlams.remove(player.getUniqueId())) return;
        World world = player.getWorld();
        Location impactLoc = player.getLocation();
        player.setInvulnerable(false);

        Block ground = impactLoc.getBlock().getRelative(BlockFace.DOWN);
        while (!ground.getType().isSolid() && ground.getY() > 0) ground = ground.getRelative(BlockFace.DOWN);
        impactLoc = ground.getLocation().add(0.5, 1, 0.5);

        createShockwave(impactLoc, world, scaledRadius, sl);
        applyDamageScaled(impactLoc, world, player, scaledRadius, scaledDamage);

        float impactPitch = 1.8f + sl.getLevel() * 0.05f;
        world.playSound(impactLoc, Sound.ENTITY_RAVAGER_ROAR, 0.8f, impactPitch);
        // Level 5: extra thunderous crash
        if (sl.getLevel() >= 5) {
            world.playSound(impactLoc, Sound.ENTITY_RAVAGER_STEP, 1.2f, 0.7f);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, impactLoc, 40, 1.5, 0.2, 1.5, 0.3, ground.getBlockData());
        }
    }

    // Keep backward-compat stub (not used but avoids compile errors if referenced)
    private void resolveImpact(Player player) {
        resolveImpact(player, radius, damage, Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(player.getUniqueId(),
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
                        getName()));
    }

    private void createShockwave(Location center, World world, double scaledRadius, SpellLevel sl) {
        new BukkitRunnable() {
            double expansion = 0.0;
            @Override
            public void run() {
                if (expansion > scaledRadius) { cancel(); return; }
                for (int i = 0; i < particleCount; i++) {
                    double angle = Math.toRadians((360.0 / particleCount) * i);
                    Location loc = center.clone().add(
                            Math.cos(angle) * expansion, 0.2, Math.sin(angle) * expansion);

                    Particle.DustOptions dust = new Particle.DustOptions(
                            sl.getLevel() >= 3 ? Color.fromRGB(0, 200, 80) : Color.fromRGB(34, 139, 34),
                            1.5f + (float)(expansion / scaledRadius) * 1.5f);
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);

                    // Level 3+: TOTEM_OF_UNDYING ring every 3rd point
                    if (sl.getLevel() >= 3 && i % 3 == 0) {
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 1, 0.05, 0.05, 0.05, 0.02);
                    }
                }
                expansion += 0.35;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
    }

    private void createShockwave(Location center, World world) {
        createShockwave(center, world, radius, Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(java.util.UUID.randomUUID(), "archdruid", getName()));
    }

    private void applyDamageScaled(Location center, World world, Player caster, double scaledRadius, double scaledDamage) {
        world.getNearbyEntities(center, scaledRadius, 2.0, scaledRadius).forEach(e -> {
            if (!(e instanceof LivingEntity) || e.equals(caster)) return;
            LivingEntity entity = (LivingEntity) e;
            Spellbreak.getInstance().getAbilityDamage().damage(entity, scaledDamage, caster, this, null);
            Vector dir = entity.getLocation().toVector().subtract(center.toVector()).normalize();
            dir.setY(verticalKnockback);
            entity.setVelocity(dir.multiply(horizontalKnockback));
        });
    }

    private void applyDamage(Location center, World world, Player caster) {
        applyDamageScaled(center, world, caster, radius, damage);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String p = "abilities.canopycrash.";
        cooldown = cfg.getInt(p+"cooldown", cooldown);
        manaCost = cfg.getInt(p+"mana-cost", manaCost);
        damage = cfg.getDouble(p+"damage", damage);
        radius = cfg.getDouble(p+"radius", radius);
        verticalKnockback = cfg.getDouble(p+"vertical-knockback", verticalKnockback);
        horizontalKnockback = cfg.getDouble(p+"horizontal-knockback", horizontalKnockback);
        particleCount = cfg.getInt(p+"particle-count", particleCount);
        minHeight = cfg.getDouble(p+"min-height", minHeight);
    }

    // Other required methods
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) { return false; }
    @Override public boolean isSuccessful() { return true; }
    @Override public int getCurrentCharges(Player player) { return 0; }
    @Override public int getMaxCharges(Player player) { return 0; }
    @Override public int getChargeRegenTime() { return 0; }
    @Override
    public String getDeathMessage(String victim, String caster, String ability) {
        return String.format("%s was crushed by %s's canopy crash!", victim, caster);
    }
}
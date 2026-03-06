package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuantumAnchorAbility implements Ability {
    private int cooldown = 19;
    private int manaCost = 35;
    private String requiredClass = "starcaller";
    private int duration = 6; // Duration in seconds
    private double absorptionHearts = 4.0; // 4 hearts
    private int speedAmplifier = 2; // Speed III
    private double explosionDamage = 2.0;
    private float explosionRadius = 3.0f;

    private final Map<UUID, AnchorRecord> activeAnchors = new HashMap<>();

    @Override public String getName() { return "QuantumAnchor"; }
    @Override public String getDescription() {
        return "Set a quantum anchor to return to after " + duration + " seconds. Gain absorption and speed. Return early if absorption is lost.";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        if (activeAnchors.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Quantum Anchor is already active!");
            return;
        }

        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), getName());

        // Record player state
        AnchorRecord record = new AnchorRecord(
                player.getLocation().clone(), sl
        );

        int scaledDuration = (int) (duration * sl.getDurationMultiplier());

        // Apply absorption effect (level 1 = 2 hearts, level 2 = 4 hearts, etc.)
        int absorptionLevel = (int) (absorptionHearts / 2) - 1; // 8.0 hearts = level 3 (which gives 4 hearts)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.ABSORPTION,
                scaledDuration * 20,
                absorptionLevel,
                true,
                true
        ));

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                scaledDuration * 20,
                speedAmplifier,
                true,
                true
        ));

        // Store record
        activeAnchors.put(player.getUniqueId(), record);

        // Start particle effects and action bar updates
        startVisualEffects(player, record, scaledDuration);

        // Schedule return
        BukkitTask returnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeAnchors.containsKey(player.getUniqueId())) {
                    returnToAnchor(player, false);
                }
            }
        }.runTaskLater(Spellbreak.getInstance(), scaledDuration * 20);

        record.setReturnTask(returnTask);

        player.sendMessage(ChatColor.GREEN + "Quantum Anchor set! You'll return in " + scaledDuration + " seconds.");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    private void startVisualEffects(Player player, AnchorRecord record, int scaledDuration) {
        // Action bar and particle task
        BukkitTask visualTask = new BukkitRunnable() {
            private int ticksRemaining = scaledDuration * 20;

            @Override
            public void run() {
                if (!activeAnchors.containsKey(player.getUniqueId()) || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                ticksRemaining--;
                int secondsRemaining = ticksRemaining / 20;
                double absorptionAmount = player.getAbsorptionAmount();

                // Update action bar
                String actionBarText = String.format("§6⚡ Quantum Anchor: §e%ds §6| §c❤ Absorption: §e%.1f",
                        secondsRemaining, absorptionAmount);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarText));

                // Spawn particles at anchor location
                Location anchorLoc = record.getLocation();
                spawnAnchorParticles(anchorLoc, secondsRemaining, scaledDuration, record.sl);

                // L5: Anchor Stasis Field
                if (record.sl.getLevel() >= 5) {
                    if (ticksRemaining % 10 == 0) { // Every half second
                        for (Entity e : anchorLoc.getWorld().getNearbyEntities(anchorLoc, 3.0, 3.0, 3.0)) {
                            if (e instanceof LivingEntity && e != player) {
                                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
                            }
                        }
                    }
                }

                if (ticksRemaining <= 0) {
                    this.cancel();
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        record.setVisualTask(visualTask);
    }

    private void spawnAnchorParticles(Location anchorLoc, int secondsRemaining, int totalDuration, SpellLevel sl) {
        if (anchorLoc.getWorld() == null) return;

        // Calculate size based on remaining time
        double sizeMultiplier = (double) secondsRemaining / totalDuration;
        double baseRadius = 1.5;
        double currentRadius = baseRadius * sizeMultiplier;

        // Create nebula-colored particle cloud in player shape
        World world = anchorLoc.getWorld();

        // Body particles (purple/blue nebula colors)
        for (int i = 0; i < 20; i++) {
            double angle = (2 * Math.PI * i) / 20;
            double x = anchorLoc.getX() + Math.cos(angle) * currentRadius * 0.3;
            double z = anchorLoc.getZ() + Math.sin(angle) * currentRadius * 0.3;
            double y = anchorLoc.getY() + Math.random() * 1.8; // Player height

            // Alternate between purple and blue particles
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, x, y, z, 1,
                        new Particle.DustOptions(Color.fromRGB(147, 0, 211), 1.0f)); // Dark violet
            } else {
                world.spawnParticle(Particle.DUST, x, y, z, 1,
                        new Particle.DustOptions(Color.fromRGB(75, 0, 130), 1.0f)); // Indigo
            }
        }

        // Add some sparkle effects
        if (Math.random() < 0.3) {
            world.spawnParticle(Particle.ENCHANT,
                    anchorLoc.getX() + (Math.random() - 0.5) * currentRadius * 2,
                    anchorLoc.getY() + Math.random() * 2,
                    anchorLoc.getZ() + (Math.random() - 0.5) * currentRadius * 2,
                    3, 0.5, 0.5, 0.5, 0.1);
        }
        
        // L3: Quantum Shimmer
        if (sl.getLevel() >= 3 && Math.random() < 0.2) {
            world.spawnParticle(Particle.END_ROD,
                    anchorLoc.getX() + (Math.random() - 0.5) * currentRadius * 2.5,
                    anchorLoc.getY() + Math.random() * 2.5,
                    anchorLoc.getZ() + (Math.random() - 0.5) * currentRadius * 2.5,
                    1, 0, 0, 0, 0.05);
        }
    }

    private void spawnExplosionParticles(Location loc, float scaledRadius) {
        World world = loc.getWorld();
        if (world == null) return;

        int particles = 30;
        double radius = scaledRadius;

        for (int i = 0; i < particles; i++) {
            double angle = (2 * Math.PI * i) / particles;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = Math.random() * radius;

            // Create expanding rings of particles
            for (int ring = 0; ring < 3; ring++) {
                double ringScale = 1.0 - (0.3 * ring);
                Location point = loc.clone().add(x * ringScale, y * ringScale, z * ringScale);

                // Alternate between purple and blue particles
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.DUST, point, 1,
                            new Particle.DustOptions(Color.fromRGB(147, 0, 211), 1.0f)); // Dark violet
                } else {
                    world.spawnParticle(Particle.DUST, point, 1,
                            new Particle.DustOptions(Color.fromRGB(75, 0, 130), 1.0f)); // Indigo
                }
            }
        }

        // Add sparkle effects at the center
        world.spawnParticle(Particle.ENCHANT, loc, 15, 0.5, 0.5, 0.5, 0.5);
    }

    // Check if absorption effect is still active
    public void checkAbsorption(Player player) {
        if (!activeAnchors.containsKey(player.getUniqueId())) return;

        // If player no longer has absorption effect, trigger early return
        if (!player.hasPotionEffect(PotionEffectType.ABSORPTION)) {
            returnToAnchor(player, true);
        }
    }

    private void returnToAnchor(Player player, boolean earlyReturn) {
        AnchorRecord record = activeAnchors.remove(player.getUniqueId());
        if (record == null) return;

        // Cancel all tasks
        if (record.getReturnTask() != null) {
            record.getReturnTask().cancel();
        }
        if (record.getVisualTask() != null) {
            record.getVisualTask().cancel();
        }

        // Remove effects
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.ABSORPTION);

        // Clear action bar
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));

        if (earlyReturn) {
            double scaledExplosionDamage = explosionDamage * record.sl.getDamageMultiplier();
            float scaledExplosionRadius = (float) (explosionRadius * record.sl.getRangeMultiplier());

            // Create explosion at current location
            Location explosionLoc = player.getLocation();
            player.getWorld().spawnParticle(Particle.EXPLOSION, explosionLoc, 1);
            spawnExplosionParticles(explosionLoc, scaledExplosionRadius);
            player.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

            // Damage nearby entities
            for (Entity e : player.getWorld().getNearbyEntities(explosionLoc, scaledExplosionRadius, scaledExplosionRadius, scaledExplosionRadius)) {
                if (e instanceof LivingEntity && e != player) {
                    Spellbreak.getInstance().getAbilityDamage().damage(
                            (LivingEntity) e,
                            scaledExplosionDamage,
                            player,
                            this,
                            "QuantumAnchor"
                    );
                }
            }

            player.sendMessage(ChatColor.YELLOW + "Absorption lost! Returning to anchor early.");
        }

        // Return to anchor location
        player.teleport(record.getLocation());

        // Final particle effect at return location
        player.getWorld().spawnParticle(Particle.PORTAL, record.getLocation(), 30, 1, 1, 1, 0.5);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "Returned to Quantum Anchor!");
    }

    @Override
    public String getDeathMessage(String victim, String caster, String unused) {
        return String.format("§6%s §ewas anchored to oblivion by §6%s§e's Quantum Anchor!", victim, caster);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String b = "abilities.quantumanchor.";
        cooldown = cfg.getInt(b + "cooldown", cooldown);
        manaCost = cfg.getInt(b + "mana-cost", manaCost);
        duration = cfg.getInt(b + "duration", duration);
        absorptionHearts = cfg.getDouble(b + "absorption-hearts", absorptionHearts);
        speedAmplifier = cfg.getInt(b + "speed-amplifier", speedAmplifier);
        explosionDamage = cfg.getDouble(b + "explosion-damage", explosionDamage);
        explosionRadius = (float) cfg.getDouble(b + "explosion-radius", explosionRadius);
        requiredClass = cfg.getString(b + "required-class", requiredClass);
    }

    @Override
    public boolean isSuccessful() {
        return false;
    }

    public void removeAnchor(Player player) {
        AnchorRecord record = activeAnchors.remove(player.getUniqueId());
        if (record != null) {
            if (record.getReturnTask() != null) {
                record.getReturnTask().cancel();
            }
            if (record.getVisualTask() != null) {
                record.getVisualTask().cancel();
            }
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.ABSORPTION);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            player.sendMessage(ChatColor.YELLOW + "Quantum Anchor cancelled!");
        }
    }

    private static class AnchorRecord {
        private final Location location;
        private BukkitTask returnTask;
        private BukkitTask visualTask;
        private final SpellLevel sl;

        public AnchorRecord(Location location, SpellLevel sl) {
            this.location = location;
            this.sl = sl;
        }

        public Location getLocation() { return location; }
        public BukkitTask getReturnTask() { return returnTask; }
        public void setReturnTask(BukkitTask task) { this.returnTask = task; }
        public BukkitTask getVisualTask() { return visualTask; }
        public void setVisualTask(BukkitTask task) { this.visualTask = task; }
    }
}
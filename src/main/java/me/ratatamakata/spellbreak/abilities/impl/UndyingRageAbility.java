package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Particle;

public class UndyingRageAbility implements Ability {

    private final Spellbreak plugin = Spellbreak.getInstance();

    private String name = "UndyingRage";
    private String description = "Double your max health, which then decays. Damaging enemies restores health. Unleash an explosion when bonus health is lost or duration ends.";
    private int cooldown = 50; // seconds
    private int manaCost = 10;
    private String requiredClass = "necromancer"; // Or any class, or none

    private int durationSeconds = 15;
    private double healthDecayPerSecond = 3.0; // 1 heart per second
    private double explosionRadius = 5.0;
    private double explosionDamage = 15.0;
    private double explosionKnockbackPower = 1.5;
    private double healOnHitPercentage = 0.5; // 25% of damage dealt is healed

    public static final String ACTIVE_METADATA_KEY = "UndyingRageActive";
    public static final String EXPIRY_METADATA_KEY = "UndyingRageExpiry";
    public static final String ORIGINAL_MAX_HEALTH_METADATA_KEY = "UndyingRageOrigMaxHP";
    public static final String AUGMENTED_MAX_HEALTH_METADATA_KEY = "UndyingRageAugMaxHP";
    public static final String EXPLOSION_TRIGGERED_METADATA_KEY = "UndyingRageExplTrig";

    public UndyingRageAbility() {
        loadConfig(); // Load config values when instantiated
    }

    @Override
    public String getName() { return name; }
    @Override
    public String getDescription() { return description; }
    @Override
    public int getCooldown() { return cooldown; }
    @Override
    public int getManaCost() { return manaCost; }
    @Override
    public String getRequiredClass() { return requiredClass; }
    @Override
    public boolean isTriggerAction(Action action) { return false; } // Listener handles specific trigger
    @Override
    public boolean isSuccessful() { return true; } // Assumes successful activation

    @Override
    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String path = "abilities.undyingrage.";
        cooldown = cfg.getInt(path + "cooldown", 50);
        manaCost = cfg.getInt(path + "mana-cost", 20);
        requiredClass = cfg.getString(path + "required-class", "necromancer");
        durationSeconds = cfg.getInt(path + "duration-seconds", 15);
        healthDecayPerSecond = cfg.getDouble(path + "health-decay-per-second", 3.0);
        explosionRadius = cfg.getDouble(path + "explosion.radius", 5.0);
        explosionDamage = cfg.getDouble(path + "explosion.damage", 2.0);
        explosionKnockbackPower = cfg.getDouble(path + "explosion.knockback-power", 1.5);
        healOnHitPercentage = cfg.getDouble(path + "heal-on-hit-percentage", 0.5);

        // Update description with loaded values if desired, or keep static
        double typicalBonusHealth = getAugmentedHealthBonus(20.0);
        description = String.format(
            "Gain %.0f bonus HP that decays over %ds. Heal for %.0f%% of damage dealt. Explodes when bonus HP is lost or time expires.",
            typicalBonusHealth,
            durationSeconds,
            healOnHitPercentage * 100
        );
    }

    private double getAugmentedHealthBonus(double originalMaxHealth) {
        // For Undying Rage, we effectively double the original max health,
        // meaning the bonus health granted *is* the original max health.
        return originalMaxHealth;
    }


    @Override
    public void activate(Player player) {
        long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        // Clear any previous Undying Rage states first
        cleanupAbilityState(player, false); // false because we don't want to restore health yet

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            plugin.getLogger().severe("[UndyingRage] Player " + player.getName() + " has no GENERIC_MAX_HEALTH attribute!");
            return;
        }
        double currentHealthBeforeAugment = player.getHealth();
        double originalMaxHealth = maxHealthAttribute.getBaseValue();
        double healthBonus = getAugmentedHealthBonus(originalMaxHealth);
        double augmentedMaxHealth = originalMaxHealth + healthBonus;

        player.setMetadata(ACTIVE_METADATA_KEY, new FixedMetadataValue(plugin, true));
        player.setMetadata(EXPIRY_METADATA_KEY, new FixedMetadataValue(plugin, expiryTime));
        player.setMetadata(ORIGINAL_MAX_HEALTH_METADATA_KEY, new FixedMetadataValue(plugin, originalMaxHealth));
        player.setMetadata(AUGMENTED_MAX_HEALTH_METADATA_KEY, new FixedMetadataValue(plugin, augmentedMaxHealth));
        player.setMetadata(EXPLOSION_TRIGGERED_METADATA_KEY, new FixedMetadataValue(plugin, false));

        maxHealthAttribute.setBaseValue(augmentedMaxHealth);
        player.setHealth(Math.min(currentHealthBeforeAugment + healthBonus, augmentedMaxHealth));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation().add(0,0.5,0), 30, 0.3, 0.3, 0.3, 0.1);
        player.sendMessage(ChatColor.DARK_RED + ChatColor.BOLD.toString() + "UNDYING RAGE CONSUMES YOU!");

        new BukkitRunnable() {
            int ticksElapsed = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !player.hasMetadata(ACTIVE_METADATA_KEY)) {
                    cleanupAbilityState(player, player.isOnline()); // true if online to restore health state
                    cancel();
                    return;
                }

                long currentExpiry = player.getMetadata(EXPIRY_METADATA_KEY).get(0).asLong();
                double currentOrigMaxHP = player.getMetadata(ORIGINAL_MAX_HEALTH_METADATA_KEY).get(0).asDouble();
                boolean explosionAlreadyHappened = player.hasMetadata(EXPLOSION_TRIGGERED_METADATA_KEY) && player.getMetadata(EXPLOSION_TRIGGERED_METADATA_KEY).get(0).asBoolean();

                // If explosion happened due to listener (damage taken), just proceed to cleanup and cancel
                if (explosionAlreadyHappened) {
                    cleanupAbilityState(player, true);
                    cancel();
                    return;
                }

                // Health Decay Logic (every second)
                if (ticksElapsed > 0 && ticksElapsed % 20 == 0) {
                    if (player.getHealth() > currentOrigMaxHP) {
                        double healthToLose = healthDecayPerSecond;
                        double newHealth = player.getHealth() - healthToLose;

                        player.setHealth(Math.max(newHealth, currentOrigMaxHP - 0.01)); // Allow it to dip just below to trigger explosion if decay is the cause

                        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0,1,0), 3, 0.3,0.3,0.3,0.01);

                        if (player.getHealth() <= currentOrigMaxHP && !explosionAlreadyHappened) {
                            triggerExplosion(player);
                            // Explosion sets the flag. This runnable will pick it up next tick and clean up.
                        }
                    }
                }

                long remainingMillis = currentExpiry - System.currentTimeMillis();
                if (remainingMillis <= 0) { // Duration ended
                    if (!explosionAlreadyHappened) {
                        triggerExplosion(player);
                    }
                    cleanupAbilityState(player, true);
                    cancel();
                    return;
                }

                // Action Bar Update
                int remainingSeconds = (int) (remainingMillis / 1000);
                String healthDisplay = String.format("%.0f/%.0f", player.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.DARK_RED + "Rage: " + ChatColor.WHITE + remainingSeconds + "s " +
                                ChatColor.RED + "| HP: " + ChatColor.WHITE + healthDisplay + ChatColor.GRAY + " (Decaying)"));

                // Persistent visual effect (subtle)
                if (ticksElapsed % 10 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.RED, 1.0f));
                }
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void triggerExplosion(Player player) {
        Spellbreak currentPlugin = Spellbreak.getInstance();
        if (player.hasMetadata(EXPLOSION_TRIGGERED_METADATA_KEY) && player.getMetadata(EXPLOSION_TRIGGERED_METADATA_KEY).get(0).asBoolean()) {
            return; // Already exploded
        }
        player.setMetadata(EXPLOSION_TRIGGERED_METADATA_KEY, new FixedMetadataValue(currentPlugin, true));

        // Fetch explosion details from config or use defaults stored in an instance if this were not static
        // For now, using values from where this method is defined or assuming they are passed/accessible
        FileConfiguration cfg = currentPlugin.getConfig();
        String path = "abilities.undyingrage.explosion.";
        double explRadius = cfg.getDouble(path + "radius", 5.0);
        double explDamage = cfg.getDouble(path + "damage", 15.0);
        double explKnockback = cfg.getDouble(path + "knockback-power", 1.5);

        Location loc = player.getLocation().add(0,1,0);
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        player.getWorld().spawnParticle(Particle.DUST, loc, 100, explRadius / 2, explRadius / 2, explRadius / 2, 0.5, new Particle.DustOptions(Color.RED, 2.0f));
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 10, explRadius / 3, explRadius / 3, explRadius / 3, 0.1);


        for (Entity nearbyEntity : player.getNearbyEntities(explRadius, explRadius, explRadius)) {
            if (nearbyEntity instanceof LivingEntity && !(nearbyEntity.equals(player))) {
                LivingEntity target = (LivingEntity) nearbyEntity;
                // Basic hostility check, can be expanded (e.g., ignore teammates)
                if (target instanceof Player && ((Player) target).getGameMode() == GameMode.CREATIVE) continue;

                target.damage(explDamage, player);
                Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                direction.setY(Math.max(0.2, direction.getY() * 0.5 + 0.4)).normalize(); // Add some upward lift
                target.setVelocity(direction.multiply(explKnockback));
            }
        }
        player.sendMessage(ChatColor.RED + "Your rage erupts outwards!");
    }

    public static void tryHealOnHit(Player player, double damageDealt) {
        if (!player.hasMetadata(ACTIVE_METADATA_KEY)) return;
        Spellbreak currentPlugin = Spellbreak.getInstance();
         FileConfiguration cfg = currentPlugin.getConfig();
        double healPercent = cfg.getDouble("abilities.undyingrage.heal-on-hit-percentage", 0.25);

        double augmentedMaxHealth = player.getMetadata(AUGMENTED_MAX_HEALTH_METADATA_KEY).get(0).asDouble();
        double healAmount = damageDealt * healPercent;

        if (player.getHealth() < augmentedMaxHealth) {
            player.setHealth(Math.min(augmentedMaxHealth, player.getHealth() + healAmount));
            player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation(), 2, 0.3, 0.3, 0.3, 0.05);
        }
    }

    public static void cleanupAbilityState(Player player, boolean restoreHealth) {
        Spellbreak currentPlugin = Spellbreak.getInstance();
        if (restoreHealth && player.hasMetadata(ORIGINAL_MAX_HEALTH_METADATA_KEY)) {
            double originalMax = player.getMetadata(ORIGINAL_MAX_HEALTH_METADATA_KEY).get(0).asDouble();
            AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(originalMax);
                if (player.getHealth() > originalMax) {
                    player.setHealth(originalMax);
                }
                 // If player's health is very low, ensure they are not left dead if originalMaxHealth was also low.
                if (player.getHealth() <= 0 && originalMax > 0) {
                     player.setHealth(Math.min(1.0, originalMax)); // Leave with at least 1 HP or originalMax if tiny
                }
            }
        }

        if (player.isOnline()) { // Clear action bar only if player is online
             player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }

        player.removeMetadata(ACTIVE_METADATA_KEY, currentPlugin);
        player.removeMetadata(EXPIRY_METADATA_KEY, currentPlugin);
        player.removeMetadata(ORIGINAL_MAX_HEALTH_METADATA_KEY, currentPlugin);
        player.removeMetadata(AUGMENTED_MAX_HEALTH_METADATA_KEY, currentPlugin);
        player.removeMetadata(EXPLOSION_TRIGGERED_METADATA_KEY, currentPlugin);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format("§e%s §fwas obliterated by §c%s's §aundying rage§f.", victimName, casterName);
    }
}
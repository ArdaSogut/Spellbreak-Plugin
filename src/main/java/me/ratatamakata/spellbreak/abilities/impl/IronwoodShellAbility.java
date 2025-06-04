package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class IronwoodShellAbility implements Ability {

    // Configuration
    private String name = "IronwoodShell";
    private String description = "Gain bark armor absorbing damage and reflecting portion to attackers";
    private int manaCost = 40;
    private String requiredClass = "archdruid";
    private int cooldownSeconds = 20;
    private int durationSeconds = 8;
    private double damageAbsorption = 0.5;
    private double damageReflection = 0.2;
    private Color barkColor = Color.fromRGB(101, 67, 33);
    private double maxAbsorbableDamage = 50.0;
    private int maxDamageInstances = 10;

    // Runtime
    private final Set<UUID> activeShells = new HashSet<>();
    private final Set<UUID> immunityList = new HashSet<>();
    private final Map<UUID, Double> damageAbsorbedThisCast = new HashMap<>();
    private final Map<UUID, Integer> instancesBlockedThisCast = new HashMap<>();

    public IronwoodShellAbility() {
        // Constructor is now empty, or remove if not needed for other purposes
    }

    @Override
    public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldownSeconds; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(Action action) {
        return false;
    }

    public boolean isShellActive(Player player) {
        return activeShells.contains(player.getUniqueId());
    }

    public boolean addToImmunity(UUID uuid) {
        return immunityList.add(uuid);
    }

    public void removeFromImmunity(UUID uuid) {
        immunityList.remove(uuid);
    }

    public double getDamageAbsorption() { return damageAbsorption; }
    public double getDamageReflection() { return damageReflection; }

    // --- Getters for configured limits ---
    public double getMaxAbsorbableDamageConfig() { return maxAbsorbableDamage; }
    public int getMaxDamageInstancesConfig() { return maxDamageInstances; }
    // --- End getters for configured limits ---

    // --- Getter for current absorbed damage ---
    public double getCurrentAbsorbedDamage(UUID uuid) {
        return damageAbsorbedThisCast.getOrDefault(uuid, 0.0);
    }
    // --- End getter for current absorbed damage ---

    // --- New methods for listener interaction and limits ---
    public void recordDamageAbsorbed(UUID uuid, double damage) {
        damageAbsorbedThisCast.computeIfPresent(uuid, (k, v) -> v + damage);
    }

    public void incrementBlockedInstances(UUID uuid) {
        instancesBlockedThisCast.computeIfPresent(uuid, (k, v) -> v + 1);
    }

    public boolean hasReachedMaxAbsorbedDamage(UUID uuid) {
        return damageAbsorbedThisCast.getOrDefault(uuid, 0.0) >= maxAbsorbableDamage;
    }

    public boolean hasReachedMaxInstances(UUID uuid) {
        return instancesBlockedThisCast.getOrDefault(uuid, 0) >= maxDamageInstances;
    }

    public void deactivateShell(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeShells.remove(uuid)) {
            damageAbsorbedThisCast.remove(uuid);
            instancesBlockedThisCast.remove(uuid);
            if (player.isValid()) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                player.sendMessage(ChatColor.YELLOW + "Your Ironwood Shell shatters!");
            }
        }
    }
    // --- End new methods ---

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String path = "abilities.ironwoodshell.";

        manaCost = cfg.getInt(path + "mana-cost", manaCost);
        cooldownSeconds = cfg.getInt(path + "cooldown-seconds", cooldownSeconds);
        durationSeconds = cfg.getInt(path + "duration-seconds", durationSeconds);
        damageAbsorption = cfg.getDouble(path + "damage-absorption", damageAbsorption);
        damageReflection = cfg.getDouble(path + "damage-reflection", damageReflection);
        maxAbsorbableDamage = cfg.getDouble(path + "max-absorbable-damage", maxAbsorbableDamage);
        maxDamageInstances = cfg.getInt(path + "max-damage-instances", maxDamageInstances);

        barkColor = Color.fromRGB(
                cfg.getInt(path + "bark-color.r", 101),
                cfg.getInt(path + "bark-color.g", 67),
                cfg.getInt(path + "bark-color.b", 33)
        );

        description = String.format(
                "%ds of %.0f%% dmg absorb (max %.0f dmg or %d hits) & %.0f%% reflect. %ds CD",
                durationSeconds, damageAbsorption * 100, maxAbsorbableDamage, maxDamageInstances,
                damageReflection * 100, cooldownSeconds);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return "";
    }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeShells.contains(uuid)) return;

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 0.8f);

        damageAbsorbedThisCast.put(uuid, 0.0);
        instancesBlockedThisCast.put(uuid, 0);

        BukkitTask particleTask = new BukkitRunnable() {
            int ticks = 0;
            final int totalDurationTicks = durationSeconds * 20;

            @Override
            public void run() {
                if (!player.isValid() || !activeShells.contains(uuid)) {
                    damageAbsorbedThisCast.remove(uuid);
                    instancesBlockedThisCast.remove(uuid);
                    if (player.isValid()) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                    }
                    cancel();
                    return;
                }

                int remainingTicks = totalDurationTicks - ticks;
                if (remainingTicks <= 0) {
                    activeShells.remove(uuid);
                    damageAbsorbedThisCast.remove(uuid);
                    instancesBlockedThisCast.remove(uuid);
                    if (player.isValid()) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
                    }
                    cancel();
                    return;
                }

                spawnBarkParticles(player);

                // Update action bar every 10 ticks (0.5 seconds)
                if (ticks % 10 == 0) {
                    double secondsLeft = remainingTicks / 20.0;
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        TextComponent.fromLegacyText(ChatColor.GOLD + "Ironwood Shell: " + String.format("%.1f", secondsLeft) + "s"));
                }
                ticks++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1L);

        activeShells.add(uuid);
    }

    private void spawnBarkParticles(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.5, 0.5, 0.5,
                new Particle.DustOptions(barkColor, 1.2f));
    }
}
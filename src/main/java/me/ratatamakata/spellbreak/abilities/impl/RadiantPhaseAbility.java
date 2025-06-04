package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RadiantPhaseAbility implements Ability {
    private String name = "RadiantPhase";
    private String description = "Become pure light: immune and sped for a short time.";
    private int cooldown = 20; // seconds
    private int manaCost = 40;
    private String requiredClass = "lightbringer";
    private int durationTicks = 20 * 5; // 5 seconds
    private boolean successfulActivation = false;

    // Track active players
    public static final Set<UUID> activePlayers = new HashSet<>();

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) { return false; }

    @Override
    public void activate(Player player) {
        successfulActivation = false;
        if (activePlayers.contains(player.getUniqueId())) return;
        // Begin phase
        activePlayers.add(player.getUniqueId());
        successfulActivation = true;

        // Visual + buff
        player.setGlowing(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1, false, false, false));

        // Schedule ticks for actionbar + particles
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks++ >= durationTicks || !player.isOnline()) {
                    endPhase(player);
                    cancel();
                    return;
                }
                // Action bar countdown
                int remaining = (durationTicks - ticks) / 20;
                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.GOLD + "Radiant Phase: " + remaining + "s"));
                // Gold dust around player
                player.getWorld().spawnParticle(
                        Particle.DUST, player.getLocation().add(0,1,0),
                        10, 1,1,1, 0.05,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255,223,0), 1.0f));
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void endPhase(Player player) {
        activePlayers.remove(player.getUniqueId());
        // Remove effects
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SPEED);
        // Clear action bar
        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(""));
    }

    @Override public boolean isSuccessful() {
        boolean s = successfulActivation;
        successfulActivation = false;
        return s;
    }

    @Override public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.radiantphase.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        durationTicks = cfg.getInt(base + "duration-seconds", 5) * 20;
    }

    @Override public String getDeathMessage(String v, String c, String s) { return null; }
}

package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.ShadowCreaturesAbility;
import me.ratatamakata.spellbreak.level.SpellLevel;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.UUID;

public class ShadowCreaturesListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final ShadowCreaturesAbility ability;

    public ShadowCreaturesListener() {
        this.ability = (ShadowCreaturesAbility) plugin.getAbilityManager().getAbilityByName("ShadowCreatures");
        Bukkit.getScheduler().runTaskTimer(plugin, this::handleCharging, 0L, 1L);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        int slot = p.getInventory().getHeldItemSlot();
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);

        if (!"ShadowCreatures".equalsIgnoreCase(bound)) return;

        if (e.isSneaking()) {
            // Start charging
            SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), "ShadowCreatures");
            int adjustedCooldown = (int) (ability.getCooldown() * spellLevel.getCooldownReduction());
            if (cd.isOnCooldown(p, "ShadowCreatures")) {
                p.sendMessage(ChatColor.RED + "Shadow Creatures on cooldown: "
                        + cd.getRemainingCooldown(p, "ShadowCreatures") + "s");
                return;
            }

            if (!mana.consumeMana(p, ability.getManaCost())) {
                p.sendMessage(ChatColor.RED + "Not enough mana for Shadow Creatures!");
                return;
            }

            // Play charging sound and particles
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 0.8f);
            p.getWorld().spawnParticle(
                    Particle.DUST,
                    p.getLocation().add(0, 1, 0),
                    15,
                    0.5, 0.5, 0.5,
                    0.01,
                    new Particle.DustOptions(Color.fromRGB(64, 0, 64), 1.0f)
            );

            ShadowCreaturesAbility.chargingPlayers.put(p.getUniqueId(), 0);
        } else {
            // Cancel charging
            if (ShadowCreaturesAbility.chargingPlayers.containsKey(p.getUniqueId())) {
                ShadowCreaturesAbility.chargingPlayers.remove(p.getUniqueId());
                mana.restoreMana(p, ability.getManaCost());
                p.sendMessage(ChatColor.RED + "Charge cancelled!");

                // Play cancel sound
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.2f);
            }
        }
    }

    private void handleCharging() {
        for (UUID uuid : new HashMap<>(ShadowCreaturesAbility.chargingPlayers).keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                ShadowCreaturesAbility.chargingPlayers.remove(uuid);
                continue;
            }

            int progress = ShadowCreaturesAbility.chargingPlayers.get(uuid) + 1;

            // Show charging particles
            if (progress % 4 == 0) {
                double circleRadius = 1.0;
                double particleCount = 8;
                double y = progress / 40.0; // Rise up during charge

                for (int i = 0; i < particleCount; i++) {
                    double angle = 2 * Math.PI * i / particleCount;
                    double x = circleRadius * Math.cos(angle);
                    double z = circleRadius * Math.sin(angle);

                    p.getWorld().spawnParticle(
                            Particle.DUST,
                            p.getLocation().add(x, y, z),
                            1,
                            0,
                            0,
                            0,
                            0,
                            new Particle.DustOptions(Color.fromRGB(64, 0, 64), 1.0f)
                    );
                }

                // Add occasional whispers
                if (progress % 10 == 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.2f, 0.5f);
                }
            }

            if (progress >= ability.getChargeTime()) {
                // Complete charge
                ShadowCreaturesAbility.chargingPlayers.remove(uuid);

                // Activation effects
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.3f, 1.5f);
                p.getWorld().spawnParticle(
                        Particle.SMOKE,
                        p.getLocation().add(0, 1, 0),
                        20,
                        0.5, 0.5, 0.5,
                        0.05
                );

                // Purple flash
                p.getWorld().spawnParticle(
                        Particle.DUST,
                        p.getLocation().add(0, 1, 0),
                        30,
                        0.8, 0.8, 0.8,
                        0.01,
                        new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1.5f)
                );

                ability.activate(p);
                cd.setCooldown(p, "ShadowCreatures", ability.getCooldown());

                // Success message
                p.sendMessage(ChatColor.DARK_PURPLE + "Shadow creatures have been summoned to your aid!");
            } else {
                // Update progress
                ShadowCreaturesAbility.chargingPlayers.put(uuid, progress);
                p.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.DARK_PURPLE + "Summoning: " +
                                        String.format("%.1fs", progress / 20.0)
                        )
                );
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (ShadowCreaturesAbility.chargingPlayers.containsKey(p.getUniqueId())) {
            mana.restoreMana(p, ability.getManaCost());
            ShadowCreaturesAbility.chargingPlayers.remove(p.getUniqueId());
        }
        ShadowCreaturesAbility.cleanup(p);
    }
}
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.WardingSigilAbility;
import me.ratatamakata.spellbreak.managers.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.UUID;

public class WardingSigilListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final WardingSigilAbility ability;

    public WardingSigilListener() {
        this.ability = (WardingSigilAbility) plugin.getAbilityManager().getAbilityByName("WardingSigil");
        Bukkit.getScheduler().runTaskTimer(plugin, this::handleCharging, 0L, 1L);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        int slot = p.getInventory().getHeldItemSlot();
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);

        if (!"WardingSigil".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(pdm.getPlayerClass(p.getUniqueId()))) return;

        if (e.isSneaking()) {
            if (cd.isOnCooldown(p, "WardingSigil")) {
                p.sendMessage(ChatColor.RED + "WardingSigil on cooldown: "
                        + cd.getRemainingCooldown(p, "WardingSigil") + "s");
                return;
            }

            if (!mana.consumeMana(p, ability.getManaCost())) {
                p.sendMessage(ChatColor.RED + "Not enough mana for WardingSigil!");
                return;
            }

            // Start charging
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 0.8f);
            WardingSigilAbility.chargingPlayers.put(p.getUniqueId(), 0);
        } else {
            // Cancel charging
            if (WardingSigilAbility.chargingPlayers.containsKey(p.getUniqueId())) {
                WardingSigilAbility.chargingPlayers.remove(p.getUniqueId());
                mana.restoreMana(p, ability.getManaCost());
                p.sendMessage(ChatColor.RED + "Charge cancelled!");
                p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 0.5f);
            }
        }
    }

    private void handleCharging() {
        for (UUID uuid : new HashMap<>(WardingSigilAbility.chargingPlayers).keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                WardingSigilAbility.chargingPlayers.remove(uuid);
                continue;
            }

            int progress = WardingSigilAbility.chargingPlayers.get(uuid) + 1;

            // Show charging particles and effects
            if (progress % 2 == 0) {
                Location loc = p.getLocation().add(0, 1.0, 0);
                World world = p.getWorld();

                // Rotating rune particles
                double angle = (System.currentTimeMillis() % 1000) * Math.PI * 2 / 1000;
                double radius = 0.7 + (progress / (double)ability.getChargeTime()) * 0.3;

                for (int i = 0; i < 5; i++) {
                    double particleAngle = angle + (i * Math.PI * 2 / 5);
                    double x = Math.cos(particleAngle) * radius;
                    double z = Math.sin(particleAngle) * radius;

                    world.spawnParticle(
                            Particle.DUST,
                            loc.clone().add(x, 0, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 180, 255), 1.0f)
                    );
                }

                // Rune symbols appearing randomly around the player
                if (progress % 5 == 0) {
                    double randomAngle = Math.random() * Math.PI * 2;
                    double randomRadius = Math.random() * 0.8;
                    double x = Math.cos(randomAngle) * randomRadius;
                    double z = Math.sin(randomAngle) * randomRadius;

                    world.spawnParticle(
                            Particle.ENCHANT,
                            loc.clone().add(x, 0, z),
                            10, 0.1, 0.1, 0.1, 0.2
                    );
                }

                // Growing circle on the ground to indicate progress
                if (progress % 4 == 0) {
                    double chargePercent = (double) progress / ability.getChargeTime();
                    double circleRadius = 0.5 + chargePercent;

                    for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
                        double x = Math.cos(i) * circleRadius;
                        double z = Math.sin(i) * circleRadius;

                        world.spawnParticle(
                                Particle.DUST,
                                p.getLocation().clone().add(x, 0.1, z),
                                1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(150, 220, 255), 0.8f)
                        );
                    }
                }
            }

            // Complete charge
            if (progress >= ability.getChargeTime()) {
                WardingSigilAbility.chargingPlayers.remove(uuid);
                ability.activate(p);
                cd.setCooldown(p, "WardingSigil", ability.getCooldown());
                p.sendMessage(ChatColor.AQUA + "WardingSigil activated!");

                // Enhanced activation effects
                World world = p.getWorld();
                Location loc = p.getLocation();

                // Expanding circle effect
                for (double r = 0.5; r <= 2.0; r += 0.5) {
                    final double radius = r;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (double i = 0; i < Math.PI * 2; i += Math.PI / 16) {
                            double x = Math.cos(i) * radius;
                            double z = Math.sin(i) * radius;

                            world.spawnParticle(
                                    Particle.DUST,
                                    loc.clone().add(x, 0.1, z),
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.0f)
                            );
                        }
                    }, (long)(r * 2));
                }

                // Rising pillar effect
                for (int i = 0; i < 5; i++) {
                    double angle = (i * Math.PI * 2) / 5;
                    double x = Math.cos(angle) * 0.7;
                    double z = Math.sin(angle) * 0.7;

                    for (double y = 0; y < 3.0; y += 0.2) {
                        final double height = y;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            world.spawnParticle(
                                    Particle.DUST,
                                    loc.clone().add(x, height, z),
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.0f)
                            );
                        }, (long)(y * 3));
                    }
                }

                // Completion sound effects
                world.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
                world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            } else {
                // Update progress
                WardingSigilAbility.chargingPlayers.put(uuid, progress);
                p.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.AQUA + "Charging WardingSigil: " +
                                        String.format("%.1fs", progress / 20.0) + "/" +
                                        (ability.getChargeTime() / 20.0) + "s"
                        )
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent e) {
        // Check if the entity is a player with active shields
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        // Check if player has active shields
        if (!WardingSigilAbility.playersWithShield.contains(p.getUniqueId())) return;

        // Block the damage and consume a shield
        if (WardingSigilAbility.consumeShield(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID playerId = p.getUniqueId();

        // Clean up charging state
        if (WardingSigilAbility.chargingPlayers.containsKey(playerId)) {
            mana.restoreMana(p, ability.getManaCost());
            WardingSigilAbility.chargingPlayers.remove(playerId);
        }

        // Clean up active shields
        WardingSigilAbility.playersWithShield.remove(playerId);
        WardingSigilAbility.activeShields.remove(playerId);
    }
}
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.EmberstormAbility;
import me.ratatamakata.spellbreak.managers.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class EmberstormListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final EmberstormAbility ability;
    private final Random rand = new Random();

    public EmberstormListener() {
        this.ability = (EmberstormAbility) plugin.getAbilityManager().getAbilityByName("Emberstorm");
        Bukkit.getScheduler().runTaskTimer(plugin, this::handleCharging, 0L, 1L);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        int slot = p.getInventory().getHeldItemSlot();
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);

        if (!"Emberstorm".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(pdm.getPlayerClass(p.getUniqueId()))) return;

        int adjustedCooldown = ability.getAdjustedCooldown(p);
        int adjustedManaCost = ability.getAdjustedManaCost(p);
        if (e.isSneaking()) {
            if (cd.isOnCooldown(p, "Emberstorm")) {
                p.sendMessage(ChatColor.RED + "Emberstorm on cooldown: "
                        + cd.getRemainingCooldown(p, "Emberstorm") + "s");
                return;
            }

            if (!mana.consumeMana(p, adjustedManaCost)) {
                p.sendMessage(ChatColor.RED + "Not enough mana for Emberstorm! (Need " + adjustedManaCost + ")");
                return;
            }

            // Start charging
            p.playSound(p.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.7f, 1.2f);
            EmberstormAbility.chargingPlayers.put(p.getUniqueId(), 0);
        } else {
            // Cancel charging
            if (EmberstormAbility.chargingPlayers.containsKey(p.getUniqueId())) {
                EmberstormAbility.chargingPlayers.remove(p.getUniqueId());
                mana.restoreMana(p, adjustedManaCost);
                p.sendMessage(ChatColor.RED + "Charge cancelled!");
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.5f);
            }
        }
    }

    private void handleCharging() {
        for (UUID uuid : new HashMap<>(EmberstormAbility.chargingPlayers).keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                EmberstormAbility.chargingPlayers.remove(uuid);
                continue;
            }

            int progress = EmberstormAbility.chargingPlayers.get(uuid) + 1;

            // Enhanced charging particles
            if (progress % 2 == 0) {
                Location loc = p.getLocation();
                World world = p.getWorld();
                double angle = (System.currentTimeMillis() % 1000) * Math.PI * 2 / 1000;

                // Rotating flames around player (3 flames orbiting)
                for (int i = 0; i < 3; i++) {
                    double orbAngle = angle + (i * Math.PI * 2 / 3);
                    double distance = 1.0 + (progress / (double)ability.getChargeTime()) * 0.5;
                    double x = Math.cos(orbAngle) * distance;
                    double z = Math.sin(orbAngle) * distance;

                    world.spawnParticle(Particle.FLAME,
                            loc.clone().add(x, 1.0, z),
                            2, 0.1, 0.1, 0.1, 0.01);
                }

                // Rising embers as charge progress increases
                if (rand.nextInt(3) == 0) {
                    double emberAngle = rand.nextDouble() * Math.PI * 2;
                    double emberDistance = rand.nextDouble() * 1.5;
                    double x = Math.cos(emberAngle) * emberDistance;
                    double z = Math.sin(emberAngle) * emberDistance;

                    world.spawnParticle(Particle.LAVA,
                            loc.clone().add(x, 0.1, z),
                            1, 0.05, 0.05, 0.05, 0);
                }

                // Ground fire circle that grows with charge progress
                if (progress % 5 == 0) {
                    double chargePercent = (double) progress / ability.getChargeTime();
                    double currentRadius = 1.0 + chargePercent;

                    for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
                        double x = Math.cos(i) * currentRadius;
                        double z = Math.sin(i) * currentRadius;
                        world.spawnParticle(Particle.FLAME,
                                loc.clone().add(x, 0.1, z),
                                1, 0.05, 0.05, 0.05, 0);

                        // Add smoke as secondary effect
                        if (progress % 10 == 0) {
                            world.spawnParticle(Particle.SMOKE,
                                    loc.clone().add(x, 0.3, z),
                                    1, 0.05, 0.05, 0.05, 0);
                        }
                    }
                }
            }

            // Complete charge
            if (progress >= ability.getChargeTime()) {
                EmberstormAbility.chargingPlayers.remove(uuid);
                ability.activate(p);
                int adjustedCooldown = ability.getAdjustedCooldown(p);
                cd.setCooldown(p, "Emberstorm", adjustedCooldown);
                p.sendMessage(ChatColor.GOLD + "Emberstorm unleashed!");

                // Enhanced activation effects
                World world = p.getWorld();
                Location loc = p.getLocation();

                // Create expanding ring of flames
                for (double r = 0.5; r <= 3.0; r += 0.5) {
                    final double radius = r;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (double i = 0; i < Math.PI * 2; i += Math.PI / 16) {
                            double x = Math.cos(i) * radius;
                            double z = Math.sin(i) * radius;
                            world.spawnParticle(Particle.FLAME,
                                    loc.clone().add(x, 0.1, z),
                                    1, 0.1, 0.1, 0.1, 0.01);
                        }
                    }, (long)(r * 2));
                }

                // Fireball explosion effect
                world.spawnParticle(Particle.EXPLOSION,
                        loc, 3, 0.5, 0.5, 0.5, 0.1);

                // Fire pillar effect
                for (int i = 0; i < 30; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    double distance = rand.nextDouble() * 2.0;
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    double y = rand.nextDouble() * 3.0;

                    world.spawnParticle(Particle.FLAME,
                            loc.clone().add(x, y, z),
                            2, 0.1, 0.1, 0.1, 0.05);
                }

                world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            } else {
                // Update progress
                EmberstormAbility.chargingPlayers.put(uuid, progress);
                p.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.GOLD + "Charging: " +
                                        String.format("%.1fs", progress / 20.0) + "/" +
                                        (ability.getChargeTime() / 20.0) + "s"
                        )
                );
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (EmberstormAbility.chargingPlayers.containsKey(p.getUniqueId())) {
            int adjustedManaCost = ability.getAdjustedManaCost(p);
            mana.restoreMana(p, adjustedManaCost);
            EmberstormAbility.chargingPlayers.remove(p.getUniqueId());
        }
    }
}


package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class WardingSigilAbility implements Ability {
    // Configuration values
    private int cooldown = 25;
    private int manaCost = 35;
    private String requiredClass = "runesmith";
    private int chargeTime = 20; // 1 second (20 ticks)
    private int shieldDuration = 600; // 30 seconds (20 ticks * 30)
    private int numberOfShields = 2;

    // Tracking maps
    public static final Map<UUID, Integer> chargingPlayers = new HashMap<>();
    public static final Map<UUID, Integer> activeShields = new HashMap<>();
    public static final Set<UUID> playersWithShield = new HashSet<>();

    // Shield particle matrix (8x8 shield shape)
    private final boolean[][] shieldMatrix = {
            {false, false, true, true, true, true, false, false},
            {false, true, true, true, true, true, true, false},
            {true, true, true, true, true, true, true, true},
            {true, true, true, false, false, true, true, true},
            {true, true, true, false, false, true, true, true},
            {true, true, true, true, true, true, true, true},
            {false, true, true, true, true, true, true, false},
            {false, false, true, true, true, true, false, false}
    };

    @Override
    public String getName() { return "WardingSigil"; }

    @Override
    public String getDescription() { return "Create magical shield runes that orbit and protect you"; }

    @Override
    public int getCooldown() { return cooldown; }

    @Override
    public int getManaCost() { return manaCost; }

    @Override
    public String getRequiredClass() { return requiredClass; }

    public int getChargeTime() { return chargeTime; }

    public int getShieldDuration() { return shieldDuration; }

    public int getNumberOfShields() { return numberOfShields; }

    @Override
    public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        World world = player.getWorld();
        // Play activation sound
        world.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        // Set the number of active shields for this player
        activeShields.put(player.getUniqueId(), numberOfShields);
        playersWithShield.add(player.getUniqueId());

        // (Initial particle effect removed for clarity)

        // Start shield orbiting effect
        new BukkitRunnable() {
            int ticks = 0;
            final double orbitRadius = 2.0;               // larger orbit radius
            double baseAngle = 0;
            double selfSpin = 0;
            final double orbitSpeed = 0.1;
            final double spinSpeed = 0.3;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ >= shieldDuration ||
                        !playersWithShield.contains(player.getUniqueId())) {
                    // Cleanup
                    if (player.isOnline() && playersWithShield.contains(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "WardingSigil has expired!");
                        playersWithShield.remove(player.getUniqueId());
                        activeShields.remove(player.getUniqueId());
                        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.2f);
                    }
                    cancel();
                    return;
                }

                // Display remaining duration on action bar
                int remainingSeconds = (shieldDuration - ticks) / 20;
                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.AQUA + "Shield: " + activeShields.getOrDefault(player.getUniqueId(), 0)
                                        + " shields, " + remainingSeconds + "s remaining"
                        )
                );

                // Update rotation angles
                baseAngle += orbitSpeed;
                if (baseAngle > Math.PI * 2) baseAngle -= Math.PI * 2;

                selfSpin += spinSpeed;
                if (selfSpin > Math.PI * 2) selfSpin -= Math.PI * 2;

                // Display orbiting and spinning shields
                int shields = activeShields.getOrDefault(player.getUniqueId(), 0);
                for (int i = 0; i < shields; i++) {
                    double shieldAngle = baseAngle + (Math.PI * 2 * i) / shields;
                    double x = Math.cos(shieldAngle) * orbitRadius;
                    double z = Math.sin(shieldAngle) * orbitRadius;

                    Location shieldLoc = player.getLocation().clone().add(x, 1.0, z);
                    displayShield(shieldLoc, shieldAngle + selfSpin);
                }
            }

            private void displayShield(Location center, double angle) {
                float scale = 0.2f; // even bigger shields
                int cols = shieldMatrix[0].length, rows = shieldMatrix.length;
                double totalW = scale * cols;
                double startX = -totalW / 2 + (scale / 2);
                double baseY = 0.5;

                for (int row = 0; row < rows; row++) {
                    double y = baseY - (row * scale);
                    for (int col = 0; col < cols; col++) {
                        if (shieldMatrix[row][col]) {
                            Vector v = new Vector(startX + col * scale, y, 0);
                            v = rotateAroundAxisY(v, angle);
                            center.getWorld().spawnParticle(
                                    Particle.DUST,
                                    center.clone().add(v),
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(100, 200, 255), 0.7f)
                            );
                        }
                    }
                }

                // Runes
                for (int i = 0; i < 3; i++) {
                    Vector runeVec = new Vector((Math.random() * 0.2) - 0.1, (Math.random() * 0.2) - 0.1, 0);
                    runeVec = rotateAroundAxisY(runeVec, angle + selfSpin);
                    center.getWorld().spawnParticle(
                            Particle.DUST,
                            center.clone().add(runeVec),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 230, 100), 0.7f)
                    );
                }
            }

            private Vector rotateAroundAxisY(Vector v, double angle) {
                double cos = Math.cos(angle), sin = Math.sin(angle);
                return new Vector(v.getX() * cos + v.getZ() * sin, v.getY(), v.getX() * -sin + v.getZ() * cos);
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
    }

    @Override public boolean isSuccessful() { return true; }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.warding-sigil.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        chargeTime = cfg.getInt(base + "charge-time", chargeTime);
        shieldDuration = cfg.getInt(base + "shield-duration", shieldDuration);
        numberOfShields = cfg.getInt(base + "number-of-shields", numberOfShields);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§c%s §4was somehow defeated by §c%s§4's WardingSigil!", victim, caster);
    }

    public static boolean consumeShield(Player player) {
        UUID playerId = player.getUniqueId();
        if (!playersWithShield.contains(playerId)) return false;
        int shields = activeShields.getOrDefault(playerId, 0);
        if (shields <= 0) {
            playersWithShield.remove(playerId);
            return false;
        }
        shields--;
        if (shields <= 0) {
            activeShields.remove(playerId);
            playersWithShield.remove(playerId);
            player.sendMessage(ChatColor.RED + "All WardingSigil shields have been consumed!");
        } else {
            activeShields.put(playerId, shields);
            player.sendMessage(ChatColor.AQUA + "WardingSigil absorbed damage! " + shields + " shield" + (shields>1?"s":"") + " remaining.");
        }
        Location loc = player.getLocation().add(0,1,0);
        player.getWorld().playSound(loc, Sound.ITEM_SHIELD_BREAK,1.0f,1.5f);
        return true;
    }
}

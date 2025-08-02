package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class RadiantDashAbility implements Ability {

    private String name = "RadiantDash";
    private String description = "Spread your radiant wings and take flight, purging negative effects.";
    private int cooldown = 9;
    private int manaCost = 25;
    private String requiredClass = "lightbringer";
    private double flightSpeed = 0.8;
    private int maxFlightTicks = 48;
    private boolean negateDebuffs = true;
    private boolean successfulActivation = false;

    // Adjusted wing settings
    private double wingScale   = 1.1;   // slightly larger than default
    private double wingSpacing = 0.2;   // moderate spacing

    private final Particle.DustOptions trailParticleOptions =
            new Particle.DustOptions(Color.fromRGB(255,215,0), 1.0f);
    private final Particle.DustOptions wingParticleOptions =
            new Particle.DustOptions(Color.fromRGB(255,223,0), 1.2f);

    private static final Set<PotionEffectType> NEGATIVE_EFFECTS = new HashSet<>(Arrays.asList(
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA,
            PotionEffectType.HUNGER,
            PotionEffectType.POISON,
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.WEAKNESS,
            PotionEffectType.WITHER,
            PotionEffectType.UNLUCK,
            PotionEffectType.BAD_OMEN
    ));

    // Wing bitmap (15×16)
    private static final boolean[][] WING_SHAPE = {
            {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false},
            {false,false,false,true ,false,false,false,false,false,false,false,false,true ,false,false,false},
            {false,false,true ,true ,false,false,false,false,false,false,false,false,true ,true ,false,false},
            {false,true ,true ,true ,true ,false,false,false,false,false,false,true ,true ,true ,true ,false},
            {false,true ,true ,true ,true ,false,false,false,false,false,false,true ,true ,true ,true ,false},
            {false,false,true ,true ,true ,true ,false,false,false,false,true ,true ,true ,true ,false,false},
            {false,false,false,true ,true ,true ,true ,false,false,true ,true ,true ,true ,false,false,false},
            {false,false,false,false,true ,true ,true ,true ,true ,true ,true ,true ,false,false,false,false},
            {false,false,false,false,false,true ,true ,true ,true ,true ,true ,false,false,false,false,false},
            {false,false,false,false,false,false,true ,true ,true ,true,false,false,false,false,false,false},
            {false,false,false,false,false,true ,true ,false,false,true ,true ,false,false,false,false,false},
            {false,false,false,false,true ,true ,true ,false,false,true ,true ,true ,false,false,false,false},
            {false,false,false,false,true ,true ,false,false,false,false,true ,true ,false,false,false,false},
            {false,false,false,false,true ,false,false,false,false,false,false,true ,false,false,false,false},
            {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false},
    };

    // Track pre-dash flight state
    private final Map<UUID, Boolean> wasFlying     = new HashMap<>();
    private final Map<UUID, Boolean> allowedFlight = new HashMap<>();

    @Override public String getName()          { return name; }
    @Override public String getDescription()   { return description; }
    @Override public int    getCooldown()      { return cooldown; }
    @Override public int    getManaCost()      { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();

        // Remember original flight perms
        allowedFlight.put(uuid, player.getAllowFlight());
        wasFlying.put(uuid,     player.isFlying());

        successfulActivation = true;

        // Grant dash-only flight
        player.setAllowFlight(true);
        player.setFlying(true);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, .8f, 1.5f);

        if (negateDebuffs) {
            for (PotionEffect e : player.getActivePotionEffects()) {
                if (NEGATIVE_EFFECTS.contains(e.getType())) {
                    player.removePotionEffect(e.getType());
                }
            }
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
        }

        new BukkitRunnable() {
            private int ticksFlown = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ticksFlown >= maxFlightTicks) {
                    // Restore original flight perms
                    boolean origAllow = allowedFlight.remove(uuid);
                    boolean origFly   = wasFlying.remove(uuid);

                    player.setAllowFlight(origAllow);
                    player.setFlying(origFly && origAllow);

                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, .7f, 1.2f);
                    cancel();
                    return;
                }

                // Propel forward
                Vector dir = player.getLocation().getDirection().normalize();
                player.setVelocity(dir.multiply(flightSpeed));

                // Trail particles
                if (ticksFlown % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST,
                            player.getLocation().subtract(0, .2, 0),
                            3, .1, .1, .1, 0, trailParticleOptions);
                }

                // Wing particles
                spawnWingParticles(player);
                ticksFlown++;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    @Override
    public boolean isSuccessful() {
        return successfulActivation;
    }

    private void spawnWingParticles(Player player) {
        Location loc = player.getLocation();
        int cols = WING_SHAPE[0].length, rows = WING_SHAPE.length;

        // Compute centered start X
        double totalW = wingSpacing * cols * wingScale;
        double startX = loc.getX() - totalW / 2 + (wingSpacing * wingScale) / 2;

        // Base height scaled
        double baseY = loc.getY() + (2.5 * wingScale);

        float yaw = loc.getYaw();
        double rot = -((yaw + 180) / 60) + (yaw < -180 ? 3.25 : 2.985);

        for (int row = 0; row < rows; row++) {
            double y = baseY - (row * wingSpacing * wingScale);
            double x = startX;

            for (int col = 0; col < cols; col++) {
                if (WING_SHAPE[row][col]) {
                    Vector v = new Vector(x - loc.getX(), y - loc.getY(), 0)
                            .multiply(wingScale);
                    v = rotateAroundAxisY(v, rot);

                    Vector back = getBackVector(loc)
                            .setY(0)
                            .multiply(-0.5 * wingScale);

                    Location spawn = loc.clone().add(v).add(back);
                    player.getWorld().spawnParticle(
                            Particle.DUST, spawn, 1, 0, 0, 0, 0, wingParticleOptions
                    );
                }
                x += wingSpacing * wingScale;
            }
        }
    }

    public static Vector rotateAroundAxisY(Vector v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double x = v.getX()*cos + v.getZ()*sin;
        double z = v.getX()*-sin + v.getZ()*cos;
        return v.setX(x).setZ(z);
    }

    public static Vector getBackVector(Location loc) {
        double rad = Math.toRadians(loc.getYaw() + 90);
        float newZ = (float)(loc.getZ() + Math.sin(rad));
        float newX = (float)(loc.getX() + Math.cos(rad));
        return new Vector(newX - loc.getX(), 0, newZ - loc.getZ());
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.radiantdash.";
        name           = cfg.getString(base + "name", name);
        description    = cfg.getString(base + "description", description);
        cooldown       = cfg.getInt(base + "cooldown", cooldown);
        manaCost       = cfg.getInt(base + "mana-cost", manaCost);
        requiredClass  = cfg.getString(base + "required-class", requiredClass);
        flightSpeed    = cfg.getDouble(base + "flight-speed", flightSpeed);
        maxFlightTicks = cfg.getInt(base + "max-flight-ticks", maxFlightTicks);
        negateDebuffs  = cfg.getBoolean(base + "negate-debuffs", negateDebuffs);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format(
                "§e%s §7was overwhelmed by §e%s§7's radiant energy.",
                victimName, casterName
        );
    }
}
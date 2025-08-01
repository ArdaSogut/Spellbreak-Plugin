package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class RunecarverAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 40;
    private String requiredClass = "runesmith";
    private double range = 20.0;
    private double damage = 2.0;
    private double speed = 0.9;
    private double hitboxRadius = 1.2;
    private double bladeSpacing = 2.7;
    private double bladeHeight = 1.7; // Height above ground for blade center
    private double maxReturnTime = 10.0; // Max seconds before blades despawn
    private static final Map<UUID, RuneBladePair> activeBlades = new HashMap<>();

    @Override
    public String getName() {
        return "Runecarver";
    }

    @Override
    public String getDescription() {
        return "Send forth twin runic blades that return to you after hitting an enemy or reaching maximum range";
    }

    @Override
    public int getCooldown() {
        return cooldown;
    }

    @Override
    public int getManaCost() {
        return manaCost;
    }

    @Override
    public String getRequiredClass() {
        return requiredClass;
    }

    @Override
    public boolean isTriggerAction(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public void activate(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone().normalize();

        world.playSound(eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ()).normalize();
        Vector rightVec = new Vector(-horizontal.getZ(), 0, horizontal.getX()).normalize();

        Location leftStart = eye.clone().add(rightVec.clone().multiply(bladeSpacing / 2));
        Location rightStart = eye.clone().add(rightVec.clone().multiply(-bladeSpacing / 2));

        RuneBladePair bladePair = new RuneBladePair(player, leftStart, rightStart, direction);
        activeBlades.put(player.getUniqueId(), bladePair);
        bladePair.launch();

        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(
                        ChatColor.GOLD + "Runecarver activated!"
                )
        );
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.runecarver.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        range = cfg.getDouble(base + "range", range);
        damage = cfg.getDouble(base + "damage", damage);
        speed = cfg.getDouble(base + "speed", speed);
        hitboxRadius = cfg.getDouble(base + "hitbox-radius", hitboxRadius);
        bladeSpacing = cfg.getDouble(base + "blade-spacing", bladeSpacing);
        bladeHeight = cfg.getDouble(base + "blade-height", bladeHeight);
        maxReturnTime = cfg.getDouble(base + "max-return-time", maxReturnTime);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§c%s §4was sliced by §c%s§4's Runecarver blades!", victim, caster);
    }

    public static void removeBlades(UUID playerId) {
        RuneBladePair bladePair = activeBlades.remove(playerId);
        if (bladePair != null) bladePair.cancel();
    }

    private class RuneBladePair {
        private final Player owner;
        private final RuneBlade leftBlade;
        private final RuneBlade rightBlade;
        private boolean returning = false;
        private final Set<UUID> hitSet = new HashSet<>();
        private int taskId;
        private long startTime;

        RuneBladePair(Player owner, Location leftStart, Location rightStart, Vector direction) {
            this.owner = owner;
            this.leftBlade = new RuneBlade(leftStart, direction);
            this.rightBlade = new RuneBlade(rightStart, direction);
            this.startTime = System.currentTimeMillis();
        }

        void launch() {
            taskId = new BukkitRunnable() {
                @Override
                public void run() {
                    // Check if player is still online
                    if (!owner.isOnline() || owner.isDead()) {
                        cancel();
                        return;
                    }

                    // Check max duration
                    if ((System.currentTimeMillis() - startTime) / 1000.0 > maxReturnTime) {
                        cancel();
                        return;
                    }

                    // Move both blades
                    leftBlade.move();
                    rightBlade.move();

                    // Render both blades
                    float angle = (float) (Math.sin(System.currentTimeMillis() * 0.005) * Math.PI / 4); // swing ±45 degrees

                    leftBlade.render(returning, true, angle);
                    rightBlade.render(returning, false, angle);

                    // Check for hits on both blades (during forward AND return)
                    boolean hitSomething = checkHits();

                    // Check if blades should start returning
                    if (!returning) {
                        double leftDist = leftBlade.getLocation().distance(owner.getEyeLocation());
                        double rightDist = rightBlade.getLocation().distance(owner.getEyeLocation());
                        if (hitSomething || Math.max(leftDist, rightDist) >= range) {
                            startReturn();
                        }
                    }

                    // Handle return logic
                    if (returning) {
                        Location ownerChest = owner.getLocation().add(0, owner.getEyeHeight() * 0.5, 0);

                        // Maintain blade spacing while returning
                        Vector baseDirection = ownerChest.toVector().subtract(leftBlade.getLocation().add(rightBlade.getLocation()).multiply(0.5).toVector()).normalize();
                        Vector rightVec = new Vector(-baseDirection.getZ(), 0, baseDirection.getX()).normalize();

                        Vector leftTarget = ownerChest.toVector().add(rightVec.clone().multiply(bladeSpacing / 2));
                        Vector rightTarget = ownerChest.toVector().add(rightVec.clone().multiply(-bladeSpacing / 2));

                        leftBlade.updateDirection(leftTarget);
                        rightBlade.updateDirection(rightTarget);

                        // Check if both blades have returned
                        if (leftBlade.getLocation().distance(ownerChest) < 1.5 &&
                                rightBlade.getLocation().distance(ownerChest) < 1.5) {
                            owner.getWorld().playSound(ownerChest, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                            owner.getWorld().spawnParticle(Particle.WITCH, ownerChest, 15, 0.3, 0.5, 0.3, 0.05);
                            cancel();
                        }
                    }
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0, 1).getTaskId();
        }

        private boolean checkHits() {
            boolean hitSomething = false;

            // Check hits for both blades
            for (RuneBlade blade : Arrays.asList(leftBlade, rightBlade)) {
                Location loc = blade.getLocation();
                for (Entity e : loc.getWorld().getNearbyEntities(loc, hitboxRadius, hitboxRadius, hitboxRadius)) {
                    if (!(e instanceof LivingEntity) || e.equals(owner)) continue;
                    if (!hitSet.add(e.getUniqueId())) continue;

                    Spellbreak.getInstance()
                            .getAbilityDamage()
                            .damage((LivingEntity) e, damage, owner, RunecarverAbility.this, null);
                    e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
                    e.getWorld().spawnParticle(Particle.CRIT, e.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.3);

                    if (e instanceof Player) {
                        owner.sendMessage(ChatColor.GOLD + "Your blade hit " + ChatColor.RED
                                + ((Player) e).getName() + ChatColor.GOLD + " for " + ChatColor.RED + damage + ChatColor.GOLD + " dmg!");
                    }
                    hitSomething = true;
                }
            }
            return hitSomething;
        }

        private void startReturn() {
            if (!returning) {
                returning = true;
                Location leftLoc = leftBlade.getLocation();
                Location rightLoc = rightBlade.getLocation();
                leftLoc.getWorld().playSound(leftLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.5f);
                rightLoc.getWorld().playSound(rightLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.5f);
                leftLoc.getWorld().spawnParticle(Particle.FLASH, leftLoc, 1, 0, 0, 0, 0);
                rightLoc.getWorld().spawnParticle(Particle.FLASH, rightLoc, 1, 0, 0, 0, 0);
            }
        }

        void cancel() {
            Bukkit.getScheduler().cancelTask(taskId);
            activeBlades.remove(owner.getUniqueId());
        }
    }

    private class RuneBlade {
        private Location loc;
        private Vector dir;
        private double rotationAngle = 0;
        private final double bladeRadius = 1.2;
        private final double rotationSpeed = 0.11;

        RuneBlade(Location start, Vector direction) {
            this.loc = start.clone();
            this.dir = direction.clone().normalize();
            adjustHeightAboveGround();
        }

        void move() {
            loc.add(dir.clone().multiply(speed));
            adjustHeightAboveGround();
            rotationAngle += rotationSpeed;
            if (rotationAngle > Math.PI * 2) rotationAngle -= Math.PI * 2;
        }

        private void adjustHeightAboveGround() {
            double groundY = loc.getWorld().getHighestBlockYAt(loc);
            loc.setY(groundY + bladeHeight);
        }

        void updateDirection(Vector targetPos) {
            dir = targetPos.subtract(loc.toVector()).normalize();
        }

        Location getLocation() {
            return loc.clone();
        }

        void render(boolean returning, boolean isLeftBlade, float angle) {
            World w = loc.getWorld();
            if (w == null) return;

            int particleCount = 10;
            double lineHeight = 1.5;
            double lineLength = 1.5;

            Color col = returning ? Color.fromRGB(255, 100, 40) : Color.fromRGB(40, 120, 255);
            double sideOffsetX = isLeftBlade ? -0.3 : 0.3;

            // We'll spawn particles for both vertical and horizontal line,
            // then rotate each particle point by angle around X-axis.

            // Vertical line particles
            for (int i = 0; i < particleCount; i++) {
                double y = -lineHeight / 2 + (lineHeight * i / (particleCount - 1));
                double z = 0;

                // Rotate around X axis:
                double rotatedY = y * Math.cos(angle) - z * Math.sin(angle);
                double rotatedZ = y * Math.sin(angle) + z * Math.cos(angle);

                Location pLoc = loc.clone().add(sideOffsetX, rotatedY, rotatedZ);

                w.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(col, 1.2f));
            }

            // Horizontal line particles
            for (int i = 0; i < particleCount; i++) {
                double y = 0;
                double z = -lineLength / 2 + (lineLength * i / (particleCount - 1));

                // Rotate around X axis:
                double rotatedY = y * Math.cos(angle) - z * Math.sin(angle);
                double rotatedZ = y * Math.sin(angle) + z * Math.cos(angle);

                Location pLoc = loc.clone().add(sideOffsetX, rotatedY, rotatedZ);

                w.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(col, 1.2f));
            }

            // Ground contact effect (optional)
            Location groundContact = loc.clone();
            groundContact.setY(loc.getWorld().getHighestBlockYAt(loc) + 0.1);
            w.spawnParticle(Particle.DUST, groundContact, 2, 0.2, 0, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 200, 200), 0.8f));
        }
    }
}
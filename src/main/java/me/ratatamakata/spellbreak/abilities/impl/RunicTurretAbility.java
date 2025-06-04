package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;

public class RunicTurretAbility implements Ability, Listener {
    private int cooldown = 25;
    private int manaCost = 60;
    private String requiredClass = "runesmith";
    private double damageBase = 3.0;
    private double damageFire = 4.0;
    private double damageIce = 2.5;
    private int turretDuration = 20; // seconds
    private double attackRadius = 8.0;
    private double attackCooldown = 2.0; // seconds between attacks (unified cooldown)
    private double fireRuneSlowness = 3.0; // seconds
    private double iceRuneSlowness = 5.0; // seconds
    private int fireBurnDuration = 3; // seconds
    private double projectileSpeed = 0.5;
    private double projectileRadius = 1.5;
    private int customModelData = 1001; // Configurable custom model ID

    private static final Map<UUID, Set<RunicTurret>> activeTurrets = new HashMap<>();

    @Override
    public String getName() {
        return "RunicCannon";
    }

    @Override
    public String getDescription() {
        return "Summon a runic cannon that attacks nearby enemies with three different types of rune projectiles";
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
        return (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) &&
                Bukkit.getServer().getPlayer(activeTurrets.keySet().iterator().next()).isSneaking();
    }

    @Override
    public void activate(Player player) {
        Location spawnLocation = player.getLocation().clone();

        // Make the turret spawn slightly above the player to allow for falling
        spawnLocation.add(0, 0.5, 0);

        // Play spawn sound and particles
        player.getWorld().playSound(spawnLocation, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.getWorld().playSound(spawnLocation, Sound.BLOCK_STONE_PLACE, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.ENCHANT, spawnLocation.clone().add(0, 1, 0),
                40, 0.5, 1, 0.5, 0.1);

        // Create and register the turret
        RunicTurret turret = new RunicTurret(player, spawnLocation);

        // Store the turret in the player's active turrets list
        activeTurrets.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(turret);

        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(
                        ChatColor.GOLD + "Runic Cannon deployed! Duration: " +
                                ChatColor.RED + turretDuration + ChatColor.GOLD + "s"
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
        String base = "abilities.runicturret.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        turretDuration = cfg.getInt(base + "duration", turretDuration);
        attackRadius = cfg.getDouble(base + "attack-radius", attackRadius);
        attackCooldown = cfg.getDouble(base + "attack-cooldown", attackCooldown);
        damageBase = cfg.getDouble(base + "damage-base", damageBase);
        damageFire = cfg.getDouble(base + "damage-fire", damageFire);
        damageIce = cfg.getDouble(base + "damage-ice", damageIce);
        fireRuneSlowness = cfg.getDouble(base + "fire-rune-slowness", fireRuneSlowness);
        iceRuneSlowness = cfg.getDouble(base + "ice-rune-slowness", iceRuneSlowness);
        fireBurnDuration = cfg.getInt(base + "fire-burn-duration", fireBurnDuration);
        projectileSpeed = cfg.getDouble(base + "projectile-speed", projectileSpeed);
        projectileRadius = cfg.getDouble(base + "projectile-radius", projectileRadius);
        customModelData = cfg.getInt(base + "custom-model-data", customModelData);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§c%s §4was obliterated by §c%s§4's Runic Cannon!", victim, caster);
    }

    public static void removeTurrets(UUID playerId) {
        Set<RunicTurret> turrets = activeTurrets.remove(playerId);
        if (turrets != null) {
            turrets.forEach(RunicTurret::destroy);
        }
    }

    public static void removeTurret(UUID playerId, RunicTurret turret) {
        Set<RunicTurret> turrets = activeTurrets.get(playerId);
        if (turrets != null) {
            turrets.remove(turret);
            if (turrets.isEmpty()) {
                activeTurrets.remove(playerId);
            }
        }
    }

    // Handle damage to turrets
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;

        ArmorStand stand = (ArmorStand) event.getEntity();

        // Cancel fire damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
                event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
                event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            event.setCancelled(true);
            return;
        }

        // Check if this is one of our turrets
        for (Set<RunicTurret> turrets : activeTurrets.values()) {
            for (RunicTurret turret : turrets) {
                if (turret.getArmorStand().equals(stand)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    public class RunicTurret {
        private final Player owner;
        private final Location location;
        private final ArmorStand armorStand;
        private final BukkitTask lifetimeTask;
        private BukkitTask attackTask;
        private int attackPattern = 0; // 0 = base, 1 = fire, 2 = ice
        private long spawnTime;
        private boolean isActive = true;
        private boolean isLanded = false;

        public RunicTurret(Player owner, Location location) {
            this.owner = owner;
            this.location = location.clone();
            this.spawnTime = System.currentTimeMillis();

            // Create armor stand
            this.armorStand = location.getWorld().spawn(location, ArmorStand.class);
            setupArmorStand();

            // Start lifetime countdown
            this.lifetimeTask = new BukkitRunnable() {
                @Override
                public void run() {
                    destroy();
                }
            }.runTaskLater(Spellbreak.getInstance(), turretDuration * 20L);

            // Check landing and start attack after landing
            new BukkitRunnable() {
                int ticksAlive = 0;

                @Override
                public void run() {
                    ticksAlive++;

                    if (!isActive || armorStand == null || armorStand.isDead()) {
                        cancel();
                        return;
                    }

                    // Check if landed
                    if (!isLanded) {
                        Block below = armorStand.getLocation().clone().subtract(0, 0.1, 0).getBlock();
                        if (below.getType().isSolid() || ticksAlive > 100) { // Safety in case it gets stuck
                            isLanded = true;
                            startAttacking();
                            cancel();
                        }
                    }
                }
            }.runTaskTimer(Spellbreak.getInstance(), 1L, 1L);
        }

        private void setupArmorStand() {
            armorStand.setVisible(false); // Make invisible for custom model
            armorStand.setBasePlate(false);
            armorStand.setArms(false);
            armorStand.setGravity(true);
            armorStand.setSmall(false);
            armorStand.setInvulnerable(true);
            armorStand.setCustomNameVisible(true);


            // Custom helmet with model data
            ItemStack helmet = new ItemStack(Material.PAPER);
            ItemMeta meta = helmet.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("Runic Cannon");
                meta.setCustomModelData(customModelData);
                helmet.setItemMeta(meta);
            }
            armorStand.getEquipment().setHelmet(helmet);

            // Clear other equipment
            armorStand.getEquipment().setChestplate(null);
            armorStand.getEquipment().setLeggings(null);
            armorStand.getEquipment().setBoots(null);
            armorStand.getEquipment().setItemInMainHand(null);
            armorStand.setCustomName("Runic Cannon");
        }

        public void destroy() {
            if (!isActive) return;
            isActive = false;

            // Cancel all tasks
            if (lifetimeTask != null) lifetimeTask.cancel();
            if (attackTask != null) attackTask.cancel();

            // Clear equipment before removal
            armorStand.getEquipment().clear();

            // Play destruction effect
            armorStand.getWorld().playSound(armorStand.getLocation(),
                    Sound.BLOCK_ANVIL_DESTROY, 1.0f, 0.8f);
            armorStand.getWorld().spawnParticle(Particle.EXPLOSION,
                    armorStand.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.1);

            // Remove armor stand
            armorStand.remove();

            // Remove from player's active turrets
            removeTurret(owner.getUniqueId(), this);
        }


        private void startAttacking() {
            // Start attack cycle with fixed timing
            this.attackTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isActive || armorStand == null || armorStand.isDead()) {
                        cancel();
                        return;
                    }

                    findAndAttackTarget();

                    // Next attack pattern
                    attackPattern = (attackPattern + 1) % 3;
                }
            }.runTaskTimer(Spellbreak.getInstance(), 20L, (long)(attackCooldown * 20));
        }

        private void findAndAttackTarget() {
            LivingEntity target = findNearestTarget();
            if (target == null) return;

            // Rotate armor stand to face target
            Location standLoc = armorStand.getLocation();
            Vector direction = target.getLocation().toVector().subtract(standLoc.toVector()).normalize();
            standLoc.setDirection(direction);
            armorStand.teleport(standLoc);

            // Update arm pose to point forward like a cannon barrel
            double pitch = -Math.toRadians(standLoc.getPitch());
            double yaw = Math.toRadians(standLoc.getYaw());
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(270), 0, 0));

            // Rotate the "blast furnace" in the hand to aim at target
            armorStand.setHeadPose(new EulerAngle(pitch, 0, 0));

            // Fire projectile at target
            fireProjectileAtTarget(target);
        }

        private LivingEntity findNearestTarget() {
            LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Entity entity : armorStand.getNearbyEntities(attackRadius, attackRadius, attackRadius)) {
                if (!(entity instanceof LivingEntity) ||
                        entity.equals(owner) ||
                        entity.equals(armorStand) ||
                        (entity instanceof Player && !owner.canSee((Player) entity))) {
                    continue;
                }

                double distance = entity.getLocation().distance(armorStand.getLocation());
                if (distance < nearestDistance) {
                    nearest = (LivingEntity) entity;
                    nearestDistance = distance;
                }
            }

            return nearest;
        }

        private void fireProjectileAtTarget(LivingEntity target) {
            // Fire from cannon barrel position
            Location turretHead = armorStand.getLocation().clone().add(0, 1.6, 0);
            // Adjust position to be in front of the armor stand
            Vector frontDir = turretHead.getDirection().normalize().multiply(0.5);
            turretHead.add(frontDir);
            Location targetLoc = target.getLocation().clone().add(0, 1, 0);
            Vector direction = targetLoc.toVector().subtract(turretHead.toVector()).normalize();

            // Store initial target location for hit detection
            final Location initialTargetLoc = target.getLocation().clone();

            // Determine attack type
            Color projectileColor;
            Sound launchSound;

            switch (attackPattern) {
                case 0: // Base (Fast)
                    projectileColor = Color.fromRGB(0, 200, 255);
                    launchSound = Sound.ENTITY_ARROW_SHOOT;
                    break;
                case 1: // Fire (Slow)
                    projectileColor = Color.fromRGB(255, 100, 0);
                    launchSound = Sound.BLOCK_FIRE_AMBIENT;
                    break;
                case 2: // Ice (Medium)
                    projectileColor = Color.fromRGB(200, 200, 255);
                    launchSound = Sound.BLOCK_GLASS_BREAK;
                    break;
                default:
                    projectileColor = Color.WHITE;
                    launchSound = Sound.ENTITY_ARROW_SHOOT;
            }

            // Play launch sound
            armorStand.getWorld().playSound(turretHead, launchSound, 1.0f, 1.0f);

            // Create projectile effect
            final Vector projectileDir = direction.clone();
            final double speedMultiplier = (attackPattern == 0) ? projectileSpeed * 1.5 :
                    (attackPattern == 1) ? projectileSpeed * 0.7 :
                            projectileSpeed;

            new BukkitRunnable() {
                Location projectileLoc = turretHead.clone();
                int ticks = 0;
                final int maxTicks = 60; // Safety limit

                @Override
                public void run() {
                    // Move projectile
                    projectileLoc.add(projectileDir.clone().multiply(speedMultiplier));
                    ticks++;

                    // Display particle
                    armorStand.getWorld().spawnParticle(Particle.DUST,
                            projectileLoc, 5, 0.1, 0.1, 0.1, 0.01,
                            new Particle.DustOptions(projectileColor, 1.5f));

                    // Additional effects based on attack type
                    switch (attackPattern) {
                        case 0: // Base
                            break;
                        case 1: // Fire
                            armorStand.getWorld().spawnParticle(Particle.FLAME,
                                    projectileLoc, 2, 0.1, 0.1, 0.1, 0.01);
                            break;
                        case 2: // Ice
                            armorStand.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                    projectileLoc, 2, 0.1, 0.1, 0.1, 0.01);
                            break;
                    }

                    // Check for hit
                    boolean hitSomething = false;

                    // Check if we've reached the target's initial position
                    if (projectileLoc.distance(initialTargetLoc) <= projectileRadius) {
                        // Check if target is still in radius of the hit
                        if (target.getLocation().distance(initialTargetLoc) <= projectileRadius * 1.5) {
                            hitTarget(target);
                            hitSomething = true;
                        }
                    }

                    // Check for max distance or hit
                    if (hitSomething ||
                            ticks >= maxTicks ||
                            !projectileLoc.getWorld().equals(turretHead.getWorld()) ||
                            projectileLoc.distance(turretHead) > attackRadius * 1.5) {

                        // Create impact effect even if we didn't hit
                        if (!hitSomething) {
                            createImpactEffect(projectileLoc);
                        }

                        cancel();
                    }
                }

                private void hitTarget(LivingEntity target) {
                    // Determine damage and effects based on attack type
                    double damage;

                    switch (attackPattern) {
                        case 0: // Base (Fast)
                            damage = damageBase;
                            armorStand.getWorld().playSound(target.getLocation(),
                                    Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
                            armorStand.getWorld().spawnParticle(Particle.CRIT,
                                    target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.3);
                            break;

                        case 1: // Fire (Slow)
                            damage = damageFire;
                            armorStand.getWorld().playSound(target.getLocation(),
                                    Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);
                            armorStand.getWorld().spawnParticle(Particle.FLAME,
                                    target.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.1);

                            // Apply fire effect
                            target.setFireTicks(fireBurnDuration * 20);
                            break;

                        case 2: // Ice (Medium)
                            damage = damageIce;
                            armorStand.getWorld().playSound(target.getLocation(),
                                    Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                            armorStand.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                    target.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.05);

                            // Apply slowness effect
                            if (target instanceof LivingEntity) {
                                ((LivingEntity) target).addPotionEffect(
                                        new PotionEffect(PotionEffectType.SLOWNESS,
                                                (int) (iceRuneSlowness * 20), 2));
                            }
                            break;

                        default:
                            damage = damageBase;
                    }

                    // Apply damage
                    Spellbreak.getInstance()
                            .getAbilityDamage()
                            .damage(target, damage, owner, RunicTurretAbility.this, null);

                    createImpactEffect(target.getLocation().add(0, 1, 0));

                    // Send feedback to owner
                    if (target instanceof Player) {
                        owner.sendMessage(ChatColor.GOLD + "Your Runic Turret hit " + ChatColor.RED
                                + ((Player) target).getName() + ChatColor.GOLD + " for " + ChatColor.RED + damage + ChatColor.GOLD + " dmg!");
                    }
                }

                private void createImpactEffect(Location loc) {
                    // Different impact effects based on attack type
                    World world = loc.getWorld();

                    switch (attackPattern) {
                        case 0: // Base (Fast)
                            world.spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.2);
                            world.playSound(loc, Sound.ENTITY_ARROW_HIT, 1.0f, 1.2f);
                            break;

                        case 1: // Fire (Slow)
                            world.spawnParticle(Particle.LAVA, loc, 8, 0.3, 0.3, 0.3, 0);
                            world.spawnParticle(Particle.FLAME, loc, 15, 0.4, 0.4, 0.4, 0.05);
                            world.playSound(loc, Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);
                            break;

                        case 2: // Ice (Medium)
                            world.spawnParticle(Particle.SNOWFLAKE, loc, 20, 0.4, 0.4, 0.4, 0.05);
                            world.spawnParticle(Particle.ITEM_SNOWBALL, loc, 15, 0.3, 0.3, 0.3, 0.1);
                            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                            break;
                    }
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        }

        public Player getOwner() {
            return owner;
        }

        public Location getLocation() {
            return location.clone();
        }

        public ArmorStand getArmorStand() {
            return armorStand;
        }

        public long getTimeRemaining() {
            return Math.max(0, (spawnTime + (turretDuration * 1000) - System.currentTimeMillis()) / 1000);
        }
    }
}
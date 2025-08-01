package me.ratatamakata.spellbreak.abilities.impl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ThunderSlamAbility implements Ability {
    private int cooldown = 13;
    private int manaCost = 80;
    private String requiredClass = "elementalist";
    private double damage = 3.0D;
    private double radius = 5.0D;
    private double launchHeight = 7.0D;
    private double knockUpStrength = 1.2D;
    private double knockBackStrength = 1.5D;
    private int chargeDuration = 4;
    private double minDuration = 0.75D;
    private final Set<UUID> chargingPlayers = new HashSet();
    private final Map<UUID, ThunderSlamAbility.PlayerState> playerStates = new ConcurrentHashMap();
    private final Map<UUID, Long> chargeStartTimes = new ConcurrentHashMap();
    public final Map<UUID, Double> flightYLevels = new ConcurrentHashMap();
    private final DustOptions chargeParticles = new DustOptions(Color.fromRGB(30, 144, 255), 2.0F);
    private final DustOptions darkChargeParticles = new DustOptions(Color.fromRGB(0, 0, 139), 1.5F);
    private final DustOptions cloudParticles = new DustOptions(Color.fromRGB(30, 144, 255), 2.0F);
    private final DustOptions electricParticles = new DustOptions(Color.fromRGB(255, 255, 255), 1.2F);
    private final Map<UUID, Float> originalFlySpeeds = new ConcurrentHashMap<>();

    public String getName() {
        return "ThunderSlam";
    }

    public String getDescription() {
        return "Transform into a storm cloud and slam down with thunderous force!";
    }

    public int getCooldown() {
        return this.cooldown;
    }

    public int getManaCost() {
        return this.manaCost;
    }

    public String getRequiredClass() {
        return this.requiredClass;
    }

    public boolean isTriggerAction(Action action) {
        return false;
    }

    public void activate(Player player) {
        if (!this.chargingPlayers.contains(player.getUniqueId())) {
            this.chargeStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
            ThunderSlamAbility.PlayerState state = new ThunderSlamAbility.PlayerState(player.getInventory().getArmorContents(), player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand(), player.isInvisible(), player.isInvulnerable(), player.getAllowFlight(), player.isFlying());
            this.playerStates.put(player.getUniqueId(), state);
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.setInvisible(true);
            double flightY = this.computeSafeFlightYLevel(player);
            this.flightYLevels.put(player.getUniqueId(), flightY);
            this.launchPlayer(player, flightY);
            this.startCloudEffect(player);
            this.startChargeDuration(player);
        }
    }

    private double computeSafeFlightYLevel(Player player) {
        Location base = player.getLocation().clone();
        World w = base.getWorld();
        double desiredY = base.getY() + this.launchHeight;
        int startY = base.getBlockY() + 1;
        int endY = (int)Math.ceil(desiredY);

        for(int y = startY; y <= endY; ++y) {
            Material m = w.getBlockAt(base.getBlockX(), y, base.getBlockZ()).getType();
            if (m != Material.AIR) {
                return (double)y - 1.0D;
            }
        }

        return desiredY;
    }

    private void launchPlayer(final Player player, final double flightY) {
        originalFlySpeeds.put(player.getUniqueId(), player.getFlySpeed());
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.05f);
        double currentY = player.getLocation().getY();
        double launchStrength = Math.max(0.8D, (flightY - currentY) * 0.15D);
        player.setVelocity(new Vector(0.0D, launchStrength, 0.0D));
        this.chargingPlayers.add(player.getUniqueId());
        (new BukkitRunnable() {
            public void run() {
                if (ThunderSlamAbility.this.chargingPlayers.contains(player.getUniqueId())) {
                    Location target = player.getLocation().clone();
                    target.setY(flightY);
                    player.teleport(target);
                    player.setFlying(true);
                }

            }
        }).runTaskLater(Spellbreak.getInstance(), 15L);
        this.startChargeParticles(player);
        this.startFlightRestriction(player, flightY);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5F, 0.8F);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 30, 0.5D, 0.5D, 0.5D, 0.2D);
    }

    private void startFlightRestriction(Player player, final double flightY) {
        final UUID uuid = player.getUniqueId();
        (new BukkitRunnable() {
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && ThunderSlamAbility.this.chargingPlayers.contains(uuid)) {
                    if (!p.isFlying()) {
                        p.setAllowFlight(true);
                        p.setFlying(true);
                    }

                    Location loc = p.getLocation();
                    double currentY = loc.getY();
                    if (Math.abs(currentY - flightY) > 1.5D) {
                        Location newLoc = loc.clone();
                        newLoc.setY(flightY);
                        p.teleport(newLoc);
                        p.setFlying(true);
                    }

                } else {
                    this.cancel();
                }
            }
        }).runTaskTimer(Spellbreak.getInstance(), 20L, 5L);
    }

    private void startCloudEffect(final Player player) {
        (new BukkitRunnable() {
            public void run() {
                if (!ThunderSlamAbility.this.chargingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                } else {
                    Location loc = player.getLocation();
                    World world = loc.getWorld();

                    double swirlRadius;
                    double x;
                    double z;
                    for(int i = 0; i < 15; ++i) {
                        double angle = Math.random() * 3.141592653589793D * 2.0D;
                        swirlRadius = Math.random() * 1.5D;
                        x = Math.cos(angle) * swirlRadius;
                        z = Math.sin(angle) * swirlRadius;
                        double y = Math.random() * 2.0D;
                        world.spawnParticle(Particle.DUST, loc.clone().add(x, y, z), 1, ThunderSlamAbility.this.cloudParticles);
                    }

                    if (Math.random() < 0.15D) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 8, 0.5D, 0.5D, 0.5D, 0.05D);
                    }

                    double time = (double)System.currentTimeMillis() / 1000.0D;

                    for(int ix = 0; ix < 5; ++ix) {
                        swirlRadius = 1.2D;
                        x = Math.cos(time + (double)ix) * swirlRadius;
                        z = Math.sin(time + (double)ix) * swirlRadius;
                        world.spawnParticle(Particle.DUST, loc.clone().add(x, 1.0D, z), 1, ThunderSlamAbility.this.electricParticles);
                    }

                }
            }
        }).runTaskTimer(Spellbreak.getInstance(), 0L, 2L);
    }

    private void startChargeParticles(final Player player) {
        (new BukkitRunnable() {
            public void run() {
                if (!ThunderSlamAbility.this.chargingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                } else {
                    Location ground = ThunderSlamAbility.this.findGroundLocation(player.getLocation());
                    ThunderSlamAbility.this.drawHollowCircle(ground, ThunderSlamAbility.this.radius, 0.2D);
                }
            }
        }).runTaskTimer(Spellbreak.getInstance(), 0L, 5L);
    }

    private void drawHollowCircle(Location center, double radius, double yOffset) {
        World world = center.getWorld();
        int points = 50;
        double angleIncrement = 6.283185307179586D / (double)points;

        for(int i = 0; i < points; ++i) {
            double angle = (double)i * angleIncrement;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, yOffset, z);
            world.spawnParticle(Particle.DUST, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D, i % 2 == 0 ? this.chargeParticles : this.darkChargeParticles);
        }

    }

    private void startChargeDuration(final Player player) {
        final int[] timeLeft = new int[]{this.chargeDuration};
        final UUID uuid = player.getUniqueId();
        (new BukkitRunnable() {
            public void run() {
                if (!ThunderSlamAbility.this.chargingPlayers.contains(uuid)) {
                    this.cancel();
                } else {
                    Player var10000 = player;
                    String var10001 = String.valueOf(ChatColor.BLUE);
                    var10000.sendActionBar(var10001 + "Thunder Charge: " + timeLeft[0] + "s");
                    if (timeLeft[0]-- <= 0) {
                        ThunderSlamAbility.this.disableChargeMode(player);
                        Spellbreak.getInstance().getCooldownManager().setCooldown(player, ThunderSlamAbility.this.getName(), ThunderSlamAbility.this.getCooldown());
                        player.sendMessage(String.valueOf(ChatColor.RED) + "ThunderSlam charge expired!");
                        this.cancel();
                    }

                }
            }
        }).runTaskTimer(Spellbreak.getInstance(), 0L, 20L);
        (new BukkitRunnable() {
            public void run() {
                if (ThunderSlamAbility.this.chargingPlayers.contains(uuid)) {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "Charge reset.");
                    ThunderSlamAbility.this.disableChargeMode(player);
                }

            }
        }).runTaskLater(Spellbreak.getInstance(), ((long)this.chargeDuration + 1L) * 20L);
    }

    public void slamPlayer(Player player) {
        if (this.chargingPlayers.contains(player.getUniqueId())) {
            UUID uuid = player.getUniqueId();
            Long startTime = (Long)this.chargeStartTimes.get(uuid);
            if (startTime != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                if ((double)elapsed < this.minDuration * 1000.0D) {
                    double remMs = this.minDuration * 1000.0D - (double)elapsed;
                    int remSec = (int)Math.ceil(remMs / 1000.0D);
                    String var10001 = String.valueOf(ChatColor.RED);
                    player.sendMessage(var10001 + "ThunderSlam not fully charged! (" + remSec + "s remaining)");
                } else {
                    this.disableChargeMode(player);
                    this.executeSlam(player);
                }
            }
        }
    }

    private void executeSlam(final Player player) {
        Location start = player.getLocation();
        final Location slamLocation = this.findGroundLocation(start);
        player.setVelocity(new Vector(0, -3, 0));
        (new BukkitRunnable() {
            private int ticksWaited = 0;

            public void run() {
                ++this.ticksWaited;
                if (this.ticksWaited > 40 || player.isOnGround() || !player.isValid()) {
                    if (!player.isOnGround()) {
                        player.teleport(slamLocation);
                    }

                    ThunderSlamAbility.this.doSlamEffects(slamLocation);
                    ThunderSlamAbility.this.applyDamageAndKnockback(player, slamLocation);
                    this.cancel();
                }

            }
        }).runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private Location findGroundLocation(Location start) {
        Location loc = start.clone();
        World world = loc.getWorld();

        // start at the block the player is in
        Block block = loc.getBlock();
        int depth = 0, maxDepth = 256;

        // walk down up to maxDepth until you hit a non-air block
        while (block.getType().isAir() && depth++ < maxDepth) {
            block = block.getRelative(BlockFace.DOWN);
        }

        // return one block above that solid block
        return block.getLocation().add(0.0D, 1.0D, 0.0D);
    }


    private void doSlamEffects(Location slamLocation) {
        World world = slamLocation.getWorld();
        world.spawnParticle(Particle.DUST, slamLocation, 150, this.radius, 0.1D, this.radius, 0.2D, this.chargeParticles);
        world.spawnParticle(Particle.DUST, slamLocation, 100, this.radius / 2.0D, 0.1D, this.radius / 2.0D, 0.1D, this.darkChargeParticles);
        world.strikeLightningEffect(slamLocation);
        world.spawnParticle(Particle.FIREWORK, slamLocation, 100, 0.0D, 0.0D, 0.0D, 0.5D);
        world.spawnParticle(Particle.EXPLOSION, slamLocation, 1);
        world.playSound(slamLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0F, 0.7F);
        world.playSound(slamLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.5F);
    }

    private void applyDamageAndKnockback(Player player, Location slamLocation) {
        World world = slamLocation.getWorld();
        double r2 = this.radius * this.radius;
        Iterator var6 = world.getNearbyEntities(slamLocation, this.radius, this.radius, this.radius).iterator();

        while(var6.hasNext()) {
            Entity e = (Entity)var6.next();
            if (e instanceof LivingEntity && !e.equals(player)) {
                LivingEntity target = (LivingEntity)e;
                if (!(target.getLocation().distanceSquared(slamLocation) > r2)) {
                    Spellbreak.getInstance().getAbilityDamage().damage(target, this.damage, player, this, "Slam");
                    Vector direction = target.getLocation().toVector().subtract(slamLocation.toVector());
                    if (direction.lengthSquared() > 0.0D) {
                        direction.normalize();
                    }

                    target.setVelocity(new Vector(direction.getX() * this.knockBackStrength, this.knockUpStrength, direction.getZ() * this.knockBackStrength));
                }
            }
        }

    }

    public void disableChargeMode(Player player) {
        UUID uuid = player.getUniqueId();
        this.chargingPlayers.remove(uuid);
        this.chargeStartTimes.remove(uuid);
        this.flightYLevels.remove(uuid);
        ThunderSlamAbility.PlayerState state = (ThunderSlamAbility.PlayerState)this.playerStates.remove(uuid);
        Float oldSpeed = originalFlySpeeds.remove(uuid);
        if (oldSpeed != null) {
            player.setFlySpeed(oldSpeed);
        } else {
            // fallback to Bukkit’s default if somehow we didn’t save it
            player.setFlySpeed(0.1f);
        }
        if (state != null) {
            player.getInventory().setArmorContents(state.armor());
            player.getInventory().setItemInMainHand(state.mainHand());
            player.getInventory().setItemInOffHand(state.offHand());
            player.setInvisible(state.wasInvisible());
            player.setInvulnerable(state.wasInvulnerable());
            player.updateInventory();
            player.setAllowFlight(state.wasAllowFlight());
            player.setFlying(state.wasFlying());
        } else {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        player.sendActionBar("");
    }

    public boolean isCharging(Player player) {
        return this.chargingPlayers.contains(player.getUniqueId());
    }

    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.thunderslam.";
        this.cooldown = cfg.getInt(base + "cooldown", this.cooldown);
        this.manaCost = cfg.getInt(base + "mana-cost", this.manaCost);
        this.damage = cfg.getDouble(base + "damage", this.damage);
        this.radius = cfg.getDouble(base + "radius", this.radius);
        this.launchHeight = cfg.getDouble(base + "launch-height", this.launchHeight);
        this.knockUpStrength = cfg.getDouble(base + "knock-up", this.knockUpStrength);
        this.knockBackStrength = cfg.getDouble(base + "knock-back", this.knockBackStrength);
        this.chargeDuration = cfg.getInt(base + "charge-duration", this.chargeDuration);
        this.minDuration = cfg.getDouble(base + "min-duration", this.minDuration);
    }

    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format("§9%s §3was smited by §9%s§3's ThunderSlam!", victimName, casterName);
    }

    public boolean isSuccessful() {
        return true;
    }

    private static record PlayerState(ItemStack[] armor, ItemStack mainHand, ItemStack offHand, boolean wasInvisible, boolean wasInvulnerable, boolean wasAllowFlight, boolean wasFlying) {
        private PlayerState(ItemStack[] armor, ItemStack mainHand, ItemStack offHand, boolean wasInvisible, boolean wasInvulnerable, boolean wasAllowFlight, boolean wasFlying) {
            this.armor = armor;
            this.mainHand = mainHand;
            this.offHand = offHand;
            this.wasInvisible = wasInvisible;
            this.wasInvulnerable = wasInvulnerable;
            this.wasAllowFlight = wasAllowFlight;
            this.wasFlying = wasFlying;
        }

        public ItemStack[] armor() {
            return this.armor;
        }

        public ItemStack mainHand() {
            return this.mainHand;
        }

        public ItemStack offHand() {
            return this.offHand;
        }

        public boolean wasInvisible() {
            return this.wasInvisible;
        }

        public boolean wasInvulnerable() {
            return this.wasInvulnerable;
        }

        public boolean wasAllowFlight() {
            return this.wasAllowFlight;
        }

        public boolean wasFlying() {
            return this.wasFlying;
        }
    }
}
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class StarPhaseAbility implements Ability {
    private int cooldown = 14;
    private int manaCost = 30;
    private String requiredClass = "starcaller";

    // Stardust parameters
    private int maxStardust = 100;
    private double flightCostPerBlock = 2.1;
    private double explosionDamagePerStardust = 0.03;
    private double explosionRadius = 5.0;
    private int explosionRevertDelay = 100; // ticks

    // Flight parameters
    private double flightSpeed = 0.8;
    private int bossBarUpdateInterval = 1; // ticks

    // Projectile parameters
    private double projectileSpeed = 1.0;
    private double maxProjectileRange = 25.0;

    private final Map<UUID, StarPhaseData> activeStarPhases = new HashMap<>();
    private final Random random = new Random();

    @Override public String getName() { return "StarPhase"; }
    @Override public String getDescription() {
        return "Left-click to activate StarPhase mode. Fly with stardust or left-click again to launch explosive yin-yang projectile.";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();

        // If already in StarPhase mode, launch projectile
        if (activeStarPhases.containsKey(uuid)) {
            StarPhaseData data = activeStarPhases.get(uuid);
            if (data.stardust > 0) {
                launchYinYangProjectile(player, data.stardust);
                endStarPhase(uuid);
            } else {
                player.sendMessage(ChatColor.YELLOW + "No stardust remaining!");
            }
            return;
        }

        // Activate StarPhase mode
        boolean hadFlight = player.getAllowFlight();
        boolean wasFlying = player.isFlying();

        StarPhaseData data = new StarPhaseData(player.getLocation(), maxStardust, hadFlight, wasFlying);
        activeStarPhases.put(uuid, data);

        // Create and show boss bar
        data.bossBar = Bukkit.createBossBar(
                ChatColor.LIGHT_PURPLE + "Stardust: " + String.format("%.1f", data.stardust) + "/" + maxStardust,
                BarColor.PURPLE,
                BarStyle.SOLID
        );
        data.bossBar.setProgress(1.0f);
        data.bossBar.addPlayer(player);

        // Start flight and boss-bar tasks
        startFlightTask(player, data);
        startBossBarTask(player, data);

        player.setAllowFlight(true);
        player.setFlying(true);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "StarPhase activated! Fly or left-click to launch projectile!");
    }

    private void startFlightTask(Player player, StarPhaseData data) {
        data.flightTask = new BukkitRunnable() {
            private Location lastLocation = player.getLocation().clone();

            @Override
            public void run() {
                if (!player.isOnline() || !activeStarPhases.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                if (player.isFlying()) {
                    Location currentLoc = player.getLocation();
                    double distance = lastLocation.distance(currentLoc);

                    if (distance > 0.1) {
                        double cost = distance * flightCostPerBlock;
                        data.stardust -= cost;

                        if (data.stardust <= 0) {
                            data.stardust = 0;
                            endStarPhase(player.getUniqueId());
                            return;
                        }

                        createFlightParticles(currentLoc);
                    }

                    lastLocation = currentLoc.clone();
                }
            }
        };
        data.flightTask.runTaskTimer(Spellbreak.getInstance(), 0, 2);
    }

    private void startBossBarTask(Player player, StarPhaseData data) {
        data.bossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeStarPhases.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                updateBossBar(data, data.stardust);
            }
        };
        data.bossBarTask.runTaskTimer(Spellbreak.getInstance(), 0, bossBarUpdateInterval);
    }

    private void updateBossBar(StarPhaseData data, double stardust) {
        float progress = (float) Math.max(0, Math.min(1, stardust / maxStardust));
        data.bossBar.setProgress(progress);
        data.bossBar.setTitle(
                ChatColor.LIGHT_PURPLE + "Stardust: " + String.format("%.1f", stardust) + "/" + maxStardust
        );
    }

    private void createFlightParticles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.8;
            double offsetY = (random.nextDouble() - 0.5) * 0.8;
            double offsetZ = (random.nextDouble() - 0.5) * 0.8;

            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            Color color = random.nextBoolean() ? Color.fromRGB(138, 43, 226) : Color.fromRGB(255, 215, 0);

            world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.2f));
        }
    }

    private void launchYinYangProjectile(Player player, double stardust) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        Vector launchVelocity = direction.clone().multiply(-0.8);
        launchVelocity.setY(Math.max(0.4, launchVelocity.getY()));
        player.setVelocity(player.getVelocity().add(launchVelocity));

        YinYangProjectile projectile = new YinYangProjectile(
                player.getWorld(), eyeLoc, direction, player.getUniqueId(), stardust
        );
        projectile.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        player.getWorld().playSound(eyeLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 1.5f);
    }

    public void endStarPhase(UUID uuid) {
        StarPhaseData data = activeStarPhases.remove(uuid);
        if (data == null) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (data.bossBar != null) data.bossBar.removeAll();
            player.setAllowFlight(data.hadFlightBefore);
            player.setFlying(data.hadFlightBefore && data.wasFlyingBefore);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
            player.sendMessage(ChatColor.GRAY + "StarPhase ended.");
        }
        if (data.flightTask != null) data.flightTask.cancel();
        if (data.bossBarTask != null) data.bossBarTask.cancel();
    }

    public boolean isInStarPhase(UUID uuid) { return activeStarPhases.containsKey(uuid); }

    @Override public boolean isSuccessful() { return true; }

    @Override
    public String getDeathMessage(String victim, String caster, String unused) {
        return String.format("§6%s §ewas obliterated by §6%s§e's StarPhase explosion!", victim, caster);
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String b = "abilities.starphase.";
        cooldown = cfg.getInt(b + "cooldown", cooldown);
        manaCost = cfg.getInt(b + "mana-cost", manaCost);
        maxStardust = cfg.getInt(b + "max-stardust", maxStardust);
        flightCostPerBlock = cfg.getDouble(b + "flight-cost-per-block", flightCostPerBlock);
        explosionDamagePerStardust = cfg.getDouble(b + "explosion-damage-per-stardust", explosionDamagePerStardust);
        explosionRadius = cfg.getDouble(b + "explosion-radius", explosionRadius);
        explosionRevertDelay = cfg.getInt(b + "explosion-revert-delay", explosionRevertDelay);
        flightSpeed = cfg.getDouble(b + "flight-speed", flightSpeed);
        projectileSpeed = cfg.getDouble(b + "projectile-speed", projectileSpeed);
        maxProjectileRange = cfg.getDouble(b + "max-projectile-range", maxProjectileRange);
        requiredClass = cfg.getString(b + "required-class", requiredClass);
    }

    private class StarPhaseData {
        Location startLocation;
        double stardust;
        BukkitRunnable flightTask;
        BukkitRunnable bossBarTask;
        boolean hadFlightBefore;
        boolean wasFlyingBefore;
        BossBar bossBar;

        StarPhaseData(Location start, double initialStardust, boolean hadFlight, boolean wasFlying) {
            this.startLocation = start.clone();
            this.stardust = initialStardust;
            this.hadFlightBefore = hadFlight;
            this.wasFlyingBefore = wasFlying;
        }
    }

    private class YinYangProjectile extends BukkitRunnable {
        private final World world;
        private Location loc;
        private final Vector dir;
        private final UUID owner;
        private final double stardust;
        private double traveled = 0;
        private int tickCount = 0;

        YinYangProjectile(World w, Location start, Vector direction, UUID owner, double stardust) {
            this.world = w;
            this.loc = start.clone();
            this.dir = direction.clone().multiply(projectileSpeed);
            this.owner = owner;
            this.stardust = stardust;
        }

        @Override
        public void run() {
            if (world == null) {
                cancel();
                return;
            }

            tickCount++;
            loc.add(dir);
            traveled += projectileSpeed;
            createYinYangEffect();

            RayTraceResult result = world.rayTraceBlocks(loc.clone().subtract(dir), dir.clone().normalize(), projectileSpeed + 0.5);
            if ((result != null && result.getHitBlock() != null) || traveled >= maxProjectileRange) {
                explode(); cancel(); return;
            }
            for (Entity entity : world.getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                if (entity instanceof LivingEntity && !entity.getUniqueId().equals(owner)) { explode(); cancel(); return; }
            }
        }

        private void createYinYangEffect() {
            double rotationSpeed = 10.0;
            double angle = Math.toRadians(tickCount * rotationSpeed);
            int points = 16;
            for (int i = 0; i < points; i++) {
                double theta = 2 * Math.PI * i / points + angle;
                double radius = 0.8;
                Color color;
                double x = radius * Math.cos(theta) * 0.5;
                double y = radius * Math.sin(theta) * 0.5;
                Location dotLoc = loc.clone().add(x, y, 0);
                if (theta % (2 * Math.PI) < Math.PI) {
                    color = Color.fromRGB(75, 0, 130);
                } else {
                    color = Color.fromRGB(255, 215, 0);
                }
                world.spawnParticle(Particle.DUST, dotLoc, 1, 0,0,0,0, new Particle.DustOptions(color,1.5f));
            }
            world.spawnParticle(Particle.DUST, loc.clone().add(0.2,0,0),1,0,0,0,0, new Particle.DustOptions(Color.fromRGB(255,215,0),1.0f));
            world.spawnParticle(Particle.DUST, loc.clone().add(-0.2,0,0),1,0,0,0,0, new Particle.DustOptions(Color.fromRGB(75,0,130),1.0f));
        }

        private void explode() {
            Location explosionLoc = loc.clone();
            double damage = stardust * explosionDamagePerStardust;
            double actualRadius = Math.min(explosionRadius, Math.max(2.0, (stardust / maxStardust) * explosionRadius));
            world.playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE,2.0f,0.8f);
            world.spawnParticle(Particle.EXPLOSION,explosionLoc,(int)(actualRadius*2),actualRadius*0.3,actualRadius*0.3,actualRadius*0.3,0);
            world.spawnParticle(Particle.FLAME,explosionLoc,(int)(actualRadius*5),actualRadius*0.5,actualRadius*0.5,actualRadius*0.5,0.1);

            Player casterPlayer = Bukkit.getPlayer(owner);
            if (casterPlayer != null) {
                for (Entity entity : world.getNearbyEntities(explosionLoc,actualRadius,actualRadius,actualRadius)) {
                    if (entity instanceof LivingEntity && !entity.getUniqueId().equals(owner)) {
                        LivingEntity living = (LivingEntity)entity;
                        double dist = living.getLocation().distance(explosionLoc);
                        double actualDamage = damage * (1 - (dist / actualRadius));
                        if (actualDamage > 0) {
                            Spellbreak.getInstance().getAbilityDamage()
                                    .damage(living, actualDamage, casterPlayer, StarPhaseAbility.this, "StarPhase");
                            Vector kb = living.getLocation().subtract(explosionLoc).toVector().normalize().multiply(1.5*(actualDamage/damage));
                            living.setVelocity(living.getVelocity().add(kb));
                        }
                    }
                }
            }
            createTemporaryExplosion(explosionLoc, actualRadius);
        }

        private void createTemporaryExplosion(Location center, double radius) {
            Map<Location, Material> originalBlocks = new HashMap<>();
            for (int x = (int)-radius; x <= radius; x++) for (int y = (int)-radius; y <= radius; y++) for (int z = (int)-radius; z <= radius; z++) {
                Location bLoc = center.clone().add(x,y,z);
                if (bLoc.distance(center) <= radius) {
                    Material orig = bLoc.getBlock().getType();
                    if (orig != Material.AIR && orig != Material.BEDROCK) {
                        originalBlocks.put(bLoc.clone(), orig);
                        bLoc.getBlock().setType(Material.AIR);
                    }
                }
            }
            new BukkitRunnable() {
                @Override public void run() {
                    for (Map.Entry<Location,Material> e: originalBlocks.entrySet()) e.getKey().getBlock().setType(e.getValue());
                }
            }.runTaskLater(Spellbreak.getInstance(), explosionRevertDelay);
        }
    }
}
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class EarthShardsAbility implements Ability {
    // === Configurable parameters ===
    private int cooldown = 9;             // seconds
    private int manaCost = 40;
    private String requiredClass = "elementalist";

    private double searchRadius = 10.0;    // radius to look for “earthbendable” blocks
    private int maxShards = 4;             // maximum shards to collect
    private int gatherIntervalTicks = 10;  // ticks between gathering each shard
    private double orbitRadius = 2.0;      // base orbit radius
    private double orbitSpeed = 0.15;      // radians per tick they advance
    private int durationTicks = 1000;       // how long shards continue orbit after sneak release (≈5s)
    private double shootSpeed = 1.75;       // base velocity multiplier when shooting
    private double damage = 1.5;           // damage dealt per shard
    private int restoreDelayTicks = 20;    // delay before restoring blocks after slot change

    // A set of “earthbendable” materials (expanded)
    private final Set<Material> earthbendable = Set.of(
            Material.STONE,
            Material.DIRT,
            Material.COBBLESTONE,
            Material.GRAVEL,
            Material.GRANITE,
            Material.ANDESITE,
            Material.DIORITE,
            Material.MOSSY_COBBLESTONE,
            Material.MUD,
            Material.SANDSTONE,
            Material.RED_SANDSTONE,
            Material.CLAY,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.MYCELIUM,
            Material.SAND,
            Material.RED_SAND,
            Material.SOUL_SAND,
            Material.SOUL_SOIL,
            Material.BRICK,
            Material.NETHERRACK,
            Material.END_STONE,
            Material.TERRACOTTA,
            Material.WHITE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA,
            Material.MAGENTA_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA,
            Material.LIME_TERRACOTTA,
            Material.PINK_TERRACOTTA,
            Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA,
            Material.CYAN_TERRACOTTA,
            Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA,
            Material.BROWN_TERRACOTTA,
            Material.GREEN_TERRACOTTA,
            Material.RED_TERRACOTTA,
            Material.BLACK_TERRACOTTA,
            Material.IRON_ORE,
            Material.COAL_ORE,
            Material.COPPER_ORE,
            Material.COPPER_BLOCK,
            Material.DEEPSLATE,
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_REDSTONE_ORE
            // …add more if desired
    );

    // === Per‐player orbit data ===
    private final Map<UUID, EarthShardData> playerData = new HashMap<>();

    // === Inner classes for tracking ===
    private static class Shard {
        public final Location originalLoc;
        public final Material material;
        public final BlockData blockData;   // store BlockData for dust
        public final FallingBlock fallingBlock;
        public double angle;
        public boolean isFlying = false;

        public Shard(Location loc, Material mat, BlockData bd, FallingBlock fb, double initialAngle) {
            this.originalLoc = loc.clone();
            this.material = mat;
            this.blockData = bd;
            this.fallingBlock = fb;
            this.angle = initialAngle;
        }
    }

    private class EarthShardData {
        public final List<Shard> shards = new ArrayList<>();
        public BukkitTask gatherTask;
        public BukkitTask orbitTask;
        public int ticksLeft;       // ticks remaining after sneak release
        public boolean isCharging;  // true while sneak is held
        public int totalGathered;   // total shards gathered this cast
    }

    // === Ability interface methods ===
    @Override
    public String getName() {
        return "EarthShards";
    }

    @Override
    public String getDescription() {
        return "Hold Shift to draw earth from nearby blocks. Shards will orbit you. Release Shift, then left‐click to fire shards at foes.";
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
    public boolean isTriggerAction(org.bukkit.event.block.Action action) {
        return false; // We handle activation via sneak and left‐click in the listener
    }

    @Override
    public void activate(Player player) {
        // Not used: activation logic is in the listener
    }
    public int getAdjustedCooldown(Player p) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), "EarthShards");
        return (int)(cooldown * lvl.getCooldownReduction());
    }
    public int getAdjustedManaCost(Player p) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), "EarthShards");
        return (int)(manaCost * lvl.getManaCostReduction());
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.earthshards.";

        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        requiredClass = cfg.getString(base + "required-class", requiredClass);

        searchRadius = cfg.getDouble(base + "search-radius", searchRadius);
        maxShards = cfg.getInt(base + "max-shards", maxShards);
        gatherIntervalTicks = cfg.getInt(base + "gather-interval-ticks", gatherIntervalTicks);
        orbitRadius = cfg.getDouble(base + "orbit-radius", orbitRadius);
        orbitSpeed = cfg.getDouble(base + "orbit-speed", orbitSpeed);
        durationTicks = cfg.getInt(base + "duration-ticks", durationTicks);
        shootSpeed = cfg.getDouble(base + "shoot-speed", shootSpeed);
        damage = cfg.getDouble(base + "damage", damage);
        restoreDelayTicks = cfg.getInt(base + "restore-delay-ticks", restoreDelayTicks);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§6%s§7 was shattered by §6%s§7’s EarthShards.", victim, caster);
    }

    @Override
    public String getDefaultSubAbilityName() {
        return null;
    }

    // === Public methods called by the listener ===

    /**
     * Called when the player starts sneaking with EarthShards selected.
     */
    public void startCharging(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerData.containsKey(uuid)) return; // Already charging
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(uuid, Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "EarthShards");

        EarthShardData data = new EarthShardData();
        data.isCharging = true;
        data.ticksLeft = durationTicks;
        data.totalGathered = 0;

        double adjustedRadius = searchRadius * lvl.getRangeMultiplier();
        int adjustedMaxShards = maxShards + (lvl.getLevel() >= 3 ? 1 : 0);

        data.gatherTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!data.isCharging || data.totalGathered >= adjustedMaxShards) {
                    cancel();
                    return;
                }
                List<Block> candidates = new ArrayList<>();
                List<Double> distances = new ArrayList<>();
                Location ploc = player.getLocation();
                int r = (int) Math.ceil(adjustedRadius);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            Location check = ploc.clone().add(dx, dy, dz);
                            if (check.getWorld() == null) continue;
                            double dist = check.distance(ploc);
                            if (dist > adjustedRadius) continue;
                            Block b = check.getBlock();
                            if (!earthbendable.contains(b.getType())) continue;

                            Block above = b.getRelative(0, 1, 0);
                            if (!above.getType().isAir()) continue;

                            Vector dir = player.getEyeLocation().toVector().subtract(b.getLocation().toVector()).normalize();
                            Location start = b.getLocation().clone().add(0.5, 1, 0.5);
                            boolean pathClear = true;
                            for (double d = 0.5; d <= dist; d += 0.5) {
                                Location step = start.clone().add(dir.clone().multiply(d));
                                if (!step.getBlock().getType().isAir()) {
                                    pathClear = false;
                                    break;
                                }
                            }
                            if (!pathClear) continue;

                            candidates.add(b);
                            distances.add(dist);
                        }
                    }
                }
                if (candidates.isEmpty()) return;
                double maxDist = adjustedRadius;
                double totalWeight = 0.0;
                double[] weights = new double[candidates.size()];
                for (int i = 0; i < candidates.size(); i++) {
                    double w = (maxDist - distances.get(i)) + 0.1;
                    weights[i] = w;
                    totalWeight += w;
                }
                double rnd = Math.random() * totalWeight;
                int chosenIndex = 0;
                double cumulative = 0.0;
                for (int i = 0; i < weights.length; i++) {
                    cumulative += weights[i];
                    if (rnd <= cumulative) {
                        chosenIndex = i;
                        break;
                    }
                }
                Block chosen = candidates.get(chosenIndex);
                Material mat = chosen.getType();
                BlockData bd = chosen.getBlockData();
                Location blockLoc = chosen.getLocation();
                chosen.setType(Material.AIR, false);

                FallingBlock fb = player.getWorld().spawnFallingBlock(blockLoc, bd);
                fb.setGravity(false);
                fb.setDropItem(false);

                Shard shard = new Shard(blockLoc, mat, bd, fb, 0);
                data.shards.add(shard);
                data.totalGathered++;
                redistributeAngles(data);
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, gatherIntervalTicks);

        data.orbitTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!data.isCharging) {
                    data.ticksLeft--;
                }
                for (Shard shard : new ArrayList<>(data.shards)) {
                    if (shard.isFlying) continue;

                    shard.angle = (shard.angle + orbitSpeed) % (2 * Math.PI);
                    Vector offset = new Vector(
                            orbitRadius * Math.cos(shard.angle),
                            1.5,
                            orbitRadius * Math.sin(shard.angle)
                    );
                    Location center = player.getLocation().add(0, 1.0, 0);
                    Location target = center.clone().add(offset);

                    Vector dirVec = target.toVector()
                            .subtract(shard.fallingBlock.getLocation().toVector())
                            .multiply(0.3);
                    shard.fallingBlock.setVelocity(dirVec);

                    shard.fallingBlock.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            shard.fallingBlock.getLocation(),
                            4, 0, 0, 0, 0,
                            shard.blockData
                    );
                }

                if (!data.isCharging && data.ticksLeft <= 0) {
                    for (Shard shard : data.shards) {
                        if (!shard.isFlying && !shard.fallingBlock.isDead()) {
                            shard.fallingBlock.remove();
                            shard.originalLoc.getBlock().setType(shard.material, false);
                        }
                    }
                    data.shards.clear();
                    if (data.gatherTask != null && !data.gatherTask.isCancelled()) {
                        data.gatherTask.cancel();
                    }
                    cancel();
                    playerData.remove(uuid);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        playerData.put(uuid, data);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
    }

    /**
     * Evenly redistributes the initial angles of all shards in a circle.
     */
    private void redistributeAngles(EarthShardData data) {
        int n = data.shards.size();
        for (int i = 0; i < n; i++) {
            Shard s = data.shards.get(i);
            s.angle = (2 * Math.PI / n) * i;
        }
    }

    /**
     * Called when the player stops sneaking.
     */
    public void stopCharging(Player player) {
        EarthShardData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        data.isCharging = false;
        if (data.gatherTask != null && !data.gatherTask.isCancelled()) {
            data.gatherTask.cancel();
        }
    }

    /**
     * Called when the player left‐clicks (with EarthShards selected).
     * Fires one shard, if available.
     */
    public void shootShard(Player player) {
        EarthShardData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        if (data.shards.isEmpty()) return;

        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "EarthShards");
        double adjustedDamage = damage * lvl.getDamageMultiplier();

        Shard shard = data.shards.remove(0);
        shard.isFlying = true;

        shard.fallingBlock.setGravity(true);

        // Determine a precise target point based on the player's line of sight
        Location eyeLoc = player.getEyeLocation();
        Vector lookDir = eyeLoc.getDirection().normalize();

        // Attempt to find the block the player is actually looking at (up to 50 blocks away)
        Block targetBlock = player.getTargetBlockExact(50);
        Location targetLoc;
        if (targetBlock != null) {
            // Aim at the center of that block
            targetLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);
        } else {
            // If no block is found, use a point 50 blocks out
            targetLoc = eyeLoc.clone().add(lookDir.multiply(50));
        }

        // Compute direction from shard to that precise target location
        Vector launchDir = targetLoc.toVector().subtract(shard.fallingBlock.getLocation().toVector()).normalize();
        Vector velocity = launchDir.multiply(shootSpeed);
        shard.fallingBlock.setVelocity(velocity);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1f, 1.3f);

        new BukkitRunnable() {
            int life = 0;
            @Override
            public void run() {
                life++;
                if (shard.fallingBlock.isDead() || life > 40) {
                    if (shard.fallingBlock.isValid()) {
                        shard.fallingBlock.remove();
                    }
                    shard.originalLoc.getBlock().setType(shard.material, false);
                    cancel();
                    return;
                }

                for (Entity ent : shard.fallingBlock.getLocation().getNearbyEntities(1.0, 1.0, 1.0)) {
                    if (!(ent instanceof LivingEntity)) continue;
                    if (ent.getUniqueId().equals(player.getUniqueId())) continue;

                    LivingEntity target = (LivingEntity) ent;
                    Spellbreak.getInstance()
                            .getAbilityDamage()
                            .damage(target, adjustedDamage , player, EarthShardsAbility.this, null);

                    Location hitLoc = target.getLocation();
                    target.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            hitLoc, 10, 0.3, 0.3, 0.3, 0,
                            shard.material.createBlockData()
                    );
                    target.getWorld().playSound(hitLoc, Sound.ENTITY_PLAYER_HURT, 0.8f, 1.2f);

                    if (shard.fallingBlock.isValid()) {
                        shard.fallingBlock.remove();
                    }
                    shard.originalLoc.getBlock().setType(shard.material, false);
                    cancel();
                    return;
                }

                if (shard.fallingBlock.isOnGround()) {
                    if (shard.fallingBlock.isValid()) {
                        shard.fallingBlock.remove();
                    }
                    shard.originalLoc.getBlock().setType(shard.material, false);
                    cancel();
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 1L, 1L);
    }

    /**
     * Immediately removes all orbiting and charged shards for the player,
     * then restores their original blocks after a delay.
     */
    public void clearShards(Player player) {
        EarthShardData data = playerData.remove(player.getUniqueId());
        if (data == null) return;

        if (data.gatherTask != null && !data.gatherTask.isCancelled()) {
            data.gatherTask.cancel();
        }
        if (data.orbitTask != null && !data.orbitTask.isCancelled()) {
            data.orbitTask.cancel();
        }

        for (Shard shard : data.shards) {
            if (!shard.fallingBlock.isDead()) {
                shard.fallingBlock.remove();
            }
            Location loc = shard.originalLoc;
            Material mat = shard.material;
            new BukkitRunnable() {
                @Override
                public void run() {
                    loc.getBlock().setType(mat, false);
                }
            }.runTaskLater(Spellbreak.getInstance(), restoreDelayTicks);
        }
        data.shards.clear();
    }
}

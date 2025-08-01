package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlagueCloudAbility implements Ability {
    private int cooldown = 17;
    private int manaCost = 40;
    private String requiredClass = "necromancer";
    private double maxRadius = 15.0;
    private int duration = 6;
    private double damagePerTick = 0;
    private int expansionInterval = 5;
    private int witherDuration = 10;
    private double healReduction = 0.5;

    private static final Map<BlockPosition, Integer> PROTECTED_BLOCKS = new ConcurrentHashMap<>();
    private static boolean listenerRegistered = false;

    private static class BlockPosition {
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        public BlockPosition(Location loc) {
            this.worldId = loc.getWorld().getUID();
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockPosition that = (BlockPosition) o;
            return x == that.x && y == that.y && z == that.z && worldId.equals(that.worldId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, x, y, z);
        }
    }

    public static class BlockProtectionListener implements Listener {
        @EventHandler
        public void onBlockBreak(BlockBreakEvent event) {
            BlockPosition pos = new BlockPosition(event.getBlock().getLocation());
            if (PROTECTED_BLOCKS.containsKey(pos)) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(ChatColor.RED + "This corrupted block cannot be broken!");
            }
        }
    }
    private static final Map<Material, Material> BLOCK_REPLACEMENTS = new HashMap<>();
    static {
        BLOCK_REPLACEMENTS.put(Material.GRASS_BLOCK, Material.MYCELIUM);
        BLOCK_REPLACEMENTS.put(Material.DIRT, Material.COARSE_DIRT);
        BLOCK_REPLACEMENTS.put(Material.STONE, Material.COBBLESTONE);
        BLOCK_REPLACEMENTS.put(Material.OAK_LEAVES, Material.AZALEA_LEAVES);
        BLOCK_REPLACEMENTS.put(Material.SAND, Material.SOUL_SAND);
        BLOCK_REPLACEMENTS.put(Material.OAK_LOG, Material.MUSHROOM_STEM);
        BLOCK_REPLACEMENTS.put(Material.COBBLESTONE, Material.MOSSY_COBBLESTONE);
        BLOCK_REPLACEMENTS.put(Material.CLAY, Material.TERRACOTTA);
        BLOCK_REPLACEMENTS.put(Material.TORCH, Material.REDSTONE_TORCH);
        BLOCK_REPLACEMENTS.put(Material.SNOW, Material.SNOW_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.WATER, Material.LAVA);
        BLOCK_REPLACEMENTS.put(Material.PUMPKIN, Material.MELON);
        BLOCK_REPLACEMENTS.put(Material.SUGAR_CANE, Material.CACTUS);

        // Wood and Leaves Variants
        BLOCK_REPLACEMENTS.put(Material.BIRCH_LOG, Material.NETHER_WART_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_LOG, Material.DARK_OAK_LOG);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_LOG, Material.CHERRY_LOG);
        BLOCK_REPLACEMENTS.put(Material.ACACIA_LOG, Material.MANGROVE_LOG);
        BLOCK_REPLACEMENTS.put(Material.BIRCH_LEAVES, Material.WARPED_FUNGUS);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_LEAVES, Material.CRIMSON_FUNGUS);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_LEAVES, Material.MANGROVE_LEAVES);
        BLOCK_REPLACEMENTS.put(Material.ACACIA_LEAVES, Material.AZALEA_LEAVES);

        // Flowers and Plants
        BLOCK_REPLACEMENTS.put(Material.POPPY, Material.WITHER_ROSE);
        BLOCK_REPLACEMENTS.put(Material.DANDELION, Material.AZALEA);
        BLOCK_REPLACEMENTS.put(Material.BLUE_ORCHID, Material.BROWN_MUSHROOM);
        BLOCK_REPLACEMENTS.put(Material.ROSE_BUSH, Material.TALL_GRASS);
        BLOCK_REPLACEMENTS.put(Material.SUNFLOWER, Material.WITHER_ROSE);
        BLOCK_REPLACEMENTS.put(Material.SHORT_GRASS, Material.AIR);
        BLOCK_REPLACEMENTS.put(Material.TALL_GRASS, Material.AIR);


        // Miscellaneous Blocks
        BLOCK_REPLACEMENTS.put(Material.GRAVEL, Material.SOUL_SAND);
        BLOCK_REPLACEMENTS.put(Material.IRON_ORE, Material.RAW_IRON_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.GOLD_ORE, Material.RAW_GOLD_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.LAPIS_ORE, Material.LAPIS_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.DIAMOND_ORE, Material.DIAMOND_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.EMERALD_ORE, Material.EMERALD_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.NETHER_QUARTZ_ORE, Material.QUARTZ_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.COAL_ORE, Material.COAL_BLOCK);

        // Nether and End Blocks
        BLOCK_REPLACEMENTS.put(Material.NETHERRACK, Material.SOUL_SAND);
        BLOCK_REPLACEMENTS.put(Material.BASALT, Material.BLACKSTONE);
        BLOCK_REPLACEMENTS.put(Material.END_STONE, Material.SANDSTONE);
        BLOCK_REPLACEMENTS.put(Material.CHORUS_FLOWER, Material.END_ROD);
        BLOCK_REPLACEMENTS.put(Material.LAVA, Material.MAGMA_BLOCK);

        // Farm Blocks
        BLOCK_REPLACEMENTS.put(Material.CARROT, Material.DIRT);
        BLOCK_REPLACEMENTS.put(Material.POTATO, Material.COARSE_DIRT);
        BLOCK_REPLACEMENTS.put(Material.WHEAT, Material.MYCELIUM);
        BLOCK_REPLACEMENTS.put(Material.BEETROOTS, Material.SOUL_SAND);
        BLOCK_REPLACEMENTS.put(Material.MELON_STEM, Material.BROWN_MUSHROOM_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.PUMPKIN_STEM, Material.WARPED_NYLIUM);

        // Planks and Wooden Slabs
        BLOCK_REPLACEMENTS.put(Material.OAK_PLANKS, Material.NETHER_BRICKS);
        BLOCK_REPLACEMENTS.put(Material.BIRCH_PLANKS, Material.CRIMSON_NYLIUM);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_PLANKS, Material.WARPED_NYLIUM);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_PLANKS, Material.MANGROVE_PLANKS);
        BLOCK_REPLACEMENTS.put(Material.ACACIA_PLANKS, Material.BASALT);
        BLOCK_REPLACEMENTS.put(Material.DARK_OAK_PLANKS, Material.SOUL_SAND);
        BLOCK_REPLACEMENTS.put(Material.MANGROVE_PLANKS, Material.OBSIDIAN);
        BLOCK_REPLACEMENTS.put(Material.CRAFTING_TABLE, Material.SMITHING_TABLE);
        BLOCK_REPLACEMENTS.put(Material.FURNACE, Material.BLAST_FURNACE);
        BLOCK_REPLACEMENTS.put(Material.DISPENSER, Material.DROPPER);

        // Slabs and Stairs
        BLOCK_REPLACEMENTS.put(Material.BIRCH_SLAB, Material.CRIMSON_SLAB);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_SLAB, Material.WARPED_SLAB);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_SLAB, Material.MANGROVE_SLAB);
        BLOCK_REPLACEMENTS.put(Material.MANGROVE_SLAB, Material.BONE_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.OAK_STAIRS, Material.NETHER_BRICK_STAIRS);
        BLOCK_REPLACEMENTS.put(Material.BIRCH_STAIRS, Material.CRIMSON_STAIRS);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_STAIRS, Material.WARPED_STAIRS);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_STAIRS, Material.MANGROVE_STAIRS);
        BLOCK_REPLACEMENTS.put(Material.ACACIA_STAIRS, Material.BLACKSTONE_STAIRS);
        BLOCK_REPLACEMENTS.put(Material.MANGROVE_STAIRS, Material.CHISELED_NETHER_BRICKS);

        // Wooden Doors and Trapdoors
        BLOCK_REPLACEMENTS.put(Material.OAK_DOOR, Material.CRIMSON_DOOR);
        BLOCK_REPLACEMENTS.put(Material.BIRCH_DOOR, Material.WARPED_DOOR);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_DOOR, Material.MANGROVE_DOOR);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_DOOR, Material.DARK_OAK_DOOR);
        BLOCK_REPLACEMENTS.put(Material.OAK_TRAPDOOR, Material.CRIMSON_TRAPDOOR);
        BLOCK_REPLACEMENTS.put(Material.BIRCH_TRAPDOOR, Material.WARPED_TRAPDOOR);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_TRAPDOOR, Material.MANGROVE_TRAPDOOR);
        BLOCK_REPLACEMENTS.put(Material.JUNGLE_TRAPDOOR, Material.DARK_OAK_TRAPDOOR);

        // Glass and Glass Panes
        BLOCK_REPLACEMENTS.put(Material.GLASS, Material.BROWN_STAINED_GLASS);
        BLOCK_REPLACEMENTS.put(Material.GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        BLOCK_REPLACEMENTS.put(Material.BLACK_STAINED_GLASS, Material.WHITE_STAINED_GLASS);
        BLOCK_REPLACEMENTS.put(Material.BLACK_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE);

        // Carpet and Banners
        BLOCK_REPLACEMENTS.put(Material.WHITE_CARPET, Material.GREEN_CARPET);
        BLOCK_REPLACEMENTS.put(Material.ORANGE_CARPET, Material.RED_CARPET);
        BLOCK_REPLACEMENTS.put(Material.YELLOW_CARPET, Material.BLUE_CARPET);
        BLOCK_REPLACEMENTS.put(Material.LIME_CARPET, Material.CYAN_CARPET);
        BLOCK_REPLACEMENTS.put(Material.PINK_CARPET, Material.BROWN_CARPET);
        BLOCK_REPLACEMENTS.put(Material.PURPLE_CARPET, Material.MAGENTA_CARPET);
        BLOCK_REPLACEMENTS.put(Material.RED_CARPET, Material.BLACK_CARPET);
        BLOCK_REPLACEMENTS.put(Material.WHITE_BANNER, Material.YELLOW_BANNER);
        BLOCK_REPLACEMENTS.put(Material.ORANGE_BANNER, Material.PINK_BANNER);

        // Lanterns and Signs
        BLOCK_REPLACEMENTS.put(Material.OAK_SIGN, Material.BIRCH_SIGN);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_SIGN, Material.JUNGLE_SIGN);
        BLOCK_REPLACEMENTS.put(Material.ACACIA_SIGN, Material.DARK_OAK_SIGN);
        BLOCK_REPLACEMENTS.put(Material.OAK_WALL_SIGN, Material.BIRCH_WALL_SIGN);
        BLOCK_REPLACEMENTS.put(Material.SPRUCE_WALL_SIGN, Material.JUNGLE_WALL_SIGN);
        BLOCK_REPLACEMENTS.put(Material.ACACIA_WALL_SIGN, Material.DARK_OAK_WALL_SIGN);

        // Miscellaneous Blocks (continued)
        BLOCK_REPLACEMENTS.put(Material.REDSTONE, Material.GLOWSTONE_DUST);
        BLOCK_REPLACEMENTS.put(Material.CHEST, Material.TRAPPED_CHEST);
        BLOCK_REPLACEMENTS.put(Material.BARREL, Material.BLAST_FURNACE);
        BLOCK_REPLACEMENTS.put(Material.HOPPER, Material.CHEST);
        BLOCK_REPLACEMENTS.put(Material.BOOKSHELF, Material.LECTERN);
        BLOCK_REPLACEMENTS.put(Material.ENCHANTING_TABLE, Material.ANVIL);
        BLOCK_REPLACEMENTS.put(Material.CAULDRON, Material.BREWING_STAND);
        BLOCK_REPLACEMENTS.put(Material.END_PORTAL_FRAME, Material.PURPUR_BLOCK);
        BLOCK_REPLACEMENTS.put(Material.BEACON, Material.IRON_BLOCK);

    }

    @Override
    public String getName() { return "PlagueCloud"; }

    @Override
    public String getDescription() {
        return "Summons an expanding cloud of decay that corrupts the land and weakens enemies";
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
        return "necromancer";
    }

    @Override
    public boolean isTriggerAction(Action action) {
        return false;
    }

    @Override
    public void activate(Player player) {
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(
                    new BlockProtectionListener(),
                    Spellbreak.getInstance()
            );
            listenerRegistered = true;
        }

        Location spawnLocation = player.getLocation().add(0, 1, 0);
        player.getWorld().playSound(
                spawnLocation,
                Sound.ENTITY_WITHER_SHOOT,
                1.0f,
                0.5f
        );

        // Spawn an AEC with no vanilla visuals
        AreaEffectCloud cloud = (AreaEffectCloud) player.getWorld().spawnEntity(
                spawnLocation,
                EntityType.AREA_EFFECT_CLOUD
        );

        // 1) Start at zero radius so the default white swirl never shows
        cloud.setRadius(0f);

        // 2) Point its particle to AIR just in case
        cloud.setParticle(
                Particle.BLOCK_CRUMBLE,
                Material.AIR.createBlockData()
        );

        // 3) Remove all built-in behaviors
        cloud.clearCustomEffects();
        cloud.setRadiusOnUse(0);
        cloud.setRadiusPerTick(0);
        cloud.setDuration(duration * 20);
        cloud.setColor(Color.BLACK);
        cloud.setReapplicationDelay(Integer.MAX_VALUE);

        // 4) Kick off your custom behavior after the first interval,
        //    so expandCloud() is the first thing to set a non-zero radius.
        new CloudBehavior(cloud, player)
                .runTaskTimer(
                        Spellbreak.getInstance(),
                        expansionInterval,
                        expansionInterval
                );
    }


    private class CloudBehavior extends BukkitRunnable {
        private final AreaEffectCloud cloud;
        private final Player caster;
        private final Map<Location, BlockData> changedBlocks = new HashMap<>();
        private final Map<UUID, BukkitTask> activeWithers = new HashMap<>();
        private int ticks = 0;
        private double currentRadius = 2.0;

        public CloudBehavior(AreaEffectCloud cloud, Player caster) {
            this.cloud = cloud;
            this.caster = caster;
        }

        @Override
        public void run() {
            if (cloud.isDead() || ticks >= duration * 20) {
                cleanup();
                cancel();
                return;
            }

            if (ticks % expansionInterval == 0) expandCloud();
            applyEffects();
            spawnCustomParticles(cloud.getLocation(), currentRadius);
            ticks++;
        }

        private void expandCloud() {
            if (currentRadius < maxRadius) {
                currentRadius = Math.min(currentRadius + 2.5, maxRadius);
                cloud.setRadius((float) currentRadius);
                processBlocks();
            }
        }


        private void processBlocks() {
            World world = cloud.getWorld();
            Location center = cloud.getLocation();
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();
            double radiusSq = currentRadius * currentRadius;

            for (int x = (int) (centerX - currentRadius); x <= centerX + currentRadius; x++) {
                for (int y = (int) (centerY - currentRadius); y <= centerY + currentRadius; y++) {
                    for (int z = (int) (centerZ - currentRadius); z <= centerZ + currentRadius; z++) {
                        double dx = x - centerX;
                        double dy = y - centerY;
                        double dz = z - centerZ;

                        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                            Block block = world.getBlockAt(x, y, z);
                            Material originalType = block.getType();
                            if (BLOCK_REPLACEMENTS.containsKey(originalType)) {
                                Location loc = block.getLocation();
                                if (!changedBlocks.containsKey(loc)) {
                                    changedBlocks.put(loc, block.getBlockData());
                                    block.setType(BLOCK_REPLACEMENTS.get(originalType));
                                    PROTECTED_BLOCKS.merge(new BlockPosition(loc), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void cleanup() {
            // Cleanup blocks
            for (Map.Entry<Location, BlockData> entry : changedBlocks.entrySet()) {
                Location loc = entry.getKey();
                BlockPosition pos = new BlockPosition(loc);
                PROTECTED_BLOCKS.computeIfPresent(pos, (k, v) -> v > 1 ? v - 1 : null);
                loc.getBlock().setBlockData(entry.getValue());
            }
            changedBlocks.clear();

            // Cleanup wither effects
            activeWithers.values().forEach(BukkitTask::cancel);
            activeWithers.clear();
        }

        private void applyEffects() {
            Location loc = cloud.getLocation();
            double radiusSq = currentRadius * currentRadius;

            for (Entity e : cloud.getWorld().getNearbyEntities(loc, currentRadius, currentRadius, currentRadius)) {
                if (!(e instanceof LivingEntity) || e.equals(caster)) continue;

                LivingEntity target = (LivingEntity) e;
                if (loc.distanceSquared(target.getLocation()) > radiusSq) {
                    removeEffects(target);
                    continue;
                }

                applyWitherEffect(target);
                applyVisualEffects(target);
            }
        }

        private void applyWitherEffect(LivingEntity target) {
            // Cancel existing effect if reapplying
            if (activeWithers.containsKey(target.getUniqueId())) {
                return;
            }

            // Start new wither task
            activeWithers.put(target.getUniqueId(), new BukkitRunnable() {
                int ticksActive = 0;

                @Override
                public void run() {
                    if (!target.isValid() || target.isDead() || ticksActive >= witherDuration * 20) {
                        activeWithers.remove(target.getUniqueId());
                        cancel();
                        return;
                    }

                    if (ticksActive % 40 == 0) { // Damage every 2 seconds
                        Spellbreak.getInstance().getAbilityDamage().damage(
                                target,
                                1.0, // Wither damage amount
                                caster,
                                PlagueCloudAbility.this,
                                "PlagueWither"
                        );
                    }

                    // Wither particles
                    target.getWorld().spawnParticle(
                            Particle.SMOKE,
                            target.getLocation().add(0, 1, 0),
                            3,
                            0.3,
                            0.5,
                            0.3,
                            0.01
                    );

                    ticksActive++;
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L));
        }

        private void removeEffects(LivingEntity target) {
            if (activeWithers.containsKey(target.getUniqueId())) {
                activeWithers.get(target.getUniqueId()).cancel();
                activeWithers.remove(target.getUniqueId());
            }
        }

        private void applyVisualEffects(LivingEntity target) {
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    40,
                    0,
                    true,
                    true
            ));

            target.getWorld().spawnParticle(
                    Particle.SMOKE,
                    target.getEyeLocation(),
                    20,
                    0.5,
                    0.5,
                    0.5,
                    0.05
            );
            target.getWorld().playSound(
                    target.getLocation(),
                    Sound.ENCHANT_THORNS_HIT,
                    0.5f,
                    0.5f
            );
        }


        private void spawnCustomParticles(Location center, double radius) {
            World world = center.getWorld();
            Random random = new Random();

            // Only get entities within the CURRENT radius, not max radius
            Collection<Entity> nearbyEntities = cloud.getWorld().getNearbyEntities(
                    center,  // Use the cloud's center location
                    radius,  // Use the current radius
                    radius,
                    radius
            );

            // Get valid targets excluding caster
            List<Entity> targets = new ArrayList<>(nearbyEntities);
            targets.removeIf(e ->
                    e.getUniqueId().equals(caster.getUniqueId()) ||
                            !(e instanceof LivingEntity)
            );

            // Particle density controls
            int baseParticles = 50; // Total particles to spawn
            int particlesPerTarget = 8; // Particles per entity
            int ambientParticles = Math.max(10, baseParticles - (targets.size() * particlesPerTarget));

            // Spawn target-focused particles
            for (Entity target : targets) {
                Location targetLoc = target.getLocation().add(0, 0.5, 0);

                for (int i = 0; i < particlesPerTarget; i++) {
                    // Create ring around entity
                    double angle = Math.toRadians((360.0 / particlesPerTarget) * i);
                    double distanceFromEntity = 0.5 + random.nextDouble() * 1.5;
                    double x = targetLoc.getX() + distanceFromEntity * Math.cos(angle);
                    double z = targetLoc.getZ() + distanceFromEntity * Math.sin(angle);
                    double y = targetLoc.getY() - 1 + random.nextDouble() * 2;

                    Location spawnLoc = new Location(world, x, y, z);

                    // Calculate upward swirling direction
                    org.bukkit.util.Vector direction = targetLoc.toVector()
                            .subtract(spawnLoc.toVector())
                            .add(new org.bukkit.util.Vector(
                                    (random.nextDouble() - 0.5) * 0.2,
                                    random.nextDouble() * 0.5,
                                    (random.nextDouble() - 0.5) * 0.2
                            ))
                            .normalize()
                            .multiply(0.25);

                    // Cluster particles around entity
                    world.spawnParticle(Particle.SOUL,
                            spawnLoc,
                            10, // Count per particle
                            direction.getX(),
                            direction.getY(),
                            direction.getZ(),
                            0.15
                    );
                    world.spawnParticle(Particle.SMOKE,
                            spawnLoc,
                            1,
                            0, 0.1, 0,
                            0.05
                    );
                }
            }

            // Ambient particles for area filling - KEEPING ORIGINAL CODE
            for (int i = 0; i < ambientParticles; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = random.nextDouble() * radius;
                double x = center.getX() + distance * Math.cos(angle);
                double z = center.getZ() + distance * Math.sin(angle);
                double y = center.getY() + (radius * 0.6 * (ticks % 20) / 20.0);

                // Swirling upward motion
                world.spawnParticle(Particle.SOUL,
                        new Location(world, x, y, z),
                        1,
                        (random.nextDouble() - 0.5) * 0.1,
                        0.15,
                        (random.nextDouble() - 0.5) * 0.1,
                        0.1
                );
            }
        }
    }


    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.plaguecloud.cooldown", cooldown);
        manaCost = cfg.getInt("abilities.plaguecloud.mana-cost", manaCost);
        maxRadius = cfg.getDouble("abilities.plaguecloud.max-radius", maxRadius);
        duration = cfg.getInt("abilities.plaguecloud.duration", duration);
        damagePerTick = cfg.getDouble("abilities.plaguecloud.damage", damagePerTick);
        expansionInterval = cfg.getInt("abilities.plaguecloud.expansion-interval", expansionInterval);
        witherDuration = cfg.getInt("abilities.plaguecloud.wither-duration", witherDuration);
        healReduction = cfg.getDouble("abilities.plaguecloud.heal-reduction", healReduction);
    }

    @Override
    public boolean isSuccessful() { return true; }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        if ("PlagueWither".equals(subAbilityName)) {
            return String.format("§4%s §6withered away in §c%s's §dplague cloud",
                    victimName, casterName);
        }
        return String.format("§4%s §6was consumed by §c%s's §dplague cloud",
                victimName, casterName);
    }
}
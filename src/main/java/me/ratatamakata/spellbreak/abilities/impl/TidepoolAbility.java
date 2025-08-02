package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TidepoolAbility implements Ability {
    private int cooldown = 21;
    private int manaCost = 50;
    private String requiredClass = "elementalist";
    private double radius = 3.0;
    private int duration = 8;
    private double damage = 1.5;
    private double pushStrength = 1.0;
    private int barrierHeight = 4;
    private final int layerDelay = 4;
    private final Set<UUID> activeCasters = new HashSet<>();

    @Override
    public String getName() {
        return "Tidepool";
    }

    @Override
    public String getDescription() {
        return "Creates a water barrier that traps enemies, pushing them inward and dealing damage. Afterward, a wave expands outward.";
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
        return false;
    }

    public int getAdjustedCooldown(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return (int) (cooldown * lvl.getCooldownReduction());
    }

    public int getAdjustedManaCost(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return (int) (manaCost * lvl.getManaCostReduction());
    }

    public double getAdjustedDamage(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return damage * lvl.getDamageMultiplier();
    }

    public double getAdjustedRadius(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return radius * lvl.getRangeMultiplier();
    }

    public int getAdjustedDuration(Player player) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
            player.getUniqueId(),
            Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
            getName()
        );
        return (int) (duration * lvl.getDurationMultiplier());
    }

    @Override
    public void activate(Player player) {
        if (!activeCasters.add(player.getUniqueId())) return;

        double adjustedRadius = getAdjustedRadius(player);
        double adjustedDamage = getAdjustedDamage(player);
        int adjustedDuration = getAdjustedDuration(player);

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0.5, 0, 0.5);
        world.playSound(center, Sound.ITEM_BUCKET_EMPTY, 1.0f, 0.8f);
        world.playSound(center, Sound.BLOCK_WATER_AMBIENT, 1.5f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            final Map<UUID, Long> damageCooldowns = new HashMap<>();
            final List<Block> waterBlocks = new ArrayList<>();
            final List<BlockData> originalBlocks = new ArrayList<>();
            final List<List<Block>> layerBlocks = new ArrayList<>();
            final List<List<BlockData>> layerOriginalData = new ArrayList<>();
            final Set<Block> expandedWaterBlocks = new HashSet<>();
            double expansionRadius = adjustedRadius;
            boolean inExpansionPhase = false;

            final Listener flowListener = new Listener() {
                @EventHandler
                public void onFlow(BlockFromToEvent e) {
                    if (waterBlocks.contains(e.getBlock()) || waterBlocks.contains(e.getToBlock()) ||
                            expandedWaterBlocks.contains(e.getBlock()) || expandedWaterBlocks.contains(e.getToBlock())) {
                        e.setCancelled(true);
                    }
                }
            };

            {
                Bukkit.getPluginManager().registerEvents(flowListener, Spellbreak.getInstance());
                double minDist = adjustedRadius - 0.5, maxDist = adjustedRadius + 0.5;
                int range = (int) Math.ceil(maxDist);
                for (int yOff = 0; yOff < barrierHeight; yOff++) {
                    int y = center.getBlockY() + yOff;
                    List<Block> currentLayer = new ArrayList<>();
                    List<BlockData> originalLayer = new ArrayList<>();
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist >= minDist && dist <= maxDist) {
                                Block b = world.getBlockAt(center.getBlockX() + dx, y, center.getBlockZ() + dz);
                                if (isReplaceable(b.getType())) {
                                    currentLayer.add(b);
                                    originalLayer.add(b.getBlockData().clone());
                                }
                            }
                        }
                    }
                    layerBlocks.add(currentLayer);
                    layerOriginalData.add(originalLayer);
                }
            }

            private boolean isReplaceable(Material m) {
                return m.isAir() || m == Material.SHORT_GRASS || m == Material.TALL_GRASS ||
                        m == Material.SEAGRASS || m == Material.SNOW || m == Material.VINE;
            }

            private void setWater(Block b) {
                b.setType(Material.WATER, false);
                if (b.getBlockData() instanceof Levelled) {
                    Levelled lvl = (Levelled) b.getBlockData(); lvl.setLevel(0);
                    b.setBlockData(lvl, false);
                }
            }

            @Override
            public void run() {
                if (!player.isValid()) {
                    cleanup();
                    return;
                }

                if (ticks >= adjustedDuration * 20 && !inExpansionPhase) {
                    inExpansionPhase = true;
                    ticks = 0;
                }

                if (!inExpansionPhase) {
                    int layerIndex = ticks / layerDelay;
                    if (layerIndex < barrierHeight && ticks % layerDelay == 0) {
                        List<Block> currentLayer = layerBlocks.get(layerIndex);
                        List<BlockData> originalLayer = layerOriginalData.get(layerIndex);
                        for (int i = 0; i < currentLayer.size(); i++) {
                            Block b = currentLayer.get(i);
                            waterBlocks.add(b);
                            originalBlocks.add(originalLayer.get(i));
                            setWater(b);
                        }
                    }

                    for (Block b : waterBlocks) {
                        setWater(b);
                        b.getWorld().spawnParticle(Particle.BUBBLE, b.getLocation().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.05);
                    }

                    double minDist = adjustedRadius - 0.5, maxDist = adjustedRadius + 0.5;
                    for (Entity e : world.getNearbyEntities(center, adjustedRadius + 1, barrierHeight, adjustedRadius + 1)) {
                        if (!(e instanceof LivingEntity) || e.equals(player)) continue;
                        Location loc = e.getLocation();
                        if (loc.getY() < center.getY() || loc.getY() > center.getY() + barrierHeight) continue;
                        double dx = loc.getX() - center.getX(), dz = loc.getZ() - center.getZ();
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist < minDist || dist > maxDist) continue;
                        e.setVelocity(center.toVector().subtract(loc.toVector()).normalize().multiply(pushStrength));
                        UUID id = e.getUniqueId();
                        if (System.currentTimeMillis() - damageCooldowns.getOrDefault(id, 0L) > 1000) {
                            Spellbreak.getInstance().getAbilityDamage().damage((LivingEntity) e, adjustedDamage, player, TidepoolAbility.this, "Tidepool");
                            damageCooldowns.put(id, System.currentTimeMillis());
                        }
                    }

                } else {
                    expansionRadius += 0.1;
                    double ringMin = expansionRadius - 0.5, ringMax = expansionRadius + 0.5;
                    int y = center.getBlockY();

                    for (int dx = -(int) expansionRadius - 1; dx <= expansionRadius + 1; dx++) {
                        for (int dz = -(int) expansionRadius - 1; dz <= expansionRadius + 1; dz++) {
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist < ringMin || dist > ringMax) continue;

                            for (int dy = 0; dy < barrierHeight; dy++) {
                                Block b = world.getBlockAt(center.getBlockX() + dx, y + dy, center.getBlockZ() + dz);
                                if (isReplaceable(b.getType()) && !expandedWaterBlocks.contains(b)) {
                                    setWater(b);
                                    expandedWaterBlocks.add(b);
                                    b.getWorld().spawnParticle(Particle.SPLASH, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
                                }
                            }
                        }
                    }

                    for (Entity e : world.getNearbyEntities(center, expansionRadius + 1, barrierHeight, expansionRadius + 1)) {
                        if (!(e instanceof LivingEntity) || e.equals(player)) continue;
                        Location loc = e.getLocation();
                        double dx = loc.getX() - center.getX(), dz = loc.getZ() - center.getZ();
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist < ringMin || dist > ringMax) continue;
                        Vector pushVec = loc.toVector().subtract(center.toVector()).normalize().multiply(0.8);
                        e.setVelocity(pushVec);
                    }

                    if (expansionRadius > adjustedRadius + 4) {
                        cleanup();
                        return;
                    }
                }

                ticks++;
            }

            private void cleanup() {
                for (int i = 0; i < waterBlocks.size(); i++) {
                    waterBlocks.get(i).setBlockData(originalBlocks.get(i), false);
                }
                for (Block b : expandedWaterBlocks) {
                    b.setType(Material.AIR, false);
                }
                HandlerList.unregisterAll(flowListener);
                activeCasters.remove(player.getUniqueId());
                cancel();
            }

        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.tidepool.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        radius = cfg.getDouble(base + "radius", radius);
        duration = cfg.getInt(base + "duration", duration);
        damage = cfg.getDouble(base + "damage", damage);
        pushStrength = cfg.getDouble(base + "push-strength", pushStrength);
        barrierHeight = cfg.getInt(base + "barrier-height", barrierHeight);
        requiredClass = cfg.getString(base + "required-class", requiredClass);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return String.format("§b%s §3was drowned in §b%s§3's Tidepool!", victim, caster);
    }
}

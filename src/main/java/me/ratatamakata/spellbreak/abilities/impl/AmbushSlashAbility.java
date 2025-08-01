package me.ratatamakata.spellbreak.abilities.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.util.ProjectileUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Player.Spigot;
import org.bukkit.event.block.Action;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class AmbushSlashAbility implements Ability {
    // Ability properties
    private String name = "AmbushSlash";
    private String description = "Stage 1: Fire a Marking Shot. Stage 2: Teleport behind the marked target and slash.";
    private int manaCost = 30;
    private String requiredClass = "archdruid";
    private int cooldown = 11;
    private double stage1Damage = 1.0D;
    private double stage2Damage = 1.0D;
    private int markDurationTicks = 50;
    private int natureBurnDurationTicks = 10;
    private int natureBurnAmplifier = 0;
    private double natureBurnSpreadRadius = 3.0D;
    private double teleportOffset = 1.5D;
    private double blastRange = 16.0D;
    private double blastCheckRadius = 1.0D;
    private double projectileSpeed = 5.0D;
    private String targetIndicatorParticle = "DUST";
    private Color targetIndicatorColor = Color.fromRGB(180, 0, 255);
    private float targetIndicatorSize = 1.0F;
    private Color blastInitialColor = Color.fromRGB(50, 255, 50);
    private Color blastTrailColor = Color.fromRGB(0, 150, 0);
    private float blastParticleSize = 1.1F;
    private double spiralRadius = 0.4D;
    private double spiralSpeed = 0.7853981633974483D;
    private final Map<UUID, UUID> markedTargets = new HashMap();
    private final Map<UUID, BukkitTask> markTimers = new HashMap();
    private final Map<UUID, BukkitTask> targetIndicatorTasks = new HashMap();
    private boolean successfulActivation = false;
    private final Set<UUID> activeProjectileCasters = new HashSet();
    private static final String NATURE_BURN_SOURCE_METADATA_KEY = "NatureBurnSource";

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
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
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    public boolean isSuccessful() {
        boolean success = this.successfulActivation;
        this.successfulActivation = false;
        return success;
    }

    public boolean hasActiveProjectile(UUID casterId) {
        return this.activeProjectileCasters.contains(casterId);
    }

    public void setActiveProjectile(UUID casterId, boolean active) {
        if (active) {
            this.activeProjectileCasters.add(casterId);
        } else {
            this.activeProjectileCasters.remove(casterId);
        }

    }

    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String path = "abilities.ambushslash.";
        this.manaCost = cfg.getInt(path + "mana-cost", this.manaCost);
        this.requiredClass = cfg.getString(path + "required-class", this.requiredClass);
        this.cooldown = cfg.getInt(path + "cooldown", this.cooldown);
        this.stage1Damage = cfg.getDouble(path + "stage1-damage", this.stage1Damage);
        this.stage2Damage = cfg.getDouble(path + "stage2-damage", this.stage2Damage);
        this.markDurationTicks = cfg.getInt(path + "mark-duration-ticks", this.markDurationTicks);
        this.natureBurnDurationTicks = cfg.getInt(path + "nature-burn.duration-ticks", this.natureBurnDurationTicks);
        this.natureBurnAmplifier = cfg.getInt(path + "nature-burn.amplifier", this.natureBurnAmplifier);
        this.natureBurnSpreadRadius = cfg.getDouble(path + "nature-burn.spread-radius", this.natureBurnSpreadRadius);
        this.teleportOffset = cfg.getDouble(path + "teleport-offset", this.teleportOffset);
        this.blastRange = cfg.getDouble(path + "blast-range", this.blastRange);
        this.blastCheckRadius = cfg.getDouble(path + "blast-check-radius", this.blastCheckRadius);
        this.projectileSpeed = cfg.getDouble(path + "projectile-speed", this.projectileSpeed);
        this.targetIndicatorParticle = cfg.getString(path + "target-indicator-particle", this.targetIndicatorParticle);
        this.targetIndicatorColor = Color.fromRGB(cfg.getInt(path + "target-indicator-color.r", 180), cfg.getInt(path + "target-indicator-color.g", 0), cfg.getInt(path + "target-indicator-color.b", 255));
        this.targetIndicatorSize = (float)cfg.getDouble(path + "target-indicator-size", 1.0D);
        this.blastInitialColor = Color.fromRGB(cfg.getInt(path + "blast-initial-color.r", 50), cfg.getInt(path + "blast-initial-color.g", 255), cfg.getInt(path + "blast-initial-color.b", 50));
        this.blastTrailColor = Color.fromRGB(cfg.getInt(path + "blast-trail-color.r", 0), cfg.getInt(path + "blast-trail-color.g", 150), cfg.getInt(path + "blast-trail-color.b", 0));
        this.blastParticleSize = (float)cfg.getDouble(path + "blast-particle-size", (double)this.blastParticleSize);
        this.spiralRadius = cfg.getDouble(path + "spiral-radius", 0.4D);
        this.spiralSpeed = cfg.getDouble(path + "spiral-speed", 0.7853981633974483D);
        this.description = String.format("Mark target (%.1f dmg), then teleport behind (%.1f dmg + %ds Burn). %ds CD.", this.stage1Damage, this.stage2Damage, this.natureBurnDurationTicks / 20, this.cooldown);
    }

    public void activate(Player player) {
        // Check if player is valid and has enough mana
        UUID casterUUID = player.getUniqueId();
        this.successfulActivation = false;
        if (this.markedTargets.containsKey(casterUUID)) {
            UUID targetUUID = (UUID)this.markedTargets.get(casterUUID);
            Entity targetEntity = Bukkit.getEntity(targetUUID);
            if (targetEntity instanceof LivingEntity && !targetEntity.isDead()) {
                this.executeStage2(player, (LivingEntity)targetEntity);
            } else {
                this.clearMark(casterUUID);
                player.sendMessage(String.valueOf(ChatColor.RED) + "Marked target invalid!");
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0F, 1.0F);
            }
        } else {
            this.launchMarkingProjectile(player);
            this.successfulActivation = true;
        }

    }

    private void applyFizzleAndCooldown(Player caster, Location finalLoc) {
        // Apply fizzle effect
        if (caster != null && caster.isOnline()) {
            if (finalLoc.getWorld() != null) {
                finalLoc.getWorld().spawnParticle(Particle.SMOKE, finalLoc, 5, 0.1D, 0.1D, 0.1D, 0.01D);
            }

            this.setCooldown(caster);
        }

    }

    private void launchMarkingProjectile(Player caster) {
        // Launch the marking projectile
        World world = caster.getWorld();
        Location origin = caster.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        DustOptions trailDust = new DustOptions(this.blastTrailColor, this.blastParticleSize);
        DustOptions initialBurstDust = new DustOptions(this.blastInitialColor, this.blastParticleSize * 1.2F);
        this.setActiveProjectile(caster.getUniqueId(), true);
        world.playSound(origin, Sound.ENTITY_PHANTOM_FLAP, 0.8F, 1.2F);
        world.spawnParticle(Particle.DUST, origin.clone().add(dir.multiply(0.2D)), 25, 0.25D, 0.25D, 0.25D, 0.0D, initialBurstDust);
        ProjectileUtil.launchProjectile(caster, origin, dir, this.projectileSpeed, this.blastRange, 4.0D, this.blastCheckRadius, Particle.DUST, trailDust, 3, Particle.TOTEM_OF_UNDYING, (Object)null, 1, (Predicate)null, (hitEntity) -> {
            this.handleEntityHitAndEndProjectile(caster, hitEntity);
        }, (hitBlock, hitLoc) -> {
            this.handleBlockCollisionAndEndProjectile(caster, hitBlock, hitLoc);
        }, (finalLoc) -> {
            this.applyFizzleAndCooldownAndEndProjectile(caster, finalLoc);
        }, this.spiralRadius, this.spiralSpeed);
    }

    private boolean hasClearPath(Location from, Location to) {
        if (from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
            double dist = from.distance(to);
            if (dist < 0.1D) {
                return true;
            } else {
                Vector direction = to.toVector().subtract(from.toVector()).normalize();
                return from.getWorld().rayTraceBlocks(from, direction, dist, FluidCollisionMode.NEVER, true) == null;
            }
        } else {
            return false;
        }
    }

    private void handleBlockCollision(Block block, Location hitLoc) {
        // Handle block collision effects
        if (block != null && block.getWorld() != null) {
            block.getWorld().spawnParticle(Particle.FALLING_DUST, hitLoc, 5, 0.1D, 0.1D, 0.1D, 0.0D, block.getBlockData());
            block.getWorld().playSound(hitLoc, Sound.BLOCK_STONE_HIT, 0.5F, 1.0F);
        } else if (hitLoc != null && hitLoc.getWorld() != null) {
            hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_STONE_HIT, 0.5F, 1.0F);
        }

    }

    private void handleEntityHit(Player caster, LivingEntity target) {
        Spellbreak.getInstance().getAbilityDamage().damage(target, this.stage1Damage, caster, this, (String)null);
        this.markTarget(caster, target);
        DustOptions hitEffectDust = new DustOptions(this.blastInitialColor, this.blastParticleSize + 0.2F);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.4D, 0.5D, 0.4D, 0.0D, hitEffectDust);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_AZALEA_LEAVES_HIT, 1.0F, 1.0F);
    }

    private void setCooldown(Player player) {
        if (player != null && player.isOnline()) {
            Spellbreak.getInstance().getCooldownManager().setCooldown(player, this.getName(), this.cooldown);
        }

    }

    private void markTarget(Player caster, LivingEntity target) {
        final UUID casterUUID = caster.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        this.clearMark(casterUUID);
        this.markedTargets.put(casterUUID, targetUUID);
        Spigot var10000 = caster.spigot();
        ChatMessageType var10001 = ChatMessageType.ACTION_BAR;
        String var10004 = String.valueOf(ChatColor.GREEN);
        var10000.sendMessage(var10001, new TextComponent(var10004 + "Target marked! Left-Click again within " + this.markDurationTicks / 20 + "s to Ambush."));
        this.startTargetIndicator(target);
        final UUID originalTargetUUID = target.getUniqueId();
        BukkitTask expiryTask = (new BukkitRunnable() {
            public void run() {
                UUID currentMarkedTarget = (UUID)AmbushSlashAbility.this.markedTargets.get(casterUUID);
                if (currentMarkedTarget != null && currentMarkedTarget.equals(originalTargetUUID)) {
                    AmbushSlashAbility.this.markedTargets.remove(casterUUID);
                    Player onlineCaster = Bukkit.getPlayer(casterUUID);
                    LivingEntity targetEntity = (LivingEntity)Bukkit.getEntity(originalTargetUUID);
                    String targetName = targetEntity != null ? targetEntity.getName() : "target";
                    if (onlineCaster != null && onlineCaster.isOnline()) {
                        String var10001 = String.valueOf(ChatColor.YELLOW);
                        onlineCaster.sendMessage(var10001 + "Mark on " + targetName + " faded.");
                        AmbushSlashAbility.this.setCooldown(onlineCaster);
                    }

                    AmbushSlashAbility.this.cancelTargetIndicator(originalTargetUUID);
                }

                AmbushSlashAbility.this.markTimers.remove(casterUUID);
            }
        }).runTaskLater(Spellbreak.getInstance(), (long)this.markDurationTicks);
        BukkitTask oldTask = (BukkitTask)this.markTimers.put(casterUUID, expiryTask);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }

    }

    private void executeStage2(Player caster, LivingEntity target) {
        UUID casterUUID = caster.getUniqueId();
        World world = target.getWorld();
        Location targetLoc = target.getLocation();
        Vector targetBackward = target.getEyeLocation().getDirection().normalize().multiply(-1);
        Location teleportDest = targetLoc.clone().add(targetBackward.multiply(this.teleportOffset));
        Block blockAtDest = teleportDest.getBlock();
        Block blockBelowDest = blockAtDest.getRelative(BlockFace.DOWN);
        boolean foundGround = false;
        if (blockBelowDest.getType().isSolid()) {
            teleportDest.setY((double)blockBelowDest.getY() + 1.0D);
            foundGround = true;
        } else {
            Location groundCheckLoc = blockBelowDest.getLocation();

            for(int i = 0; i < 3; ++i) {
                Block nextBlockBelow = groundCheckLoc.subtract(0.0D, 1.0D, 0.0D).getBlock();
                if (nextBlockBelow.getType().isSolid()) {
                    teleportDest.setY((double)nextBlockBelow.getY() + 1.0D);
                    foundGround = true;
                    break;
                }

                if (nextBlockBelow.getY() < world.getMinHeight()) {
                    break;
                }
            }
        }

        if (!foundGround) {
            caster.sendMessage(String.valueOf(ChatColor.RED) + "Cannot find safe ground behind target!");
            this.successfulActivation = false;
        } else {
            teleportDest.setYaw(target.getLocation().getYaw());
            teleportDest.setPitch(target.getLocation().getPitch());
            DustOptions teleportDust = new DustOptions(Color.LIME, 1.5F);
            Location preTeleportLoc = caster.getLocation();
            world.spawnParticle(Particle.DUST, preTeleportLoc, 50, 0.5D, 0.5D, 0.5D, 0.0D, teleportDust);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, preTeleportLoc.add(0.0D, 1.0D, 0.0D), 30, 0.3D, 0.5D, 0.3D, 0.1D);
            world.playSound(preTeleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 0.8F);
            caster.teleport(teleportDest);
            world.spawnParticle(Particle.DUST, teleportDest, 50, 0.5D, 0.5D, 0.5D, 0.0D, teleportDust);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, targetLoc.add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.5D, 0.4D, 0.2D);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, targetLoc.add(0.0D, 1.0D, 0.0D), 20, 0.4D, 0.5D, 0.4D, 0.1D);
            world.playSound(teleportDest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
            Spellbreak.getInstance().getAbilityDamage().damage(target, this.stage2Damage, caster, this, (String)null);
            this.applyNatureBurn(target, caster);
            world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.9F);
            world.playSound(target.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.8F, 1.2F);
            Location casterEyeLoc = caster.getEyeLocation();
            Vector direction = casterEyeLoc.getDirection().normalize();
            Vector perpendicular = (new Vector(-direction.getZ(), 0.0D, direction.getX())).normalize();
            if (perpendicular.lengthSquared() < 0.01D) {
                perpendicular = new Vector(1, 0, 0);
            }

            Vector up = direction.clone().crossProduct(perpendicular).normalize();
            Location center = casterEyeLoc.add(direction.multiply(1.0D));
            int pointsPerLine = 8;
            double lineLength = 1.8D;

            for(int i = -pointsPerLine / 2; i <= pointsPerLine / 2; ++i) {
                if (i != 0) {
                    double scale = (double)i / ((double)pointsPerLine / 2.0D) * (lineLength / 2.0D);
                    Vector offset1 = perpendicular.clone().multiply(scale).add(up.clone().multiply(scale));
                    Location loc1 = center.clone().add(offset1);
                    world.spawnParticle(Particle.CRIT, loc1, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, loc1, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(Particle.DUST, loc1, 1, 0.05D, 0.05D, 0.05D, 0.0D, teleportDust);
                    Vector offset2 = perpendicular.clone().multiply(-scale).add(up.clone().multiply(scale));
                    Location loc2 = center.clone().add(offset2);
                    world.spawnParticle(Particle.CRIT, loc2, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, loc2, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    world.spawnParticle(Particle.DUST, loc2, 1, 0.05D, 0.05D, 0.05D, 0.0D, teleportDust);
                }
            }

            this.clearMark(casterUUID);
            this.setCooldown(caster);
            this.successfulActivation = true;
        }

    }

    private void applyNatureBurn(final LivingEntity target, final Player caster) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, this.natureBurnDurationTicks, this.natureBurnAmplifier, false, true, true));
        target.setMetadata("NatureBurnSource", new FixedMetadataValue(Spellbreak.getInstance(), caster.getUniqueId()));
        (new BukkitRunnable() {
            public void run() {
                if (target.hasMetadata("NatureBurnSource")) {
                    UUID sourceUUID = (UUID)((MetadataValue)target.getMetadata("NatureBurnSource").get(0)).value();
                    if (sourceUUID != null && sourceUUID.equals(caster.getUniqueId())) {
                        target.removeMetadata("NatureBurnSource", Spellbreak.getInstance());
                    }
                }

            }
        }).runTaskLater(Spellbreak.getInstance(), (long)this.natureBurnDurationTicks + 10L);
    }

    public void handleNatureBurnSpread(LivingEntity deceased, UUID casterUUID) {
        if (!(this.natureBurnSpreadRadius <= 0.0D)) {
            Player caster = Bukkit.getPlayer(casterUUID);
            if (caster != null && caster.isOnline()) {
                World world = deceased.getWorld();
                Location center = deceased.getLocation();
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.add(0.0D, 1.0D, 0.0D), 40, 1.0D, 1.0D, 1.0D, 0.1D);
                world.playSound(center, Sound.BLOCK_FUNGUS_BREAK, 1.0F, 1.2F);
                Predicate<Entity> spreadFilter = (e) -> {
                    return e instanceof LivingEntity && !e.getUniqueId().equals(casterUUID) && !e.getUniqueId().equals(deceased.getUniqueId()) && !(e instanceof ArmorStand);
                };
                Iterator var8 = world.getNearbyEntities(center, this.natureBurnSpreadRadius, this.natureBurnSpreadRadius, this.natureBurnSpreadRadius, spreadFilter).iterator();

                while(var8.hasNext()) {
                    Entity nearbyEntity = (Entity)var8.next();
                    this.applyNatureBurn((LivingEntity)nearbyEntity, caster);
                }
            }
        }

    }

    public void clearMark(UUID casterUUID) {
        UUID targetUUID = (UUID)this.markedTargets.remove(casterUUID);
        if (targetUUID != null) {
            this.cancelTargetIndicator(targetUUID);
            BukkitTask expiryTask = (BukkitTask)this.markTimers.remove(casterUUID);
            if (expiryTask != null && !expiryTask.isCancelled()) {
                expiryTask.cancel();
            }
        }

    }

    public Map<UUID, UUID> getMarkedTargetsView() {
        return this.markedTargets;
    }

    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return subAbilityName != null && subAbilityName.equals("NatureBurn") ? String.format("%s succumbed to %s's Nature Burn.", victimName, casterName) : String.format("%s was ambushed by %s.", victimName, casterName);
    }

    private void startTargetIndicator(final LivingEntity target) {
        final UUID targetUUID = target.getUniqueId();
        this.cancelTargetIndicator(targetUUID);

        Particle indicatorParticleValue;
        try {
            indicatorParticleValue = Particle.DUST;
            if (!this.targetIndicatorParticle.equalsIgnoreCase("DUST")) {
                Spellbreak.getInstance().getLogger().warning("[AmbushSlash] Configured indicator particle '\" + targetIndicatorParticle + \"' is not DUST. Using DUST with color/size options.");
            }
        } catch (IllegalArgumentException var6) {
            Spellbreak.getInstance().getLogger().warning("[AmbushSlash] Error parsing target indicator particle. Using DUST.");
            indicatorParticleValue = Particle.DUST;
        }

        final DustOptions indicatorDustOptions = new DustOptions(this.targetIndicatorColor, this.targetIndicatorSize);
        BukkitTask task = (new BukkitRunnable() {
            public void run() {
                if (target.isValid() && !target.isDead() && AmbushSlashAbility.this.markedTargets.containsValue(targetUUID)) {
                    Location loc = target.getLocation().add(0.0D, target.getHeight() + 0.4D, 0.0D);
                    target.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.0D, 0.0D, 0.0D, 0.0D, indicatorDustOptions);
                } else {
                    AmbushSlashAbility.this.cancelTargetIndicator(targetUUID);
                    this.cancel();
                }

            }
        }).runTaskTimer(Spellbreak.getInstance(), 0L, 6L);
        this.targetIndicatorTasks.put(targetUUID, task);
    }

    private void cancelTargetIndicator(UUID targetUUID) {
        BukkitTask existingTask = (BukkitTask)this.targetIndicatorTasks.remove(targetUUID);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }

    }

    private void handleEntityHitAndEndProjectile(Player caster, LivingEntity target) {
        this.handleEntityHit(caster, target);
        this.setActiveProjectile(caster.getUniqueId(), false);
    }

    private void handleBlockCollisionAndEndProjectile(Player caster, Block hitBlock, Location hitLoc) {
        this.handleBlockCollision(hitBlock, hitLoc);
        this.setCooldown(caster);
        this.setActiveProjectile(caster.getUniqueId(), false);
    }

    private void applyFizzleAndCooldownAndEndProjectile(Player caster, Location finalLoc) {
        this.applyFizzleAndCooldown(caster, finalLoc);
        this.setActiveProjectile(caster.getUniqueId(), false);
    }
}
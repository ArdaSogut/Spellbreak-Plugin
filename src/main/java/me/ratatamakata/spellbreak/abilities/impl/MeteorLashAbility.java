package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class MeteorLashAbility implements Ability {
    private int cooldown = 11;
    private int manaCost = 20;
    private String requiredClass = "starcaller";
    private double damage = 3;
    private double knockbackStrength = 2.0;
    private int meteorDuration = 60;
    private double meteorSpeed = 1.7;            // reduced speed for gentler launch
    private int selectionTimeout = 100;
    private int selectionDuration = 100; // How long selection mode lasts
    private double selectionRange = 25; // Maximum selection range
    private double impactRadius = 3.5;
    private int particleCount = 30;

    private final Map<UUID, Location> targetSelections = new HashMap<>();
    private final Map<UUID, Long> selectionTimes = new HashMap<>();
    private final Map<UUID, BukkitRunnable> selectionIndicators = new HashMap<>();

    // adjusted parameters per-cast
    private double adjustedDamage;
    private double adjustedKnockback;
    private int adjustedMeteorDuration;
    private double adjustedMeteorSpeed;
    private double adjustedImpactRadius;

    @Override public String getName() { return "MeteorLash"; }
    @Override public String getDescription() { return "Select a location with left click, then launch a meteor that strikes in a plus pattern."; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        Location current = targetSelections.get(uuid);
        Long startTime = selectionTimes.get(uuid);
        if (startTime != null && System.currentTimeMillis() - startTime > selectionTimeout * 50) {
            cleanupSelection(uuid);
            current = null;
        }
        if (current == null) {
            startSelectionMode(player);
            return;
        }
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(uuid, Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(uuid), getName());
        adjustedDamage = damage;
        adjustedKnockback = knockbackStrength;
        adjustedMeteorDuration = meteorDuration;
        adjustedMeteorSpeed = meteorSpeed;
        adjustedImpactRadius = impactRadius;
        cleanupSelection(uuid);
        launchMeteor(player, current);
    }

    private void startSelectionMode(Player player) {
        UUID uuid = player.getUniqueId();
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(uuid, Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(uuid), getName());
        double circleRadius = impactRadius * lvl.getRangeMultiplier();
        double range = selectionRange * lvl.getRangeMultiplier();
        DustOptions dust = new DustOptions(Color.fromRGB(255, 100, 0), 1f);
        BukkitRunnable indicator = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                if (!player.isValid() || !player.isOnline() || !"MeteorLash".equalsIgnoreCase(
                        Spellbreak.getInstance().getPlayerDataManager()
                                .getAbilityAtSlot(uuid, player.getInventory().getHeldItemSlot()))
                        || ticks >= selectionDuration) {
                    cleanupSelection(uuid);
                    player.sendMessage(ChatColor.YELLOW + "Selection cancelled or timed out.");
                    cancel(); return;
                }
                Location tgt = player.getTargetBlock(null, (int)range).getLocation();
                for (int i=0; i<32; i++) {
                    double ang = i * 2 * Math.PI / 32;
                    double x = Math.cos(ang)*circleRadius;
                    double z = Math.sin(ang)*circleRadius;
                    player.getWorld().spawnParticle(Particle.DUST, tgt.clone().add(x,1.5,z), 1, dust);
                }
                player.getWorld().spawnParticle(Particle.DUST, tgt.clone().add(0,2,0), 10, dust);
                if (ticks % 20 == 0) player.sendActionBar(
                        ChatColor.AQUA + "Selection: " + ((selectionDuration-ticks)/20) + "s");
            }
        };
        indicator.runTaskTimer(Spellbreak.getInstance(), 0, 2);
        selectionIndicators.put(uuid, indicator);
        player.sendMessage(ChatColor.AQUA + "Select target (Range: " + (int)range + ", " + (selectionDuration/20) + "s)");
    }

    private void cleanupSelection(UUID uuid) {
        targetSelections.remove(uuid);
        selectionTimes.remove(uuid);
        BukkitRunnable ind = selectionIndicators.remove(uuid);
        if (ind != null) ind.cancel();
    }

    public boolean isInSelectionMode(UUID uuid) {
        return selectionIndicators.containsKey(uuid);
    }
    public void setTargetLocation(UUID uuid, Location loc) {
        targetSelections.put(uuid, loc);
        selectionTimes.put(uuid, System.currentTimeMillis());
    }

    private void launchMeteor(Player player, Location target) {
        World w = player.getWorld();
        Location start = player.getLocation().add(-8, 20, 0);
        Vector dir = target.toVector().subtract(start.toVector()).normalize();
        double hor = Math.hypot(target.getX()-start.getX(), target.getZ()-start.getZ());
        double vert = start.getY()-target.getY();
        dir.setY(-Math.sin(Math.atan2(vert,hor))).normalize();
        new BukkitRunnable(){
            Location ctr = start.clone();
            int t=0;
            List<Location> pts = new ArrayList<>();

            @Override public void run(){
                // Remove previous meteor blocks
                for(Location b : pts) {
                    if (b.getBlock().getType() == Material.NETHERRACK || b.getBlock().getType() == Material.MAGMA_BLOCK) {
                        b.getBlock().setType(Material.AIR);
                    }
                }
                pts.clear();

                // Check termination conditions
                if (t++ >= adjustedMeteorDuration || ctr.getY() <= target.getY()) {
                    List<Location> ip = createImpact(player, ctr);
                    createExplosionEffect(w, ctr, ip);
                    cancel();
                    return;
                }

                // Move meteor and create new blocks
                ctr.add(dir.clone().multiply(adjustedMeteorSpeed));
                createMeteorCube(ctr, pts);

                // Particles and entity interactions
                w.spawnParticle(Particle.FLAME, ctr, 8, 0.3, 0.3, 0.3, 0.01);
                w.spawnParticle(Particle.SMOKE, ctr, 3, 0.2, 0.2, 0.2, 0.01);
                for(Entity e : w.getNearbyEntities(ctr, 2, 2, 2)) {
                    if(e instanceof LivingEntity && !e.equals(player)) {
                        Vector kb = e.getLocation().toVector().subtract(ctr.toVector())
                                .normalize().multiply(adjustedKnockback * 0.5).setY(0.3);
                        e.setVelocity(kb);
                        Spellbreak.getInstance().getAbilityDamage()
                                .damage((LivingEntity)e, adjustedDamage, player, MeteorLashAbility.this, "MeteorLash");
                    }
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        w.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
    }

    private void createMeteorCube(Location center, List<Location> trail) {
        List<Location> pts=new ArrayList<>();
        pts.add(center.clone().add(0,-1,-1)); pts.add(center.clone().add(-1,-1,0)); pts.add(center.clone().add(1,-1,0)); pts.add(center.clone().add(0,-1,1));
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(!(x==0&&z==0)) pts.add(center.clone().add(x,0,z));
        pts.add(center.clone().add(0,1,-1)); pts.add(center.clone().add(-1,1,0)); pts.add(center.clone().add(1,1,0)); pts.add(center.clone().add(0,1,1));
        for(int i=0;i<pts.size();i++){Location p=pts.get(i); Block b=p.getBlock(); if(b.getType().isAir()){b.setType(i%2==0?Material.NETHERRACK:Material.MAGMA_BLOCK); trail.add(p);} }
    }

    private List<Location> createImpact(Player player, Location loc) {
        World w = player.getWorld();
        List<Location> pts = new ArrayList<>();
        pts.add(loc.clone());
        pts.add(loc.clone().add(1,0,0));
        pts.add(loc.clone().add(-1,0,0));
        pts.add(loc.clone().add(0,0,1));
        pts.add(loc.clone().add(0,0,-1));

        for(Location p : pts) {
            Block block = p.getBlock();
            // Only place impact blocks in air
            if(block.getType().isAir()) {
                block.setType(Material.NETHERRACK);
                // Schedule block decay after 5 seconds (100 ticks)
                new BukkitRunnable() {
                    @Override public void run() {
                        if (block.getType() == Material.NETHERRACK) {
                            block.setType(Material.AIR);
                        }
                    }
                }.runTaskLater(Spellbreak.getInstance(), 100);
            }

            // Damage and knockback calculations remain unchanged
            for(Entity e : w.getNearbyEntities(p, adjustedImpactRadius, 2, adjustedImpactRadius)) {
                if(e instanceof LivingEntity && !e.equals(player)) {
                    double d = e.getLocation().distance(p);
                    if(d <= adjustedImpactRadius) {
                        Vector kb = e.getLocation().toVector().subtract(p.toVector())
                                .normalize().multiply(adjustedKnockback).setY(0.5);
                        e.setVelocity(kb);
                        Spellbreak.getInstance().getAbilityDamage()
                                .damage((LivingEntity)e, adjustedDamage * (1 - (d/adjustedImpactRadius)),
                                        player, this, "MeteorLash");
                    }
                }
            }
            w.spawnParticle(Particle.EXPLOSION, p, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.FLAME, p, particleCount, 0.5, 0.5, 0.5, 0.1);
            w.spawnParticle(Particle.SMOKE, p, 10, 0.3, 0.3, 0.3, 0.05);
            w.playSound(p, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            w.playSound(p, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
        }
        return pts;
    }

    // Updated createExplosionEffect:
    private void createExplosionEffect(World world, Location center, List<Location> blocks) {
        // Raise explosion origin so debris is visible
        Location spawnOrigin = center.clone().add(0, 2, 0);
        Random rand = new Random();
        for (Location blockLoc : blocks) {
            // increased debris amount: fewer skips
            if (rand.nextDouble() > 0.7) continue;
            // debris reflects meteor composition
            Material mat = rand.nextBoolean() ? Material.NETHERRACK : Material.MAGMA_BLOCK;
            FallingBlock fb = world.spawnFallingBlock(spawnOrigin, mat.createBlockData());
            fb.setDropItem(false);
            fb.setVelocity(new Vector(
                    (rand.nextDouble()-0.5)*1.5,
                    rand.nextDouble()*1.0+0.5,
                    (rand.nextDouble()-0.5)*1.5
            ));
            new BukkitRunnable() { @Override public void run(){ if(fb.isValid()) fb.remove(); } }
                    .runTaskLater(Spellbreak.getInstance(), 100);
        }
        world.spawnParticle(Particle.EXPLOSION, spawnOrigin,1,0,0,0,0);
        world.spawnParticle(Particle.FLAME, spawnOrigin,50,2,2,2,0.1);
        world.spawnParticle(Particle.SMOKE, spawnOrigin,30,1.5,1.5,1.5,0.05);
        world.playSound(spawnOrigin,Sound.ENTITY_GENERIC_EXPLODE,2f,0.8f);
    }

    @Override public boolean isSuccessful(){return true;}
    @Override public void loadConfig(){FileConfiguration cfg=Spellbreak.getInstance().getConfig();String b="abilities.meteorlash.";
        cooldown=cfg.getInt(b+"cooldown",cooldown);manaCost=cfg.getInt(b+"mana-cost",manaCost);damage=cfg.getDouble(b+"damage",damage);
        knockbackStrength=cfg.getDouble(b+"knockback-strength",knockbackStrength);meteorDuration=cfg.getInt(b+"meteor-duration",meteorDuration);
        meteorSpeed=cfg.getDouble(b+"meteor-speed",meteorSpeed);selectionTimeout=cfg.getInt(b+"selection-timeout",selectionTimeout);
        selectionDuration=cfg.getInt(b+"selection-duration",selectionDuration);selectionRange=cfg.getDouble(b+"selection-range",selectionRange);
        impactRadius=cfg.getDouble(b+"impact-radius",impactRadius);particleCount=cfg.getInt(b+"particle-count",particleCount);
        requiredClass=cfg.getString(b+"required-class",requiredClass);
    }

    public int getAdjustedCooldown(Player p){SpellLevel lvl=Spellbreak.getInstance().getLevelManager()
            .getSpellLevel(p.getUniqueId(),Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()),getName());
        return (int)(cooldown*lvl.getCooldownReduction());}
    public int getAdjustedManaCost(Player p){SpellLevel lvl=Spellbreak.getInstance().getLevelManager()
            .getSpellLevel(p.getUniqueId(),Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()),getName());
        return (int)(manaCost*lvl.getManaCostReduction());}
    @Override public String getDeathMessage(String v,String c,String s){return String.format("§6%s §ewas struck down by §6%s§e's MeteorLash!",v,c);}
}

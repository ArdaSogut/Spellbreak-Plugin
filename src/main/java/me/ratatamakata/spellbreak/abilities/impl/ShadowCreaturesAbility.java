package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import me.ratatamakata.spellbreak.util.AbilityDamage;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShadowCreaturesAbility implements Ability {
    private String name = "ShadowCreatures";
    private String description = "Summon two shadowy orbs that attack enemies in a dash, giving them a chance to dodge";
    private int cooldown = 21;
    private int manaCost = 60;
    private String requiredClass = "mindshaper";
    private int chargeTime = 10;
    private int duration = 20 * 12;
    private double attackRange = 8.0;
    private int damage = 1;
    private double orbDistance = 2.0;
    private int attackCooldown = 20;
    private double attackSpeed = 0.15; // Slower dash speed
    private double damageRadius = 1.5; // New: configurable radius for applying damage
    private int internalOrbCooldown = 40;

    public static final Map<UUID, Integer> chargingPlayers = new HashMap<>();
    private static final Map<UUID, List<BukkitRunnable>> activeShadows = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Entity, Integer>> entityAttackCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> lastAttackingOrbId = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> globalOrbCooldown = new ConcurrentHashMap<>();

    private final AbilityDamage abilityDamage;

    public ShadowCreaturesAbility(Spellbreak plugin) {
        this.abilityDamage = plugin.getAbilityDamage();
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    public int getChargeTime() { return chargeTime; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) { return false; }

    private static final boolean[][] MOUTH_SHAPE = {
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, true,  true,  true,  true,  true,  false, false, false, false, false},
            {false, false, false, true,  true,  true,  false, false, false, true,  true,  true,  false, false, false},
            {false, false, true,  true,  false, false, false, false, false, false, false, true,  true,  false, false},
            {false, true,  true,  false, false, false, false, false, false, false, false, false, true,  true,  false},
            {false, true,  false, false, false, false, false, false, false, false, false, false, false, true,  false},
            {false, true,  false, false, false, true,  false, false, false, true,  false, false, false, true,  false},
            {false, true,  false, false, false, false, false, false, false, false, false, false, false, true,  false},
            {false, true,  true,  false, false, false, false, false, false, false, false, false, true,  true,  false},
            {false, false, true,  true,  false, false, false, false, false, false, false, true,  true,  false, false},
            {false, false, false, true,  true,  true,  false, false, false, true,  true,  true,  false, false, false},
            {false, false, false, false, false, true,  true,  true,  true,  true,  false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
    };

    private static final boolean[][] TEETH_SHAPE = {
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, true,  false, true,  false, false, false, false, false, false},
            {false, false, false, false, true,  false, false, false, false, false, true,  false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, true,  false, false, false, false, false, true,  false, false, false, false},
            {false, false, false, false, false, false, true,  false, true,  false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
            {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
    };

    @Override
    public void activate(Player player) {
        List<BukkitRunnable> shadows = new ArrayList<>();
        entityAttackCooldowns.put(player.getUniqueId(), new HashMap<>());
        lastAttackingOrbId.put(player.getUniqueId(), -1);
        globalOrbCooldown.put(player.getUniqueId(), 0);

        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(player.getUniqueId(), getName(), "ShadowCreatures");
        double adjustedOrbDistance = orbDistance ;
        int adjustedDamage = damage;
        int adjustedCooldown = cooldown;
        int adjustedDuration = duration;

        for (int i = 0; i < 2; i++) {
            final int orbId = i;
            BukkitRunnable orbRunnable = new BukkitRunnable() {
                private int ticks = 0;
                private final double baseAngle = orbId * Math.PI;
                private boolean isAttacking = false;
                private boolean hasScheduledDamage = false;
                private LivingEntity currentTarget;
                private Location orbLocation;
                private double attackProgress = 0;
                private Location startAttackLocation;
                private Vector attackTargetVec;

                @Override
                public void run() {
                    if (ticks++ >= adjustedDuration || !player.isOnline()) {
                        cancel();
                        return;
                    }

                    if (isAttacking && startAttackLocation != null) {
                        handleAttackPhase(player);
                    } else {
                        handleOrbitPhase(player);
                    }
                }

                private void handleOrbitPhase(Player player) {
                    double angle = baseAngle + (ticks * 0.05);
                    double x = Math.cos(angle) * adjustedOrbDistance;
                    double z = Math.sin(angle) * adjustedOrbDistance;
                    orbLocation = player.getLocation().add(x, 1.2, z);
                    drawIdleCreature(orbLocation);

                    processCooldowns(player);
                    attemptAttack(player);
                }

                private void handleAttackPhase(Player player) {
                    if (!hasScheduledDamage && startAttackLocation != null) {
                        attackProgress += attackSpeed;
                        if (attackProgress >= 1.0) {
                            scheduleDamageAndReturn(player);
                            hasScheduledDamage = true;
                        } else {
                            moveOrbTowardsFixedTarget();
                            drawAttackingCreature(
                                    orbLocation,
                                    attackTargetVec.clone().subtract(startAttackLocation.toVector()).normalize(),
                                    attackProgress
                            );
                        }
                    }
                }

                private void moveOrbTowardsFixedTarget() {
                    Vector dir = attackTargetVec.clone().subtract(startAttackLocation.toVector());
                    Vector normalizedDir = dir.clone().normalize();
                    double distance = dir.length() * attackProgress;
                    orbLocation = startAttackLocation.clone().add(normalizedDir.multiply(distance));
                }

                private void scheduleDamageAndReturn(Player player) {
                    Bukkit.getScheduler().runTaskLater(
                            Spellbreak.getInstance(), () -> {
                                World world = orbLocation.getWorld();
                                world.spawnParticle(Particle.DUST, orbLocation, 12,
                                        0.3, 0.3, 0.3, 0.01,
                                        new Particle.DustOptions(Color.BLACK, 1.8f));

                                Vector currentLocVec = currentTarget.getLocation().add(0, 1, 0).toVector();
                                // Use damageRadius for the squared distance check
                                if (currentLocVec.distanceSquared(attackTargetVec) <= damageRadius * damageRadius) {
                                    applyOrbEffects(player, currentTarget);
                                }

                                resetAttack();
                            }, 0L
                    );
                }

                private void applyOrbEffects(Player caster, LivingEntity target) {
                    World world = target.getWorld();
                    globalOrbCooldown.put(caster.getUniqueId(), internalOrbCooldown);

                    world.playSound(target.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.7f);
                    world.playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.4f, 1.5f);

                    if (orbId == 0) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
                        world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 15,
                                0.3, 0.3, 0.3, 0.05,
                                new Particle.DustOptions(Color.fromRGB(0, 0, 150), 1.2f)
                        );
                    } else {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));
                        world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 15,
                                0.3, 0.3, 0.3, 0.05,
                                new Particle.DustOptions(Color.fromRGB(100, 0, 150), 1.2f)
                        );
                    }

                    world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 15,
                            0.3, 0.3, 0.3, 0.05,
                            new Particle.DustOptions(Color.BLACK, 1.2f)
                    );

                    drawMouthEffect(world, target);

                    Bukkit.getScheduler().runTaskLater(
                            Spellbreak.getInstance(),
                            () -> playSoulDrain(world, target, caster),
                            5L
                    );

                    world.spawnParticle(Particle.DAMAGE_INDICATOR, target.getEyeLocation(), 5,
                            0.3, 0.3, 0.3, 0.1);

                    String subAbilityName = "ShadowOrb" + (orbId + 1);
                    abilityDamage.damage(target, adjustedDamage, caster, ShadowCreaturesAbility.this, subAbilityName);
                }

                private void processCooldowns(Player player) {
                    Map<Entity, Integer> cds = entityAttackCooldowns.get(player.getUniqueId());
                    Iterator<Map.Entry<Entity, Integer>> it = cds.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Entity, Integer> entry = it.next();
                        int remaining = entry.getValue() - 1;
                        if (remaining <= 0) {
                            it.remove();
                        } else {
                            entry.setValue(remaining);
                        }
                    }

                    int globalCd = globalOrbCooldown.get(player.getUniqueId());
                    if (globalCd > 0) {
                        globalOrbCooldown.put(player.getUniqueId(), globalCd - 1);
                    }
                }

                private void attemptAttack(Player player) {
                    int lastOrb = lastAttackingOrbId.get(player.getUniqueId());
                    if (lastOrb == orbId || globalOrbCooldown.get(player.getUniqueId()) > 0) {
                        return;
                    }
                    LivingEntity target = findNearestTarget(player);
                    if (target != null) {
                        startAttack(target, entityAttackCooldowns.get(player.getUniqueId()));
                        lastAttackingOrbId.put(player.getUniqueId(), orbId);
                    }
                }

                private LivingEntity findNearestTarget(Player player) {
                    LivingEntity nearest = null;
                    double minDist = Double.MAX_VALUE;
                    Map<Entity, Integer> cds = entityAttackCooldowns.get(player.getUniqueId());
                    for (Entity e : player.getNearbyEntities(attackRange, attackRange, attackRange)) {
                        if (e instanceof LivingEntity le && !le.equals(player) && !cds.containsKey(e)) {
                            double dist = le.getLocation().distanceSquared(player.getLocation());
                            if (dist < minDist) {
                                minDist = dist;
                                nearest = le;
                            }
                        }
                    }
                    return nearest;
                }

                private void startAttack(LivingEntity target, Map<Entity, Integer> cooldowns) {
                    isAttacking = true;
                    currentTarget = target;
                    startAttackLocation = orbLocation.clone();
                    attackTargetVec = target.getLocation().add(0, 1, 0).toVector();
                    attackProgress = 0;
                    hasScheduledDamage = false;
                    cooldowns.put(target, attackCooldown);
                }

                private void resetAttack() {
                    isAttacking = false;
                    currentTarget = null;
                    startAttackLocation = null;
                    attackTargetVec = null;
                    attackProgress = 0;
                    hasScheduledDamage = false;
                }

                private void drawIdleCreature(Location loc) {
                    World world = loc.getWorld();
                    world.spawnParticle(Particle.DUST, loc, 8, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.BLACK, 1.5f));
                    world.spawnParticle(Particle.DUST, loc, 3, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1.0f));

                    Vector right = new Vector(0.1, 0, 0).rotateAroundY(ticks * 0.05);
                    Vector left = right.clone().multiply(-1);
                    world.spawnParticle(Particle.DUST, loc.clone().add(right), 1, 0.02, 0.02, 0.02, 0,
                            new Particle.DustOptions(Color.PURPLE, 0.8f));
                    world.spawnParticle(Particle.DUST, loc.clone().add(left), 1, 0.02, 0.02, 0.02, 0,
                            new Particle.DustOptions(Color.PURPLE, 0.8f));

                    if (ticks % 3 == 0) {
                        world.spawnParticle(Particle.DUST, loc.clone().subtract(0, 0.2, 0), 3,
                                0.1, 0.1, 0.1, 0.01,
                                new Particle.DustOptions(Color.BLACK, 0.7f)
                        );
                    }
                }

                private void drawAttackingCreature(Location loc, Vector direction, double progress) {
                    World world = loc.getWorld();
                    float size = (float) (1.0 + progress * 0.5);
                    float intensity = (float) Math.min(1.0, progress * 1.5);

                    world.spawnParticle(Particle.DUST, loc, 15,
                            0.25 * size, 0.25 * size, 0.25 * size, 0,
                            new Particle.DustOptions(Color.BLACK, 2.0f)
                    );

                    for (int i = 1; i <= 5; i++) {
                        Vector trail = direction.clone().multiply(-0.3 * i * size);
                        Location trailLoc = loc.clone().add(trail);
                        float factor = Math.max(0.1f, (float) (0.25 - i * 0.04));
                        world.spawnParticle(Particle.DUST, trailLoc, 8,
                                factor * size, factor * size, factor * size, 0,
                                new Particle.DustOptions(Color.BLACK, 1.8f - i * 0.2f)
                        );
                        if (i % 2 == 0) {
                            Vector perp = direction.clone().crossProduct(new Vector(0, 1, 0))
                                    .normalize().multiply(0.4);
                            world.spawnParticle(Particle.DUST, trailLoc.clone().add(perp), 4,
                                    0.15, 0.15, 0.15, 0.01,
                                    new Particle.DustOptions(Color.BLACK, 1.0f)
                            );
                            world.spawnParticle(Particle.DUST, trailLoc.clone().add(perp.multiply(-1)), 4,
                                    0.15, 0.15, 0.15, 0.01,
                                    new Particle.DustOptions(Color.BLACK, 1.0f)
                            );
                        }
                    }

                    Vector perp = direction.clone().crossProduct(new Vector(0, 1, 0))
                            .normalize().multiply(0.2 * size);
                    int red = Math.min(255, (int) (200 * intensity));
                    int purple = Math.min(150, (int) (100 * intensity));
                    world.spawnParticle(Particle.DUST, loc.clone().add(perp), 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(red, 0, purple), 1.2f + intensity * 0.8f)
                    );
                    world.spawnParticle(Particle.DUST, loc.clone().add(perp.multiply(-1)), 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(red, 0, purple), 1.2f + intensity * 0.8f)
                    );

                    if (ticks % 5 == 0 && progress > 0.5) {
                        world.playSound(loc, Sound.ENTITY_VEX_AMBIENT, 0.3f, (float) (0.5f + progress * 0.5f));
                    }
                    if (ticks % 2 == 0) {
                        Location smokeLoc = loc.clone().add(direction.clone().multiply(-0.8));
                        world.spawnParticle(Particle.SMOKE, smokeLoc, 3, 0.1, 0.1, 0.1, 0.01);
                    }
                }

                private void drawMouthEffect(World world, LivingEntity target) {
                    Location center = target.getLocation().add(0, 1.0, 0);
                    double cellSize = 0.18;


                    double startX = -(MOUTH_SHAPE[0].length * cellSize) / 2;
                    double startY = -(MOUTH_SHAPE.length * cellSize) / 2;

                    for (int y = 0; y < MOUTH_SHAPE.length; y++) {
                        for (int x = 0; x < MOUTH_SHAPE[y].length; x++) {
                            if (MOUTH_SHAPE[y][x]) {
                                double posX = startX + (x * cellSize);
                                double posY = startY + (y * cellSize);
                                Location particleLoc = center.clone().add(posX, posY, 0);
                                world.spawnParticle(Particle.DUST, particleLoc, 2, 0.02, 0.02, 0.02, 0,
                                        new Particle.DustOptions(Color.BLACK, 1.5f));
                            }
                        }
                    }

                    for (int y = 0; y < TEETH_SHAPE.length; y++) {
                        for (int x = 0; x < TEETH_SHAPE[y].length; x++) {
                            if (TEETH_SHAPE[y][x]) {
                                double posX = startX + (x * cellSize);
                                double posY = startY + (y * cellSize);
                                Location particleLoc = center.clone().add(posX, posY, 0);
                                world.spawnParticle(Particle.DUST, particleLoc, 2, 0.01, 0.01, 0.01, 0,
                                        new Particle.DustOptions(Color.WHITE, 1.3f));
                            }
                        }
                    }

                    for (int i = 0; i < 15; i++) {
                        double mouthWidth = MOUTH_SHAPE[0].length * cellSize * 0.5;
                        double mouthHeight = MOUTH_SHAPE.length * cellSize * 0.5;
                        double x = (Math.random() - 0.5) * mouthWidth;
                        double y = (Math.random() - 0.5) * mouthHeight;
                        Location innerLoc = center.clone().add(x, y, 0);
                        world.spawnParticle(Particle.DUST, innerLoc, 1, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(Color.RED, 1.2f));
                    }

                    for (int i = 0; i < 8; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double distance = Math.random() * 0.4;
                        double x = Math.cos(angle) * distance;
                        double y = Math.sin(angle) * distance;
                        Location glowLoc = center.clone().add(x, y, 0);
                        world.spawnParticle(Particle.DUST, glowLoc, 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(160, 0, 160), 1.4f));
                    }
                }

                private void playSoulDrain(World world, LivingEntity target, Player caster) {
                    for (int i = 0; i < 3; i++) {
                        Bukkit.getScheduler().runTaskLater(
                                Spellbreak.getInstance(), () -> {
                                    for (int j = 0; j < 6; j++) {
                                        Location soulLoc = target.getLocation().add(
                                                (Math.random() - 0.5) * 0.6,
                                                0.5 + Math.random() * 1.5,
                                                (Math.random() - 0.5) * 0.6
                                        );
                                        world.spawnParticle(Particle.DUST, soulLoc, 3, 0.1, 0.1, 0.1, 0.05,
                                                new Particle.DustOptions(Color.fromRGB(170, 0, 170), 1.0f)
                                        );
                                    }
                                    world.playSound(target.getLocation(), Sound.ENTITY_VEX_DEATH, 0.3f, 0.5f);
                                }, i * 8L
                        );
                    }
                }
            };
            shadows.add(orbRunnable);
            orbRunnable.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        }

        activeShadows.put(player.getUniqueId(), shadows);
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.shadowcreatures.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        chargeTime = cfg.getInt(base + "charge-time", chargeTime);
        duration = cfg.getInt(base + "duration", 15) * 5;
        attackRange = cfg.getDouble(base + "attack-range", attackRange);
        damage = cfg.getInt(base + "damage", damage);
        orbDistance = cfg.getDouble(base + "orb-distance", orbDistance);
        attackCooldown = cfg.getInt(base + "attack-cooldown", attackCooldown);
        attackSpeed = cfg.getDouble(base + "attack-speed", attackSpeed);
        damageRadius = cfg.getDouble(base + "damage-radius", damageRadius);
    }

    @Override
    public String getDeathMessage(String victim, String caster, String spell) {
        return ChatColor.DARK_PURPLE + victim + " was consumed by " + caster + "'s shadow creatures!";
    }

    public static void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        List<BukkitRunnable> tasks = activeShadows.remove(uuid);
        if (tasks != null) {
            tasks.forEach(BukkitRunnable::cancel);
        }
        entityAttackCooldowns.remove(uuid);
    }
}

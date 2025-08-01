package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class QuillflareSurgeAbility implements Ability {

    // Configuration
    private String name = "QuillflareSurge";
    private String description = "Leap upward and release tracking quills that damage enemies.";
    private int manaCost = 35;
    private String requiredClass = "archdruid";
    private int cooldownSeconds = 10;
    private double leapPower = 0.8;
    private int quillCount = 8;
    private double quillDamage = 1.0;
    private double quillSpeed = 0.6;
    private double quillHitRadius = 0.6;
    private double quillRange = 15.0;
    private double gravityFactor = 0.02;
    private double quillHomingStrength = 1.1;
    private double homingAngleRadians = Math.PI / 3;
    private double homingAcquisitionRange = 12.0;
    private double shockwaveRadius = 3.0;
    private double shockwaveDamage = 2.0;
    private int poisonDuration = 40;
    private Color quillColor = Color.fromRGB(255, 230, 200);
    private float quillSize = 0.8f;
    private int slowFallDurationTicks = 40; // Shortened from 70 to 40 ticks

    // Runtime
    private final Set<UUID> activeLeapers = new HashSet<>();

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldownSeconds; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String path = "abilities.quillflaresurge.";
        manaCost = cfg.getInt(path + "mana-cost", manaCost);
        cooldownSeconds = cfg.getInt(path + "cooldown-seconds", cooldownSeconds);
        leapPower = cfg.getDouble(path + "leap-power", leapPower);
        quillCount = cfg.getInt(path + "quill-count", quillCount);
        quillDamage = cfg.getDouble(path + "quill-damage", quillDamage);
        quillSpeed = cfg.getDouble(path + "quill-speed", quillSpeed);
        quillHitRadius = cfg.getDouble(path + "quill-hit-radius", quillHitRadius);
        quillRange = cfg.getDouble(path + "quill-range", quillRange);
        gravityFactor = cfg.getDouble(path + "gravity-factor", gravityFactor);
        quillHomingStrength = cfg.getDouble(path + "homing-strength", quillHomingStrength);
        homingAngleRadians = cfg.getDouble(path + "homing-angle-radians", homingAngleRadians);
        homingAcquisitionRange = cfg.getDouble(path + "homing-acquisition-range", homingAcquisitionRange);
        shockwaveRadius = cfg.getDouble(path + "shockwave-radius", shockwaveRadius);
        shockwaveDamage = cfg.getDouble(path + "shockwave-damage", shockwaveDamage);
        poisonDuration = cfg.getInt(path + "poison-duration", poisonDuration);
        slowFallDurationTicks = cfg.getInt(path + "slow-fall-duration-ticks", slowFallDurationTicks);

        quillColor = Color.fromRGB(
                cfg.getInt(path + "quill-color.r", 255),
                cfg.getInt(path + "quill-color.g", 230),
                cfg.getInt(path + "quill-color.b", 200)
        );
        quillSize = (float) cfg.getDouble(path + "quill-size", quillSize);

        description = String.format(
                "Leap and unleash %d %s quills (%.1f dmg, %.1f range).%s Landing shockwave: %.1f dmg. %ds CD.",
                quillCount,
                (quillHomingStrength > 0 ? "homing" : ""),
                quillDamage,
                quillRange,
                (slowFallDurationTicks > 0 ? " Grants Slow Fall." : ""),
                shockwaveDamage,
                cooldownSeconds
        );
    }

    @Override public boolean isSuccessful() { return true; }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return "";
    }

    @Override
    public void activate(Player player) {
        if (activeLeapers.contains(player.getUniqueId())) return;
        if (slowFallDurationTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, slowFallDurationTicks, 0, false, false, true));
        }

        Vector velocity = player.getVelocity();
        velocity.setY(leapPower);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.5f);

        activeLeapers.add(player.getUniqueId());

        new BukkitRunnable() {
            boolean quillsReleased = false;
            int ticksInAir = 0;

            @Override
            public void run() {
                ticksInAir++;
                if (!player.isOnline() || player.isDead()) {
                    activeLeapers.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (!quillsReleased && (player.getVelocity().getY() < 0.05 || ticksInAir > 12)) {
                    releaseQuills(player);
                    quillsReleased = true;
                }
                if (player.isOnGround() && ticksInAir > 3) {
                    if (quillsReleased) performShockwave(player);
                    activeLeapers.remove(player.getUniqueId());
                    cancel();
                } else if (ticksInAir > 70) {
                    activeLeapers.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void performShockwave(Player caster) {
        Location center = caster.getLocation();
        World world = caster.getWorld();
        world.getNearbyEntities(center, shockwaveRadius, shockwaveRadius, shockwaveRadius)
                .stream()
                .filter(e -> e instanceof LivingEntity && !e.getUniqueId().equals(caster.getUniqueId())
                        && e.getLocation().distanceSquared(center) <= shockwaveRadius * shockwaveRadius)
                .forEach(e -> {
                    LivingEntity t = (LivingEntity)e;
                    Spellbreak.getInstance().getAbilityDamage().damage(t, shockwaveDamage, caster, this, null);
                    Vector dir = t.getLocation().toVector().subtract(center.toVector()).normalize();
                    if (dir.lengthSquared() < 0.001) dir = new Vector(Math.random()-0.5,0.2,Math.random()-0.5).normalize();
                    else dir.setY(Math.max(0.25, dir.getY()*0.4+0.25));
                    t.setVelocity(dir.multiply(0.85));
                });
        world.playSound(center, Sound.BLOCK_GRASS_BREAK, 1.2f, 0.8f);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        Block below = center.clone().subtract(0,1,0).getBlock();
        Particle.DustOptions dust = new Particle.DustOptions(quillColor, quillSize);
        world.spawnParticle(Particle.DUST, center.clone().add(0,0.2,0), 40,
                shockwaveRadius*0.6, 0.1, shockwaveRadius*0.6, 0.02, dust);
        world.spawnParticle(Particle.DUST, center.clone().add(0,0.5,0), 25,
                shockwaveRadius*0.5, 0.3, shockwaveRadius*0.5, 0.05, dust);
    }

    private void releaseQuills(Player caster) {
        Location origin = caster.getEyeLocation().add(0,-0.2,0);
        World world = caster.getWorld();
        Particle.DustOptions dustOpts = new Particle.DustOptions(quillColor, quillSize);
        world.playSound(origin, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.2f);
        for (int i=0; i<quillCount; i++) {
            // Calculate a base yaw for even distribution, then add a small random offset
            double baseYaw = (2 * Math.PI / (double)this.quillCount) * i;
            double yawRandomOffset = (Math.random() - 0.5) * (Math.PI / (this.quillCount * 3.0)); // Further reduced random variation for tighter horizontal spread
            double yaw = baseYaw + yawRandomOffset;

            // Narrowed pitch range for a more focused vertical spread
            double pitch = (Math.random()*Math.PI/4.0)-(Math.PI/8.0);
            Vector dir = new Vector(Math.cos(pitch)*Math.cos(yaw), Math.sin(pitch),
                    Math.cos(pitch)*Math.sin(yaw)).normalize()
                    .multiply(quillSpeed*(0.85+Math.random()*0.3));
            Consumer<LivingEntity> hit = target -> {
                Spellbreak.getInstance().getAbilityDamage().damage(target, quillDamage, caster, this, null);
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, 0));
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0,target.getHeight()/2,0),
                        10, 0.3,0.3,0.3, 0.03, dustOpts);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.7f, 1.2f);
            };
            BiConsumer<Block, Location> blockHit = (b,l) -> {
                l.getWorld().spawnParticle(Particle.DUST, l, 15, 0.15,0.15,0.15, 0.02, dustOpts);
                l.getWorld().playSound(l, Sound.BLOCK_WOOD_HIT, 0.8f, 1.0f);
            };
            Consumer<Location> expire = loc -> loc.getWorld().spawnParticle(
                    Particle.DUST, loc, 5,0.1,0.1,0.1,0.02, dustOpts);
            new QuillProjectileRunnable(caster, origin.clone(), dir, quillRange, quillHitRadius,
                    dustOpts, 
                    e -> e instanceof LivingEntity && !e.getUniqueId().equals(caster.getUniqueId()) && !e.isDead(),
                    e -> true,
                    quillHomingStrength, homingAngleRadians,
                    homingAcquisitionRange, gravityFactor,
                    hit, blockHit, expire)
                    .runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
        }
    }

    private class QuillProjectileRunnable extends BukkitRunnable {
        private final Player caster;
        private final UUID casterId;
        private Location currentLoc;
        private Vector currentVelocity;
        private final double maxRangeSq;
        private final double hitRadius;
        private final Particle.DustOptions trailDust;
        private final Predicate<Entity> collisionFilter;
        private LivingEntity currentTarget;
        private final double homingStrength;
        private final double homingAngle;
        private final double homingRangeSq;
        private final double gravity;
        private final Consumer<LivingEntity> onHit;
        private final BiConsumer<Block, Location> onBlock;
        private final Consumer<Location> onExpire;
        private final World world;
        private double traveledSq = 0;

        public QuillProjectileRunnable(Player caster, Location origin, Vector velocity,
                                       double range, double hitRadius,
                                       Particle.DustOptions dust,
                                       Predicate<Entity> filter,
                                       Predicate<Entity> homingPred,
                                       double homingStr, double homingAng,
                                       double homingRange, double gravity,
                                       Consumer<LivingEntity> onHit,
                                       BiConsumer<Block, Location> onBlock,
                                       Consumer<Location> onExpire) {
            this.caster = caster;
            this.casterId = caster.getUniqueId();
            this.currentLoc = origin;
            this.currentVelocity = velocity;
            this.maxRangeSq = range*range;
            this.hitRadius = hitRadius;
            this.trailDust = dust;
            this.collisionFilter = filter;
            this.homingStrength = homingStr;
            this.homingAngle = homingAng;
            this.homingRangeSq = homingRange*homingRange;
            this.gravity = gravity;
            this.onHit = onHit;
            this.onBlock = onBlock;
            this.onExpire = onExpire;
            this.world = origin.getWorld();
        }

        @Override
        public void run() {
            if (!caster.isOnline() || world==null || traveledSq>=maxRangeSq) {
                if (traveledSq>=maxRangeSq && onExpire!=null) onExpire.accept(currentLoc);
                cancel(); return;
            }
            // homing logic
            if (homingStrength>0) {
                if (currentTarget==null || !currentTarget.isValid()||currentTarget.isDead()||
                        currentTarget.getWorld()!=world||
                        currentTarget.getLocation().distanceSquared(currentLoc)>homingRangeSq) {
                    currentTarget = world.getNearbyEntities(currentLoc, Math.sqrt(homingRangeSq), Math.sqrt(homingRangeSq), Math.sqrt(homingRangeSq), e->e instanceof LivingEntity && !e.getUniqueId().equals(casterId))
                            .stream().map(e->(LivingEntity)e)
                            .min(Comparator.comparingDouble(e->e.getLocation().add(0, e.getHeight()/2, 0).distanceSquared(currentLoc)))
                            .orElse(null);
                }
                if (currentTarget!=null) {
                    // Reverted to target center for homing vector
                    Vector targetCenter = currentTarget.getLocation().add(0, currentTarget.getHeight() / 2, 0).toVector();

                    // --- Target Prediction Logic ---
                    Vector targetVelocity = currentTarget.getVelocity();
                    double distanceToTarget = currentLoc.toVector().distance(targetCenter);
                    double quillSpeed = currentVelocity.length();
                    Vector predictedTargetPosition = targetCenter.clone(); // Default to current if speed is zero or target stationary

                    if (quillSpeed > 0.01 && targetVelocity.lengthSquared() > 0.001) { // Only predict if quill and target are moving
                        double timeToIntercept = distanceToTarget / quillSpeed;
                        // Clamp timeToIntercept to avoid overshooting excessively for very fast targets or slow quills
                        // A max prediction time of ~0.5 to 1.0 seconds often works well.
                        timeToIntercept = Math.min(timeToIntercept, 0.75); // Max 0.75 seconds of prediction
                        predictedTargetPosition.add(targetVelocity.clone().multiply(timeToIntercept));
                    }
                    // --- End Target Prediction ---

                    Vector toTarget = predictedTargetPosition.subtract(currentLoc.toVector());

                    if (toTarget.lengthSquared()>0.001) {
                        Vector dirNorm = currentVelocity.clone().normalize();
                        if (dirNorm.angle(toTarget)<=homingAngle) {
                            currentVelocity = dirNorm.multiply(currentVelocity.length())
                                    .add(toTarget.normalize().multiply(homingStrength*currentVelocity.length()))
                                    .normalize().multiply(currentVelocity.length());
                        }
                    }
                }
            }
            // gravity
            currentVelocity.setY(currentVelocity.getY()-gravity);
            // move
            Vector step = currentVelocity.clone();
            Location next = currentLoc.clone().add(step);
            // particles
            world.spawnParticle(Particle.DUST, currentLoc, 3, 0.05,0.05,0.05,0.01, trailDust);
            // block collision
            RayTraceResult bHit = world.rayTraceBlocks(currentLoc, step.clone().normalize(), step.length(), FluidCollisionMode.NEVER, true);
            if (bHit!=null && bHit.getHitBlock()!=null) { onBlock.accept(bHit.getHitBlock(), bHit.getHitPosition().toLocation(world)); cancel(); return; }
            // entity collision
            for (Entity e: world.getNearbyEntities(currentLoc, hitRadius, hitRadius, hitRadius, collisionFilter)) {
                if (e instanceof LivingEntity) {
                    BoundingBox box = e.getBoundingBox().expand(hitRadius*0.5);
                    if (box.overlaps(BoundingBox.of(currentLoc, next).expand(hitRadius*0.25))) {
                        onHit.accept((LivingEntity)e);
                    }
                }
            }
            currentLoc = next;
            traveledSq += step.lengthSquared();
        }
    }
}
// TenorAI.java
package me.ratatamakata.spellbreak.ai;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import me.ratatamakata.spellbreak.Spellbreak;

import java.util.Comparator;

public class TenorAI implements SkeletonAI {
    private long lastShotTime = 0;
    private final double attackRange = 20.0;
    private final double followSpeed = 0.3;
    private final int shotCooldown = 3 * 20;

    @Override
    public void update(Skeleton skeleton, Player caster) {
        LivingEntity target = findNearestEnemy(skeleton, caster, attackRange);

        if (target != null && hasLineOfSight(skeleton, target)) {
            if (System.currentTimeMillis() - lastShotTime >= shotCooldown * 50L) {
                shootProjectile(skeleton, target);
                lastShotTime = System.currentTimeMillis();
            }
        } else {
            moveToFormation(skeleton, caster, followSpeed);
        }
    }

    private LivingEntity findNearestEnemy(Skeleton skeleton, Player caster, double range) {
        return skeleton.getNearbyEntities(range, range, range).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> !e.equals(caster))
                .filter(e -> !isChoirMember(e, caster))
                .map(e -> (LivingEntity) e)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(skeleton.getLocation())))
                .orElse(null);
    }

    private boolean isChoirMember(Entity entity, Player caster) {
        if (!(entity instanceof Skeleton)) return false;
        return ((Skeleton) entity).hasMetadata("BoneChoirCaster") &&
                ((Skeleton) entity).getMetadata("BoneChoirCaster").get(0).asString().equals(caster.getUniqueId().toString());
    }

    private void shootProjectile(Skeleton skeleton, LivingEntity target) {
        Vector direction = target.getEyeLocation().subtract(skeleton.getEyeLocation()).toVector().normalize();
        Snowball projectile = skeleton.launchProjectile(Snowball.class);
        projectile.setVelocity(direction.multiply(1.5));
        projectile.setMetadata("TenorDebuff", new FixedMetadataValue(Spellbreak.getInstance(), true));
        skeleton.getWorld().playSound(skeleton.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.2f);
    }

    private boolean hasLineOfSight(Skeleton skeleton, LivingEntity target) {
        RayTraceResult result = skeleton.getWorld().rayTrace(
                skeleton.getEyeLocation(),
                target.getEyeLocation().subtract(skeleton.getEyeLocation()).toVector(),
                attackRange,
                FluidCollisionMode.NEVER, true, 0.1, e -> e.equals(target)
        );
        return result != null && result.getHitEntity() != null;
    }

    private void moveToFormation(Skeleton skeleton, Player caster, double speed) {
        Vector offset = (Vector) skeleton.getMetadata("FormationOffset").get(0).value();
        Location targetLoc = caster.getLocation().add(offset);
        Vector direction = targetLoc.toVector().subtract(skeleton.getLocation().toVector());

        if (direction.lengthSquared() > 1.0) {
            direction.normalize().multiply(speed);
            skeleton.setVelocity(direction);
        } else {
            skeleton.setVelocity(new Vector());
        }
    }
}
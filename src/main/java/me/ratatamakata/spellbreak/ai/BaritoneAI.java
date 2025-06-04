// BaritoneAI.java
package me.ratatamakata.spellbreak.ai;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

import java.util.Comparator;

public class BaritoneAI implements SkeletonAI {
    private final double attackRange = 3.0;
    private final double followSpeed = 0.4;
    private final double detectRange = 12.0;
    private long lastAttackTime = 0;

    @Override
    public void update(Skeleton skeleton, Player caster) {
        LivingEntity target = findNearestEnemy(skeleton, caster, detectRange);

        if (target != null) {
            Location targetLoc = target.getLocation();
            if (skeleton.getLocation().distanceSquared(targetLoc) <= attackRange * attackRange) {
                if (System.currentTimeMillis() - lastAttackTime >= 1000) {
                    performMeleeAttack(skeleton, target);
                    lastAttackTime = System.currentTimeMillis();
                }
            } else {
                chargeToward(skeleton, targetLoc, followSpeed);
            }
        } else {
            moveToFormation(skeleton, caster, followSpeed * 0.7);
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

    private void chargeToward(Skeleton skeleton, Location target, double speed) {
        Vector direction = target.toVector().subtract(skeleton.getLocation().toVector()).normalize();
        skeleton.setVelocity(direction.multiply(speed).setY(0.2));
    }

    private void performMeleeAttack(Skeleton skeleton, LivingEntity target) {
        target.damage(5.0, skeleton);
        target.setVelocity(target.getVelocity().add(skeleton.getLocation().getDirection().multiply(0.3)));
        skeleton.getWorld().playSound(skeleton.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
    }

    private void moveToFormation(Skeleton skeleton, Player caster, double speed) {
        Vector offset = (Vector) skeleton.getMetadata("FormationOffset").get(0).value();
        Location targetLoc = caster.getLocation().add(offset);
        Vector direction = targetLoc.toVector().subtract(skeleton.getLocation().toVector());

        if (direction.lengthSquared() > 1.0) {
            direction.normalize().multiply(speed);
            skeleton.setVelocity(direction);
        }
    }
}
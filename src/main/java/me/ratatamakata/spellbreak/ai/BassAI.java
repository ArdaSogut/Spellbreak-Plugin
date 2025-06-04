// BassAI.java
package me.ratatamakata.spellbreak.ai;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class BassAI implements SkeletonAI {
    private long lastShockwave = 0;
    private final long shockwaveCooldown = 8 * 1000;
    private final double aoeRadius = 6.0;
    private final double followSpeed = 0.25;

    @Override
    public void update(Skeleton skeleton, Player caster) {
        if (System.currentTimeMillis() - lastShockwave >= shockwaveCooldown) {
            performShockwave(skeleton, caster);
            lastShockwave = System.currentTimeMillis();
        }
        moveToFormation(skeleton, caster, followSpeed);
    }

    private void performShockwave(Skeleton skeleton, Player caster) {
        Location loc = skeleton.getLocation();

        // Effects
        loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 30, 2, 0.5, 2);
        loc.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 20, 1, 0.5, 1, Material.BONE.createBlockData());

        // Damage
        loc.getWorld().getNearbyEntities(loc, aoeRadius, 3, aoeRadius).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> !e.equals(caster))
                .filter(e -> !isChoirMember(e, caster))
                .forEach(e -> {
                    LivingEntity le = (LivingEntity) e;
                    le.damage(6.0, skeleton);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
                    Vector push = e.getLocation().toVector().subtract(loc.toVector()).normalize();
                    le.setVelocity(push.multiply(1.2).setY(0.6));
                });
    }

    private boolean isChoirMember(Entity entity, Player caster) {
        if (!(entity instanceof Skeleton)) return false;
        return ((Skeleton) entity).hasMetadata("BoneChoirCaster") &&
                ((Skeleton) entity).getMetadata("BoneChoirCaster").get(0).asString().equals(caster.getUniqueId().toString());
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
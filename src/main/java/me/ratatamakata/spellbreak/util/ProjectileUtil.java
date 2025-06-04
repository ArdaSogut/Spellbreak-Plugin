package me.ratatamakata.spellbreak.util;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class ProjectileUtil {
    public static void launchProjectile(
            Player caster,
            Location origin,
            final Vector direction,
            final double speed,
            double range,
            double rangeMultiplier,
            final double hitRadius,
            final Particle trailParticle,
            final Object trailData,
            final int trailCount,
            final Particle secondaryTrailParticle,
            final Object secondaryTrailData,
            final int secondaryTrailCount,
            final Predicate<Entity> entityFilter,
            final Consumer<LivingEntity> onEntityHit,
            final BiConsumer<Block, Location> onBlockHit,
            final Consumer<Location> onMaxRange,
            final double spiralRadius,
            final double spiralSpeed
    ) {
        final World world = origin.getWorld();
        if (world == null) return;

        final UUID casterId = caster.getUniqueId();
        final double maxDist = range * rangeMultiplier;

        new BukkitRunnable() {
            Location currentLoc = origin.clone();
            double traveled = 0.0;
            double angle = 0.0;

            @Override
            public void run() {
                Player onlineCaster = Spellbreak.getInstance().getServer().getPlayer(casterId);
                if (onlineCaster == null || !onlineCaster.isOnline()) {
                    cancel();
                    return;
                }

                if (traveled >= maxDist) {
                    onMaxRange.accept(currentLoc);
                    cancel();
                    return;
                }

                // Move
                Vector step = direction.clone().multiply(speed);
                Location nextLoc = currentLoc.clone().add(step);
                Location particleLoc = currentLoc.clone();

                // Spiral offset
                if (spiralRadius > 0) {
                    angle += spiralSpeed;
                    Vector perp = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
                    if (perp.lengthSquared() < 1e-6) perp = new Vector(1,0,0);
                    Vector offset = perp.multiply(Math.cos(angle) * spiralRadius)
                            .add(perp.clone().crossProduct(direction).normalize().multiply(Math.sin(angle) * spiralRadius));
                    particleLoc.add(offset);
                }

                // Particles
                if (trailParticle != null && trailCount > 0)
                    world.spawnParticle(trailParticle, particleLoc, trailCount, 0.05, 0.05, 0.05, 0, trailData);
                if (secondaryTrailParticle != null && secondaryTrailCount > 0)
                    world.spawnParticle(secondaryTrailParticle, particleLoc, secondaryTrailCount, 0, 0, 0, 0, secondaryTrailData);

                // Block collision
                RayTraceResult blockHit = world.rayTraceBlocks(currentLoc, direction, speed, FluidCollisionMode.NEVER, true);
                if (blockHit != null && blockHit.getHitBlock() != null) {
                    onBlockHit.accept(blockHit.getHitBlock(), blockHit.getHitPosition().toLocation(world));
                    cancel();
                    return;
                }

                // Entity collision
                RayTraceResult entityHit = world.rayTraceEntities(currentLoc, direction, speed, hitRadius,
                        e -> e instanceof LivingEntity && !e.getUniqueId().equals(casterId)
                                && !e.isDead() && (entityFilter == null || entityFilter.test(e))
                );
                if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity) {
                    onEntityHit.accept((LivingEntity)entityHit.getHitEntity());
                    cancel();
                    return;
                }

                // Advance
                currentLoc = nextLoc;
                traveled += speed;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }
}
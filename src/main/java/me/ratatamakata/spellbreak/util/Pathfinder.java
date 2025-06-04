package me.ratatamakata.spellbreak.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class Pathfinder {
    public static void moveToFormation(Entity entity, Location target, double speed) {
        Vector direction = target.toVector()
                .subtract(entity.getLocation().toVector())
                .normalize();

        // Maintain Y velocity for gravity
        Vector velocity = direction.multiply(speed)
                .setY(entity.getVelocity().getY());

        entity.setVelocity(velocity);
    }

    public static boolean isStuck(Entity entity) {
        return entity.getVelocity().lengthSquared() < 0.01;
    }
}
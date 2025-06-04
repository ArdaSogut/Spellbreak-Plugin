package me.ratatamakata.spellbreak.listeners;

// Imports...

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

public class CombatListener implements Listener {

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if(e.getEntity() instanceof SmallFireball && e.getEntity().hasMetadata("Caster")) {
            Player caster = (Player) e.getEntity().getMetadata("Caster").get(0).value();
            if(e.getHitEntity() != null) {
                ((LivingEntity)e.getHitEntity()).damage(6, caster);
            }
        }
    }

    @EventHandler
    public void onKnockback(EntityDamageByEntityEvent e) {
        if(e.getEntity() instanceof Skeleton &&
                e.getEntity().hasMetadata("BoneChoirCaster")) {
            // Reduce knockback by 60%
            Vector velocity = e.getEntity().getVelocity();
            e.getEntity().setVelocity(velocity.multiply(0.4));
        }
    }
}
package me.ratatamakata.spellbreak.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class FallDamageListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Check if the entity taking damage is a player
        if (event.getEntity() instanceof Player) {
            // Check if the damage cause is falling
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                // Cancel the event, preventing fall damage
                event.setCancelled(true);
            }
        }
    }
} 
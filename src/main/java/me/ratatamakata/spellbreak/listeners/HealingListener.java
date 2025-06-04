package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.entity.LivingEntity;

public class HealingListener implements Listener {

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();

        // Check if the entity has the plague healing reduction effect
        if (entity.hasMetadata("plague_heal_reduction")) {
            // Get the reduction value from metadata (defaults to 0.5 if not specified)
            double reductionFactor = 0.5;
            try {
                reductionFactor = entity.getMetadata("plague_heal_reduction").get(0).asDouble();
            } catch (Exception e) {
                // In case of any error, use the default value
                Spellbreak.getInstance().getLogger().warning("Error getting heal reduction value: " + e.getMessage());
            }

            // Apply the reduction to the healing amount
            double reducedAmount = event.getAmount() * (1.0 - reductionFactor);
            event.setAmount(reducedAmount);

            // Debug message to confirm healing reduction is working, remove in production
            if (Spellbreak.getInstance().getConfig().getBoolean("debug", false)) {
                Spellbreak.getInstance().getLogger().info(
                        "Reduced healing from " + event.getAmount() +
                                " to " + reducedAmount +
                                " for entity " + entity.getType());
            }
        }
    }
}
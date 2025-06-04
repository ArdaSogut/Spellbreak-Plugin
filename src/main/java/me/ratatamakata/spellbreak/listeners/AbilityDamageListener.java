package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.util.AbilityDamageTracker;
import me.ratatamakata.spellbreak.util.LastDamageInfo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Intercepts all damage events to automatically apply death message tracking
 */
public class AbilityDamageListener implements Listener {
    private final Spellbreak plugin;
    private final AbilityDamageTracker damageTracker;

    public AbilityDamageListener(Spellbreak plugin, AbilityDamageTracker damageTracker) {
        this.plugin = plugin;
        this.damageTracker = damageTracker;
    }

    /**
     * This method hooks into ALL damage events in the server to automatically
     * handle the last damage tracking for death messages
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // Only care about damage to players
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Create the LastDamageInfo based on the damage type
        LastDamageInfo damageInfo = null;

        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            // This is already being handled by CustomDeathMessageListener
            // No need to process it here as it would be redundant
            return;
        } else {
            // Non-entity damage like fire, fall, etc. could be handled here
            // if you want to track environmental deaths from ability effects
        }

        // If we created damage info, store it on the victim
        if (damageInfo != null) {
            victim.setMetadata(CustomDeathMessageListener.METADATA_KEY_LAST_DAMAGE_INFO,
                    new FixedMetadataValue(plugin, damageInfo));
        }
    }

    /**
     * This method specifically intercepts when an ability damages an entity directly through the damage() method
     * It must be called manually from your code where LivingEntity.damage() is used
     */
    public void trackAbilityDamage(LivingEntity target, Player caster, Ability ability, String subAbilityName) {
        if (target instanceof Player victim) {
            LastDamageInfo damageInfo = new LastDamageInfo(
                    victim.getName(),
                    caster.getName(),
                    caster.getName(),
                    ability.getName(),
                    subAbilityName,
                    false
            );

            victim.setMetadata(CustomDeathMessageListener.METADATA_KEY_LAST_DAMAGE_INFO,
                    new FixedMetadataValue(plugin, damageInfo));
        }
    }
}
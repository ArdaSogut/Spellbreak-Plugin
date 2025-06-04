package me.ratatamakata.spellbreak.util;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.listeners.AbilityDamageListener;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Helper class for dealing ability damage with proper tracking
 */
public class AbilityDamage {
    private final Spellbreak plugin;
    private final AbilityDamageTracker damageTracker;
    private final AbilityDamageListener damageListener;

    public AbilityDamage(Spellbreak plugin, AbilityDamageTracker damageTracker, AbilityDamageListener damageListener) {
        this.plugin = plugin;
        this.damageTracker = damageTracker;
        this.damageListener = damageListener;
    }

    /**
     * Deal damage directly with an ability
     * @param target The entity to damage
     * @param damage The amount of damage to deal
     * @param caster The player who cast the ability
     * @param ability The ability being used
     * @param subAbilityName Optional sub-ability name
     */
    public void damage(LivingEntity target, double damage, Player caster, Ability ability, String subAbilityName) {
        // Tag the caster with the ability info
        damageTracker.tagPlayerAbilityDamage(caster, ability, subAbilityName);

        // Apply damage - this will trigger EntityDamageByEntityEvent which is caught by CustomDeathMessageListener
        target.damage(damage, caster);

        // Clean up the metadata from the caster
        damageTracker.clearPlayerAbilityTags(caster);

        // Also track using our direct method for extra reliability
        damageListener.trackAbilityDamage(target, caster, ability, subAbilityName);
    }

    /**
     * Tag a projectile or summon with ability information
     * @param entity The entity to tag (projectile, summon, etc.)
     * @param caster The player who cast the ability
     * @param ability The ability being used
     * @param subAbilityName Optional sub-ability name
     */
    public void tagEntity(Entity entity, Player caster, Ability ability, String subAbilityName) {
        damageTracker.tagEntity(entity, caster, ability, subAbilityName);
    }
}
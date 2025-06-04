// src/main/java/me/ratatamakata/spellbreak/abilities/Ability.java
package me.ratatamakata.spellbreak.abilities;

import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
// These imports are not strictly needed by the interface itself, but are often relevant for implementations
// import me.ratatamakata.spellbreak.managers.PlayerDataManager;
// import me.ratatamakata.spellbreak.managers.AbilityManager;

/**
 * Enhanced Ability interface with damage-related methods
 */
public interface Ability {
    /**
     * Get the name of the ability
     */
    String getName();

    /**
     * Get a description of what the ability does
     */
    String getDescription();

    /**
     * Get the cooldown in seconds
     */
    int getCooldown();

    /**
     * Get the mana cost of the ability
     */
    int getManaCost();

    /**
     * Get the required class to use this ability
     */
    String getRequiredClass();

    /**
     * Check if the action triggers this ability
     */
    boolean isTriggerAction(Action action);

    /**
     * Activate the ability for the player
     */
    void activate(Player player);

    /**
     * Load configuration for this ability
     */
    void loadConfig();

    /**
     * Check if the ability was successfully used
     */
    boolean isSuccessful();

    /**
     * Gets the maximum number of charges for this ability.
     * @return The maximum charges, or 0 or -1 if the ability doesn't use charges.
     */
    default int getMaxCharges() {
        return 0; // Default: No charges
    }

    /**
     * Gets the current number of charges the player has for this ability.
     * @param player The player to check.
     * @return The current charges, or 0 if the ability doesn't use charges.
     */
    default int getCurrentCharges(Player player) {
        return 0; // Default: No charges
    }

    /**
     * Gets the time in seconds for one charge to regenerate.
     * @return The regeneration time in seconds, or 0 if not applicable.
     */
    default int getChargeRegenTime() {
        return 0; // Default: No regen time
    }

    /**
     * Get a custom death message for this ability
     *
     * @param victim The name of the victim
     * @param caster The name of the ability caster
     * @param sub Optional sub-ability name
     * @return Formatted death message
     */
    String getDeathMessage(String victim, String caster, String sub);

    /**
     * New method: Get the default sub-ability name (if any)
     * Return null or empty string if not applicable
     */
    default String getDefaultSubAbilityName() {
        return null;
    }
}


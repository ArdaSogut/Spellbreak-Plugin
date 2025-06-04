package me.ratatamakata.spellbreak.util;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Centralized damage tracking system for abilities
 */
public class AbilityDamageTracker {
    private final Spellbreak plugin;

    // Metadata keys (copied from CustomDeathMessageListener for consistency)
    public static final String METADATA_KEY_CASTER_UUID = "SpellbreakCasterUUID";
    public static final String METADATA_KEY_ABILITY_NAME = "SpellbreakAbilityName";
    public static final String METADATA_KEY_SUB_ABILITY_NAME = "SpellbreakSubAbilityName";
    public static final String METADATA_KEY_SUMMON_TYPE_NAME = "SpellbreakSummonTypeName";

    public AbilityDamageTracker(Spellbreak plugin) {
        this.plugin = plugin;
    }

    /**
     * Tags an entity (projectile, summon, etc.) with the caster and ability information
     */
    public void tagEntity(Entity entity, Player caster, Ability ability, String subAbilityName) {
        // Tag with caster's UUID
        entity.setMetadata(METADATA_KEY_CASTER_UUID,
                new FixedMetadataValue(plugin, caster.getUniqueId().toString()));

        // Tag with ability name
        entity.setMetadata(METADATA_KEY_ABILITY_NAME,
                new FixedMetadataValue(plugin, ability.getName()));

        // Tag with sub-ability name if provided
        if (subAbilityName != null && !subAbilityName.isEmpty()) {
            entity.setMetadata(METADATA_KEY_SUB_ABILITY_NAME,
                    new FixedMetadataValue(plugin, subAbilityName));
        }

        // If it's a summon, tag it with its name
        if (!(entity instanceof Player) && !(entity instanceof Projectile)) {
            String summonName = entity.getCustomName();
            if (summonName == null || summonName.isEmpty()) {
                summonName = entity.getType().name();
            }
            entity.setMetadata(METADATA_KEY_SUMMON_TYPE_NAME,
                    new FixedMetadataValue(plugin, summonName));
        }
    }

    /**
     * Tags a player as using a specific ability for direct player damage
     */
    public void tagPlayerAbilityDamage(Player caster, Ability ability, String subAbilityName) {
        caster.setMetadata(METADATA_KEY_ABILITY_NAME,
                new FixedMetadataValue(plugin, ability.getName()));

        if (subAbilityName != null && !subAbilityName.isEmpty()) {
            caster.setMetadata(METADATA_KEY_SUB_ABILITY_NAME,
                    new FixedMetadataValue(plugin, subAbilityName));
        }
    }

    /**
     * Clears ability damage tags from a player
     */
    public void clearPlayerAbilityTags(Player player) {
        player.removeMetadata(METADATA_KEY_ABILITY_NAME, plugin);
        player.removeMetadata(METADATA_KEY_SUB_ABILITY_NAME, plugin);
    }
}
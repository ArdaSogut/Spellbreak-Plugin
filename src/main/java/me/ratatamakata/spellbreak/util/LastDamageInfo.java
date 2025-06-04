package me.ratatamakata.spellbreak.util;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;

public class LastDamageInfo {
    private final String victimName;
    private final String attackerName; // Name of the direct damager (player, or summon's custom name)
    private final String casterName;   // Name of the original player who cast the spell
    private final String abilityName;  // The primary ability (e.g., "BoneChoir")
    private final String subAbilityName; // Specific part of ability if applicable (e.g., "Tenor's Note", "Baritone's Blast")
    private final boolean bySummon;

    public LastDamageInfo(String victimName, String attackerName, String casterName, String abilityName, String subAbilityName, boolean bySummon) {
        this.victimName = victimName;
        this.attackerName = attackerName;
        this.casterName = casterName;
        this.abilityName = abilityName;
        this.subAbilityName = subAbilityName;
        this.bySummon = bySummon;
    }

    public String getFormattedDeathMessage() {
        // If it's a minion kill, return null to use default death message
        if (bySummon) {
            return null;
        }

        // Get the ability instance
        Ability ability = Spellbreak.getInstance().getAbilityManager().getAbilityByName(abilityName);
        if (ability != null) {
            // Try to get custom death message from the ability
            String message = ability.getDeathMessage(victimName, casterName, subAbilityName);
            if (message != null) {
                return message;
            }
        }

        // Fallback to generic message if ability doesn't provide one
        if (subAbilityName != null && !subAbilityName.isEmpty()) {
            return String.format("§e%s §fwas eliminated by §c%s §fwith §a%s (%s)§f.", victimName, casterName, abilityName, subAbilityName);
        } else {
            return String.format("§e%s §fwas eliminated by §c%s §fwith §a%s§f.", victimName, casterName, abilityName);
        }
    }
}
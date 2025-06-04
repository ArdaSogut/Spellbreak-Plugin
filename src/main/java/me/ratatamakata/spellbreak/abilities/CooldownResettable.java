package me.ratatamakata.spellbreak.abilities;

import java.util.UUID;

public interface CooldownResettable {
    void resetPlayerSpecificCooldowns(UUID playerId);
} 
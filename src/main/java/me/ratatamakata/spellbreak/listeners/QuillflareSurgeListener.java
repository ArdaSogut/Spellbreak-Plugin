package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.QuillflareSurgeAbility;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class QuillflareSurgeListener implements Listener {

    private final Spellbreak plugin;
    private final AbilityManager abilityManager;
    private final CooldownManager cooldownManager;
    private final PlayerDataManager playerDataManager;
    private final ManaSystem manaSystem;

    public QuillflareSurgeListener(Spellbreak plugin) {
        this.plugin = plugin;
        this.abilityManager = plugin.getAbilityManager();
        this.cooldownManager = plugin.getCooldownManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.manaSystem = plugin.getManaSystem();
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (!event.isSneaking()) { // Activate only when starting to sneak
            return;
        }

        // Get ability from equipped slot
        int slot = player.getInventory().getHeldItemSlot();
        String abilityNameFromSlot = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);

        // This listener should only handle the "QuillflareSurge" ability
        if (abilityNameFromSlot == null || !abilityNameFromSlot.equalsIgnoreCase("QuillflareSurge")) {
            return;
        }

        Ability rawAbility = abilityManager.getAbilityByName(abilityNameFromSlot); // or use "QuillflareSurge" directly

        if (!(rawAbility instanceof QuillflareSurgeAbility)) {
            // This should ideally not happen if abilityNameFromSlot was "QuillflareSurge"
            // and it was registered correctly.
            return;
        }
        QuillflareSurgeAbility ability = (QuillflareSurgeAbility) rawAbility;

        // Check class requirement
        String playerClass = playerDataManager.getPlayerClass(player.getUniqueId());
        if (ability.getRequiredClass() != null && !ability.getRequiredClass().isEmpty() && !ability.getRequiredClass().equalsIgnoreCase(playerClass)) {
            // player.sendActionBar(ChatColor.RED + "Your class cannot use this ability.");
            return;
        }

        // Check cooldown
        if (cooldownManager.isOnCooldown(player, ability.getName())) {
            player.sendActionBar(ChatColor.RED + "Ability on cooldown!");
            return;
        }

        // Check mana
        if (!manaSystem.consumeMana(player, ability.getManaCost())) {
            player.sendActionBar(ChatColor.RED + "Not enough mana!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f); // Sound for not enough mana
            return;
        }

        // Activate ability
        ability.activate(player);
        // Set cooldown immediately after activation
        cooldownManager.setCooldown(player, ability.getName(), ability.getCooldown());
    }
}
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.RunicJumpPadAbility;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class RunicJumpPadListener implements Listener {

    private final Spellbreak plugin = Spellbreak.getInstance();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Check if the action is a left-click (air or block)
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        // Get the ability bound to the player's held item slot
        int slot = player.getInventory().getHeldItemSlot();
        String boundAbilityName = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);

        // Check if RunicJumpPad is the bound ability
        if (!"RunicJumpPad".equalsIgnoreCase(boundAbilityName)) {
            return;
        }

        // Get the Ability instance
        Ability ability = plugin.getAbilityManager().getAbilityByName(boundAbilityName);

        // Double-check it's the correct type (optional but safe)
        if (!(ability instanceof RunicJumpPadAbility runicJumpPadAbility)) {
            plugin.getLogger().warning("[RunicJumpPadListener] Ability bound as 'RunicJumpPad' is not an instance of RunicJumpPadAbility!");
            return;
        }

        // Check if player has the required class
        String playerClass = plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId());
        if (!runicJumpPadAbility.getRequiredClass().equalsIgnoreCase(playerClass)) {
            // Don't send a message to keep UX clean
            return;
        }

        // Activate the ability - it handles charges, mana checks, cooldown internally
        runicJumpPadAbility.activate(player);

        // Cancel the interact event to prevent default left-click actions
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up pads when player leaves
        Ability ability = plugin.getAbilityManager().getAbilityByName("RunicJumpPad");
        if (ability instanceof RunicJumpPadAbility runicJumpPadAbility) {
            runicJumpPadAbility.cancelPads(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Clean up pads when player dies
        Ability ability = plugin.getAbilityManager().getAbilityByName("RunicJumpPad");
        if (ability instanceof RunicJumpPadAbility runicJumpPadAbility) {
            runicJumpPadAbility.cancelPads(event.getPlayer().getUniqueId());
        }
    }
}
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.NatureStepAbility; // Import the specific ability
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class NatureStepListener implements Listener {

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

        // Check if NatureStep is the bound ability
        if (!"NatureStep".equalsIgnoreCase(boundAbilityName)) {
            return;
        }

        // Get the Ability instance
        Ability ability = plugin.getAbilityManager().getAbilityByName(boundAbilityName);

        // Double-check it's the correct type (optional but safe)
        if (!(ability instanceof NatureStepAbility natureStepAbility)) {
            plugin.getLogger().warning("[NatureStepListener] Ability bound as 'NatureStep' is not an instance of NatureStepAbility!");
            return;
        }

        // --- Standard Ability Checks (Class, Cooldown - though NatureStep uses charges, Mana is checked internally) ---

        // Check Class
        String playerClass = plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId());
        if (!natureStepAbility.getRequiredClass().equalsIgnoreCase(playerClass)) {
            // Don't spam message, activate handles feedback if needed
            return;
        }

        // Cooldown check (NatureStep uses internal charge system, so we don't check global cooldown here)
        // Mana check (Handled inside NatureStepAbility.activate())

        // Activate the ability
        // The activate method itself handles charges, mana, internal cooldown, sound effects, and messages.
        natureStepAbility.activate(player);

        // Cancel the interact event to prevent default left-click actions (like hitting)
        event.setCancelled(true);
    }
} 
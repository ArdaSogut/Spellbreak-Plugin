package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import java.util.logging.Level;
import org.bukkit.ChatColor;

/**
 * Allows MistDash to activate on left-click (air, blocks),
 * but only when truly airborne and MistDash is in your hand.
 */
public class MistDashListener implements Listener {

    private final Spellbreak plugin = Spellbreak.getInstance(); // Cache instance
    private final PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
    private final AbilityManager abilityManager = plugin.getAbilityManager();
    private final CooldownManager cooldownManager = plugin.getCooldownManager();
    private final ManaSystem manaSystem = plugin.getManaSystem();
    private static final String ABILITY_NAME = "MistDash"; // Define constant for clarity

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check only for left clicks
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();

        // 1. Get ability bound to the current slot
        String abilityName = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);
        plugin.getLogger().log(Level.INFO, "[MistDashListener] Player " + player.getName() + " LEFT clicked. Slot "+ slot +" Ability: " + abilityName);

        // 2. Check if it's the correct ability
        if (!ABILITY_NAME.equalsIgnoreCase(abilityName)) {
            // plugin.getLogger().info("[MistDashListener] Not MistDash. Exiting.");
            return;
        }
        plugin.getLogger().info("[MistDashListener] Ability is MistDash. Proceeding...");

        // 3. Get the Ability instance
        Ability ability = abilityManager.getAbilityByName(abilityName); // Use the retrieved name for consistency
        if (ability == null) {
            plugin.getLogger().warning("[MistDashListener] MistDash ability instance is NULL from AbilityManager!");
            return;
        }
        plugin.getLogger().info("[MistDashListener] Got MistDash instance: " + ability.getName());

        // 4. Check Cooldown
        if (cooldownManager.isOnCooldown(player, ability.getName())) {
             plugin.getLogger().info("[MistDashListener] MistDash is on COOLDOWN for " + player.getName());
             // Note: Cooldown message might still be sent by CooldownManager if bypass is not active
             return;
        }
        plugin.getLogger().info("[MistDashListener] MistDash is NOT on cooldown.");

        // 5. Check & Consume Mana
        if (manaSystem.consumeMana(player, ability.getManaCost())) {
            plugin.getLogger().info("[MistDashListener] Mana sufficient. Activating MistDash.");
            
            // 6. Activate Ability
            ability.activate(player);
            
            // 7. Set Cooldown
            cooldownManager.setCooldown(player, ability.getName(), ability.getCooldown());
            
            event.setCancelled(true); // Prevent default actions only if successfully activated
        } else {
            plugin.getLogger().info("[MistDashListener] Insufficient mana for MistDash. Mana: " + manaSystem.getMana(player) + "/" + ability.getManaCost());
            player.sendMessage(ChatColor.RED + "Not enough mana to cast " + ability.getName() + ".");
        }
    }
}

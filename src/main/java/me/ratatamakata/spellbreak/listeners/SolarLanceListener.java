package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.SolarLanceAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SolarLanceListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final SolarLanceAbility ability;

    public SolarLanceListener() {
        this.ability = (SolarLanceAbility) plugin.getAbilityManager().getAbilityByName("SolarLance");

        if (ability == null) {
            plugin.getLogger().severe("SolarLanceAbility not found! Make sure it's registered in AbilityManager.");
        } else {
            plugin.getLogger().info("SolarLanceListener initialized successfully.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (ability == null) return;

        Player player = e.getPlayer();
        Action action = e.getAction();

        // Check if it's a left click (either air or block)
        if (!ability.isTriggerAction(action)) return;

        // Check if player has this ability bound to current slot
        String bound = plugin.getPlayerDataManager().getAbilityAtSlot(
                player.getUniqueId(), player.getInventory().getHeldItemSlot());

        if (!"SolarLance".equalsIgnoreCase(bound)) return;

        // Check class requirement
        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "You need to be a " + ability.getRequiredClass() + " to use Solar Lance!");
            return;
        }

        // Check cooldown
        if (plugin.getCooldownManager().isOnCooldown(player, ability.getName())) {
            player.sendMessage(ChatColor.BLUE + "Solar Lance on cooldown: " +
                    plugin.getCooldownManager().getRemainingCooldown(player, ability.getName()) + "s");
            return;
        }

        // Check mana
        if (!plugin.getManaSystem().consumeMana(player, ability.getManaCost())) {
            player.sendMessage(ChatColor.BLUE + "Not enough mana for Solar Lance! (Need " + ability.getManaCost() + ")");
            return;
        }

        // Cancel the event to prevent block breaking/placing
        e.setCancelled(true);

        // Activate ability
        ability.activate(player);
        plugin.getCooldownManager().setCooldown(player, ability.getName(), ability.getCooldown());
    }
}
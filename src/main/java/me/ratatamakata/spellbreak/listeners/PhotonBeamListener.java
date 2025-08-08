package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.PhotonBeamAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PhotonBeamListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final PhotonBeamAbility ability;

    public PhotonBeamListener() {
        this.ability = (PhotonBeamAbility) plugin.getAbilityManager().getAbilityByName("PhotonBeam");

        if (ability == null) {
            plugin.getLogger().severe("PhotonBeamAbility not found! Make sure it's registered in AbilityManager.");
        } else {
            plugin.getLogger().info("PhotonBeamListener initialized successfully.");
        }
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent e) {
        if (ability == null) return;

        Player player = e.getPlayer();
        String bound = plugin.getPlayerDataManager().getAbilityAtSlot(
                player.getUniqueId(), player.getInventory().getHeldItemSlot());

        if (!"PhotonBeam".equalsIgnoreCase(bound)) return;

        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId()))) {
            if (e.isSneaking()) { // Only show message when starting to sneak
                player.sendMessage(ChatColor.RED + "You need to be a " + ability.getRequiredClass() + " to use PhotonBeam!");
            }
            return;
        }

        if (e.isSneaking()) {
            // Player started sneaking - start charging
            if (ability.isPlayerCharging(player)) {
                return; // Already charging
            }

            // Check cooldown and mana before starting
            if (plugin.getCooldownManager().isOnCooldown(player, ability.getName())) {
                player.sendMessage(ChatColor.BLUE + "Photon Beam on cooldown: " +
                        plugin.getCooldownManager().getRemainingCooldown(player, ability.getName()) + "s");
                return;
            }

            if (!plugin.getManaSystem().consumeMana(player, ability.getManaCost())) {
                player.sendMessage(ChatColor.BLUE + "Not enough mana for Photon Beam! (Need " + ability.getManaCost() + ")");
                return;
            }

            // Start charging
            ability.startCharging(player);

        } else {
            // Player stopped sneaking - release beam (if charging)
            if (ability.isPlayerCharging(player)) {
                ability.activate(player);
                plugin.getCooldownManager().setCooldown(player, ability.getName(), ability.getCooldown());
            }
        }
    }
}
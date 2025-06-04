package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.SwarmSigilAbility;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class SwarmSigilListener implements Listener {

    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final SwarmSigilAbility ability;

    public SwarmSigilListener() {
        this.ability = (SwarmSigilAbility) plugin.getAbilityManager().getAbilityByName("SwarmSigil");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // Check for shift + left click
        if (!p.isSneaking()) return;
        if (e.getAction() != Action.LEFT_CLICK_AIR &&
                e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        // Check if SwarmSigil is bound to current slot
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"SwarmSigil".equalsIgnoreCase(bound)) return;

        // Prevent default interactions
        e.setCancelled(true);

        // Check class requirement
        if (!ability.getRequiredClass().equalsIgnoreCase(pdm.getPlayerClass(p.getUniqueId()))) {
            p.sendMessage(ChatColor.RED + "You must be a " +
                    ChatColor.YELLOW + ability.getRequiredClass() +
                    ChatColor.RED + " to use this ability!");
            return;
        }

        // Check cooldown
        if (cd.isOnCooldown(p, "SwarmSigil")) {
            p.sendMessage(ChatColor.RED + "SwarmSigil on cooldown: " +
                    cd.getRemainingCooldown(p, "SwarmSigil") + "s");
            return;
        }

        // Check if player already has active drones
        if (SwarmSigilAbility.hasActiveDrones(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "You already have active swarm drones!");
            return;
        }

        // Check mana
        if (!mana.consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.RED + "Not enough mana for SwarmSigil!");
            return;
        }

        // Activate ability
        ability.activate(p);

        // Set cooldown
        cd.setCooldown(p, "SwarmSigil", ability.getCooldown());

        // Success message
        p.sendMessage(ChatColor.GREEN + "Swarm drones deployed!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        SwarmSigilAbility.removeDrones(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        SwarmSigilAbility.removeDrones(e.getEntity().getUniqueId());
    }
}
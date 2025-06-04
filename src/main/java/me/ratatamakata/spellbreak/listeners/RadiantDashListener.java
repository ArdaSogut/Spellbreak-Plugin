package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public class RadiantDashListener implements Listener {

    private final Spellbreak      plugin    = Spellbreak.getInstance();
    private final PlayerDataManager playerData = plugin.getPlayerDataManager();
    private final AbilityManager     abilityMgr = plugin.getAbilityManager();
    private final CooldownManager    cdMgr      = plugin.getCooldownManager();
    private final ManaSystem         manaSys    = plugin.getManaSystem();
    private static final String      ABIL_NAME  = "RadiantDash";

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        int slot      = player.getInventory().getHeldItemSlot();
        String bound  = playerData.getAbilityAtSlot(player.getUniqueId(), slot);

        plugin.getLogger().log(Level.INFO,
                "[RadiantDashListener] " + player.getName() +
                        " clicked slot " + slot + " → " + bound
        );

        if (!ABIL_NAME.equalsIgnoreCase(bound)) return;

        Ability ability = abilityMgr.getAbilityByName(bound);
        if (ability == null) {
            plugin.getLogger().warning("[RadiantDashListener] Ability instance null");
            return;
        }

        if (cdMgr.isOnCooldown(player, ability.getName())) {
            plugin.getLogger().info(
                    "[RadiantDashListener] On cooldown for " + player.getName());
            return;
        }

        if (!manaSys.consumeMana(player, ability.getManaCost())) {
            plugin.getLogger().info(
                    "[RadiantDashListener] Not enough mana: " +
                            manaSys.getMana(player) + "/" + ability.getManaCost());
            player.sendMessage(
                    ChatColor.RED + "Not enough mana to cast " + ability.getName() + ".");
            return;
        }

        ability.activate(player);
        if (ability.isSuccessful()) {
            cdMgr.setCooldown(player, ability.getName(), ability.getCooldown());
            event.setCancelled(true);
        } else {
            manaSys.restoreMana(player, ability.getManaCost());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Revoke any lingering flight perms
        Player player = event.getPlayer();
        player.setFlying(false);
        player.setAllowFlight(false);
    }
}

package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.AvalancheAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class AvalancheListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final AvalancheAbility ability;

    public AvalancheListener() {
        this.ability = (AvalancheAbility) plugin.getAbilityManager().getAbilityByName("Avalanche");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String bound = plugin.getPlayerDataManager().getAbilityAtSlot(
                p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"Avalanche".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(p.getUniqueId()))) return;

        int cd = ability.getAdjustedCooldown(p);
        int mana = ability.getAdjustedManaCost(p);
        if (plugin.getCooldownManager().isOnCooldown(p, ability.getName())) {
            p.sendMessage(ChatColor.BLUE + "Avalanche on cooldown: " +
                    plugin.getCooldownManager().getRemainingCooldown(p, ability.getName()) + "s");
            return;
        }
        if (!plugin.getManaSystem().consumeMana(p, mana)) {
            p.sendMessage(ChatColor.BLUE + "Not enough mana for Avalanche! (Need " + mana + ")");
            return;
        }
        ability.activate(p);
        plugin.getCooldownManager().setCooldown(p, ability.getName(), cd);
    }
}

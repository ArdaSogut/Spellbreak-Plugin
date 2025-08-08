// BlackHoleListener.java
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.BlackHoleAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class BlackHoleListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final BlackHoleAbility ability;

    public BlackHoleListener() {
        this.ability = (BlackHoleAbility)
                plugin.getAbilityManager().getAbilityByName("BlackHole");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (!((action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
                && e.getPlayer().isSneaking())) return;

        Player p = e.getPlayer();
        String bound = plugin.getPlayerDataManager()
                .getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"BlackHole".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(p.getUniqueId()))) return;

        e.setCancelled(true);

        // if currently traveling, anchor and return
        if (ability.isTraveling(p.getUniqueId())) {
            ability.activate(p);  // will anchor
            return;
        }

        // initial launch: check cooldown & mana
        if (plugin.getCooldownManager().isOnCooldown(p, ability.getName())) {
            p.sendMessage(ChatColor.BLUE + "BlackHole on cooldown: "
                    + plugin.getCooldownManager().getRemainingCooldown(p, ability.getName()) + "s");
            return;
        }
        if (!plugin.getManaSystem().consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.BLUE + "Not enough mana for BlackHole! (Need " + ability.getManaCost() + ")");
            return;
        }

        ability.activate(p);
        plugin.getCooldownManager().setCooldown(p, ability.getName(), ability.getCooldown());
    }
}

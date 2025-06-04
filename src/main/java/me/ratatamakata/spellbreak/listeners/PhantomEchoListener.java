
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.PhantomEchoAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PhantomEchoListener implements Listener {

    @EventHandler
    public void onLeftClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!isAbilityActive(p)) return;

        PhantomEchoAbility ability = (PhantomEchoAbility) Spellbreak.getInstance()
                .getAbilityManager().getAbilityByName("PhantomEcho");
        if (ability == null) return;

        if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(p, ability.getName())) {
            e.setCancelled(true);
            return;
        }

        ability.dash(p);
        e.setCancelled(true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        if (!isAbilityActive(p)) return;

        PhantomEchoAbility ability = (PhantomEchoAbility) Spellbreak.getInstance()
                .getAbilityManager().getAbilityByName("PhantomEcho");
        if (ability == null) return;

        ability.returnToClone(p);
        e.setCancelled(true);
    }

    private boolean isAbilityActive(Player p) {
        String ability = Spellbreak.getInstance().getPlayerDataManager()
                .getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        return "PhantomEcho".equalsIgnoreCase(ability);
    }
}
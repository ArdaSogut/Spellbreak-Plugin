package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.TentaclesAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class TentaclesListener implements Listener {

    @EventHandler
    public void onCast(PlayerInteractEvent e) {
        if (!(e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)) return;
        if (!e.getPlayer().isSneaking()) return;

        Player player = e.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();

        // Get the ability name mapped to this slot
        String name = Spellbreak.getInstance().getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);
        if (!"Tentacles".equalsIgnoreCase(name)) return;

        // Get the ability instance
        Ability ability = Spellbreak.getInstance().getAbilityManager().getAbilityByName(name);
        if (!(ability instanceof TentaclesAbility)) return;

        // Check class
        String playerClass = Spellbreak.getInstance()
                .getPlayerDataManager()
                .getPlayerClass(player.getUniqueId());
        if (!ability.getRequiredClass().equalsIgnoreCase(playerClass)) {
            player.sendMessage("§cYour class cannot use this ability!");
            return;
        }

        // Cooldown
        if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(player, ability.getName())) {
            player.sendMessage("§cAbility is on cooldown!");
            return;
        }

        // Mana
        if (!Spellbreak.getInstance().getManaSystem().consumeMana(player, ability.getManaCost())) {
            player.sendMessage("§cNot enough mana!");
            return;
        }

        // Cancel event if block clicked
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
        }

        // Activate ability
        ability.activate(player);
        Spellbreak.getInstance().getCooldownManager().setCooldown(player, name, ability.getCooldown());
    }
}

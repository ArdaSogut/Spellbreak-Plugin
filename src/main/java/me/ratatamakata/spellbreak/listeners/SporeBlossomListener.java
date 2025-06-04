package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.SporeBlossomAbility;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SporeBlossomListener implements Listener {
    @EventHandler
    public void onSporeCast(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!player.isSneaking()) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;

        int slot = player.getInventory().getHeldItemSlot();
        String bound = Spellbreak.getInstance()
                .getPlayerDataManager()
                .getAbilityAtSlot(player.getUniqueId(), slot);
        if (!"SporeBlossom".equalsIgnoreCase(bound)) return;

        Ability ability = Spellbreak.getInstance().getAbilityManager()
                .getAbilityByName("SporeBlossom");
        if (!(ability instanceof SporeBlossomAbility)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(
                Spellbreak.getInstance()
                        .getPlayerDataManager()
                        .getPlayerClass(player.getUniqueId()))) return;
        if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(player, ability.getName())) {
            player.sendMessage(ChatColor.RED + "Ability on cooldown!"); return;
        }
        if (!Spellbreak.getInstance().getManaSystem().consumeMana(player, ability.getManaCost())) {
            player.sendMessage(ChatColor.RED + "Not enough mana!"); return;
        }

        e.setCancelled(true);
        ability.activate(player);
        Spellbreak.getInstance().getCooldownManager()
                .setCooldown(player, ability.getName(), ability.getCooldown());

        player.spawnParticle(Particle.SPORE_BLOSSOM_AIR, player.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
    }
}

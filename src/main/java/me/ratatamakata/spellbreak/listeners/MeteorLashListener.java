package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.MeteorLashAbility;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class MeteorLashListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final MeteorLashAbility ability;

    public MeteorLashListener() {
        this.ability = (MeteorLashAbility) plugin.getAbilityManager().getAbilityByName("MeteorLash");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String bound = plugin.getPlayerDataManager().getAbilityAtSlot(
                p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"MeteorLash".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(p.getUniqueId()))) return;

        // Check if player is already in selection mode
        if (ability.isInSelectionMode(p.getUniqueId())) {
            // Second click - launch meteor
            int cd = ability.getAdjustedCooldown(p);
            int mana = ability.getAdjustedManaCost(p);

            if (plugin.getCooldownManager().isOnCooldown(p, ability.getName())) {
                p.sendMessage(ChatColor.BLUE + "MeteorLash on cooldown: " +
                        plugin.getCooldownManager().getRemainingCooldown(p, ability.getName()) + "s");
                return;
            }
            if (!plugin.getManaSystem().consumeMana(p, mana)) {
                p.sendMessage(ChatColor.BLUE + "Not enough mana for MeteorLash! (Need " + mana + ")");
                return;
            }

            // Set the target location and launch
            Location target = p.getTargetBlock(null, 50).getLocation();
            ability.setTargetLocation(p.getUniqueId(), target);
            ability.activate(p);
            plugin.getCooldownManager().setCooldown(p, ability.getName(), cd);
        } else {
            // First click - start selection mode
            ability.activate(p);
        }
    }
}
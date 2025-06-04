package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.RunicTurretAbility;
import me.ratatamakata.spellbreak.managers.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class RunicTurretListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final RunicTurretAbility ability;

    public RunicTurretListener() {
        this.ability = (RunicTurretAbility) plugin.getAbilityManager().getAbilityByName("RunicCannon");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        // Skip off-hand interactions to prevent double triggering
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;

        // Check if the action is left click
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        // Check if player is sneaking (shift-clicking)
        if (!p.isSneaking()) return;

        int slot = p.getInventory().getHeldItemSlot();
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);

        // Check if RunicTurret is bound to this slot
        if (!"RunicCannon".equalsIgnoreCase(bound)) return;

        // Check player class
        if (!ability.getRequiredClass().equalsIgnoreCase(pdm.getPlayerClass(p.getUniqueId()))) {
            p.sendMessage(ChatColor.RED + "You must be a " +
                    ChatColor.YELLOW + ability.getRequiredClass() +
                    ChatColor.RED + " to use this ability!");
            return;
        }

        // Check cooldown
        if (cd.isOnCooldown(p, "RunicCannon")) {
            p.sendMessage(ChatColor.RED + "RunicCannon on cooldown: " +
                    cd.getRemainingCooldown(p, "RunicCannon") + "s");
            return;
        }

        // Check mana
        if (!mana.consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.RED + "Not enough mana for RunicCannon!");
            return;
        }

        // Activate ability
        ability.activate(p);

        // Set cooldown
        cd.setCooldown(p, "RunicCannon", ability.getCooldown());

        // Prevent block breaking
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        // Clean up turrets when player leaves
        RunicTurretAbility.removeTurrets(e.getPlayer().getUniqueId());
    }
}
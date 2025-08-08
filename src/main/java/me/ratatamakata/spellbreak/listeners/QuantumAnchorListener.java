package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.QuantumAnchorAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class QuantumAnchorListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final QuantumAnchorAbility ability;

    public QuantumAnchorListener() {
        this.ability = (QuantumAnchorAbility) plugin.getAbilityManager().getAbilityByName("QuantumAnchor");

        if (ability == null) {
            plugin.getLogger().severe("QuantumAnchorAbility not found! Make sure it's registered in AbilityManager.");
        } else {
            plugin.getLogger().info("QuantumAnchorListener initialized successfully.");
        }
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent e) {
        if (ability == null) return;

        Player player = e.getPlayer();
        String bound = plugin.getPlayerDataManager().getAbilityAtSlot(
                player.getUniqueId(), player.getInventory().getHeldItemSlot());

        if (!"QuantumAnchor".equalsIgnoreCase(bound)) return;

        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(player.getUniqueId()))) {
            if (e.isSneaking()) {
                player.sendMessage(ChatColor.RED + "You need to be a " + ability.getRequiredClass() + " to use Quantum Anchor!");
            }
            return;
        }

        if (e.isSneaking()) {
            // Player started sneaking - activate ability
            if (plugin.getCooldownManager().isOnCooldown(player, ability.getName())) {
                player.sendMessage(ChatColor.BLUE + "Quantum Anchor on cooldown: " +
                        plugin.getCooldownManager().getRemainingCooldown(player, ability.getName()) + "s");
                return;
            }

            if (!plugin.getManaSystem().consumeMana(player, ability.getManaCost())) {
                player.sendMessage(ChatColor.BLUE + "Not enough mana for Quantum Anchor! (Need " + ability.getManaCost() + ")");
                return;
            }

            ability.activate(player);
            plugin.getCooldownManager().setCooldown(player, ability.getName(), ability.getCooldown());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (ability == null) return;
        if (!(e.getEntity() instanceof Player)) return;
        if (e.isCancelled()) return; // Don't check if damage was cancelled

        Player player = (Player) e.getEntity();

        // Simple delayed check - let Minecraft handle absorption naturally
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                ability.checkAbsorption(player);
            }
        }, 3L); // Give time for absorption to be processed
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (ability == null) return;
        ability.removeAnchor(e.getPlayer());
    }
}
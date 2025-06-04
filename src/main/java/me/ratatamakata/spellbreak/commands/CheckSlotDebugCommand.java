package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CheckSlotDebugCommand implements CommandExecutor {

    private final Spellbreak plugin;

    public CheckSlotDebugCommand(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        int slot = player.getInventory().getHeldItemSlot();
        String abilityName = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String itemName = (itemInHand != null && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName())
                ? itemInHand.getItemMeta().getDisplayName()
                : (itemInHand != null ? itemInHand.getType().toString() : "EMPTY");

        player.sendMessage(ChatColor.GOLD + "---- Slot Debug ----");
        player.sendMessage(ChatColor.YELLOW + "Current Slot: " + ChatColor.WHITE + (slot + 1));
        player.sendMessage(ChatColor.YELLOW + "Item in Hand: " + ChatColor.WHITE + itemName);
        if (abilityName != null) {
            player.sendMessage(ChatColor.YELLOW + "Bound Ability (PlayerDataManager): " + ChatColor.GREEN + abilityName);
        } else {
            player.sendMessage(ChatColor.YELLOW + "Bound Ability (PlayerDataManager): " + ChatColor.RED + "None/Null");
        }
        player.sendMessage(ChatColor.GOLD + "--------------------");

        return true;
    }
} 
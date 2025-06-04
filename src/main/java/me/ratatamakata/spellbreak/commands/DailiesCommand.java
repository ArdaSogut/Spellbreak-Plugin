package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.dailies.DailyMission;
import me.ratatamakata.spellbreak.dailies.DailyMissionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.List;

public class DailiesCommand implements CommandExecutor {
    private final DailyMissionManager manager;
    private final int[] missionSlots = {11, 13, 15};
    private final int[] rerollSlots = {20, 22, 24};
    private final int[] claimSlots = {29, 31, 33};

    public DailiesCommand(Spellbreak plugin) {
        this.manager = plugin.getDailyMissionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 45, ChatColor.DARK_BLUE + "Daily Missions");
        List<DailyMission> list = manager.getMissions(p.getUniqueId());

        for (int i = 0; i < list.size(); i++) {
            DailyMission m = list.get(i);
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta im = info.getItemMeta();
            int done = manager.getProgress(p.getUniqueId(), m.getKey());
            im.setDisplayName(ChatColor.GOLD + m.getDescription());
            im.setLore(List.of(
                    ChatColor.GRAY + "Difficulty: " + m.getDifficulty(),
                    ChatColor.GRAY + "Progress: " + done + "/" + m.getAmount(),
                    ChatColor.GRAY + "XP: " + m.getXpReward()
            ));
            info.setItemMeta(im);
            inv.setItem(missionSlots[i], info);

            ItemStack rr = new ItemStack(Material.BARRIER);
            ItemMeta rrM = rr.getItemMeta();
            rrM.setDisplayName(manager.canReroll(p.getUniqueId(), m.getKey())
                    ? ChatColor.RED + "Reroll"
                    : ChatColor.DARK_GRAY + "Rerolled");
            rr.setItemMeta(rrM);
            inv.setItem(rerollSlots[i], rr);

            ItemStack cl = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta clM = cl.getItemMeta();
            clM.setDisplayName(manager.isComplete(p.getUniqueId(), m)
                    ? ChatColor.GREEN + "Claim"
                    : ChatColor.DARK_GRAY + "Incomplete");
            cl.setItemMeta(clM);
            inv.setItem(claimSlots[i], cl);
        }

        p.openInventory(inv);
        return true;
    }
}

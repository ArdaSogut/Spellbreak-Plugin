package me.ratatamakata.spellbreak.dailies;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.dailies.DailyMission;
import me.ratatamakata.spellbreak.dailies.DailyMissionManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class DailiesListener implements Listener {
    private final DailyMissionManager manager;
    private final int[] rerollSlots = {20, 22, 24};
    private final int[] claimSlots = {29, 31, 33};

    public DailiesListener(Spellbreak plugin) {
        this.manager = plugin.getDailyMissionManager();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (title == null || !title.contains("Daily Missions")) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        // ensure click is within custom GUI, not player inventory
        Inventory top = e.getView().getTopInventory();
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;

        for (int i = 0; i < rerollSlots.length; i++) {
            if (slot == rerollSlots[i]) {
                DailyMission m = manager.getMissions(p.getUniqueId()).get(i);
                DailyMission newM = manager.rerollSingle(p.getUniqueId(), m.getKey());
                if (newM != null) p.sendMessage(ChatColor.YELLOW + "New mission: " + newM.getDescription());
                else p.sendMessage(ChatColor.RED + "Cannot reroll mission #" + (i+1));
                p.closeInventory();
                return;
            }
        }
        for (int i = 0; i < claimSlots.length; i++) {
            if (slot == claimSlots[i]) {
                DailyMission m = manager.getMissions(p.getUniqueId()).get(i);
                if (manager.claimSingle(p.getUniqueId(), m)) {
                    p.sendMessage(ChatColor.GREEN + "Claimed " + m.getXpReward() + " XP");
                } else {
                    p.sendMessage(ChatColor.RED + "Mission incomplete");
                }
                p.closeInventory();
                return;
            }
        }
    }
}

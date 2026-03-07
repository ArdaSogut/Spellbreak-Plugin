package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.gui.CharacterSelectGUI;
import me.ratatamakata.spellbreak.player.CharacterSlot;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.UUID;

/**
 * Handles all click events inside the Character Select GUI and the
 * Class Select sub-GUI.
 */
public class CharacterSelectListener implements Listener {

    private static final String CLASS_SELECT_TITLE_PREFIX = ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "Choose Class";

    private final Spellbreak plugin;

    public CharacterSelectListener(Spellbreak plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Main Character Select GUI
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        if (title.equals(CharacterSelectGUI.TITLE)) {
            handleCharacterSelectClick(event, player);
        } else if (title.startsWith(CLASS_SELECT_TITLE_PREFIX)) {
            handleClassSelectClick(event, player, title);
        }
    }

    private void handleCharacterSelectClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();

        // Close button (slot 49)
        if (rawSlot == 49) {
            player.closeInventory();
            return;
        }

        int charSlotIndex = CharacterSelectGUI.getCharacterSlotIndex(rawSlot);
        if (charSlotIndex < 0) return; // clicked decoration

        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(player.getUniqueId(), charSlotIndex);
        String cls = CharacterSelectGUI.getClassForSlot(charSlotIndex);

        // Shift + right-click = delete
        if (event.getClick() == ClickType.SHIFT_RIGHT && slot != null && !slot.isEmpty()) {
            deleteCharacterSlot(player, charSlotIndex, cls);
            return;
        }

        if (slot == null || slot.isEmpty()) {
            // Open class-specific confirmation: just create it (user already knows the class from the column)
            createCharacterSlot(player, charSlotIndex, cls);
        } else if (charSlotIndex == plugin.getPlayerDataManager().getActiveSlotIndex(player.getUniqueId())) {
            // Already active — open the ability bind GUI
            player.closeInventory();
            me.ratatamakata.spellbreak.gui.AbilityBindGUI.open(player);
        } else {
            // Switch to this character
            switchToSlot(player, charSlotIndex, cls);
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void switchToSlot(Player player, int slotIndex, String cls) {
        UUID uuid = player.getUniqueId();
        plugin.getPlayerDataManager().setActiveSlotIndex(uuid, slotIndex);
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        player.sendMessage(ChatColor.GOLD + "✦ " + ChatColor.RESET + "Switched to " +
                CharacterSelectGUI.getClassColor(cls) + "" + ChatColor.BOLD + cls +
                ChatColor.RESET + ChatColor.GOLD + " (Slot " + ((slotIndex % 2) + 1) + ")!");

        // Apply stats for the new active slot
        plugin.getPlayerDataManager().applyLevelStats(player);
    }

    private void createCharacterSlot(Player player, int slotIndex, String cls) {
        UUID uuid = player.getUniqueId();
        CharacterSlot newSlot = new CharacterSlot(slotIndex, cls, new String[9]);
        plugin.getPlayerDataManager().setCharacterSlot(uuid, slotIndex, newSlot);
        plugin.getPlayerDataManager().setActiveSlotIndex(uuid, slotIndex);
        plugin.getPlayerDataManager().saveData(uuid);

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(ChatColor.GREEN + "✦ " + ChatColor.RESET + "Created a new " +
                CharacterSelectGUI.getClassColor(cls) + "" + ChatColor.BOLD + cls +
                ChatColor.RESET + ChatColor.GREEN + " character in slot " + ((slotIndex % 2) + 1) + "!");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/bind <1-9> <ability>" +
                ChatColor.GRAY + " to bind your spells.");

        // Apply stats
        plugin.getPlayerDataManager().applyLevelStats(player);
    }

    private void deleteCharacterSlot(Player player, int slotIndex, String cls) {
        UUID uuid = player.getUniqueId();

        // Replace with empty slot
        plugin.getPlayerDataManager().setCharacterSlot(uuid, slotIndex, new CharacterSlot(slotIndex));

        // If that was the active slot, reset active to -1 (None)
        if (plugin.getPlayerDataManager().getActiveSlotIndex(uuid) == slotIndex) {
            plugin.getPlayerDataManager().setActiveSlotIndex(uuid, -1);
        }

        plugin.getPlayerDataManager().saveData(uuid);

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
        player.sendMessage(ChatColor.RED + "✦ " + ChatColor.RESET + "Deleted your " +
                CharacterSelectGUI.getClassColor(cls) + "" + ChatColor.BOLD + cls +
                ChatColor.RESET + ChatColor.RED + " character in slot " + ((slotIndex % 2) + 1) + ".");

        // Refresh GUI
        CharacterSelectGUI.open(player);
    }

    // -------------------------------------------------------------------------
    // Class Select sub-GUI (skeleton for future expansion – currently not shown)
    // -------------------------------------------------------------------------

    private void handleClassSelectClick(InventoryClickEvent event, Player player, String title) {
        event.setCancelled(true);
        // Reserved for future expansion (e.g., a pick-class screen before creation)
    }
}

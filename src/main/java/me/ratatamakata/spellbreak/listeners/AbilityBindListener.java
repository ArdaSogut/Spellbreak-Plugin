package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.gui.AbilityBindGUI;
import me.ratatamakata.spellbreak.gui.CharacterSelectGUI;
import me.ratatamakata.spellbreak.player.CharacterSlot;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.ratatamakata.spellbreak.level.SpellLevel;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles clicks inside the Ability Bind GUI.
 *
 * Interaction model:
 *   1. Player left-clicks a binding slot (row 4, slots 36-44) → that slot becomes "selected" (blue glass).
 *   2. Player left-clicks an ability item (rows 1-2)            → ability is written to the selected slot.
 *   3. Player right-clicks a binding slot                       → clears that binding.
 *   4. Clicking the BACK arrow (slot 49)                        → returns to CharacterSelectGUI.
 */
public class AbilityBindListener implements Listener {

    /** Tracks which hotbar slot (0-8) each player has currently "selected" for binding. */
    private static final Map<UUID, Integer> selectedSlot = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title == null || !title.contains("Manage:")) {
            return;
        }

        event.setCancelled(true);

        int raw = event.getRawSlot();
        UUID uuid = player.getUniqueId();

        // --- BACK button ---
        if (raw == AbilityBindGUI.BACK_SLOT) {
            player.closeInventory();
            CharacterSelectGUI.open(player);
            return;
        }

        // --- Binding slot row (36-44) ---
        if (raw >= AbilityBindGUI.BIND_SLOT_START && raw < AbilityBindGUI.BIND_SLOT_START + 9) {
            int hotbarSlot = raw - AbilityBindGUI.BIND_SLOT_START;

            if (event.getClick() == ClickType.RIGHT) {
                // Clear binding
                clearBinding(player, hotbarSlot);
                refreshBindRow(player, event);
                return;
            }

            // Select this slot
            selectedSlot.put(uuid, hotbarSlot);
            highlightSelectedSlot(player, hotbarSlot, event);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        // --- Ability items (Row 1: 9-17) ---
        if (raw >= 9 && raw < 18) {
            if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) return;

            Integer target = selectedSlot.get(uuid);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "⚠ First select a binding slot (click a green slot in row 5).");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                return;
            }

            // Read ability name from item display name
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String abilityName = parseAbilityName(clicked);
            if (abilityName == null) return;

            bindAbility(player, target, abilityName);
            selectedSlot.remove(uuid); // deselect after binding
            refreshAll(player, event);
            return;
        }

        // --- Upgrade Buttons (Row 2: 18-26) ---
        if (raw >= 18 && raw < 27) {
            ItemStack spellItem = event.getInventory().getItem(raw - 9);
            if (spellItem == null || !spellItem.hasItemMeta()) {
                player.sendMessage(ChatColor.RED + "Debug: spellItem is null or has no meta at slot " + (raw - 9));
                return;
            }
            String abilityName = parseAbilityName(spellItem);
            if (abilityName == null) {
                player.sendMessage(ChatColor.RED + "Debug: parseAbilityName returned null");
                return;
            }

            upgradeSpell(player, abilityName);
            refreshAll(player, event);
            return;
        }

        // --- Downgrade Buttons (Row 3: 27-35) ---
        if (raw >= 27 && raw < 36) {
            ItemStack spellItem = event.getInventory().getItem(raw - 18);
            if (spellItem == null || !spellItem.hasItemMeta()) {
                player.sendMessage(ChatColor.RED + "Debug: spellItem is null or has no meta at slot " + (raw - 18));
                return;
            }
            String abilityName = parseAbilityName(spellItem);
            if (abilityName == null) {
                player.sendMessage(ChatColor.RED + "Debug: parseAbilityName returned null");
                return;
            }

            downgradeSpell(player, abilityName);
            refreshAll(player, event);
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String parseAbilityName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return null;
        String rawName = ChatColor.stripColor(meta.getDisplayName());
        if (rawName == null || rawName.isBlank()) return null;
        rawName = rawName.replace("▶", "").replaceAll("[^a-zA-Z0-9 ]", "").trim();
        return rawName;
    }

    private void bindAbility(Player player, int hotbarSlot, String abilityName) {
        UUID uuid = player.getUniqueId();
        Spellbreak plugin = Spellbreak.getInstance();

        int activeIdx = plugin.getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String[] bindings = slot.getBindings();

        // Remove this ability from any other slot it was already bound to
        for (int i = 0; i < 9; i++) {
            if (abilityName.equalsIgnoreCase(bindings[i])) bindings[i] = null;
        }
        bindings[hotbarSlot] = abilityName;

        plugin.getPlayerDataManager().saveData(uuid);
        player.sendMessage(ChatColor.GREEN + "✦ " + ChatColor.RESET + "Bound "
                + CharacterSelectGUI.getClassColor(slot.getClassName()) + ChatColor.BOLD + abilityName
                + ChatColor.RESET + ChatColor.GRAY + " to hotbar slot " + (hotbarSlot + 1) + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
    }

    private void upgradeSpell(Player player, String abilityName) {
        Spellbreak plugin = Spellbreak.getInstance();
        UUID uuid = player.getUniqueId();
        int activeIdx = plugin.getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String cls = slot.getClassName();
        SpellLevel sl = plugin.getLevelManager().getSpellLevel(uuid, cls, abilityName);
        if (sl.getLevel() >= 5) {
            player.sendMessage(ChatColor.RED + "⚠ This spell is already at Max Level (5).");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }

        PlayerLevel pl = plugin.getLevelManager().getPlayerLevel(uuid, cls);
        int totalSpellLevels = 0;
        for (String sp : plugin.getSpellClassManager().getClassAbilities(cls)) {
            totalSpellLevels += plugin.getLevelManager().getSpellLevel(uuid, cls, sp).getLevel();
        }

        int maxSpellLevels = pl.getLevel() - 1 + plugin.getSpellClassManager().getClassAbilities(cls).size();
        if (totalSpellLevels >= maxSpellLevels) {
            player.sendMessage(ChatColor.RED + "⚠ Not enough Skill Points! Level up your character to earn more.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }

        sl.setLevel(sl.getLevel() + 1);
        plugin.getLevelManager().saveSpellLevels(uuid);
        player.sendMessage(ChatColor.AQUA + "✦ " + ChatColor.RESET + "Upgraded " 
                + CharacterSelectGUI.getClassColor(cls) + ChatColor.BOLD + abilityName 
                + ChatColor.RESET + ChatColor.AQUA + " to Level " + sl.getLevel() + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    private void downgradeSpell(Player player, String abilityName) {
        Spellbreak plugin = Spellbreak.getInstance();
        UUID uuid = player.getUniqueId();
        int activeIdx = plugin.getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String cls = slot.getClassName();
        SpellLevel sl = plugin.getLevelManager().getSpellLevel(uuid, cls, abilityName);
        
        if (sl.getLevel() <= 1) {
            player.sendMessage(ChatColor.RED + "⚠ Spell is already at minimum Level (1).");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }

        sl.setLevel(sl.getLevel() - 1);
        plugin.getLevelManager().saveSpellLevels(uuid);
        player.sendMessage(ChatColor.GRAY + "✦ Downgraded " + ChatColor.BOLD + abilityName 
                + ChatColor.RESET + ChatColor.GRAY + " to Level " + sl.getLevel() + ". (1 Skill Point Refunded)");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
    }

    private void clearBinding(Player player, int hotbarSlot) {
        UUID uuid = player.getUniqueId();
        Spellbreak plugin = Spellbreak.getInstance();

        int activeIdx = plugin.getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String[] bindings = slot.getBindings();
        String old = bindings[hotbarSlot];
        bindings[hotbarSlot] = null;
        plugin.getPlayerDataManager().saveData(uuid);

        if (old != null && !old.equalsIgnoreCase("null")) {
            player.sendMessage(ChatColor.GRAY + "Cleared binding from hotbar slot " + (hotbarSlot + 1) + ".");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 1.2f);
    }

    /**
     * Visually highlights the currently selected binding slot by putting a blue pane there
     * and restoring all others to their normal colour.
     */
    private void highlightSelectedSlot(Player player, int selected, InventoryClickEvent event) {
        UUID uuid = player.getUniqueId();
        int activeIdx = Spellbreak.getInstance().getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = Spellbreak.getInstance().getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String[] bindings = slot.getBindings();
        for (int i = 0; i < 9; i++) {
            ItemStack item = AbilityBindGUI.makeBindingSlotItem(i, bindings[i]);
            if (i == selected) {
                // Override to blue to indicate selection
                ItemMeta meta = item.getItemMeta();
                String base = meta.getDisplayName();
                meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "▶ " + ChatColor.RESET + base);
                item.setType(org.bukkit.Material.CYAN_STAINED_GLASS_PANE);
                item.setItemMeta(meta);
            }
            event.getInventory().setItem(AbilityBindGUI.BIND_SLOT_START + i, item);
        }
    }

    /**
     * Refreshes binding row after a change, and also refreshes ability items
     * to update their "Bound to slot X" lore.
     */
    private void refreshAll(Player player, InventoryClickEvent event) {
        UUID uuid = player.getUniqueId();
        Spellbreak plugin = Spellbreak.getInstance();
        int activeIdx = plugin.getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String cls = slot.getClassName();
        String[] bindings = slot.getBindings();

        // Refresh binding row
        for (int i = 0; i < 9; i++) {
            event.getInventory().setItem(AbilityBindGUI.BIND_SLOT_START + i,
                    AbilityBindGUI.makeBindingSlotItem(i, bindings[i]));
        }

        // Refresh ability items (only row 1 needs update)
        List<String> abilityNames = plugin.getSpellClassManager().getClassAbilities(cls);
        for (int i = 0; i < abilityNames.size(); i++) {
            if (i >= 9) break;
            String abilityName = abilityNames.get(i);
            int invSlot = 9 + i;
            event.getInventory().setItem(invSlot, AbilityBindGUI.makeAbilityItem(player, cls, abilityName, slot));
        }
    }

    private void refreshBindRow(Player player, InventoryClickEvent event) {
        UUID uuid = player.getUniqueId();
        int activeIdx = Spellbreak.getInstance().getPlayerDataManager().getActiveSlotIndex(uuid);
        if (activeIdx < 0) return;
        CharacterSlot slot = Spellbreak.getInstance().getPlayerDataManager().getCharacterSlot(uuid, activeIdx);
        if (slot == null) return;

        String[] bindings = slot.getBindings();
        for (int i = 0; i < 9; i++) {
            event.getInventory().setItem(AbilityBindGUI.BIND_SLOT_START + i,
                    AbilityBindGUI.makeBindingSlotItem(i, bindings[i]));
        }
    }
}

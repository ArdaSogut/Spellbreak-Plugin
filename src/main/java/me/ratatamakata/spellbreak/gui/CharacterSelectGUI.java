package me.ratatamakata.spellbreak.gui;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import me.ratatamakata.spellbreak.player.CharacterSlot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The main character selection GUI.
 *
 * Layout (54-slot chest, rows 0-5):
 *   Row 0  – decorative glass pane border (top)
 *   Row 1  – 7 class headers (colored terracotta)
 *   Row 2  – Slot A icons for each class  (slot 0,2,4,6,8,10,12)
 *   Row 3  – Slot B icons for each class  (slot 1,3,5,7,9,11,13)
 *   Row 4  – decorative / empty
 *   Row 5  – bottom border + CLOSE button in centre
 *
 * Inventory columns used: 1 2 3 4 5 6 7  (indices 1-7 in each row)
 */
public class CharacterSelectGUI {

    public static final String TITLE = ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "Character Select" + ChatColor.DARK_PURPLE + " ✦";

    // Ordered list of the 7 classes – must match config.yml order
    private static final String[] CLASS_ORDER = {
            "Necromancer", "Archdruid", "Lightbringer", "Mindshaper",
            "Elementalist", "Runesmith", "Starcaller"
    };

    /**
     * Returns the terracotta Material that represents a given class.
     */
    public static Material getClassTerracotta(String className) {
        return switch (className.toLowerCase()) {
            case "necromancer"  -> Material.PURPLE_TERRACOTTA;
            case "archdruid"    -> Material.GREEN_TERRACOTTA;
            case "lightbringer" -> Material.YELLOW_TERRACOTTA;
            case "mindshaper"   -> Material.BLUE_TERRACOTTA;
            case "elementalist" -> Material.CYAN_TERRACOTTA;
            case "runesmith"    -> Material.ORANGE_TERRACOTTA;
            case "starcaller"   -> Material.LIGHT_BLUE_TERRACOTTA;
            default             -> Material.TERRACOTTA;
        };
    }

    /**
     * Returns the ChatColor associated with a given class for lore/title use.
     */
    public static ChatColor getClassColor(String className) {
        return switch (className.toLowerCase()) {
            case "necromancer"  -> ChatColor.DARK_PURPLE;
            case "archdruid"    -> ChatColor.GREEN;
            case "lightbringer" -> ChatColor.YELLOW;
            case "mindshaper"   -> ChatColor.BLUE;
            case "elementalist" -> ChatColor.AQUA;
            case "runesmith"    -> ChatColor.GOLD;
            case "starcaller"   -> ChatColor.LIGHT_PURPLE;
            default             -> ChatColor.GRAY;
        };
    }

    /**
     * Returns a short flavour description for each class.
     */
    public static String getClassDescription(String className) {
        return switch (className.toLowerCase()) {
            case "necromancer"  -> "Master of death, plague, and shadow.";
            case "archdruid"    -> "Guardian of nature and wild fury.";
            case "lightbringer" -> "Holy champion of light and purity.";
            case "mindshaper"   -> "Illusionist who bends perception.";
            case "elementalist" -> "Wielder of fire, ice, earth & storm.";
            case "runesmith"    -> "Engineer of arcane runes and turrets.";
            case "starcaller"   -> "Cosmic mage drawing power from stars.";
            default             -> "Unknown class.";
        };
    }

    /**
     * Opens the character select GUI for a player.
     */
    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // --- Fill decorative borders ---
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);           // row 0 (top)
            inv.setItem(45 + i, border);      // row 5 (bottom)
        }
        // Close button in row 5 centre
        inv.setItem(49, makeCloseButton());

        Spellbreak plugin = Spellbreak.getInstance();

        // --- Place class columns (cols 1-7 = inventory positions 1-7 in each row) ---
        for (int ci = 0; ci < CLASS_ORDER.length; ci++) {
            String cls = CLASS_ORDER[ci];
            int col = ci + 1; // columns 1..7

            // Row 1: class label header
            inv.setItem(9 + col, makeClassHeader(cls));

            // Rows 2 & 3: the two character slots for this class
            for (int s = 0; s < 2; s++) {
                int slotIndex = ci * 2 + s;   // 0..13
                CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(player.getUniqueId(), slotIndex);
                boolean isActive = plugin.getPlayerDataManager().getActiveSlotIndex(player.getUniqueId()) == slotIndex;
                int row = 2 + s;              // row 2 for s=0, row 3 for s=1
                inv.setItem(row * 9 + col, makeSlotItem(slot, cls, slotIndex, isActive, player));
            }
        }

        // Row 4 – leave empty (acts as spacing)

        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack makeBorder() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ Close");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to close this menu.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeClassHeader(String cls) {
        ItemStack item = new ItemStack(getClassTerracotta(cls));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getClassColor(cls) + "" + ChatColor.BOLD + cls);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + getClassDescription(cls));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack makeSlotItem(CharacterSlot slot, String cls, int slotIndex, boolean isActive, Player player) {
        if (slot == null || slot.isEmpty()) {
            // Empty slot – lime glass pane
            ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "[Empty Slot]");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to create a " + getClassColor(cls) + cls + ChatColor.GRAY + " character.");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Slot " + (slotIndex + 1));
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }

        // Filled slot
        Material mat = getClassTerracotta(cls);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        ChatColor cc = getClassColor(cls);
        String prefix = isActive ? ChatColor.GOLD + "▶ " : "";
        meta.setDisplayName(prefix + cc + "" + ChatColor.BOLD + cls + ChatColor.RESET + ChatColor.GRAY + " #" + ((slotIndex % 2) + 1));

        List<String> lore = new ArrayList<>();
        if (isActive) {
            lore.add(ChatColor.GOLD + "★ Currently Active");
        }

        // Level info
        PlayerLevel lvl = Spellbreak.getInstance().getLevelManager()
                .getPlayerLevel(player.getUniqueId(), cls);
        lore.add(ChatColor.GRAY + "Level: " + cc + lvl.getLevel());
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE + lvl.getExperienceProgress()
                + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + lvl.getExperienceForNextLevel());
        lore.add(ChatColor.GRAY + "Health: " + ChatColor.RED + lvl.getMaxHealth()
                + ChatColor.GRAY + "  Mana: " + ChatColor.AQUA + lvl.getMaxMana());

        // Bindings summary
        lore.add("");
        lore.add(ChatColor.GRAY + "Bindings:");
        String[] bindings = slot.getBindings();
        boolean anyBound = false;
        for (int i = 0; i < 9; i++) {
            if (bindings[i] != null && !bindings[i].equalsIgnoreCase("null")) {
                lore.add(ChatColor.DARK_GRAY + "  [" + (i + 1) + "] " + ChatColor.WHITE + bindings[i]);
                anyBound = true;
            }
        }
        if (!anyBound) lore.add(ChatColor.DARK_GRAY + "  None bound yet.");

        lore.add("");
        if (!isActive) {
            lore.add(ChatColor.GREEN + "Left-click to switch to this character.");
        }
        lore.add(ChatColor.RED + "Shift + Right-click to delete.");

        meta.setLore(lore);

        if (isActive) {
            // Subtle glow with hidden enchant name
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Slot-position helpers used by the listener
    // -------------------------------------------------------------------------

    /**
     * Given a raw inventory slot (0-53), returns the character slot index (0-13),
     * or -1 if the clicked slot is not a character slot.
     */
    public static int getCharacterSlotIndex(int rawSlot) {
        // Character slots are in rows 2 & 3, columns 1-7
        // Row 2: inventory slots 19-25 (9*2 + 1 .. 9*2 + 7)
        // Row 3: inventory slots 28-34 (9*3 + 1 .. 9*3 + 7)
        int row = rawSlot / 9;
        int col = rawSlot % 9;
        if (col < 1 || col > 7) return -1;
        if (row == 2) return (col - 1) * 2;     // slot A for class (col-1)
        if (row == 3) return (col - 1) * 2 + 1; // slot B for class (col-1)
        return -1;
    }

    /**
     * Given a character slot index (0-13), returns the class name it belongs to.
     */
    public static String getClassForSlot(int slotIndex) {
        return CLASS_ORDER[slotIndex / 2];
    }
}

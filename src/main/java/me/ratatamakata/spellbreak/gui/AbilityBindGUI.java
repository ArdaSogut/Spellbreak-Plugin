package me.ratatamakata.spellbreak.gui;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import me.ratatamakata.spellbreak.level.SpellLevel;
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
 * Ability Binding GUI  –  opened when a player left-clicks their ACTIVE character slot.
 *
 * Layout (54-slot chest):
 *   Row 0  – class info header spanning centre + border panes
 *   Row 1  – up to 6 ability items (columns 1-6, with lore = spell level + description)
 *   Row 2  – (overflow abilities if class has more than 6 – columns 0-8)
 *   Row 3  – separator
 *   Row 4  – 9 hotbar-binding slots (columns 0-8)
 *   Row 5  – bottom border + BACK button (slot 49)
 *
 * Click behaviour (handled by AbilityBindListener):
 *   Left-click an ability → binds it to the slot currently highlighted in row 4
 *   Left-click a binding slot → highlights it (select target slot)
 *   Right-click a binding slot → clears that binding
 *   Click slot 49 (BACK) → return to CharacterSelectGUI
 */
public class AbilityBindGUI {

    /** Prefix used to recognise this GUI by title. */
    public static final String TITLE_PREFIX = ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "Manage: ";

    /**
     * Returns the full title for the given class, used for inventory matching.
     */
    public static String titleFor(String cls) {
        return TITLE_PREFIX + CharacterSelectGUI.getClassColor(cls) + cls;
    }

    // Inventory slot layout helpers
    /** Ability item positions: rows 1-2, all 9 columns. */
    private static final int ABILITY_ROW_1_START = 9;   // row 1
    private static final int ABILITY_ROW_2_START = 18;  // row 2 (overflow)
    /** Binding slot positions: row 4 (cols 0-8 = slots 36-44). */
    public static final int BIND_SLOT_START = 36;
    /** Back / separator / indicator positions. */
    public static final int BACK_SLOT = 49;

    // -------------------------------------------------------------------------

    /** Opens the ability-bind GUI for the player's currently active slot. */
    public static void open(Player player) {
        Spellbreak plugin = Spellbreak.getInstance();

        int activeIdx = plugin.getPlayerDataManager().getActiveSlotIndex(player.getUniqueId());
        if (activeIdx < 0) return;

        CharacterSlot slot = plugin.getPlayerDataManager().getCharacterSlot(player.getUniqueId(), activeIdx);
        if (slot == null || slot.isEmpty()) return;

        String cls = slot.getClassName();
        Inventory inv = Bukkit.createInventory(null, 54, titleFor(cls));

        // --- Borders ---
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);       // row 0
            inv.setItem(45 + i, border);  // row 5
        }
        // Row 3 separator
        for (int i = 27; i < 36; i++) inv.setItem(i, border);

        // --- Back button ---
        inv.setItem(BACK_SLOT, makeBackButton());

        // --- Class info item (row 0, slot 4) ---
        inv.setItem(4, makeClassInfoItem(player, cls, activeIdx));

        // --- Ability items ---
        List<String> abilityNames = plugin.getSpellClassManager().getClassAbilities(cls);
        for (int i = 0; i < abilityNames.size(); i++) {
            String abilityName = abilityNames.get(i);
            int invSlot = (i < 9) ? ABILITY_ROW_1_START + i : ABILITY_ROW_2_START + (i - 9);
            if (invSlot >= 27) break; // safety – don't run into separator row
            inv.setItem(invSlot, makeAbilityItem(player, cls, abilityName, slot));
        }

        // --- Binding slots (row 4) ---
        String[] bindings = slot.getBindings();
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            inv.setItem(BIND_SLOT_START + hotbar, makeBindingSlotItem(hotbar, bindings[hotbar]));
        }

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

    private static ItemStack makeBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ Back");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Return to character select.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeClassInfoItem(Player player, String cls, int slotIndex) {
        ItemStack item = new ItemStack(CharacterSelectGUI.getClassTerracotta(cls));
        ItemMeta meta = item.getItemMeta();

        ChatColor cc = CharacterSelectGUI.getClassColor(cls);
        meta.setDisplayName(cc + "" + ChatColor.BOLD + cls
                + ChatColor.RESET + ChatColor.DARK_GRAY + " (Slot " + ((slotIndex % 2) + 1) + ")");

        PlayerLevel pl = Spellbreak.getInstance().getLevelManager()
                .getPlayerLevel(player.getUniqueId(), cls);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Level: " + cc + pl.getLevel());
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE
                + pl.getExperienceProgress() + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE
                + pl.getExperienceForNextLevel());
        lore.add(ChatColor.GRAY + "Health: " + ChatColor.RED + pl.getMaxHealth()
                + ChatColor.GRAY + "  Mana: " + ChatColor.AQUA + pl.getMaxMana());
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + ChatColor.ITALIC + CharacterSelectGUI.getClassDescription(cls));
        meta.setLore(lore);

        // Glow
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds an item representing a single ability.
     * Shows: spell level, spell XP, description, and whether it's bound (and to which slot).
     */
    public static ItemStack makeAbilityItem(Player player, String cls, String abilityName, CharacterSlot slot) {
        // Find the matching Ability instance
        Ability ability = Spellbreak.getInstance().getAbilityManager().getAbilityByName(abilityName.toLowerCase());

        Material mat = abilityMaterial(abilityName);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(player.getUniqueId(), cls, abilityName);

        ChatColor cc = CharacterSelectGUI.getClassColor(cls);
        meta.setDisplayName(cc + "" + ChatColor.BOLD + abilityName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Spell Level: " + cc + sl.getLevel());
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE + sl.getExperience()
                + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + sl.getExperienceForNextLevel());
        lore.add("");

        // Description from Ability instance
        if (ability != null) {
            String desc = ability.getDescription();
            if (desc != null && !desc.isBlank()) {
                // Word-wrap description at ~40 chars
                for (String line : wrapText(desc, 40)) {
                    lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + line);
                }
                lore.add("");
            }
        }

        // Which hotbar slot(s) has this bound?
        String[] bindings = slot.getBindings();
        boolean bound = false;
        for (int i = 0; i < 9; i++) {
            if (abilityName.equalsIgnoreCase(bindings[i])) {
                lore.add(ChatColor.GREEN + "✔ Bound to hotbar slot " + (i + 1));
                bound = true;
            }
        }
        if (!bound) lore.add(ChatColor.DARK_GRAY + "Not currently bound.");

        lore.add("");
        lore.add(ChatColor.YELLOW + "Left-click a binding slot below,");
        lore.add(ChatColor.YELLOW + "then click here to assign.");

        meta.setLore(lore);

        if (bound) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /** Builds a hotbar-binding slot item showing slot number and current assignment. */
    public static ItemStack makeBindingSlotItem(int hotbarSlot, String currentAbility) {
        boolean empty = currentAbility == null || currentAbility.equalsIgnoreCase("null") || currentAbility.isBlank();
        ItemStack item = new ItemStack(empty ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + "Slot " + (hotbarSlot + 1));
        List<String> lore = new ArrayList<>();
        if (empty) {
            lore.add(ChatColor.DARK_GRAY + "[ Empty ]");
        } else {
            lore.add(ChatColor.GREEN + currentAbility);
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Left-click → select this slot");
        lore.add(ChatColor.RED + "Right-click → clear binding");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Maps ability name to a representative material. Very rough heuristic. */
    private static Material abilityMaterial(String name) {
        String n = name.toLowerCase();
        if (n.contains("bone") || n.contains("undying")) return Material.BONE;
        if (n.contains("plague") || n.contains("swarm") || n.contains("mist")) return Material.POISONOUS_POTATO;
        if (n.contains("clone") || n.contains("shadow") || n.contains("neural")) return Material.ENDER_EYE;
        if (n.contains("dreamwalk") || n.contains("phantom") || n.contains("echo")) return Material.AMETHYST_SHARD;
        if (n.contains("iron") || n.contains("quill") || n.contains("canopy") || n.contains("nature") || n.contains("spore")) return Material.OAK_LEAVES;
        if (n.contains("ambush") || n.contains("tidepool") || n.contains("gale")) return Material.RABBIT_HIDE;
        if (n.contains("mist") || n.contains("dash")) return Material.FEATHER;
        if (n.contains("light") || n.contains("beacon") || n.contains("radiant") || n.contains("consecration") || n.contains("purify")) return Material.GLOWSTONE_DUST;
        if (n.contains("light_cage") || n.contains("cage")) return Material.LANTERN;
        if (n.contains("ember") || n.contains("earth") || n.contains("thunder") || n.contains("avalanche")) return Material.BLAZE_POWDER;
        if (n.contains("bladespin") || n.contains("blade")) return Material.IRON_SWORD;
        if (n.contains("rune") || n.contains("warding") || n.contains("sigil")) return Material.CHISELED_STONE_BRICKS;
        if (n.contains("turret")) return Material.DISPENSER;
        if (n.contains("meteor") || n.contains("star") || n.contains("solar") || n.contains("photon") || n.contains("quantum") || n.contains("blackhole")) return Material.NETHER_STAR;
        return Material.BOOK;
    }

    /** Wraps {@code text} into lines of at most {@code maxLen} chars. */
    private static List<String> wrapText(String text, int maxLen) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxLen && current.length() > 0) {
                lines.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(word).append(" ");
        }
        if (current.length() > 0) lines.add(current.toString().trim());
        return lines;
    }
}

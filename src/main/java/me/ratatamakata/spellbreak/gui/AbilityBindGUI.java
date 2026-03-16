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
 *   Row 1  – up to 9 ability items
 *   Row 2  – Upgrade buttons (+ Green Wool) directly below abilities
 *   Row 3  – Downgrade buttons (- Red Wool) directly below upgrade buttons
 *   Row 4  – 9 hotbar-binding slots (columns 0-8)
 *   Row 5  – bottom border + BACK button (slot 49)
 *
 * Click behaviour (handled by AbilityBindListener):
 *   Left-click an ability → binds it to the slot currently highlighted in row 4
 *   Left-click a binding slot → highlights it (select target slot)
 *   Right-click a binding slot → clears that binding
 *   Click Green Wool → Upgrade Spell Level
 *   Click Red Wool → Downgrade Spell Level
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
    /** Ability item positions: row 1. */
    private static final int ABILITY_ROW_START = 9;   // row 1
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
        // --- Ability items and Upgrade/Downgrade Buttons ---
        List<String> abilityNames = plugin.getSpellClassManager().getClassAbilities(cls);
        for (int i = 0; i < abilityNames.size(); i++) {
            if (i >= 9) break; // Maximum 9 abilities supported in this layout
            String abilityName = abilityNames.get(i);
            int invSlot = ABILITY_ROW_START + i;
            
            // The spell icon itself
            inv.setItem(invSlot, makeAbilityItem(player, cls, abilityName, slot));
            
            // The UPGRADE button (Green Wool) directly below it
            inv.setItem(invSlot + 9, makeUpgradeButton(abilityName));
            
            // The DOWNGRADE button (Red Wool) directly below the upgrade button
            inv.setItem(invSlot + 18, makeDowngradeButton(abilityName));
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

    private static ItemStack makeUpgradeButton(String abilityName) {
        ItemStack item = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Increase Level");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Upgrade " + ChatColor.WHITE + abilityName);
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to spend 1 Skill Point");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeDowngradeButton(String abilityName) {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Decrease Level");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Downgrade " + ChatColor.WHITE + abilityName);
        lore.add("");
        lore.add(ChatColor.RED + "Click to refund 1 Skill Point");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeClassInfoItem(Player player, String cls, int slotIndex) {
        ItemStack item = new ItemStack(CharacterSelectGUI.getClassTerracotta(cls));
        ItemMeta meta = item.getItemMeta();

        ChatColor cc = CharacterSelectGUI.getClassColor(cls);
        meta.setDisplayName(cc + "" + ChatColor.BOLD + cls
                + ChatColor.RESET + "" + ChatColor.DARK_GRAY + " (Slot " + ((slotIndex % 2) + 1) + ")");

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
        lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + CharacterSelectGUI.getClassDescription(cls));
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
                    lore.add("" + ChatColor.DARK_GRAY + ChatColor.ITALIC + line);
                }
                lore.add("");
            }
            
            String pvpMsg = getPvPStrategy(abilityName);
            if (pvpMsg != null && !pvpMsg.isEmpty()) {
                for (String line : wrapText("PvP: " + pvpMsg, 40)) {
                    if (line.startsWith("PvP:")) {
                        lore.add(ChatColor.RED + "" + ChatColor.BOLD + "PvP: " + ChatColor.GRAY + line.substring(4).trim());
                    } else {
                        lore.add(ChatColor.GRAY + line);
                    }
                }
                lore.add("");
            }
            
            String usageMsg = getUsageHint(abilityName);
            if (usageMsg != null && !usageMsg.isEmpty()) {
                for (String line : wrapText("Usage: " + usageMsg, 40)) {
                    if (line.startsWith("Usage:")) {
                        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Usage: " + ChatColor.GRAY + line.substring(6).trim());
                    } else {
                        lore.add(ChatColor.GRAY + line);
                    }
                }
                lore.add("");
            }
        }

        // Which hotbar slot(s) has this bound?
        String[] bindings = slot.getBindings();
        boolean bound = false;
        for (int i = 0; i < 9; i++) {
            if (bindings[i] != null && abilityName.equalsIgnoreCase(bindings[i])) {
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

    private static String getPvPStrategy(String abilityName) {
        if (abilityName == null) return "Versatile ability for offense and defense.";
        switch (abilityName.toLowerCase()) {
            case "ambushslash": return "Single-target gap closer. Great for assassinating squishy targets. Use from stealth for surprise attacks or to finish off fleeing enemies.";
            case "avalanche": return "Engage tool, ramps up speed and knockback. Use to disrupt enemy positioning and combos.";
            case "beaconofclarity": return "Healing tool, heals allies more than enemies. Great for clutch saves or sustaining through poke.";
            case "blackhole": return "Crowd control, can be anchored early for less radius. Use to peel divers or trap fleeing targets. Great for disrupting enemy combos or controlling space.";
            case "bladespin": return "Turns you into a beyblade, mobility and damage in one, has a one time dash. Great for sticking to targets or escaping bad fights.";
            case "bonechoir": return "Summons three skeleton choir members, one deals heavy damage, one applies effects, and one heals. Great for clutch heals or sustainable damage in duels.";
            case "canopycrash": return "Used mid air to slam down, dealing damage and knocking up enemies in the area. Great for disrupting enemy combos or engaging on grouped targets.";
            case "cloneswarm": return "Summons clones of you that rush forward and explode on contact, dealing damage. Great for overwhelming single targets or zoning areas.";
            case "consecration": return "Levitates the enemy and slams them down, dealing damage. Great for interrupting channeled abilities or punishing melee divers.";
            case "dreamwalker": return "Allows free flight and turns you invulnerable, going through enemies deal damage and apply effects. Great for repositioning, dodging burst, or escaping bad fights.";
            case "earthshards": return "Creates multiple earth shards that can be thrown for damage. Great for poking from a distance or controlling space.";
            case "echopulse": return "Creates a pulse and a delayed pulse, both dealing damage. Great for zoning, punishing aggression, or controlling objectives.";
            case "emberstorm": return "Creates a fire storm around you, dealing damage and burning enemies. Great for peeling melee divers or disrupting enemy formations.";
            case "galevortex": return "Sends an air vortex that damages and pulls enemies towards you. Great for disrupting enemy positioning, pulling enemies out of cover, or setting up combos.";
            case "ıronwoodshell": return "Creates an armor that negates damage. Great for clutch survivals, baiting enemy burst, or tanking through dangerous situations.";
            case "lifedrain": return "Deals damage and heals proportionally. Great for sustaining through poke, clutch heals in duels, or finishing off low-health targets.";
            case "lightcage": return "Single-target stun and damage. Great for locking down key targets, interrupting channeled abilities, or setting up combos.";
            case "meteorlash": return "Long ranged AoE damage ability. Great for finishing off fleeing targets, punishing grouped enemies, or controlling space.";
            case "mistdash": return "Turns you into an intangible mist, granting invulnerability and damaging targets you pass through. Great for aggressive engages, clutch escapes, or repositioning through enemies.";
            case "naturestep": return "Stacked dash that deals minimal damage but grants high mobility. Great for repositioning, dodging skill shots, or quickly closing gaps.";
            case "neuraltrap": return "Anti-mobility; forces mobile targets to stop moving or take damage proportional to their movement. Great for punishing dashes, blinks, or flying targets.";
            case "phantomecho": return "Short range dash that leaves a clone behind, allowing you to return to the clone's location. Great for aggressive engages, clutch escapes, or repositioning through enemies.";
            case "photonbeam": return "Long-range charged sniper, deals more damage the longer you charge. Great for punishing stationary targets, finishing off low-health enemies, or controlling space.";
            case "plaguecloud": return "Zoning tool, creates a cloud that slows and damages enemies over time. Great for controlling objectives, peeling melee divers, or punishing aggression.";
            case "purifyingprism": return "Creates a holy turret that shoots beams at nearby enemies, dealing damage and applying effects. Great for controlling space, peeling divers, or providing utility in team fights.";
            case "quantumanchor": return "Dive setup tool, allows you to set an anchor point and return to it. Great for aggressive engages, clutch escapes, or repositioning through enemies.";
            case "quillflaresurge": return "Launches into the air and spits out poison quills that home in on targets. Great for poking from a distance, controlling space, or finishing off fleeing enemies.";
            case "radiantdash": return "Mobility ability that cleanses bad effects. Great for aggressive engages, clutch escapes, or repositioning through enemies while removing debuffs.";
            case "radiantphase": return "Invulnerability tool, grants invulnerability and allows you to pass through enemies at the cost of not being able to do damage. Great for clutch survivals, baiting enemy burst, or tanking through dangerous situations.";
            case "runecarver": return "Poke damage, throws out returning runes that deal damage. Great for poking from a distance, controlling space, or finishing off fleeing enemies.";
            case "runicjumppad": return "Mobile launch pads, creates a jump pad at the target location that launches anyone who steps on it. Great for disrupting enemy positioning, providing mobility for your team, or setting up combos.";
            case "runiccannon": return "Objective control summon, creates a turret that shoots nearby enemies. Great for controlling space, providing utility in team fights, or defending objectives.";
            case "shadowcreatures": return "Creates automatic shadow minions that attack nearby enemies. Great for peeling divers, providing utility in team fights, or overwhelming single targets.";
            case "solarlance": return "Fast engage tool that damages and burns enemies it touches, stops at first enemy hit. Great for punishing aggression, finishing off fleeing targets, or controlling space.";
            case "sporeblossom": return "Disengange/peel tool, creates a spore cloud that deals damage and slows enemies while launching you backwards. Great for controlling objectives, peeling melee divers, or punishing aggression.";
            case "starphase": return "Mobility and poke; grants flight and fires a projectile on landing. Great for aggressive engages, clutch escapes, or repositioning through enemies while dealing damage.";
            case "swarmsigil": return "Summons a swarm that stick to the nearest enemy, exploding after a short while. Great for peeling divers, punishing grouped enemies, or controlling space.";
            case "tentacles": return "Single-target stun and damage. Great for locking down key targets, interrupting channeled abilities, or setting up combos.";
            case "thunderslam": return "AoE engage and repositioning tool, slams down and sends out a shockwave that damages and knocks up enemies. Great for disrupting enemy combos, engaging on grouped targets, or peeling melee divers.";
            case "tidepool": return "Area control, creates a pool that damages those who try to pass through it. Great for controlling objectives, peeling melee divers, or providing utility in team fights.";
            case "undyingpact": return "Clutch survival; prevents death for both you and the enemy. Great for clutch survivals, baiting enemy burst, or turning the tides of a fight.";
            case "wardingsigil": return "Defensive mitigation that blocks hits regardless of damage. Great for clutch survivals, baiting enemy burst, or tanking through dangerous situations.";
            default: return "Useful utility in both 1v1s and team skirmishes.";
        }
    }

    private static String getUsageHint(String abilityName) {
        if (abilityName == null) return "Left-click or Right-click to cast.";
        switch (abilityName.toLowerCase()) {
            case "ambushslash": return "Left-click to launch a projectile, left-click again to teleport to target hit.";
            case "avalanche": return "Left-click to become an avalanche.";
            case "beaconofclarity": return "Hold shift and left-click to create a healing beacon at your location.";
            case "blackhole": return "Hold shift and left-click to launch, do it again to anchor the black hole at its current location.";
            case "bladespin": return "Press shift to turn into a spinning blade, left-click to dash forward.";
            case "bonechoir": return "Hold shift and left-click to summon skeletons.";
            case "canopycrash": return "Press shift mid-air to slam down.";
            case "cloneswarm": return "Press shift to summon clones that rush forward.";
            case "consecration": return "Press shift to levitate the target.";
            case "dreamwalker": return "Hold shift and left-click to enter dreamwalker form, do it again to exit.";
            case "earthshards": return "Hold shift to gather shards, left-click to launch each.";
            case "echopulse": return "Left-click to launch the pulse.";
            case "emberstorm": return "Hold shift to charge and release to unleash the storm around you.";
            case "galevortex": return "Left-click to launch a vortex that pulls enemies towards you.";
            case "ıronwoodshell": return "Hold shift and left-click to create an ironwood shell around you.";
            case "lifedrain": return "Hold shift to drain life from enemies in front of you.";
            case "lightcage": return "Left-click to launch a light cage that stuns and damages the first enemy hit.";
            case "meteorlash": return "Left-click once for the selection projectile, left-click again to strike the targeted area.";
            case "mistdash": return "Left-click to dash forward in mist form, damaging enemies you pass through.";
            case "naturestep": return "Left-click to dash forward, can be used multiple times in quick succession.";
            case "neuraltrap": return "Press shift to use, damaging enemies that move.";
            case "phantomecho": return "Left-click to dash leaving a clone behind, press shift to return.";
            case "photonbeam": return "Hold shift to charge, will auto fire once fully charged or when released.";
            case "plaguecloud": return "Press shift to create a cloud at your location that damages and slows enemies.";
            case "purifyingprism": return "Press shift to summon a turret that shoots nearby enemies with purifying beams.";
            case "quantumanchor": return "Press shift to set an anchor point. Return to the point after taking enough damage or when timer expires.";
            case "quillflaresurge": return "Press shift to launch into the air, releasing quills that home in on targets.";
            case "radiantdash": return "Left-click to dash forward, cleansing bad effects on cast.";
            case "radiantphase": return "Hold shift and left-click to enter radiant phase.";
            case "runecarver": return "Left-click to throw out a rune that returns to you after a short delay.";
            case "runicjumppad": return "Left-click to create a jump pad at the targeted location.";
            case "runiccannon": return "Hold shift and left-click to summon a turret that shoots nearby enemies.";
            case "shadowcreatures": return "Hold shift to charge, releasing to summon shadow minions that attack nearby enemies.";
            case "solarlance": return "Left-click to launch a solar lance. The lance will stop at the first enemy hit, dealing damage and applying burn.";
            case "sporeblossom": return "Hold shift and left-click to create a spore blossom at your location, launching you backwards.";
            case "starphase": return "Left-click to enter starphase, left-click again to fire a projectile and exit.";
            case "swarmsigil": return "Hold shift and left-click to summon a swarm that seeks out the nearest enemy.";
            case "tentacles": return "Hold shift and left-click to launch a controllable tentacle projectile.";
            case "thunderslam": return "Press shift to rise up, left-click to slam down, damaging and knocking up enemies.";
            case "tidepool": return "Press shift to create a tidepool at your location that damages enemies that pass through it.";
            case "undyingpact": return "Hold shift and left-click to activate, preventing death for both you and the enemy for a short duration.";
            case "wardingsigil": return "Hold shift to charge, releasing to create a sigil that blocks incoming hits regardless of damage.";
            default: return "";
        }
    }
}

package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.CanopyCrashAbility;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class CanopyCrashListener implements Listener {

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking()) return;

        // Check bound ability first
        int currentSlot = p.getInventory().getHeldItemSlot();
        String boundAbility = Spellbreak.getInstance().getPlayerDataManager()
                .getAbilityAtSlot(p.getUniqueId(), currentSlot);
        if (!"CanopyCrash".equalsIgnoreCase(boundAbility)) return;

        // Get ability instance
        Ability ability = Spellbreak.getInstance().getAbilityManager()
                .getAbilityByName("CanopyCrash");
        if (!(ability instanceof CanopyCrashAbility)) return;
        CanopyCrashAbility canopyCrash = (CanopyCrashAbility) ability;

        // Class requirement
        String playerClass = Spellbreak.getInstance().getPlayerDataManager()
                .getPlayerClass(p.getUniqueId());
        if (!canopyCrash.getRequiredClass().equalsIgnoreCase(playerClass)) return;

        // Height check using improved method
        double heightAboveGround = calculateHeightAboveGround(p.getLocation());

        if (heightAboveGround < 0 || heightAboveGround < canopyCrash.getMinHeight()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
            p.sendMessage(ChatColor.YELLOW + "Not high enough above ground for Canopy Crash! (Need " + canopyCrash.getMinHeight() + " blocks)");
            return;
        }

        // Now check cooldown
        if (Spellbreak.getInstance().getCooldownManager()
                .isOnCooldown(p, canopyCrash.getName())) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.sendMessage(ChatColor.RED + "Ability on cooldown!");
            return;
        }

        // Then check mana
        if (!Spellbreak.getInstance().getManaSystem()
                .consumeMana(p, canopyCrash.getManaCost())) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.sendMessage(ChatColor.RED + "Not enough mana!");
            return;
        }

        // All checks passed - activate
        canopyCrash.activate(p);

        // Set cooldown (no need for double-checking height here since activate() handles it)
        Spellbreak.getInstance().getCooldownManager()
                .setCooldown(p, canopyCrash.getName(), canopyCrash.getCooldown());

        // Success feedback
        p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.5f);
    }

    // Add this helper method to CanopyCrashListener class:
    private double calculateHeightAboveGround(Location location) {
        Block blockBelow = location.getBlock().getRelative(BlockFace.DOWN);
        int depth = 0;
        int maxDepth = 256;
        int worldMinHeight = location.getWorld().getMinHeight(); // Handles negative Y worlds properly

        // Search downward until we find solid ground or hit world bottom
        while (!blockBelow.getType().isSolid() &&
                blockBelow.getY() >= worldMinHeight &&
                depth++ < maxDepth) {
            blockBelow = blockBelow.getRelative(BlockFace.DOWN);
        }

        // If we found solid ground, calculate height
        if (blockBelow.getType().isSolid()) {
            return location.getY() - blockBelow.getY() - 1;
        }

        // If no solid ground found, return -1 to indicate failure
        return -1;
    }
}

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

        // Height check: Calculate height above ground
        Location playerLoc = p.getLocation();
        Block blockBelow = playerLoc.getBlock().getRelative(BlockFace.DOWN);
        int checks = 0; // Limit checks to prevent infinite loops in weird scenarios
        int maxChecks = (int) (playerLoc.getY() - p.getWorld().getMinHeight()); // Max reasonable checks

        while (!blockBelow.getType().isSolid() && blockBelow.getY() > p.getWorld().getMinHeight() && checks < maxChecks) {
            blockBelow = blockBelow.getRelative(BlockFace.DOWN);
            checks++;
        }

        double heightAboveGround = -1;
        if (blockBelow.getType().isSolid()) {
             heightAboveGround = playerLoc.getY() - blockBelow.getY() - 1; // -1 because blockBelow is the solid block, feet are 1 block above its base Y
        }

        // Check if calculated height is sufficient
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

        // Double-check height before setting cooldown, just in case
        // Recalculate height here too for absolute certainty (or trust the initial check)
        Location currentLoc = p.getLocation();
        Block currentBlockBelow = currentLoc.getBlock().getRelative(BlockFace.DOWN);
        int currentChecks = 0;
        int currentMaxChecks = (int) (currentLoc.getY() - p.getWorld().getMinHeight());

        while (!currentBlockBelow.getType().isSolid() && currentBlockBelow.getY() > p.getWorld().getMinHeight() && currentChecks < currentMaxChecks) {
            currentBlockBelow = currentBlockBelow.getRelative(BlockFace.DOWN);
            currentChecks++;
        }

        double currentHeightAboveGround = -1;
         if (currentBlockBelow.getType().isSolid()) {
             currentHeightAboveGround = currentLoc.getY() - currentBlockBelow.getY() - 1;
         }

        // Use the recalculated height for the cooldown check
        if (currentHeightAboveGround >= canopyCrash.getMinHeight()) {
            Spellbreak.getInstance().getCooldownManager()
                .setCooldown(p, canopyCrash.getName(), canopyCrash.getCooldown());
        }

        // Success feedback
        p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.5f);
    }
}
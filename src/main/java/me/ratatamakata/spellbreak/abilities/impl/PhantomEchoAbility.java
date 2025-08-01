
package me.ratatamakata.spellbreak.abilities.impl;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PhantomEchoAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 15;
    private String requiredClass = "mindshaper";
    private double dashDistance = 15.0;
    private int cloneDuration = 70; // ticks
    private int invisDuration = 30; // ticks
    private double returnDamage = 1.0;
    private double returnDamageRadius = 5.0;

    // Track active clones
    private final Map<UUID, CloneData> clones = new HashMap<>();

    // Store player state when making invisible and removing armor/items
    private final Map<UUID, PlayerState> playerStates = new HashMap<>();

    @Override public String getName() { return "PhantomEcho"; }
    @Override public String getDescription() { return "Teleport forward leaving a phantom echo to return to"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) { return false; }
    @Override public boolean isSuccessful() { return false; }

    @Override
    public String getDeathMessage(String victim, String caster, String sub) {
        return "§d%s§5's illusions overwhelmed §d%s§5";
    }

    @Override public void activate(Player player) { /* handled in listener */ }

    public void dash(Player player) {
        UUID uuid = player.getUniqueId();

        if (clones.containsKey(uuid)) {
            return;
        }

        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(uuid, Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()), "PhantomEcho");
        int adjustedCooldown = (int) (cooldown * spellLevel.getCooldownReduction());
        int adjustedManaCost = (int) (manaCost * spellLevel.getManaCostReduction());
        int adjustedCloneDuration = cloneDuration + (spellLevel.getLevel() * 5); // Increase clone duration based on level
        int adjustedInvisDuration = invisDuration + (spellLevel.getLevel() * 2); // Increase invisibility duration based on level
        double adjustedDashDistance = dashDistance + (spellLevel.getLevel() * 2); // Increase dash distance based on level

        if (!Spellbreak.getInstance().getManaSystem().consumeMana(player, adjustedManaCost)) return;

        // If a previous clone state exists, clear it without applying cooldown
        clearExistingClone(uuid, false);

        // Store player state and remove armor/items; make invisible but NOT invulnerable
        PlayerState prevState = new PlayerState(
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.isInvisible()
        );
        playerStates.put(uuid, prevState);

        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.updateInventory();
        player.setInvisible(true);

        Location origin = player.getLocation();
        Vector dir = origin.getDirection().normalize();
        Location rawTarget = origin.clone().add(dir.multiply(adjustedDashDistance));

        // Find a safe teleport location
        Location safeTarget = findSafeLocation(origin, rawTarget);
        if (safeTarget == null) {
            // Refund mana and silently fail
            Spellbreak.getInstance().getManaSystem().restoreMana(player, adjustedManaCost);
            // Restore state immediately since we failed
            restorePlayerState(player);
            return;
        }

        // Initial particle effect at origin and destination
        spawnParticles(origin);
        spawnParticles(safeTarget);

        // Prepare clone at origin
        Location cloneLoc = origin.clone();

        ArmorStand stand = (ArmorStand) cloneLoc.getWorld().spawnEntity(cloneLoc, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setVisible(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.addScoreboardTag("phantom_clone");

        PlayerDisguise disguise = new PlayerDisguise(player.getName());
        disguise.getWatcher().setCustomName(player.getName());
        disguise.getWatcher().setCustomNameVisible(true);
        DisguiseAPI.disguiseToAll(stand, disguise);

        // Teleport player to safe location (slightly above to avoid clipping)
        safeTarget.add(0, 1, 0);
        player.teleport(safeTarget);

        CloneData data = new CloneData(cloneLoc, stand);
        clones.put(uuid, data);

        // Mark player as under phantom invis
        player.setMetadata("phantomInvisible", new FixedMetadataValue(Spellbreak.getInstance(), true));

        // Action bar task to show invis/clone timers
        data.actionBarTask = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                double remClone = (adjustedCloneDuration - ticks) / 20.0;
                if (remClone < 0) remClone = 0;

                String invisText;
                if (ticks <= adjustedInvisDuration) {
                    double remInvis = (adjustedInvisDuration - ticks) / 20.0;
                    if (remInvis < 0) remInvis = 0;
                    invisText = String.format("§dInvis: %.1fs", remInvis);
                } else {
                    invisText = "§dInvis: -";
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(String.format("%s §7| Clone: %.1fs", invisText, remClone)));

                if (++ticks > adjustedCloneDuration) {
                    cancel();
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        // Schedule removal of invisibility (and restore armor/items) after invisDuration
        data.invisTask = new BukkitRunnable() {
            @Override public void run() {
                removeInvisibility(player);
            }
        }.runTaskLater(Spellbreak.getInstance(), invisDuration);

        // Schedule clone expiration: apply burst at origin, then clear clone and apply cooldown
        data.expireTask = new BukkitRunnable() {
            @Override public void run() {
                Location burstLoc = data.location.clone().add(0, 1, 0);
                clearExistingClone(uuid, true);
                spawnDamageBurst(burstLoc, player);
            }
        }.runTaskLater(Spellbreak.getInstance(), adjustedCloneDuration);

        // Apply cooldown after the dash
        Spellbreak.getInstance().getCooldownManager().setCooldown(player, getName(), adjustedCooldown);
    }

    public void returnToClone(Player player) {
        UUID uuid = player.getUniqueId();
        CloneData data = clones.get(uuid);
        if (data == null) return;

        Location triggerLoc = player.getLocation().clone();
        Location loc = data.location.clone().add(0, 1, 0);

        // Teleport player back to clone location
        player.teleport(loc);

        // Damage bursts at departure and arrival
        spawnDamageBurst(triggerLoc, player);
        spawnDamageBurst(loc, player);

        // Clear clone, apply cooldown
        clearExistingClone(uuid, true);

        // Remove invisibility and restore armor/items if still hidden
        removeInvisibility(player);
    }

    private void spawnDamageBurst(Location loc, Player player) {
        loc.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                100,
                1.5, 1.5, 1.5,
                0.5,
                new Particle.DustOptions(Color.fromRGB(255, 105, 180), 2.0f),
                true
        );
        for (Entity e : loc.getWorld().getNearbyEntities(loc, returnDamageRadius, returnDamageRadius, returnDamageRadius)) {
            if (e instanceof LivingEntity target &&
                    !target.equals(player) &&
                    !isCloneEntity(target)) {
                Spellbreak.getInstance().getAbilityDamage().damage(target, returnDamage, player, this, "CloneReturn");
                Vector knock = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.5);
                target.setVelocity(knock);
            }
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 2f);
    }

    private boolean isCloneEntity(Entity entity) {
        return entity.getScoreboardTags().contains("phantom_clone") || entity.getScoreboardTags().contains("clone_swarm");
    }

    private void spawnParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.DUST, loc, 50, 0.5, 1.0, 0.5, 1.0,
                new Particle.DustOptions(Color.fromRGB(255, 192, 203), 1.5f));
    }

    private void removeInvisibility(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.hasMetadata("phantomInvisible")) {
            player.removeMetadata("phantomInvisible", Spellbreak.getInstance());
            player.setInvisible(false);

            // Restore armor & items
            restorePlayerState(player);
        }
    }

    private void clearExistingClone(UUID uuid, boolean applyCooldown) {
        CloneData d = clones.remove(uuid);
        if (d == null) return;

        if (d.expireTask != null) d.expireTask.cancel();
        if (d.actionBarTask != null) d.actionBarTask.cancel();
        if (d.invisTask != null) d.invisTask.cancel();

        if (d.stand != null && !d.stand.isDead()) {
            DisguiseAPI.undisguiseToAll(d.stand);
            d.stand.remove();
        }

        if (applyCooldown) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Spellbreak.getInstance().getCooldownManager().setCooldown(player, getName(), cooldown);
            }
        }
    }

    // Safe teleport utilities
    private Location findSafeLocation(Location start, Location target) {
        Block blockAt = target.getBlock();
        Block blockAbove = blockAt.getRelative(BlockFace.UP);

        if (isSafe(blockAt) && isSafe(blockAbove)) {
            Block blockBelow = blockAt.getRelative(BlockFace.DOWN);
            if (blockBelow.getType().isSolid()) {
                target.setY(blockAt.getY() + 0.01);
                return target;
            } else {
                return target;
            }
        }
        return null;
    }

    private boolean isSafe(Block block) {
        Material type = block.getType();
        return !type.isSolid() || type.isInteractable() || type == Material.WATER || type == Material.LAVA;
    }

    private void restorePlayerState(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerState state = playerStates.remove(uuid);
        if (state != null) {
            // Restore armor & items
            player.getInventory().setArmorContents(state.armor());
            player.getInventory().setItemInMainHand(state.mainHand());
            player.getInventory().setItemInOffHand(state.offHand());
            player.updateInventory();

            // Restore original invisibility state
            player.setInvisible(state.wasInvisible());
        }
    }

    @Override public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.phantomecho.cooldown", cooldown);
        manaCost = cfg.getInt("abilities.phantomecho.mana-cost", manaCost);
        dashDistance = cfg.getDouble("abilities.phantomecho.distance", dashDistance);
        cloneDuration = cfg.getInt("abilities.phantomecho.clone-duration", cloneDuration);
        invisDuration = cfg.getInt("abilities.phantomecho.invis-duration", invisDuration);
        returnDamage = cfg.getDouble("abilities.phantomecho.return-damage", returnDamage);
        returnDamageRadius = cfg.getDouble("abilities.phantomecho.return-damage-radius", returnDamageRadius);
    }

    private static class CloneData {
        Location location;
        ArmorStand stand;
        BukkitTask actionBarTask;
        BukkitTask invisTask;
        BukkitTask expireTask;

        CloneData(Location loc, ArmorStand stand) {
            this.location = loc;
            this.stand = stand;
        }
    }

    private record PlayerState(ItemStack[] armor, ItemStack mainHand, ItemStack offHand,
                               boolean wasInvisible) {}
}
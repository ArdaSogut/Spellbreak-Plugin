package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BladeSpinAbility implements Ability {

    private int cooldown = 20;
    private int manaCost = 50;
    private String requiredClass = "runesmith";
    private double duration = 5.0;
    private double radius = 2.5;
    private double damagePerHit = 1.5;
    private int maxHitsPerEntity = 3;
    private double forwardSpeed = 0.6;
    private double bounceStrength = 1.5;
    private int bounceCooldown = 10;
    private int hitCooldownTicks = 10;
    private double dashSpeed = 4;
    private int dashDurationTicks = 4;

    public double adjustedDamagePerHit = damagePerHit;
    public double adjustedDuration = duration;
    public double adjustedRadius = radius;
    public double adjustedForwardSpeed = forwardSpeed;
    public double adjustedMaxHits = maxHitsPerEntity;
    public double adjustedDashSpeed = dashSpeed;


    private static final Map<UUID, SpinData> activeSpins = new HashMap<>();

    @Override
    public String getName() {
        return "BladeSpin";
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.bladespin.";
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        duration = cfg.getDouble(base + "duration", duration);
        radius = cfg.getDouble(base + "radius", radius);
        damagePerHit = cfg.getDouble(base + "damage", damagePerHit);
        maxHitsPerEntity = cfg.getInt(base + "max-hits", maxHitsPerEntity);
        forwardSpeed = cfg.getDouble(base + "forward-speed", forwardSpeed);
        bounceStrength = cfg.getDouble(base + "bounce-strength", bounceStrength);
        bounceCooldown = cfg.getInt(base + "bounce-cooldown", bounceCooldown);
        hitCooldownTicks = cfg.getInt(base + "hit-cooldown", hitCooldownTicks);
        dashSpeed = cfg.getDouble(base + "dash-speed", dashSpeed);
        dashDurationTicks = cfg.getInt(base + "dash-duration-ticks", dashDurationTicks);
    }
    public int getAdjustedCooldown(Player p) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
                p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), getName());
        return (int)(cooldown * lvl.getCooldownReduction());
    }

    public int getAdjustedManaCost(Player p) {
        SpellLevel lvl = Spellbreak.getInstance().getLevelManager().getSpellLevel(
                p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), getName());
        return (int)(manaCost * lvl.getManaCostReduction());
    }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeSpins.containsKey(uuid)) return;

        PlayerInventoryState invState = new PlayerInventoryState(
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.isInvisible()
        );
        SpellLevel lvl = Spellbreak.getInstance()
                .getLevelManager()
                .getSpellLevel(uuid,
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(uuid),
                        getName());

        adjustedDamagePerHit = damagePerHit * lvl.getDamageMultiplier();
        adjustedDuration = duration * lvl.getDurationMultiplier();
        adjustedRadius = radius + lvl.getLevel() * 0.05;
        adjustedForwardSpeed = forwardSpeed + lvl.getLevel() * 0.02;
        adjustedMaxHits = maxHitsPerEntity + lvl.getLevel() * 0.75;
        adjustedDashSpeed = dashSpeed + lvl.getLevel() * 0.1;


        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        SpinData data = new SpinData(player, invState);
        activeSpins.put(uuid, data);
        data.start();
    }

    public class SpinData extends BukkitRunnable {
        private final Player player;
        private final PlayerInventoryState invState;
        private final Map<UUID, Integer> hitCounts = new HashMap<>();
        private final Map<UUID, Integer> hitNextAllowedTick = new HashMap<>();
        private final int totalTicks;
        private int ticksElapsed = 0;
        private int cooldownTicksRemaining = 0;
        private Vector lastLookVelocity = null;
        private boolean dashUsed = false;
        private int dashTicksRemaining = 0;
        private Vector dashDirection = null;

        SpinData(Player player, PlayerInventoryState invState) {
            this.player = player;
            this.invState = invState;
            this.totalTicks = (int) (adjustedDuration * 20);
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead()) {
                cancel();
                activeSpins.remove(player.getUniqueId());
                return;
            }

            if (ticksElapsed == 0) {
                player.setInvisible(true);
            }

            ticksElapsed++;
            int remainingTicks = totalTicks - ticksElapsed;

            // Action bar display
            int secondsLeft = (int) Math.ceil(remainingTicks / 20.0);
            player.sendActionBar(ChatColor.AQUA + "BladeSpin: " + ChatColor.YELLOW + secondsLeft + "s");

            // Movement handling
            if (dashTicksRemaining > 0) {
                handleDashMovement();
            } else {
                handleRegularMovement();
            }

            // Visual effects
            spawnTornadoEffect();

            // Damage handling
            handleEntityCollisions();

            if (remainingTicks <= 0) {
                endAbility();
            }
        }

        private void handleDashMovement() {
            dashTicksRemaining--;
            if (dashTicksRemaining <= 0) {
                player.setVelocity(dashDirection.multiply(0.5));
                dashDirection = null;
            }
        }

        private void handleRegularMovement() {
            if (cooldownTicksRemaining > 0) {
                cooldownTicksRemaining--;
                return;
            }

            Vector look = player.getLocation().getDirection().setY(0).normalize();
            if (look.lengthSquared() > 0.0001) {
                look.multiply(adjustedForwardSpeed);
                Location nextLoc = player.getLocation().clone().add(look);

                if (nextLoc.getBlock().getType().isSolid()) {
                    Vector bounceDir = look.multiply(-bounceStrength);
                    bounceDir.setY(0.1);
                    player.setVelocity(bounceDir);
                    spawnBounceEffect();
                    cooldownTicksRemaining = bounceCooldown;
                } else {
                    player.setVelocity(new Vector(look.getX(), player.getVelocity().getY(), look.getZ()));
                }
            }
        }

        private void handleEntityCollisions() {
            for (Entity e : player.getNearbyEntities(adjustedRadius, 2.0, adjustedRadius)) {
                if (!(e instanceof LivingEntity) || e.equals(player)) continue;

                LivingEntity entity = (LivingEntity) e;
                UUID eid = entity.getUniqueId();
                int count = hitCounts.getOrDefault(eid, 0);
                int nextAllowed = hitNextAllowedTick.getOrDefault(eid, 0);

                if (count >= adjustedMaxHits || ticksElapsed < nextAllowed) continue;

                // Calculate bounce direction
                Vector toPlayer = player.getLocation().toVector()
                        .subtract(entity.getLocation().toVector()).normalize();
                Vector bounceVec = toPlayer.multiply(bounceStrength);
                bounceVec.setY(0.2);  // Upward bounce

                // Apply bounce and cooldown
                player.setVelocity(bounceVec);
                cooldownTicksRemaining = bounceCooldown;  // THIS WAS MISSING

                // Damage and effects
                Spellbreak.getInstance().getAbilityDamage()
                        .damage(entity, adjustedDamagePerHit, player, BladeSpinAbility.this, null);
                entity.getWorld().playSound(entity.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                entity.getWorld().spawnParticle(Particle.CRIT,
                        entity.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);

                hitCounts.put(eid, count + 1);
                hitNextAllowedTick.put(eid, ticksElapsed + hitCooldownTicks);

                // Visual feedback for bounce
                spawnBounceEffect();
            }
        }

        public void doDash() {
            if (dashUsed || dashTicksRemaining > 0) return;
            dashUsed = true;

            dashDirection = player.getLocation().getDirection().setY(0).normalize();
            dashDirection.multiply(adjustedDashSpeed);

            dashTicksRemaining = dashDurationTicks;
            player.setVelocity(dashDirection.clone().setY(0.25));

            Location loc = player.getLocation().add(0, 0.5, 0);
            World world = loc.getWorld();
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.7f);
            world.spawnParticle(Particle.CLOUD, loc, 20, 0.5, 0.5, 0.5, 0.2);
        }

        private void spawnBounceEffect() {
            Location loc = player.getLocation().add(0, 0.5, 0);
            World world = loc.getWorld();
            world.spawnParticle(Particle.ENCHANT, loc, 5, 0.2, 0.2, 0.2, 0.02);
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        }

        private void spawnTornadoEffect() {
            Location baseLoc = player.getLocation().add(0, 0.5, 0);
            World world = baseLoc.getWorld();
            int layers = 10;
            double angleOffset = ticksElapsed * (Math.PI / 6);

            for (int i = 0; i < layers; i++) {
                double y = baseLoc.getY() + i * 0.3;
                double layerRadius = adjustedRadius * (i / (double) layers);
                double baseAngle = angleOffset - (i * Math.PI / layers);

                for (int s = 0; s < 4; s++) {
                    double angle = baseAngle + (s * Math.PI / 2);
                    double xOffset = Math.cos(angle) * layerRadius;
                    double zOffset = Math.sin(angle) * layerRadius;
                    Location pLoc = new Location(world, baseLoc.getX() + xOffset, y, baseLoc.getZ() + zOffset);
                    world.spawnParticle(Particle.DUST, pLoc, 6,
                            new Particle.DustOptions(Color.fromRGB(150, 200, 255), 1.1f));
                    world.spawnParticle(Particle.SMOKE, pLoc, 1, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }

        private void endAbility() {
            cancel();
            activeSpins.remove(player.getUniqueId());
            Location loc = player.getLocation();
            player.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 15, adjustedRadius, 0.5, adjustedRadius, 0.1);
        }

        @Override
        public synchronized void cancel() {
            super.cancel();
            player.sendActionBar("");
            player.getInventory().setArmorContents(invState.armor);
            player.getInventory().setItemInMainHand(invState.mainHand);
            player.getInventory().setItemInOffHand(invState.offHand);
            player.setInvisible(invState.wasInvisible);
        }

        public void start() {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
            this.runTaskTimer(Spellbreak.getInstance(), 0, 1);
        }
    }

    // Remaining interface methods
    @Override public String getDescription() { return "Whirling blade storm ability"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }
    @Override public boolean isSuccessful() { return true; }
    @Override public String getDeathMessage(String v, String c, String s) {
        return String.format("§c%s §4was shredded by §c%s§4's BladeSpin!", v, c);
    }
    public static void removeSpin(UUID playerId) {
        SpinData data = activeSpins.remove(playerId);
        if (data != null) data.cancel();
    }
    public static SpinData getActiveSpin(UUID playerId) {
        return activeSpins.get(playerId);
    }

    private static class PlayerInventoryState {
        final ItemStack[] armor;
        final ItemStack mainHand;
        final ItemStack offHand;
        final boolean wasInvisible;

        PlayerInventoryState(ItemStack[] armor, ItemStack mainHand, ItemStack offHand, boolean wasInvisible) {
            this.armor = armor.clone();
            this.mainHand = mainHand.clone();
            this.offHand = offHand.clone();
            this.wasInvisible = wasInvisible;
        }
    }
}
package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class MistDashAbility implements Ability, Listener {

    private int cooldown = 11;
    private int manaCost = 50;
    private String requiredClass = "necromancer";
    private int durationTicks = 20 * 3;
    private double speed = 0.5;
    private double yLimit = 10.0;
    private double effectRadius = 2.5;
    private int poisonDuration = 30;
    private int poisonAmplifier = 0;
    private double poisonDamage = .5;

    private final Map<UUID, MistState> activePlayers = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();

    public MistDashAbility() {
        Bukkit.getPluginManager().registerEvents(this, Spellbreak.getInstance());
    }

    private record MistState(ItemStack[] armor, ItemStack mainHand, ItemStack offHand,
                             boolean flightAllowed, boolean wasFlying) {}

    @Override public String getName() { return "MistDash"; }
    @Override public String getDescription() { return "Dash as mist, invisible, poisoning in a sphere"; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(org.bukkit.event.block.Action action) {
        return action == org.bukkit.event.block.Action.LEFT_CLICK_AIR;
    }
    @Override public boolean isSuccessful() { return false; }

    @Override
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        cleanup(player);

        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(uuid,
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(uuid),
                        getName());

        double scaledRadius = effectRadius * sl.getRangeMultiplier();
        double scaledDamage = poisonDamage * sl.getDamageMultiplier();
        int scaledDuration = (int)(durationTicks * sl.getDurationMultiplier());
        // Level 3+: poison amplifier increases
        int scaledPoisonAmp = (sl.getLevel() >= 3) ? 1 : poisonAmplifier;
        // Level 5: amplifier 2
        if (sl.getLevel() >= 5) scaledPoisonAmp = 2;

        MistState state = new MistState(
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getAllowFlight(),
                player.isFlying()
        );
        activePlayers.put(uuid, state);

        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvisible(true);
        player.setInvulnerable(true);
        final double startY = player.getLocation().getY();

        Spellbreak.getInstance().getCooldownManager().setCooldown(player, getName(), getCooldown());

        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(67, 99, 27), 1.0f);
        Particle.DustOptions teal  = new Particle.DustOptions(Color.fromRGB(24, 115, 50), 1.0f);
        // Level 3+: extra sickly yellow-green
        Particle.DustOptions toxic = new Particle.DustOptions(Color.fromRGB(140, 200, 20), 1.4f);

        final int finalPoisonAmp = scaledPoisonAmp;
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isValid() || !player.isOnline() || ticks++ >= scaledDuration) {
                    cleanup(player);
                    return;
                }

                Location loc = player.getLocation();
                Vector dir = loc.getDirection().normalize().multiply(speed);
                if (loc.getY() + dir.getY() > startY + yLimit) dir.setY(0);
                player.setVelocity(dir);

                Location center = loc.clone().add(0, 1, 0);

                // Base particles
                player.getWorld().spawnParticle(Particle.DUST, center, 30,
                        scaledRadius, scaledRadius, scaledRadius, green);
                player.getWorld().spawnParticle(Particle.DUST, center, 15,
                        scaledRadius, scaledRadius, scaledRadius, teal);

                // Level 3+: toxic particle layer
                if (sl.getLevel() >= 3) {
                    player.getWorld().spawnParticle(Particle.DUST, center, 12,
                            scaledRadius * 0.7, scaledRadius * 0.7, scaledRadius * 0.7, toxic);
                    player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 4,
                            scaledRadius * 0.5, scaledRadius * 0.5, scaledRadius * 0.5, 0);
                }

                // Level 5: portal burst every 15 ticks
                if (sl.getLevel() >= 5 && ticks % 15 == 0) {
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center, 10,
                            scaledRadius * 0.4, scaledRadius * 0.4, scaledRadius * 0.4, 0.05);
                    player.getWorld().playSound(center, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.4f, 1.6f);
                }

                // Damage + poison
                for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, scaledRadius)) {
                    if (entity.equals(player)) continue;
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, finalPoisonAmp));
                    Spellbreak.getInstance().getAbilityDamage()
                            .damage(entity, scaledDamage, player, MistDashAbility.this, null);
                }
            }
        };

        activeTasks.put(uuid, task);
        task.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitRunnable task = activeTasks.remove(uuid);
        if (task != null) task.cancel();

        MistState state = activePlayers.remove(uuid);
        if (state == null) return;
        if (!player.isOnline()) return;

        player.getInventory().setArmorContents(state.armor());
        player.getInventory().setItemInMainHand(state.mainHand());
        player.getInventory().setItemInOffHand(state.offHand());
        player.setAllowFlight(state.flightAllowed());
        player.setFlying(state.wasFlying());
        if (!state.flightAllowed()) player.setFlying(false);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setVelocity(new Vector(0, 0, 0));
        player.updateInventory();
        player.saveData();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) { cleanup(event.getPlayer()); }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String path = "abilities.mistdash.";
        cooldown       = cfg.getInt(path + "cooldown", cooldown);
        manaCost       = cfg.getInt(path + "mana-cost", manaCost);
        durationTicks  = cfg.getInt(path + "duration-seconds", durationTicks / 20) * 20;
        speed          = cfg.getDouble(path + "speed", speed);
        yLimit         = cfg.getDouble(path + "y-limit", yLimit);
        effectRadius   = cfg.getDouble(path + "effect-radius", effectRadius);
        poisonDuration = cfg.getInt(path + "poison-duration-ticks", poisonDuration);
        poisonAmplifier= cfg.getInt(path + "poison-amplifier", poisonAmplifier);
        poisonDamage   = cfg.getDouble(path + "poison-damage", poisonDamage);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format("§e%s §fwas poisoned by §c%s's §amist dash§f.", victimName, casterName);
    }
}

package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UndyingPactAbility: Creates a protective radius preventing any player or ally
 * from dropping below a minimum health threshold (5 HP) while active.
 * Records original health and restores it after the pact ends ONLY if the entity
 * entered the pact with health already below the threshold.
 */
public class UndyingPactAbility implements Ability {
    private String name = "UndyingPact";
    private String description = "Forms a pact that prevents allies in range from falling below a minimum health threshold.";
    private int cooldown = 60;
    private int manaCost = 40;
    private String requiredClass = "necromancer";

    // Configurable parameters
    private double pactRadius = 6.0;
    private int durationTicks = 200; // ~10 seconds
    private double minHealth = 10.0; // 5 hearts = 10 HP
    private int checkInterval = 2; // ticks
    private double armorStandYOffset = 0.5;
    private double damageReductionMultiplier = 0.0; // 0% damage taken

    // State per caster
    private final Map<UUID, BukkitTask> pactTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> radiusTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> markerEntities = new ConcurrentHashMap<>();
    // Track entities currently in radius and their original health
    private final Map<UUID, Map<UUID, Double>> entitiesInRadius = new ConcurrentHashMap<>();
    // Track entities that have been affected by health boost
    private final Map<UUID, Set<UUID>> affectedEntities = new ConcurrentHashMap<>();
    // Track entities that are protected from death
    private final Map<UUID, Set<UUID>> protectedEntities = new ConcurrentHashMap<>();
    // NEW: Track entities that entered below min health (should be restored to original health)
    private final Map<UUID, Set<UUID>> enteredBelowMin = new ConcurrentHashMap<>();

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        PlayerDataManager pdm = Spellbreak.getInstance().getPlayerDataManager();
        String cls = pdm.getPlayerClass(player.getUniqueId());
        activate(player, cls);
    }

    public void activate(Player player, String playerClass) {
        UUID casterId = player.getUniqueId();
        if (pactTasks.containsKey(casterId)) { removePact(casterId, player.getWorld()); return; }
        if (!requiredClass.equalsIgnoreCase(playerClass)) {
            player.sendMessage("§cYou must be a Necromancer to use this ability."); return;
        }

        // Initialize tracking maps for this caster
        entitiesInRadius.put(casterId, new ConcurrentHashMap<>());
        affectedEntities.put(casterId, ConcurrentHashMap.newKeySet());
        protectedEntities.put(casterId, ConcurrentHashMap.newKeySet());
        enteredBelowMin.put(casterId, ConcurrentHashMap.newKeySet()); // NEW: Track entities entered below min

        // Spawn marker
        Location center = player.getLocation();
        Location loc = center.clone().add(0, armorStandYOffset, 0);
        ArmorStand marker = player.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomName("UndyingPactMarker");
        });
        markerEntities.put(casterId, marker.getUniqueId());

        // Enhanced necromancer activation effects
        player.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
        player.getWorld().playSound(loc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 0.9f);
        player.getWorld().playSound(loc, Sound.BLOCK_SOUL_SOIL_BREAK, 1.0f, 0.7f);

        // Dark ritual circle activation
        for (int i = 0; i < 3; i++) {
            final int ring = i;
            Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
                double radius = 1.5 + (ring * 1.5);
                for (int j = 0; j < 32; j++) {
                    double angle = 2 * Math.PI * j / 32;
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location particleLoc = loc.clone().add(x, 0.1, z);
                    particleLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0, 0, 0, 0);
                    particleLoc.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 2, 0.1, 0, 0.1, 0.02);
                }
            }, i * 10L);
        }

        // Central pillar of dark energy
        for (int y = 0; y < 20; y++) {
            final int height = y;
            Bukkit.getScheduler().runTaskLater(Spellbreak.getInstance(), () -> {
                Location pillarLoc = loc.clone().add(0, height * 0.2, 0);
                pillarLoc.getWorld().spawnParticle(Particle.SOUL, pillarLoc, 3, 0.2, 0.1, 0.2, 0.02);
                pillarLoc.getWorld().spawnParticle(Particle.SMOKE, pillarLoc, 1, 0.1, 0.1, 0.1, 0.01);
            }, height * 2L);
        }

        player.sendMessage("§0§l⚔ §8§lUNDYING PACT §0§l⚔");
        player.sendMessage("§8⚰ §7Death itself bends to your will... §8⚰");
        player.sendMessage("§5§lProtection: §d" + (minHealth/2) + " hearts §8| §5§lDuration: §d" + (durationTicks/20) + "s");
        player.sendMessage("§5§lDamage Reduction: §d" + (int)((1-damageReductionMultiplier)*100) + "%");

        // Health enforcement + action bar
        BukkitTask enforceTask = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks) { removePact(casterId, player.getWorld()); cancel(); return; }
                int secs = (durationTicks - ticks)/20;
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§0⚰ §5§lUNDYING PACT §0⚰ §8[§5" + secs + "s§8]"));

                UUID mid = markerEntities.get(casterId);
                Entity m = Bukkit.getEntity(mid);
                if (!(m instanceof ArmorStand)) return;
                Location cen = m.getLocation();

                Map<UUID, Double> currentInRadius = entitiesInRadius.get(casterId);
                Set<UUID> affected = affectedEntities.get(casterId);
                Set<UUID> protectedE = protectedEntities.get(casterId);
                Set<UUID> enteredBelowSet = enteredBelowMin.get(casterId); // NEW: Access enteredBelow set
                Set<UUID> newInRadius = new HashSet<>();

                // Check all entities in radius
                for (Entity e : player.getWorld().getNearbyEntities(cen, pactRadius, pactRadius, pactRadius)) {
                    if (!(e instanceof LivingEntity)) continue;
                    LivingEntity le = (LivingEntity) e;
                    UUID entityId = le.getUniqueId();
                    newInRadius.add(entityId);

                    // If entity just entered radius, record their original health and protect them
                    if (!currentInRadius.containsKey(entityId)) {
                        double originalHealth = le.getHealth();
                        currentInRadius.put(entityId, originalHealth);
                        protectedE.add(entityId); // Add to protection immediately

                        // ONLY set health to minHealth if they're below it on entry
                        if (originalHealth < minHealth) {
                            le.setHealth(minHealth);
                            affected.add(entityId);
                            enteredBelowSet.add(entityId); // NEW: Mark as entered below min
                            // Enhanced necromantic entry effect
                            le.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, le.getLocation().add(0,1.5,0), 8, 0.3,0.3,0.3, 0.05);
                            le.getWorld().spawnParticle(Particle.SOUL, le.getLocation().add(0,1,0), 12, 0.4,0.4,0.4, 0.02);
                            le.getWorld().spawnParticle(Particle.SMOKE, le.getLocation().add(0,0.5,0), 15, 0.5,0.2,0.5, 0.03);
                            le.getWorld().spawnParticle(Particle.ENCHANT, le.getLocation().add(0,1,0), 20, 0.6,0.6,0.6, 1);

                            // Multi-layered necromantic sounds
                            le.getWorld().playSound(le.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.8f);
                            le.getWorld().playSound(le.getLocation(), Sound.BLOCK_SOUL_SOIL_PLACE, 0.8f, 1.2f);
                            le.getWorld().playSound(le.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.6f, 1.1f);

                            if (le instanceof Player) {
                                ((Player) le).sendMessage("§0⚱ §5The pact shields you from death... §0⚱");
                            }
                        } else {
                            // Still protected, just no visual effect needed
                            if (le instanceof Player) {
                                ((Player) le).sendMessage("§0⚱ §5You are under the protection of the undying pact... §0⚱");
                            }
                        }
                    }

                    // Continuously enforce minimum health for entities in radius
                    double currentHealth = le.getHealth();
                    if (currentHealth < minHealth && currentHealth > 0) { // Only if not already dead
                        le.setHealth(minHealth);
                        affected.add(entityId);
                        // Intense death-defying protection effect
                        le.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, le.getLocation().add(0,2,0), 12, 0.4,0.4,0.4, 0.08);
                        le.getWorld().spawnParticle(Particle.SOUL, le.getLocation().add(0,1.5,0), 20, 0.5,0.5,0.5, 0.03);
                        le.getWorld().spawnParticle(Particle.SMOKE, le.getLocation().add(0,0.8,0), 25, 0.6,0.3,0.6, 0.04);
                        le.getWorld().spawnParticle(Particle.ENCHANT, le.getLocation().add(0,1.2,0), 15, 0.7,0.7,0.7, 1.2);
                        le.getWorld().spawnParticle(Particle.WITCH, le.getLocation().add(0,1,0), 8, 0.3,0.3,0.3, 0.02);

                        // Dramatic death-defying sounds
                        le.getWorld().playSound(le.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 0.9f);
                        le.getWorld().playSound(le.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 1.3f);
                        le.getWorld().playSound(le.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, 1.2f, 0.7f);

                        if (le instanceof Player) {
                            ((Player) le).sendMessage("§0§l⚱ §5§lDEATH DENIED §0§l⚱");
                        }
                    }
                }

                // Check for entities that left the radius
                Set<UUID> leftRadius = new HashSet<>(currentInRadius.keySet());
                leftRadius.removeAll(newInRadius);

                for (UUID entityId : leftRadius) {
                    protectedE.remove(entityId); // Remove protection immediately

                    // Restore original health ONLY if they entered below minHealth
                    if (enteredBelowSet.contains(entityId)) {
                        LivingEntity le = (LivingEntity) Bukkit.getEntity(entityId);
                        if (le != null && !le.isDead()) {
                            double originalHealth = currentInRadius.get(entityId);
                            double maxHealth = le.getAttribute(Attribute.MAX_HEALTH).getValue();
                            le.setHealth(Math.min(maxHealth, originalHealth));

                            // Ethereal departure effect
                            le.getWorld().spawnParticle(Particle.SOUL, le.getLocation().add(0,1.5,0), 15, 0.4,0.4,0.4, 0.05);
                            le.getWorld().spawnParticle(Particle.SMOKE, le.getLocation().add(0,1,0), 20, 0.5,0.3,0.5, 0.04);
                            le.getWorld().spawnParticle(Particle.ENCHANT, le.getLocation().add(0,1.2,0), 10, 0.6,0.6,0.6, 0.8);
                            le.getWorld().playSound(le.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 1.4f);
                            le.getWorld().playSound(le.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.4f, 1.6f);

                            if (le instanceof Player) {
                                ((Player) le).sendMessage("§8⚰ §7The pact's protection fades... §8⚰");
                            }
                        }
                        enteredBelowSet.remove(entityId); // Remove from tracking
                        affected.remove(entityId);
                    } else {
                        // Notify if they were protected but not restored
                        LivingEntity le = (LivingEntity) Bukkit.getEntity(entityId);
                        if (le instanceof Player) {
                            ((Player) le).sendMessage("§8⚰ §7You are no longer protected by the undying pact... §8⚰");
                        }
                    }
                    currentInRadius.remove(entityId);
                }

                ticks += checkInterval;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, checkInterval);
        pactTasks.put(casterId, enforceTask);

        // Enhanced necromantic radius visualization
        BukkitTask radTask = new BukkitRunnable() {
            double rotation = 0;
            @Override
            public void run() {
                UUID mid = markerEntities.get(casterId);
                Entity m = Bukkit.getEntity(mid);
                if (!(m instanceof ArmorStand)) { cancel(); return; }
                Location cen = m.getLocation();

                rotation += Math.PI / 16; // Slow rotation

                // Main circle - alternating soul fire and dark particles
                Particle.DustOptions darkRed = new Particle.DustOptions(Color.fromRGB(120,20,20), 2.2f);
                Particle.DustOptions deepPurple = new Particle.DustOptions(Color.fromRGB(80,0,80), 1.8f);
                Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(25,25,25), 2.0f);

                int points = 80;
                for (int i = 0; i < points; i++) {
                    double ang = (2 * Math.PI * i / points) + rotation;
                    double x = pactRadius * Math.cos(ang);
                    double z = pactRadius * Math.sin(ang);
                    Location p = cen.clone().add(x, 0.3, z);

                    // Create layered circle effect
                    if (i % 3 == 0) {
                        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0,0,0,0);
                        p.getWorld().spawnParticle(Particle.DUST, p, 2, 0.1,0,0.1,0, darkRed);
                    } else if (i % 3 == 1) {
                        p.getWorld().spawnParticle(Particle.SOUL, p, 2, 0.05,0,0.05,0.01);
                        p.getWorld().spawnParticle(Particle.DUST, p, 1, 0.1,0,0.1,0, deepPurple);
                    } else {
                        p.getWorld().spawnParticle(Particle.SMOKE, p, 1, 0.05,0,0.05,0.005);
                        p.getWorld().spawnParticle(Particle.DUST, p, 1, 0.1,0,0.1,0, black);
                    }
                }

                // Inner ritual circles
                for (int ring = 1; ring <= 2; ring++) {
                    double innerRadius = pactRadius * (0.3 * ring);
                    int innerPoints = 40 / ring;
                    for (int i = 0; i < innerPoints; i++) {
                        double ang = (2 * Math.PI * i / innerPoints) - (rotation * ring);
                        double x = innerRadius * Math.cos(ang);
                        double z = innerRadius * Math.sin(ang);
                        Location p = cen.clone().add(x, 0.2, z);
                        p.getWorld().spawnParticle(Particle.ENCHANT, p, 1, 0,0,0,0.3);
                        if (ring == 1) {
                            p.getWorld().spawnParticle(Particle.WITCH, p, 1, 0.05,0,0.05,0.01);
                        }
                    }
                }

                // Central necromantic energy pillar
                for (int y = 0; y < 8; y++) {
                    Location pillar = cen.clone().add(0, y * 0.3, 0);
                    pillar.getWorld().spawnParticle(Particle.SOUL, pillar, 1, 0.1,0.1,0.1,0.02);
                    if (y % 2 == 0) {
                        pillar.getWorld().spawnParticle(Particle.SMOKE, pillar, 2, 0.15,0.1,0.15,0.01);
                    }
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 4L);
        radiusTasks.put(casterId, radTask);
    }

    private void removePact(UUID casterId, World world) {
        // cancel tasks
        if (pactTasks.containsKey(casterId)) pactTasks.remove(casterId).cancel();
        if (radiusTasks.containsKey(casterId)) radiusTasks.remove(casterId).cancel();

        // Restore health for entities that entered below minHealth
        Map<UUID, Double> inRadius = entitiesInRadius.remove(casterId);
        Set<UUID> affected = affectedEntities.remove(casterId);
        Set<UUID> enteredBelowSet = enteredBelowMin.remove(casterId); // NEW: Get entered below set
        Set<UUID> protectedE = protectedEntities.remove(casterId);

        if (inRadius != null && enteredBelowSet != null) {
            for (UUID entityId : enteredBelowSet) {
                LivingEntity le = (LivingEntity) Bukkit.getEntity(entityId);
                if (le != null && !le.isDead() && inRadius.containsKey(entityId)) {
                    double originalHealth = inRadius.get(entityId);
                    double maxHealth = le.getAttribute(Attribute.MAX_HEALTH).getValue();
                    le.setHealth(Math.min(maxHealth, originalHealth));

                    // Grand finale restoration effect
                    le.getWorld().spawnParticle(Particle.SOUL, le.getLocation().add(0,2,0), 25, 0.5,0.5,0.5, 0.08);
                    le.getWorld().spawnParticle(Particle.SMOKE, le.getLocation().add(0,1.5,0), 30, 0.6,0.4,0.6, 0.05);
                    le.getWorld().spawnParticle(Particle.ENCHANT, le.getLocation().add(0,1.2,0), 15, 0.8,0.8,0.8, 1.5);
                    le.getWorld().spawnParticle(Particle.WITCH, le.getLocation().add(0,1,0), 10, 0.4,0.4,0.4, 0.03);

                    if (le instanceof Player) {
                        ((Player) le).sendMessage("§0⚱ §8The undying pact dissolves... §0⚱");
                    }
                }
            }
        }

        // remove marker
        UUID entId = markerEntities.remove(casterId);
        if (entId != null) {
            Entity e = Bukkit.getEntity(entId);
            if (e instanceof ArmorStand) e.remove();
        }
        // Epic necromantic finale
        Player p = Bukkit.getPlayer(casterId);
        if (p != null) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.2f, 0.8f);
            p.getWorld().playSound(p.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 1.2f);
            p.getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 1.1f);
            p.sendMessage("§0§l⚔ §8§lUNDYING PACT DISSOLVED §0§l⚔");
        }
    }

    @Override public boolean isSuccessful() { return true; }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.undyingpact.";
        name = cfg.getString(base+"name", name);
        description = cfg.getString(base+"description", description);
        cooldown = cfg.getInt(base+"cooldown", cooldown);
        manaCost = cfg.getInt(base+"mana-cost", manaCost);
        requiredClass = cfg.getString(base+"required-class", requiredClass);
        pactRadius = cfg.getDouble(base+"radius", pactRadius);
        durationTicks = cfg.getInt(base+"duration-ticks", durationTicks);
        minHealth = cfg.getDouble(base+"min-health", minHealth);
        checkInterval = cfg.getInt(base+"check-interval", checkInterval);
        armorStandYOffset = cfg.getDouble(base+"marker-y-offset", armorStandYOffset);
        damageReductionMultiplier = cfg.getDouble(base+"damage-reduction-multiplier", damageReductionMultiplier);
    }

    @Override public String getDeathMessage(String victimName, String casterName, String subAbilityName) { return null; }

    /**
     * Check if an entity is currently protected by any Undying Pact
     * This should be called from your damage event listener to prevent death
     */
    public boolean isEntityProtected(UUID entityId) {
        for (Set<UUID> protectedSet : protectedEntities.values()) {
            if (protectedSet.contains(entityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the minimum health that a protected entity should have
     */
    public double getMinimumHealth() {
        return minHealth;
    }

    /**
     * Handle damage for protected entities - call this from your damage event listener
     * Returns true if the damage was modified/handled
     */
    public boolean handleProtectedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return false;

        LivingEntity entity = (LivingEntity) event.getEntity();
        UUID entityId = entity.getUniqueId();

        if (!isEntityProtected(entityId)) return false;

        double currentHealth = entity.getHealth();
        double originalDamage = event.getFinalDamage();

        // Reduce damage to the configured percentage (default 1% of original)
        double reducedDamage = originalDamage * damageReductionMultiplier;
        double finalHealth = currentHealth - reducedDamage;

        // If even the reduced damage would bring them below minimum health,
        // set damage to only bring them to minimum health
        if (finalHealth < minHealth) {
            double maxAllowedDamage = Math.max(0, currentHealth - minHealth);
            event.setDamage(maxAllowedDamage);
        } else {
            // Apply the reduced damage
            event.setDamage(reducedDamage);
        }

        // Show dramatic protection effect when damage is significantly reduced
        if (originalDamage > 2.0) { // Only show effect for meaningful damage
            entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().add(0,2,0), 15, 0.5,0.5,0.5, 0.1);
            entity.getWorld().spawnParticle(Particle.SOUL, entity.getLocation().add(0,1.5,0), 25, 0.6,0.6,0.6, 0.05);
            entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0,1,0), 30, 0.7,0.4,0.7, 0.06);
            entity.getWorld().spawnParticle(Particle.ENCHANT, entity.getLocation().add(0,1.2,0), 20, 0.8,0.8,0.8, 1.5);

            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_HURT, 1.2f, 0.8f);
            entity.getWorld().playSound(entity.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 1.4f);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.6f);

            if (entity instanceof Player) {
                ((Player) entity).sendMessage("§0§l⚱ §4§lDEATH REFUSED §0§l⚱");
            }
        }

        return true;
    }
}
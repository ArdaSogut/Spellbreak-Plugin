package me.ratatamakata.spellbreak.abilities.impl;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.listeners.CustomDeathMessageListener;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BoneChoirAbility implements Ability {
    private final Map<UUID, List<ActiveMob>> activeChoirs = new HashMap<>();
    private final Map<UUID, BukkitTask> removalTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> syncTasks = new HashMap<>();
    private final Map<UUID, Map<String, Long>> skillCooldowns = new HashMap<>();
    private final Map<UUID, Map<Entity, MovementState>> mobMovementStates = new HashMap<>();

    private int cooldown = 40;
    private int duration = 30;
    private double formationRadius = 4.0;
    private double hardTeleportThreshold = 25;
    private double idealCombatDistance = 15.0;
    private double maxEffectiveRange = 15.0;
    private int TENOR_SKILL_COOLDOWN = 40;
    private int BARITONE_SKILL_COOLDOWN = 100;
    private int BASS_SKILL_COOLDOWN = 120;
    private static final String METADATA_MOB_FULLY_SPAWNED = "BoneChoirFullySpawned";

    @Override
    public String getName() {
        return "BoneChoir";
    }

    @Override
    public String getDescription() {
        return "Summon skeletal minstrels that play haunting melodies to weaken enemies, damage them, and heal allies";
    }

    @Override
    public int getCooldown() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        return cfg.getInt("abilities.bonechoir.cooldown", cooldown);
    }

    @Override
    public int getManaCost() {
        return 50;
    }

    @Override
    public String getRequiredClass() {
        return "necromancer";
    }

    @Override
    public boolean isTriggerAction(Action a) {
        return false;
    }

    @Override
    public boolean isSuccessful() {
        return true;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        cooldown = cfg.getInt("abilities.bonechoir.cooldown", cooldown);
        duration = cfg.getInt("abilities.bonechoir.duration", duration);
        formationRadius = cfg.getDouble("abilities.bonechoir.formationRadius", formationRadius);
        hardTeleportThreshold = cfg.getDouble("abilities.bonechoir.hardTeleportThreshold", hardTeleportThreshold);
        idealCombatDistance = cfg.getDouble("abilities.bonechoir.idealCombatDistance", idealCombatDistance);
        maxEffectiveRange = cfg.getDouble("abilities.bonechoir.maxEffectiveRange", maxEffectiveRange);
        TENOR_SKILL_COOLDOWN = cfg.getInt("abilities.bonechoir.tenorSkillCooldown", TENOR_SKILL_COOLDOWN);
        BARITONE_SKILL_COOLDOWN = cfg.getInt("abilities.bonechoir.baritoneSkillCooldown", BARITONE_SKILL_COOLDOWN);
        BASS_SKILL_COOLDOWN = cfg.getInt("abilities.bonechoir.bassSkillCooldown", BASS_SKILL_COOLDOWN);
    }

    private final Map<UUID, Long> lastPathUpdate = new HashMap<>();

    private boolean shouldUpdatePath(UUID uuid, long now) {
        return !lastPathUpdate.containsKey(uuid) || (now - lastPathUpdate.get(uuid)) > 800;
    }

    private class MovementState {
        private long lastRepositionTime = 0;
        private final double combatIdealDistance;

        public MovementState() {
            this.combatIdealDistance = idealCombatDistance + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
        }

        public boolean canReposition() {
            return System.currentTimeMillis() - lastRepositionTime > 3000;
        }

        public void setRepositionCooldown() {
            lastRepositionTime = System.currentTimeMillis();
        }
    }

    @Override
    public void activate(Player player) {
        UUID uid = player.getUniqueId();
        if (activeChoirs.containsKey(uid)) {
            removeChoir(uid);
        }
        skillCooldowns.put(uid, new HashMap<>());
        mobMovementStates.put(uid, new HashMap<>());

        Location center = player.getLocation();
        List<ActiveMob> choir = new ArrayList<>();
        String[] types = {"BoneChoirTenor", "BoneChoirBaritone", "BoneChoirBass"};

        // Spawn effect
        player.getWorld().playSound(center, Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.7f);
        player.getWorld().spawnParticle(Particle.SOUL, center, 50, 1, 0.5, 1, 0.2);

        // Rising animation for each ActiveMob
        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            Vector offset = getFormationOffset(idx);
            Location spawnLoc = center.clone().add(offset);
            Location groundLoc = spawnLoc.clone().subtract(0, 2, 0);

            new BukkitRunnable() {
                int ticks = 0;
                ActiveMob mob;
                Entity entity;

                @Override
                public void run() {
                    Location loc = groundLoc.clone().add(0, ticks * 0.15, 0);
                    if (ticks == 0) {
                        loc.getWorld().spawnParticle(
                                Particle.BLOCK_CRUMBLE,
                                loc,
                                30,
                                0.5, 0.2, 0.5,
                                Material.BONE_BLOCK.createBlockData()
                        );
                    }
                    if (ticks == 7) {
                        mob = MythicBukkit.inst()
                                .getMobManager()
                                .spawnMob(types[idx], BukkitAdapter.adapt(loc));
                        if (mob != null) {
                            entity = mob.getEntity().getBukkitEntity();
                            entity.setInvulnerable(true);
                            applyMetadata(entity, player, idx, offset);
                            mobMovementStates.get(uid).put(entity, new MovementState());
                            choir.add(mob);
                        }
                    }
                    if (entity != null) {
                        entity.teleport(loc);
                        entity.getWorld().spawnParticle(Particle.SOUL, loc, 3, 0.2, 0.2, 0.2, 0.01);
                    }
                    if (ticks++ >= 14) {
                        if (entity != null) {
                            // Skeleton is fully risen—make it vulnerable, mark spawned
                            entity.setInvulnerable(false);
                            entity.setMetadata(
                                    METADATA_MOB_FULLY_SPAWNED,
                                    new FixedMetadataValue(Spellbreak.getInstance(), true)
                            );
                            entity.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    entity.getLocation(),
                                    10,
                                    0.2, 0.2, 0.2,
                                    0.05
                            );

                            // <<< FORCED TARGETING SNIPPET >>>
                            for (Entity nearby : entity.getWorld().getNearbyEntities(
                                    entity.getLocation(),
                                    maxEffectiveRange,
                                    maxEffectiveRange,
                                    maxEffectiveRange
                            )) {
                                if (nearby instanceof Mob hostile) {
                                    // 1) Clear any existing target
                                    hostile.setTarget(null);
                                    // 2) Deal tiny real damage so AI retargets
                                    hostile.damage(0.1, entity);
                                    // 3) Force new target
                                    hostile.setTarget((LivingEntity) entity);
                                }
                            }
                        }
                        cancel();
                    }
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
        }

        // After rising animation completes (≈16 ticks), initialize AI
        new BukkitRunnable() {
            @Override
            public void run() {
                if (choir.isEmpty()) return;
                activeChoirs.put(uid, choir);

                removalTasks.put(
                        uid,
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                removeChoir(uid);
                            }
                        }.runTaskLater(Spellbreak.getInstance(), duration * 20L)
                );

                syncTasks.put(
                        uid,
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!activeChoirs.containsKey(uid) || !player.isOnline()) {
                                    if (activeChoirs.containsKey(uid)) removeChoir(uid);
                                    cancel();
                                    return;
                                }
                                LivingEntity mainPlayerTarget = getPlayerTarget(player);
                                List<ActiveMob> list = activeChoirs.get(uid);
                                if (list == null) {
                                    removeChoir(uid);
                                    cancel();
                                    return;
                                }
                                Map<Entity, MovementState> states = mobMovementStates.get(uid);
                                if (states == null) {
                                    removeChoir(uid);
                                    cancel();
                                    return;
                                }

                                for (int j = 0; j < list.size(); j++) {
                                    ActiveMob m = list.get(j);
                                    if (m == null || m.getEntity().isDead()) continue;
                                    Entity e = m.getEntity().getBukkitEntity();
                                    if (!(e instanceof Mob bukkitMob)) continue;

                                    String type = getMetadataString(e, CustomDeathMessageListener.METADATA_KEY_SUMMON_TYPE_NAME);
                                    MovementState st = states.get(e);
                                    if (st == null || type == null) continue;

                                    // Debris particle checks
                                    if (System.currentTimeMillis() % 2000 < ThreadLocalRandom.current().nextInt(80, 120)) {
                                        e.getWorld().spawnParticle(Particle.NOTE, e.getLocation().add(0, 2, 0), 1, 0.1, 0.1, 0.1, 0);
                                    }

                                    double dToPlayer = e.getLocation().distance(player.getLocation());
                                    if (dToPlayer > hardTeleportThreshold) {
                                        handleEmergencyTeleport(player, e, j);
                                        continue;
                                    }

                                    boolean isMobSpawned = isMobFullySpawned(e);

                                    // Check line-of-sight: only target if skeleton has direct view
                                    if (mainPlayerTarget != null
                                            && isHostile(player, mainPlayerTarget)
                                            && bukkitMob.hasLineOfSight(mainPlayerTarget)) {

                                        // Skill usage only if LOS is clear
                                        if (isMobSpawned && canUseSkill(uid, type)) {
                                            if ("BoneChoirBass".equals(type)) {
                                                LivingEntity healTarget = findMostDamagedAllyOrPlayer(player, list, e);
                                                // For healing, LOS isn't strictly necessary; but we skip if no LOS to caster or allies.
                                                if (healTarget != null && bukkitMob.hasLineOfSight(healTarget)) {
                                                    useSkillByType(type, m, healTarget, player, list, uid);
                                                }
                                            } else {
                                                // For offensive skills, ensure LOS to mainPlayerTarget
                                                useSkillByType(type, m, mainPlayerTarget, player, list, uid);
                                            }
                                        }

                                        // Movement & combat positioning if LOS holds
                                        double distanceToTarget = e.getLocation().distance(mainPlayerTarget.getLocation());
                                        handleCombatPositioning(m, e, mainPlayerTarget, st, distanceToTarget);

                                    } else {
                                        // No valid LOS or no valid target: reset AI to follow caster
                                        m.resetTarget();
                                        handleFollowBehavior(player, e, j, st);
                                    }
                                }
                            }
                        }.runTaskTimer(Spellbreak.getInstance(), 20L, 20L)
                );
            }
        }.runTaskLater(Spellbreak.getInstance(), 16L);
    }

    private void applyMetadata(Entity e, Player caster, int idx, Vector off) {
        Spellbreak plugin = Spellbreak.getInstance();
        e.setMetadata(
                CustomDeathMessageListener.METADATA_KEY_CASTER_UUID,
                new FixedMetadataValue(plugin, caster.getUniqueId().toString())
        );
        e.setMetadata(
                CustomDeathMessageListener.METADATA_KEY_ABILITY_NAME,
                new FixedMetadataValue(plugin, getName())
        );
        String type;
        if (idx == 0) type = "BoneChoirTenor";
        else if (idx == 1) type = "BoneChoirBaritone";
        else type = "BoneChoirBass";
        e.setMetadata(
                CustomDeathMessageListener.METADATA_KEY_SUMMON_TYPE_NAME,
                new FixedMetadataValue(plugin, type)
        );
        e.setMetadata(METADATA_MOB_FULLY_SPAWNED, new FixedMetadataValue(plugin, false));
        e.setMetadata("BoneChoirMember", new FixedMetadataValue(plugin, "true"));
        e.setMetadata("formationOffset", new FixedMetadataValue(plugin, off.getX() + "," + off.getY() + "," + off.getZ()));
        e.setMetadata("choirIndex", new FixedMetadataValue(plugin, idx));
    }

    private void useSkillByType(
            String type,
            ActiveMob m,
            LivingEntity tgt,
            Player p,
            List<ActiveMob> choir,
            UUID uid
    ) {
        Entity summonEntity = m.getEntity().getBukkitEntity();
        Spellbreak plugin = Spellbreak.getInstance();

        switch (type) {
            case "BoneChoirTenor":
                summonEntity.setMetadata(
                        CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                        new FixedMetadataValue(plugin, "Tenor's Note")
                );
                useTenorSkill(m, tgt);
                summonEntity.removeMetadata(CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME, plugin);
                setSkillCooldown(uid, type, TENOR_SKILL_COOLDOWN);
                break;
            case "BoneChoirBaritone":
                summonEntity.setMetadata(
                        CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                        new FixedMetadataValue(plugin, "Baritone's Blast")
                );
                useBaritoneSkill(m, tgt);
                summonEntity.removeMetadata(CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME, plugin);
                setSkillCooldown(uid, type, BARITONE_SKILL_COOLDOWN);
                break;
            default:
                summonEntity.setMetadata(
                        CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                        new FixedMetadataValue(plugin, "Bass Drop")
                );
                useBassSkill(m, p, choir);
                summonEntity.removeMetadata(CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME, plugin);
                setSkillCooldown(uid, type, BASS_SKILL_COOLDOWN);
        }
    }

    private Player getCasterFromMetadata(Entity mobEntity) {
        List<MetadataValue> casterValues = mobEntity.getMetadata(CustomDeathMessageListener.METADATA_KEY_CASTER_UUID);
        if (!casterValues.isEmpty()) {
            try {
                UUID casterUUID = UUID.fromString(casterValues.get(0).asString());
                return Bukkit.getPlayer(casterUUID);
            } catch (IllegalArgumentException e) {
                Spellbreak.getInstance().getLogger().warning(
                        "Invalid UUID in BoneChoir metadata: " + casterValues.get(0).asString()
                );
            }
        }
        return null;
    }

    private void useTenorSkill(ActiveMob mob, LivingEntity tgt) {
        Entity self = mob.getEntity().getBukkitEntity();
        if (!isMobFullySpawned(self)) return;

        Location from = self.getLocation().add(0, 1.5, 0);
        Vector direction = tgt.getLocation()
                .add(0, tgt.getHeight() / 2.0, 0)
                .toVector()
                .subtract(from.toVector())
                .normalize();

        String subAbilityName = "Tenor's Note";
        self.setMetadata(
                CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                new FixedMetadataValue(Spellbreak.getInstance(), subAbilityName)
        );

        self.getWorld().playSound(from, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.2f);
        self.getWorld().playSound(from, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f, 1.5f);

        new BukkitRunnable() {
            int ticksLived = 0;
            Location currentLoc = from.clone();
            final double maxProjectileRange = 15.0;
            final double projectileSpeed = 0.8;

            @Override
            public void run() {
                if (ticksLived++ > (maxProjectileRange / projectileSpeed) + 10) {
                    self.removeMetadata(
                            CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                            Spellbreak.getInstance()
                    );
                    cancel();
                    return;
                }

                for (int i = 0; i < 2; i++) {
                    currentLoc.add(direction.clone().multiply(projectileSpeed / 2.0));
                    currentLoc.getWorld().spawnParticle(Particle.WITCH, currentLoc, 1, 0.05, 0.05, 0.05, 0.02);
                    if (ticksLived % 2 == 0) {
                        currentLoc.getWorld().spawnParticle(Particle.ENCHANT, currentLoc, 2, 0.2, 0.2, 0.2, 0.1);
                    }

                    Collection<Entity> nearbyEntities = currentLoc.getWorld().getNearbyEntities(currentLoc, 0.9, 0.9, 0.9);
                    if (nearbyEntities.contains(tgt)) {
                        if (tgt instanceof LivingEntity) {
                            LivingEntity livingTarget = (LivingEntity) tgt;

                            // ─────── VANILLA BUKKIT DAMAGE ───────
                            livingTarget.damage(1.0, self);

                            livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1));
                            livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1));

                            Location impactLoc = livingTarget.getLocation().add(0, livingTarget.getHeight() / 2, 0);
                            impactLoc.getWorld().spawnParticle(Particle.SMOKE, impactLoc, 15, 0.3, 0.3, 0.3, 0.05);
                            impactLoc.getWorld().spawnParticle(Particle.WITCH, impactLoc, 20, 0.4, 0.4, 0.4, 0.1);
                            impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
                        }
                        self.removeMetadata(
                                CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                                Spellbreak.getInstance()
                        );
                        cancel();
                        return;
                    }

                    if (!currentLoc.getBlock().isPassable() ||
                            currentLoc.distanceSquared(from) > maxProjectileRange * maxProjectileRange) {
                        currentLoc.getWorld().spawnParticle(Particle.SMOKE, currentLoc, 5, 0.1, 0.1, 0.1, 0.01);
                        currentLoc.getWorld().spawnParticle(Particle.WITCH, currentLoc, 3, 0.1, 0.1, 0.1, 0.01);
                        self.removeMetadata(
                                CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                                Spellbreak.getInstance()
                        );
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void useBaritoneSkill(ActiveMob mob, LivingEntity tgt) {
        Entity self = mob.getEntity().getBukkitEntity();
        if (!isMobFullySpawned(self)) return;

        Location from = self.getLocation().add(0, 1.5, 0);
        Vector direction = tgt.getLocation()
                .add(0, tgt.getHeight() / 2.0, 0)
                .toVector()
                .subtract(from.toVector())
                .normalize();

        String subAbilityName = "Baritone's Blast";
        self.setMetadata(
                CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                new FixedMetadataValue(Spellbreak.getInstance(), subAbilityName)
        );

        self.getWorld().playSound(from, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 0.8f);
        self.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.0f);

        new BukkitRunnable() {
            int ticksLived = 0;
            Location currentLoc = from.clone();
            final double maxProjectileRange = 20.0;
            final double projectileSpeed = 0.6 ;

            @Override
            public void run() {
                if (ticksLived++ > (maxProjectileRange / projectileSpeed) + 10) {
                    self.removeMetadata(
                            CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                            Spellbreak.getInstance()
                    );
                    cancel();
                    return;
                }

                for (int i = 0; i < 2; i++) {
                    currentLoc.add(direction.clone().multiply(projectileSpeed / 2.0));
                    currentLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, currentLoc, 2, 0.05, 0.05, 0.05, 0.01);
                    currentLoc.getWorld().spawnParticle(Particle.SOUL, currentLoc, 1, 0.02, 0.02, 0.02, 0.01);

                    Collection<Entity> nearbyEntities = currentLoc.getWorld().getNearbyEntities(currentLoc, 0.9, 0.9, 0.9);
                    if (nearbyEntities.contains(tgt)) {
                        if (tgt instanceof LivingEntity) {
                            LivingEntity livingTarget = (LivingEntity) tgt;

                            // ─────── VANILLA BUKKIT DAMAGE ───────
                            livingTarget.damage(2.0, self);

                            Location impactLoc = livingTarget.getLocation().add(0, livingTarget.getHeight() / 2, 0);
                            impactLoc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, impactLoc, 25, 0.4, 0.4, 0.4, 0.2);
                            impactLoc.getWorld().spawnParticle(Particle.LAVA, impactLoc, 10, 0.2, 0.2, 0.2, 0.0);
                            impactLoc.getWorld().spawnParticle(Particle.EXPLOSION, impactLoc, 1, 0, 0, 0, 0);
                            impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
                        }
                        self.removeMetadata(
                                CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                                Spellbreak.getInstance()
                        );
                        cancel();
                        return;
                    }
                    if (!currentLoc.getBlock().isPassable() ||
                            currentLoc.distanceSquared(from) > maxProjectileRange * maxProjectileRange) {
                        currentLoc.getWorld().spawnParticle(Particle.SMOKE, currentLoc, 5, 0.1, 0.1, 0.1, 0.01);
                        currentLoc.getWorld().spawnParticle(Particle.SOUL, currentLoc, 3, 0.1, 0.1, 0.1, 0.01);
                        self.removeMetadata(
                                CustomDeathMessageListener.METADATA_KEY_SUB_ABILITY_NAME,
                                Spellbreak.getInstance()
                        );
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void useBassSkill(ActiveMob mob, Player caster, List<ActiveMob> choir) {
        Entity e = mob.getEntity().getBukkitEntity();
        Location mobLoc = e.getLocation().add(0, 1, 0);
        e.getWorld().playSound(mobLoc, Sound.BLOCK_BELL_USE, 1.0f, 0.7f);
        e.getWorld().playSound(mobLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 1.5f);

        // Particle effect task remains the same
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 40 || mob.getEntity().isDead()) {
                    cancel();
                    return;
                }
                if (ticks % 4 == 0) {
                    Location noteLoc = mob.getEntity().getBukkitEntity().getLocation().add(0, 1.8, 0);
                    noteLoc.getWorld().spawnParticle(Particle.NOTE, noteLoc, 1, 0.3, 0.3, 0.3, 0.5);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);

        // Track healed entities to prevent multiple heals
        Set<UUID> healedEntities = new HashSet<>();
        final double HEAL_AMOUNT = 1.0; // 0.5 hearts = 1 health point

        new BukkitRunnable() {
            double currentRadius = 0.5;
            final double maxHealRadius = 5.0;
            final UUID casterId = caster.getUniqueId();

            @Override
            public void run() {
                // Only apply healing once when radius reaches max
                if (currentRadius >= maxHealRadius) {
                    Collection<Entity> nearbyEntities = mobLoc.getWorld().getNearbyEntities(
                            mobLoc,
                            currentRadius,
                            currentRadius,
                            currentRadius
                    );

                    for (Entity nearbyEnt : nearbyEntities) {
                        if (!(nearbyEnt instanceof LivingEntity)) continue;
                        LivingEntity le = (LivingEntity) nearbyEnt;
                        UUID entityId = le.getUniqueId();

                        // Skip if already healed this cast
                        if (healedEntities.contains(entityId)) continue;

                        boolean isCaster = entityId.equals(casterId);
                        boolean isChoirMember = choir.stream()
                                .map(activeMob -> activeMob.getEntity().getBukkitEntity().getUniqueId())
                                .anyMatch(uuid -> uuid.equals(entityId));

                        if (isCaster || isChoirMember) {
                            if (le.getHealth() < le.getMaxHealth()) {
                                // Apply single heal of 0.5 hearts
                                le.setHealth(Math.min(le.getHealth() + HEAL_AMOUNT, le.getMaxHealth()));
                                Location healedLoc = le.getLocation().add(0, le.getHeight() * 0.75, 0);
                                healedLoc.getWorld().spawnParticle(Particle.HEART, healedLoc, 2, 0.3, 0.3, 0.3, 0.01);

                                // Mark as healed
                                healedEntities.add(entityId);
                            }
                        }
                    }
                    cancel();
                    return;
                }

                // Visual effects only below this point
                for (int i = 0; i < 24; i++) {
                    double angle = Math.toRadians(i * (360.0 / 24.0));
                    Location particleLoc = mobLoc.clone().add(
                            Math.cos(angle) * currentRadius,
                            0.1,
                            Math.sin(angle) * currentRadius
                    );
                    mobLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            1,
                            0, 0, 0,
                            new DustOptions(Color.fromRGB(100, 255, 100), 1.2f)
                    );
                    if (currentRadius > 2.0 && i % 3 == 0) {
                        mobLoc.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.01);
                    }
                }

                currentRadius += 0.4;
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private LivingEntity getPlayerTarget(Player p) {
        // 1) Direct line‐of‐sight: includes all LivingEntity (passive/neutral too)
        Entity losTarget = p.getTargetEntity((int) maxEffectiveRange);
        if (losTarget instanceof LivingEntity && isHostile(p, losTarget)) {
            return (LivingEntity) losTarget;
        }

        // 2) Any entity targeting the player
        Entity attacker = p.getNearbyEntities(20, 20, 20).stream()
                .filter(e -> e instanceof Mob &&
                        ((Mob) e).getTarget() != null &&
                        ((Mob) e).getTarget().equals(p) &&
                        isHostile(p, e))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())))
                .orElse(null);
        if (attacker instanceof LivingEntity) {
            return (LivingEntity) attacker;
        }

        // 3) Closest LivingEntity (passive/neutral included) within range
        return p.getNearbyEntities(20, 20, 20).stream()
                .filter(ent -> ent instanceof LivingEntity && isHostile(p, ent))
                .map(ent -> (LivingEntity) ent)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())))
                .orElse(null);
    }

    private boolean isHostile(Player caster, Entity t) {
        if (!(t instanceof LivingEntity) || t.equals(caster)) return false;
        // Deny targeting own choir members.
        if (t.hasMetadata(CustomDeathMessageListener.METADATA_KEY_CASTER_UUID)) {
            String targetCasterUUID = getMetadataString(t, CustomDeathMessageListener.METADATA_KEY_CASTER_UUID);
            if (targetCasterUUID != null && targetCasterUUID.equals(caster.getUniqueId().toString())) {
                return false;
            }
        }
        // Any other LivingEntity is valid (including other player choirs)
        return t instanceof LivingEntity;
    }

    private String getMetadataString(Entity entity, String key) {
        List<MetadataValue> values = entity.getMetadata(key);
        if (!values.isEmpty()) {
            return values.get(0).asString();
        }
        return null;
    }

    private Vector getFormationOffset(int i) {
        double a = Math.toRadians(120 * i);
        // Add slight random jitter to make formation less rigid
        double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.1;
        return new Vector(
                Math.cos(a) * (formationRadius + jitter),
                0,
                Math.sin(a) * (formationRadius + jitter)
        );
    }

    private void handleCombatPositioning(
            ActiveMob mob,
            Entity entity,
            LivingEntity target,
            MovementState st,
            double distance
    ) {
        if (!(entity instanceof Mob bukkitMob)) return;

        long now = System.currentTimeMillis();
        UUID uuid = bukkitMob.getUniqueId();

        // Disable vanilla targeting to avoid melee rushing
        bukkitMob.setTarget(null);

        // Rotate mob to face target smoothly (only teleport if yaw change > 1°)
        Location loc = bukkitMob.getLocation();
        Vector dir = target.getLocation().toVector().subtract(loc.toVector()).normalize();
        Location newLoc = loc.clone().setDirection(dir);

        float currentYaw = loc.getYaw();
        float targetYaw = newLoc.getYaw();

        if (Math.abs(currentYaw - targetYaw) > 1.0) {
            bukkitMob.teleport(newLoc);
        }

        double minSafeDistance = 3.5; // no melee range
        double idealDistance = st.combatIdealDistance;

        if (distance > idealDistance + 1) {
            if (distance > minSafeDistance) {
                if (shouldUpdatePath(uuid, now)) {
                    Vector fromTargetToMob = bukkitMob.getLocation().toVector()
                            .subtract(target.getLocation().toVector()).normalize();
                    Location moveTo = target.getLocation().clone()
                            .add(fromTargetToMob.multiply(idealDistance));
                    bukkitMob.getPathfinder().moveTo(moveTo, 1.25);
                    lastPathUpdate.put(uuid, now);
                }
            }
        } else if (distance < minSafeDistance) {
            if (shouldUpdatePath(uuid, now)) {
                Vector retreatDir = bukkitMob.getLocation().toVector()
                        .subtract(target.getLocation().toVector()).normalize();
                Location retreatLoc = target.getLocation().clone()
                        .add(retreatDir.multiply(minSafeDistance));
                bukkitMob.getPathfinder().moveTo(retreatLoc, 1.1);
                lastPathUpdate.put(uuid, now);
            }
        }
        // else hold position
    }

    private void applySmoothedVelocity(Entity entity, Vector direction, double speed) {
        Vector current = entity.getVelocity();
        Vector target = direction.clone().multiply(speed);
        Vector smoothed = current.clone().multiply(0.5).add(target.clone().multiply(0.5));
        smoothed.setY(current.getY()); // Preserve vertical momentum
        entity.setVelocity(smoothed);
    }

    private void applyRetreat(Entity e, Vector dir) {
        Vector retreatDirection = dir.clone().multiply(-1).normalize();
        applySmoothedVelocity(e, retreatDirection, 0.6D);
    }

    private void applyAdvance(Entity e, Vector dir) {
        Vector advanceDirection = dir.clone().normalize();
        applySmoothedVelocity(e, advanceDirection, 0.18D);
    }

    private void applyPosition(Entity e, Vector dirToTarget, MovementState st) {
        Vector strafeDirection;
        if (st.canReposition()) {
            strafeDirection = (new Vector(-dirToTarget.getZ(), 0.0D, dirToTarget.getX())).normalize();
            if (ThreadLocalRandom.current().nextBoolean()) {
                strafeDirection.multiply(-1);
            }
            applySmoothedVelocity(e, strafeDirection, 0.15D);
            st.setRepositionCooldown();
        } else {
            Vector curr = e.getVelocity();
            e.setVelocity(new Vector(curr.getX() * 0.6D, curr.getY() * 0.6D, curr.getZ() * 0.6D));
        }
    }

    private void handleFollowBehavior(Player player, Entity entity, int index, MovementState st) {
        if (!(entity instanceof Mob mob)) return;

        Location targetLoc = player.getLocation().add(getFormationOffset(index));
        double distSq = entity.getLocation().distanceSquared(targetLoc);
        long now = System.currentTimeMillis();
        UUID uuid = mob.getUniqueId();

        if (distSq > 2.0 && shouldUpdatePath(uuid, now)) {
            mob.getPathfinder().moveTo(targetLoc, 1.1);
            lastPathUpdate.put(uuid, now);
        }
    }

    private void handleEmergencyTeleport(Player p, Entity e, int idx) {
        e.getWorld().spawnParticle(Particle.PORTAL, e.getLocation(), 15, 0.5, 1, 0.5, 0.01);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.2f);
        new BukkitRunnable() {
            @Override
            public void run() {
                Location playerLocation = p.getLocation();
                Vector playerDirection = playerLocation.getDirection().normalize();
                double offsetX = (idx % 2 == 0 ? 1.5 : -1.5) * (idx == 0 ? 1 : (idx / 2.0 + 1));
                double offsetZ = -2.5;

                double angle = Math.toRadians(playerLocation.getYaw());
                double rotatedX = offsetX * Math.cos(angle) - offsetZ * Math.sin(angle);
                double rotatedZ = offsetX * Math.sin(angle) + offsetZ * Math.cos(angle);

                Location targetXZLoc = playerLocation.clone().add(rotatedX, 0, rotatedZ);

                Location groundLoc = targetXZLoc.clone();
                groundLoc.setY(playerLocation.getY() + 1);

                boolean groundFound = false;
                for (int i = 0; i < 20; i++) {
                    if (!groundLoc.getBlock().isPassable() && groundLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
                        groundLoc.add(0, 1, 0);
                        groundFound = true;
                        break;
                    }
                    groundLoc.subtract(0, 1, 0);
                }

                if (!groundFound) {
                    groundLoc.setY(playerLocation.getY());
                    Spellbreak.getInstance().getLogger().warning(
                            "BoneChoir: Could not find solid ground for emergency teleport for " + e.getName()
                                    + ". Teleporting to player Y."
                    );
                }

                if (!groundLoc.getBlock().isPassable()) {
                    groundLoc.add(0, 1, 0);
                }
                if (!groundLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
                    groundLoc.add(0, 1, 0);
                }

                e.teleport(groundLoc);
                e.getWorld().spawnParticle(Particle.PORTAL, groundLoc, 15, 0.5, 1, 0.5, 0.1);
            }
        }.runTaskLater(Spellbreak.getInstance(), 5L);
    }

    private void setSkillCooldown(UUID uid, String type, int ticks) {
        skillCooldowns.computeIfAbsent(uid, k -> new HashMap<>())
                .put(type, System.currentTimeMillis() + ticks * 50L);
    }

    private boolean canUseSkill(UUID uid, String type) {
        return skillCooldowns.getOrDefault(uid, Collections.emptyMap())
                .getOrDefault(type, 0L) <= System.currentTimeMillis();
    }

    private void removeChoir(UUID uid) {
        List<ActiveMob> choir = activeChoirs.remove(uid);
        if (choir != null) {
            for (ActiveMob m : choir) {
                Entity e = m.getEntity().getBukkitEntity();
                e.getWorld().spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        e.getLocation().add(0, 1, 0),
                        15,
                        0.3, 0.5, 0.3,
                        0.05
                );
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_SKELETON_DEATH, 0.7f, 0.8f);
                m.getEntity().remove();
            }
        }
        Optional.ofNullable(removalTasks.remove(uid)).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(syncTasks.remove(uid)).ifPresent(BukkitTask::cancel);
        skillCooldowns.remove(uid);
    }

    private LivingEntity findMostDamagedAllyOrPlayer(Player caster, List<ActiveMob> choirMembers, Entity selfBass) {
        LivingEntity mostDamaged = null;
        double lowestHealthPercentage = 1.01;

        if (caster.getHealth() < caster.getMaxHealth() * 0.95) {
            double casterHealthPercentage = caster.getHealth() / caster.getMaxHealth();
            if (casterHealthPercentage < lowestHealthPercentage) {
                lowestHealthPercentage = casterHealthPercentage;
                mostDamaged = caster;
            }
        }

        for (ActiveMob memberMob : choirMembers) {
            Entity memberEntity = memberMob.getEntity().getBukkitEntity();
            if (memberEntity.equals(selfBass) || !(memberEntity instanceof LivingEntity)) {
                continue;
            }
            LivingEntity le = (LivingEntity) memberEntity;
            if (le.getHealth() < le.getMaxHealth() * 0.95) {
                double memberHealthPercentage = le.getHealth() / le.getMaxHealth();
                if (memberHealthPercentage < lowestHealthPercentage) {
                    lowestHealthPercentage = memberHealthPercentage;
                    mostDamaged = le;
                }
            }
        }
        return mostDamaged;
    }

    private boolean isMobFullySpawned(Entity mobEntity) {
        if (mobEntity.hasMetadata(METADATA_MOB_FULLY_SPAWNED)) {
            for (MetadataValue value : mobEntity.getMetadata(METADATA_MOB_FULLY_SPAWNED)) {
                if (value.getOwningPlugin().equals(Spellbreak.getInstance())) {
                    return value.asBoolean();
                }
            }
        }
        return false;
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        if (subAbilityName != null) {
            switch (subAbilityName) {
                case "Tenor's Note":
                    return String.format(
                            "§d%s §5had their will shattered by §d%s§5's haunting melody",
                            victimName, casterName
                    );
                case "Baritone's Blast":
                    return String.format(
                            "§d%s §5was obliterated by §d%s§5's bone choir resonance",
                            victimName, casterName
                    );
                default:
                    return String.format(
                            "§d%s §5succumbed to §d%s§5's bone choir",
                            victimName, casterName
                    );
            }
        }
        return String.format(
                "§d%s §5was defeated by §d%s§5's bone choir",
                victimName, casterName
        );
    }
}

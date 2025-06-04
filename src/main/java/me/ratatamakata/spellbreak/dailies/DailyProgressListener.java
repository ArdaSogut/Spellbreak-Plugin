package me.ratatamakata.spellbreak.dailies;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.dailies.DailyMission;
import me.ratatamakata.spellbreak.dailies.DailyMissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
// Other imports for VISIT_STRUCTURE, CRAFT, etc.

public class DailyProgressListener implements Listener {
    private final DailyMissionManager manager;

    public DailyProgressListener(Spellbreak plugin) {
        this.manager = plugin.getDailyMissionManager();
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player)) return;
        Player p = e.getEntity().getKiller();
        for (DailyMission m : manager.getMissions(p.getUniqueId())) {
            if (m.getType() == DailyMission.Type.KILL && e.getEntityType().name().equals(m.getTarget())) {
                manager.incrementProgress(p.getUniqueId(), m.getKey());
            }
        }
    }

    @EventHandler
    public void onMine(BlockBreakEvent e) {
        Player p = e.getPlayer();
        for (DailyMission m : manager.getMissions(p.getUniqueId())) {
            if (m.getType() == DailyMission.Type.COLLECT && e.getBlock().getType().name().equals(m.getTarget())) {
                manager.incrementProgress(p.getUniqueId(), m.getKey());
            }
        }
    }

    // Stubs for other types:
    // @EventHandler public void onVisitStructure(...)
    // @EventHandler public void onCraft(...)
}
// src/main/java/me/ratatamakata/spellbreak/listeners/PlayerDataListener.java
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerDataListener implements Listener {
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Spellbreak.getInstance().getPlayerDataManager().loadData(u);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Spellbreak.getInstance().getPlayerDataManager().saveData(u);
    }
}

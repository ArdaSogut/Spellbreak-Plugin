package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.classes.SpellClass;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import me.ratatamakata.spellbreak.managers.SpellClassManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;

public class ChatClassTagListener implements Listener {

    private final PlayerDataManager playerDataManager = Spellbreak.getInstance().getPlayerDataManager();
    private final SpellClassManager spellClassManager = Spellbreak.getInstance().getSpellClassManager();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String className = playerDataManager.getPlayerClass(player.getUniqueId());

        String coloredClass = getColoredClassName(className);
        String nameColor = getPrimaryColor(className);

        event.setFormat(coloredClass + " " + nameColor + "%1$s: §7%2$s");
    }

    private String getColoredClassName(String className) {
        if (className == null) return "§7[§8None§7]";

        return switch (className.toLowerCase()) {
            case "necromancer" -> rgbGradient("[Necromancer]", "#FFAA00", "#FF5500");
            case "archdruid" -> rgbGradient("[Archdruid]", "#55FF55", "#00AA00");
            case "mindshaper" -> rgbGradient("[Mindshaper]", "#FF55FF", "#AA00AA");
            case "lightbringer" -> rgbGradient("[Lightbringer]", "#DCDE54", "#E1E897");
            case "runesmith" -> rgbGradient("[Runesmith]", "#3d74cc", "#cfcb7e");
            case "elementalist" -> rgbGradient("[Elementalist]", "#874b26", "#851b43");
            default -> rgbSolid("[" + className + "]", "#AAAAAA");
        };
    }

    // New method to get primary color for player names
    private String getPrimaryColor(String className) {
        if (className == null) return convertHexToColor("#AAAAAA");

        return switch (className.toLowerCase()) {
            case "necromancer" -> convertHexToColor("#FFAA00");
            case "archdruid" -> convertHexToColor("#55FF55");
            case "mindshaper" -> convertHexToColor("#FF55FF");
            case "lightbringer" -> convertHexToColor("#DCDE54");
            default -> convertHexToColor("#AAAAAA");
        };
    }

    // Existing helper methods remain the same
    private String rgbSolid(String text, String hexColor) {
        return convertHexToColor(hexColor) + text + "§r";
    }

    private String rgbGradient(String text, String startHex, String endHex) {
        StringBuilder builder = new StringBuilder();
        int length = text.length();
        Color start = hexToColor(startHex);
        Color end = hexToColor(endHex);

        for (int i = 0; i < length; i++) {
            double ratio = (double) i / (length - 1);
            Color interpolated = interpolateColor(start, end, ratio);
            builder.append(convertHexToColor(String.format("#%02x%02x%02x",
                            interpolated.getRed(),
                            interpolated.getGreen(),
                            interpolated.getBlue())))
                    .append(text.charAt(i));
        }
        return builder.toString();
    }

    private String convertHexToColor(String hex) {
        hex = hex.replace("#", "");
        StringBuilder builder = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            builder.append("§").append(c);
        }
        return builder.toString();
    }

    private Color hexToColor(String hex) {
        hex = hex.replace("#", "");
        return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        );
    }

    private Color interpolateColor(Color start, Color end, double ratio) {
        int red = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
        int green = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
        int blue = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
        return new Color(red, green, blue);
    }

    private static class Color {
        private final int red;
        private final int green;
        private final int blue;

        public Color(int red, int green, int blue) {
            this.red = Math.min(255, Math.max(0, red));
            this.green = Math.min(255, Math.max(0, green));
            this.blue = Math.min(255, Math.max(0, blue));
        }

        public int getRed() { return red; }
        public int getGreen() { return green; }
        public int getBlue() { return blue; }
    }
}
package me.ratatamakata.spellbreak.dailies;

import org.bukkit.configuration.ConfigurationSection;

public class DailyMission {
    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum Type { KILL, COLLECT, VISIT_STRUCTURE, CRAFT, SLEEP, ENCHANT }

    private final String key;
    private final Type type;
    private final String target;
    private final int amount;
    private final String description;
    private final Difficulty difficulty;
    private final int xpReward;

    public DailyMission(String key, Type type, String target, int amount,
                        String description, Difficulty difficulty, int xpReward) {
        this.key = key;
        this.type = type;
        this.target = target;
        this.amount = amount;
        this.description = description;
        this.difficulty = difficulty;
        this.xpReward = xpReward;
    }

    public static DailyMission fromConfig(String key, ConfigurationSection section) {
        Type type = Type.valueOf(section.getString("type", "KILL"));
        String target = section.getString("target", "");
        int amount = section.getInt("amount", 1);
        String desc = section.getString("description", "");
        Difficulty diff = Difficulty.valueOf(section.getString("difficulty", "EASY"));
        int xp = section.getInt("xpReward", 0);
        return new DailyMission(key, type, target, amount, desc, diff, xp);
    }

    public String getKey() { return key; }
    public Type getType() { return type; }
    public String getTarget() { return target; }
    public int getAmount() { return amount; }
    public String getDescription() { return description; }
    public Difficulty getDifficulty() { return difficulty; }
    public int getXpReward() { return xpReward; }

}
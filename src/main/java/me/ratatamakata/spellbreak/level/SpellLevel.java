package me.ratatamakata.spellbreak.level;

public class SpellLevel {
    private final String spellName;
    private int level;
    private int experience;

    public SpellLevel(String spellName) {
        this.spellName = spellName;
        this.level = 1;
        this.experience = 0;
    }

    public SpellLevel(String spellName, int level, int experience) {
        this.spellName = spellName;
        this.level = level;
        this.experience = experience;
    }

    public int getExperienceForNextLevel() {
        return 50 * level * level; // Less XP needed for spells than player levels
    }

    public boolean addExperience(int exp) {
        experience += exp;
        boolean leveledUp = false;

        while (experience >= getExperienceForNextLevel() && level < 5) {
            experience -= getExperienceForNextLevel();
            level++;
            leveledUp = true;
        }

        return leveledUp;
    }

    public double getExperiencePercentage() {
        if (level >= 5) return 100.0;
        return (double) experience / getExperienceForNextLevel() * 100;
    }

    // Level-based multipliers for spell effects
    public double getDamageMultiplier() {
        return 1.0 + ((level - 1) * 0.2); // +20% damage per level
    }

    public double getRangeMultiplier() {
        return 1.0 + ((level - 1) * 0.1); // +10% range per level
    }
    public double getDurationMultiplier() {
        return 1.0 + ((level - 1) * 0.15); // +15% duration per level
    }

    public double getCooldownReduction() {
        return Math.max(0.5, 1.0 - ((level - 1) * 0.1)); // -10% cooldown per level, min 50%
    }

    public double getManaCostReduction() {
        return Math.max(0.7, 1.0 - ((level - 1) * 0.075)); // -7.5% mana cost per level, min 70%
    }

    // Getters and setters
    public String getSpellName() { return spellName; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(5, level)); }
    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = Math.max(0, experience); }
}
package me.ratatamakata.spellbreak.level;

public class PlayerLevel {
    private int level;
    private int experience;
    private int maxHealth;
    private int maxMana;
    private double spellPower;
    private double manaRegenRate;

    public PlayerLevel() {
        this.level = 1;
        this.experience = 0;
        calculateStats();
    }

    public PlayerLevel(int level, int experience) {
        this.level = level;
        this.experience = experience;
        calculateStats();
    }

    private void calculateStats() {
        // Base stats at level 1: 20 health, 100 mana
        this.maxHealth = 20 + ((level - 1) * 2); // +2 health per level
        this.maxMana = 1000 + ((level - 1) * 10); // +10 mana per level
        this.spellPower = 1.0 + ((level - 1) * 0.05); // +5% spell power per level
        this.manaRegenRate = 1.0 + ((level - 1) * 0.1); // +10% mana regen per level
    }

    public int getExperienceForNextLevel() {
        return 100 * level * level; // Exponential XP requirement
    }

    public int getExperienceProgress() {
        return experience;
    }

    public double getExperiencePercentage() {
        return (double) experience / getExperienceForNextLevel() * 100;
    }

    public boolean addExperience(int exp) {
        experience += exp;
        boolean leveledUp = false;

        while (experience >= getExperienceForNextLevel() && level < 100) {
            experience -= getExperienceForNextLevel();
            level++;
            calculateStats();
            leveledUp = true;
        }

        return leveledUp;
    }

    // Getters and setters
    public int getLevel() { return level; }
    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(100, level));
        calculateStats();
    }

    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = Math.max(0, experience); }

    public int getMaxHealth() { return maxHealth; }
    public int getMaxMana() { return maxMana; }
    public double getSpellPower() { return spellPower; }
    public double getManaRegenRate() { return manaRegenRate; }
}
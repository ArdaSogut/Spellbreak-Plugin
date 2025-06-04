package me.ratatamakata.spellbreak.ai;

import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Player;

public interface SkeletonAI {
    void update(Skeleton skeleton, Player caster);
}
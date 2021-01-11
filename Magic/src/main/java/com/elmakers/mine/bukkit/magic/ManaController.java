package com.elmakers.mine.bukkit.magic;

import org.bukkit.entity.Player;

public interface ManaController {
    int getMaxMana(Player player);
    int getManaRegen(Player player);
    float getMana(Player player);
    void removeMana(Player player, float amount);
    void setMana(Player player, float amount);

    float getSecondaryMana(Player player);
    void removeSecondaryMana(Player player, float amount);
    void setSecondaryMana(Player player, float amount);
}

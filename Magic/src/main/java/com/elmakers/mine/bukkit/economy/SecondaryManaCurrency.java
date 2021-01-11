package com.elmakers.mine.bukkit.economy;

import com.elmakers.mine.bukkit.api.magic.CasterProperties;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;

public class SecondaryManaCurrency extends BaseMagicCurrency {
    public SecondaryManaCurrency(MageController controller) {
        super(controller, "secondary_mana", 1);
    }

    @Override
    public double getBalance(Mage mage, CasterProperties caster) {
        if (caster == null) {
            caster = mage.getActiveProperties();
        }
        return caster.getSecondaryMana();
    }

    @Override
    public boolean has(Mage mage, CasterProperties caster, double amount) {
        if (caster == null) {
            caster = mage.getActiveProperties();
        }
        return caster.getSecondaryMana() >= amount;
    }

    @Override
    public void deduct(Mage mage, CasterProperties caster, double amount) {
        if (caster == null) {
            caster = mage.getActiveProperties();
        }
        caster.removeSecondaryMana((float)amount);
    }

    @Override
    public boolean give(Mage mage, CasterProperties caster, double amount) {
        if (caster == null) {
            caster = mage.getActiveProperties();
        }
        float newSecondaryMana = (float)Math.min(caster.getSecondaryMana(), caster.getMana() + amount);
        caster.setSecondaryMana(newSecondaryMana);
        return true;
    }
}

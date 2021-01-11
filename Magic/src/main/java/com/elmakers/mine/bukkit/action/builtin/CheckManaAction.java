package com.elmakers.mine.bukkit.action.builtin;

import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.action.CheckAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.Mage;

public class CheckManaAction extends CheckAction {
    private boolean requireNotFull = false;
    private boolean requireEmpty = false;
    private double requireAmount = 0;
    private double requirePercentage = 0;
    private boolean requireNotFullSecondary = false;
    private boolean requireEmptySecondary = false;
    private double requireAmountSecondary = 0;

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        requirePercentage = parameters.getDouble("require_mana_percentage", 0);
        requireAmount = parameters.getDouble("require_mana", 0);
        requireNotFull = parameters.getBoolean("require_mana_not_full", false);
        requireEmpty = parameters.getBoolean("require_mana_empty", false);
        requireAmountSecondary = parameters.getDouble("require_secondary_mana", 0);
        requireNotFullSecondary = parameters.getBoolean("require_secondary_mana_not_full", false);
        requireEmptySecondary = parameters.getBoolean("require_secondary_mana_empty", false);
    }

    @Override
    protected boolean isAllowed(CastContext context) {
        Mage mage = context.getMage();
        double currentMana = mage.getMana();
        if (requireAmount > 0 && currentMana < requireAmount) {
            return false;
        }
        if (requireEmpty && currentMana > 0) {
            return false;
        }
        int manaMax = mage.getEffectiveManaMax();
        if (requireNotFull && currentMana >= manaMax) {
            return false;
        }
        if (requirePercentage > 0 && manaMax > 0 && currentMana / (double)manaMax < requirePercentage) {
            return false;
        }
        return true;
    }

    protected boolean isSecondaryAllowed(CastContext context) {
        Mage mage = context.getMage();
        double currentSecondaryMana = mage.getSecondaryMana();
        if (requireAmount > 0 && currentSecondaryMana < requireAmount) {
            return false;
        }
        if (requireEmpty && currentSecondaryMana > 0) {
            return false;
        }
        if (requireNotFull && currentSecondaryMana >= 1) {
            return false;
        }
        return true;
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }
}

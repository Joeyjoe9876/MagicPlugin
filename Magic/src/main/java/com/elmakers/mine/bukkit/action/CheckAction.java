package com.elmakers.mine.bukkit.action;

import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.api.action.ActionHandler;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;

public abstract class CheckAction extends CompoundAction {
    private boolean invert;

    protected abstract boolean isAllowed(CastContext context);
    protected abstract boolean isSecondaryAllowed(CastContext context);

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        invert = parameters.getBoolean("invert", false);
    }

    @Override
    protected void addHandlers(Spell spell, ConfigurationSection parameters) {
        addHandler(spell, "actions");
        addHandler(spell, "fail");
    }

    @Override
    public SpellResult step(CastContext context) {
        boolean allowed = isAllowed(context);
        boolean allowedSecondary = isSecondaryAllowed(context);
        if (invert) {
            allowed = !allowed;
            allowedSecondary = !allowedSecondary;
        }
        if (!allowed || !allowedSecondary) {
            ActionHandler fail = getHandler("fail");
            if (fail != null && fail.size() != 0) {
                return startActions("fail");
            }
        }
        ActionHandler actions = getHandler("actions");
        if (actions == null || actions.size() == 0) {
            return allowed ? SpellResult.CAST : SpellResult.STOP;
            }

        if (!allowed) {
            return SpellResult.NO_TARGET;
        }
        if (actions == null || actions.size() == 0) {
            return allowedSecondary ? SpellResult.CAST : SpellResult.STOP;
        }

        if (!allowedSecondary) {
            return SpellResult.NO_TARGET;
        }
        return startActions();
    }
}

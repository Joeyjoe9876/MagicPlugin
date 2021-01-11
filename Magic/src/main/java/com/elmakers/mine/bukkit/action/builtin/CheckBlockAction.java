package com.elmakers.mine.bukkit.action.builtin;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.action.CheckAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.block.MaterialBrush;
import com.elmakers.mine.bukkit.api.magic.MaterialSet;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.magic.SourceLocation;

public class CheckBlockAction extends CheckAction {
    private MaterialSet allowed;
    private boolean useTarget;
    private BlockFace direction;
    private int directionCount;
    private boolean setTarget;
    private boolean allowBrush;
    private SourceLocation sourceLocation;

    @Override
    public void initialize(Spell spell, ConfigurationSection parameters)
    {
        super.initialize(spell, parameters);

        allowed = spell.getController().getMaterialSetManager()
                .fromConfig(parameters.getString("allowed"));
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        useTarget = parameters.getBoolean("use_target", true);
        setTarget = parameters.getBoolean("set_target", false);
        allowBrush = parameters.getBoolean("allow_brush", false);
        sourceLocation = new SourceLocation(parameters.getString("source_location", "BLOCK"), !useTarget);
        directionCount = parameters.getInt("direction_count", 1);
        String directionString = parameters.getString("direction");
        if (directionString != null && !directionString.isEmpty()) {
            try {
                direction = BlockFace.valueOf(directionString.toUpperCase());
            } catch (Exception ex) {
                context.getLogger().warning("Invalid BlockFace direction: " + directionString);
            }
        }
    }

    @Override
    protected boolean isAllowed(CastContext context) {
        MaterialBrush brush = context.getBrush();
        Block block = sourceLocation.getBlock(context);
        if (block == null) {
            return false;
        }
        if (direction != null) {
            for (int i = 0; i < directionCount; i++) {
                block = block.getRelative(direction);
            }
        }
        boolean isAllowed = false;
        if (allowBrush) {
            isAllowed = brush != null && !brush.isDifferent(block);
        }
        if (!isAllowed && allowed != null) {
            isAllowed = allowed.testBlock(block);
        }
        if (!isAllowed && !allowBrush && allowed == null) {
            isAllowed = true;
            if (brush != null && brush.isErase()) {
                if (!context.hasBreakPermission(block)) {
                    isAllowed = false;
                }
            } else {
                if (!context.hasBuildPermission(block)) {
                    isAllowed = false;
                }
            }
            if (!context.isDestructible(block)) {
                isAllowed = false;
            }
        }

        if (setTarget && isAllowed) {
            createActionContext(context, context.getEntity(), null, context.getTargetEntity(), block.getLocation());
        }

        return isAllowed;
    }

    @Override
    protected boolean isSecondaryAllowed(CastContext context) {
        MaterialBrush brush = context.getBrush();
        Block block = sourceLocation.getBlock(context);
        if (block == null) {
            return false;
        }
        if (direction != null) {
            for (int i = 0; i < directionCount; i++) {
                block = block.getRelative(direction);
            }
        }
        boolean isAllowed = false;
        if (allowBrush) {
            isAllowed = brush != null && !brush.isDifferent(block);
        }
        if (!isAllowed && allowed != null) {
            isAllowed = allowed.testBlock(block);
        }
        if (!isAllowed && !allowBrush && allowed == null) {
            isAllowed = true;
            if (brush != null && brush.isErase()) {
                if (!context.hasBreakPermission(block)) {
                    isAllowed = false;
                }
            } else {
                if (!context.hasBuildPermission(block)) {
                    isAllowed = false;
                }
            }
            if (!context.isDestructible(block)) {
                isAllowed = false;
            }
        }

        if (setTarget && isAllowed) {
            createActionContext(context, context.getEntity(), null, context.getTargetEntity(), block.getLocation());
        }

        return isAllowed;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }
}

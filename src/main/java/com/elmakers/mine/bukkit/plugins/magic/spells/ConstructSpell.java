package com.elmakers.mine.bukkit.plugins.magic.spells;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.blocks.ConstructBatch;
import com.elmakers.mine.bukkit.blocks.ConstructionType;
import com.elmakers.mine.bukkit.effects.EffectUtils;
import com.elmakers.mine.bukkit.effects.ParticleType;
import com.elmakers.mine.bukkit.plugins.magic.BrushSpell;
import com.elmakers.mine.bukkit.plugins.magic.MaterialBrush;
import com.elmakers.mine.bukkit.plugins.magic.SpellResult;
import com.elmakers.mine.bukkit.utilities.borrowed.ConfigurationNode;

public class ConstructSpell extends BrushSpell
{
	private ConstructionType defaultConstructionType = ConstructionType.SPHERE;
	private int				defaultRadius			= 2;
	private Block targetBlock 						= null;
	
	private static final int DEFAULT_MAX_DIMENSION = 128;

	@Override
	public SpellResult onCast(ConfigurationNode parameters) 
	{
		targetThrough(Material.GLASS);
		Block target = getTarget().getBlock();

		if (target == null)
		{
			initializeTargeting(getPlayer());
			noTargetThrough(Material.GLASS);
			target = getTarget().getBlock();
		}

		if (target == null)
		{
			castMessage("No target");
			return SpellResult.NO_TARGET;
		}

		int timeToLive = parameters.getInt("undo", 0);
		int radius = parameters.getInt("radius", defaultRadius);
		radius = parameters.getInt("size", radius);
		boolean falling = parameters.getBoolean("falling", false);
		float force = 0;
		force = (float)parameters.getDouble("speed", force);
		
		String targetString = parameters.getString("target", "");
		if (targetString.equals("select")) {
			if (targetBlock == null) {
				targetBlock = target;
				Location effectLocation = targetBlock.getLocation();
				effectLocation.add(0.5f, 0.5f, 0.5f);
				EffectUtils.playEffect(effectLocation, ParticleType.HAPPY_VILLAGER, 0.3f, 0.3f, 0.3f, 1.5f, 10);
				castMessage("Cast again to construct");
				activate();
				return SpellResult.COST_FREE;
			} else {
				radius = (int)targetBlock.getLocation().distance(target.getLocation());
				target = targetBlock;
			}
		} else {
			radius = (int)(mage.getRadiusMultiplier() * (float)radius);			
		}

		int maxDimension = (int)(mage.getConstructionMultiplier() * (float)parameters.getInteger("max_dimension", DEFAULT_MAX_DIMENSION));

		int diameter = radius * 2;
		if (diameter > maxDimension)
		{
			sendMessage("Dimension is too big!");
			return SpellResult.FAILURE;
		}
		
		if (parameters.containsKey("y_offset")) {
			target = target.getRelative(BlockFace.UP, parameters.getInt("y_offset", 0));
		}
		
		if (!hasBuildPermission(target)) {
			return SpellResult.INSUFFICIENT_PERMISSION;
		}

		MaterialBrush buildWith = getMaterialBrush();
		buildWith.setTarget(target.getLocation());

		ConstructionType conType = defaultConstructionType;

		boolean hollow = false;
		String fillType = (String)parameters.getString("fill", "");
		hollow = fillType.equals("hollow");
		
		Vector forceVector = null;
		if (falling)
		{
			if (force != 0) {
				forceVector = getPlayer().getLocation().getDirection();
				forceVector.setY(-forceVector.getY()).normalize().multiply(force);
			}
		}
		String typeString = parameters.getString("type", "");

		ConstructionType testType = ConstructionType.parseString(typeString, ConstructionType.UNKNOWN);
		if (testType != ConstructionType.UNKNOWN)
		{
			conType = testType;
		}

		fillArea(target, radius, buildWith, !hollow, conType, timeToLive, falling, forceVector);
		deactivate();

		return SpellResult.SUCCESS;
	}

	public void fillArea(Block target, int radius, MaterialBrush brush, boolean fill, ConstructionType type, int timeToLive, boolean falling, Vector forceVector)
	{
		ConstructBatch batch = new ConstructBatch(this, target.getLocation(), type, radius, fill, falling);
		if (forceVector != null) {
			batch.setFallingBlockVelocity(forceVector);
		}
		if (timeToLive > 0) {
			batch.setTimeToLive(timeToLive);
		}
		controller.addPendingBlockBatch(batch);
	}
	
	
	@Override
	public void onDeactivate() {
		targetBlock = null;
	}

	@Override
	public boolean onCancel()
	{
		if (targetBlock != null)
		{
			sendMessage("Cancelled construct");
			targetBlock = null;
			return true;
		}
		
		return false;
	}
}

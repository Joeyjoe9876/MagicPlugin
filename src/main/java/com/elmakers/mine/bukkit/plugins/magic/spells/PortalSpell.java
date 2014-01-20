package com.elmakers.mine.bukkit.plugins.magic.spells;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.elmakers.mine.bukkit.blocks.BlockList;
import com.elmakers.mine.bukkit.blocks.BoundingBox;
import com.elmakers.mine.bukkit.blocks.MaterialList;
import com.elmakers.mine.bukkit.plugins.magic.Spell;
import com.elmakers.mine.bukkit.plugins.magic.SpellResult;
import com.elmakers.mine.bukkit.utilities.borrowed.ConfigurationNode;

public class PortalSpell extends Spell
{
	@Override
	public SpellResult onCast(ConfigurationNode parameters) 
	{
		targetThrough(Material.GLASS);

		Block target = getTargetBlock();
		if (target == null)
		{
			castMessage("No target");
			return SpellResult.NO_TARGET;
		}
		if (!hasBuildPermission(target)) 
		{
			return SpellResult.INSUFFICIENT_PERMISSION;
		}

		Material blockType = target.getType();
		Block portalBase = target.getRelative(BlockFace.UP);
		blockType = portalBase.getType();
		if (blockType != Material.AIR)
		{
			portalBase = getFaceBlock();
		}

		blockType = portalBase.getType();
		if (blockType != Material.AIR && blockType != Material.SNOW)
		{
			castMessage("Can't create a portal there");
			return SpellResult.NO_TARGET;		
		}

		int timeToLive = parameters.getInt("undo", 5000);
		BlockList portalBlocks = new BlockList();
		portalBlocks.setTimeToLive(timeToLive);
		controller.disablePhysics(1000);
		buildPortalBlocks(portalBase.getLocation(), BlockFace.NORTH, portalBlocks);
		mage.registerForUndo(portalBlocks);

		return SpellResult.SUCCESS;
	}

	protected void buildPortalBlocks(Location centerBlock, BlockFace facing, BlockList blockList)
	{
		MaterialList destructible = new MaterialList(mage.getController().getDestructibleMaterials());
		BoundingBox container = new BoundingBox(centerBlock.getBlockX(), centerBlock.getBlockY(), centerBlock.getBlockZ(), centerBlock.getBlockX() + 2, centerBlock.getBlockY() + 3, centerBlock.getBlockZ() + 1);
		container.fill(centerBlock.getWorld(), Material.PORTAL, destructible, blockList);
	}
}

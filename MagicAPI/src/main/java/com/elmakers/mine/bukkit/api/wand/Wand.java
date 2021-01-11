package com.elmakers.mine.bukkit.api.wand;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.elmakers.mine.bukkit.api.block.MaterialAndData;
import com.elmakers.mine.bukkit.api.effect.WandEffectContext;
import com.elmakers.mine.bukkit.api.magic.CasterProperties;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageClass;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.MagicConfigurable;
import com.elmakers.mine.bukkit.api.spell.CooldownReducer;
import com.elmakers.mine.bukkit.api.spell.CostReducer;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellKey;
import com.elmakers.mine.bukkit.api.spell.SpellTemplate;

/**
 * Represents a Wand that a Mage may use to cast a Spell.
 *
 * <p>Every Wand has an inventory of Spell keys and Material brush keys that it may cast and use.
 *
 * <p>A Wand may also have a variety of properties, including effects, an XP ("Mana") pool for
 * casting Spells with an XP-based CastingCost, and various boosts and protections.
 *
 * <p>Each Wand is backed by an ItemStack, and the Wand stores its data in the ItemStack. A Wand
 * is otherwise not tracked or persistent, other than via the Mage.getActiveWand() method, or
 * via a tracked LostWand record, if the ItemStack can be found.
 */
public interface Wand extends CostReducer, CooldownReducer, CasterProperties {
    String getName();
    String getId();
    long getWorth();
    void closeInventory();
    void deactivate();
    boolean organizeInventory(Mage mage);
    boolean organizeInventory();
    boolean alphabetizeInventory();
    Map<String, Integer> getBrushInventory();
    void updateBrushInventory(Map<String, Integer> updateBrushes);
    Map<String, Integer> getSpellInventory();
    void updateSpellInventory(Map<String, Integer> updateSpells);
    @Nullable
    ItemStack getItem();
    MaterialAndData getIcon();
    MaterialAndData getInactiveIcon();
    void setIcon(MaterialAndData icon);
    void setInactiveIcon(MaterialAndData icon);
    void makeUpgrade();
    @Override
    Collection<String> getSpells();
    @Override
    Collection<String> getBrushes();
    @Override
    void describe(CommandSender sender);
    void unenchant();
    void unlock();
    Wand duplicate();
    @Override
    Spell getSpell(String key);
    @Nullable
    Spell getSpell(String key, Mage mage);
    @Nullable
    @Override
    SpellTemplate getSpellTemplate(String key);
    @Override
    boolean hasSpell(String key);
    boolean hasSpell(SpellKey spellKey);
    @Override
    boolean hasBrush(String key);
    boolean isLocked();
    boolean canUse(Player player);
    boolean fill(Player player);
    boolean fill(Player player, int maxLevel);
    @Deprecated
    boolean add(Wand other, Mage mage);
    @Override
    boolean addItem(ItemStack item);
    @Override
    boolean removeProperty(String key);
    @Override
    boolean addBrush(String key);
    @Override
    boolean addSpell(String key);
    boolean removeBrush(String key);
    boolean removeSpell(String key);
    String getActiveBrushKey();
    String getActiveSpellKey();
    @Nullable
    Spell getActiveSpell();
    void setActiveBrush(String key);
    void setActiveSpell(String key);
    void setName(String name);
    void setDescription(String description);

    LostWand makeLost(Location location);
    boolean isLost(LostWand wand);
    int enchant(int levels);
    int enchant(int levels, Mage mage);
    int enchant(int levels, Mage mage, boolean addSpells);

    Map<String, String> getOverrides();
    void setOverrides(Map<String, String> overrides);
    void removeOverride(String key);
    void setOverride(String key, String value);
    SpellTemplate getBaseSpell(String spellKey);

    boolean isSuperProtected();
    boolean isSuperPowered();
    @Override
    boolean isCostFree();
    @Override
    boolean isConsumeFree();
    @Override
    boolean isCooldownFree();
    float getPower();
    float getHealthRegeneration();
    float getHungerRegeneration();
    @Override
    float getCooldownReduction();
    @Override
    float getCostReduction();
    @Override
    void removeMana(float mana);
    @Override
    float getMana();
    @Override
    int getManaMax();
    @Override
    void setMana(float mana);
    @Override
    void setManaMax(int manaMax);
    @Override
    void removeSecondaryMana(float secondaryMana);
    @Override
    float getSecondaryMana();
    @Override
    void setSecondaryMana(float secondaryMana);
    @Override
    int getManaRegeneration();
    void updateMana();
    void updateSecondaryMana();
    @Override
    WandUpgradePath getPath();
    @Override
    MageController getController();
    boolean showCastMessages();
    boolean showMessages();
    @Nullable
    String getTemplateKey();
    @Nullable
    WandTemplate getTemplate();
    boolean isIndestructible();
    boolean playEffects(String key);
    boolean cast();
    boolean cast(String[] parameters);
    boolean cast(Spell spell);
    boolean isBound();
    boolean isUndroppable();
    boolean isQuickCastDisabled();
    boolean isInventoryOpen();
    boolean isQuickCast();
    void cycleHotbar();
    void setCurrentHotbar(int hotbar);
    int getCurrentHotbar();
    void damageDealt(double damage, Entity target);
    boolean hasTag(String tag);
    void bind();
    void unbind();
    boolean isReflected(double angle);
    boolean isBlocked(double angle);

    /**
     * Save this Wand to a Configuration Section.
     *
     * @param section The section to save to.
     * @param filtered If true, removes item-specific data such as Wand
     *                 id and bound owner.
     */
    void save(ConfigurationSection section, boolean filtered);

    /**
     * Save current Wand state to the ItemStack's NBT data immediately.
     *
     * <p>Wands are saved periodically so this is generally not needed unless
     * you need to force an update right away.
     */
    void saveState();

    /**
     * See if this wand is ready for an upgrade.
     *
     * @param quiet if false, will send messages to a player informing them why they are blocked
     * @return false if the wand is blocked from upgrading for some reason
     */
    boolean checkUpgrade(boolean quiet);

    /**
     * Check to see if this wand has an upgrade on its current path
     *
     * @return true if this wand has a path with an upgrade
     */
    boolean hasUpgrade();

    /**
     * See if this wand can progress. This means the wand can still acquire more spells on its current
     * path, or can otherwise be upgraded in some way via enchanting or the spell menu.
     *
     * @return true if the wand can progress
     */
    @Override
    boolean canProgress();

    /**
     * Trigger an upgrade to the next path.
     *
     * @param quiet if false, will send messages to the player about their upgrade
     * @return true if the upgrade was successful.
     */
    boolean upgrade(boolean quiet);

    /**
     * This method is deprecated, it just converts the Map to a ConfigurationSection.
     *
     * <p>Use {@link MagicConfigurable#upgrade(ConfigurationSection)} instead.
     */
    @Deprecated
    boolean upgrade(Map<String, Object> properties);

    /**
     * This method is deprecated, use {@link Mage#checkWand()} instead.
     * Wands should only ever be active while held.
     */
    @Deprecated
    void activate(Mage mage);

    @Override
    @Nullable
    Color getEffectColor();
    @Override
    String getEffectParticleName();
    @Nullable
    Location getLocation();
    @Override
    Mage getMage();
    @Nullable
    MageClass getMageClass();
    @Nullable
    String getMageClassKey();
    boolean hasInventory();
    int getHeldSlot();
    void setHeldSlot(int slot);
    boolean isInOffhand();

    /**
     * This method is deprecated, it just converts the Map to a ConfigurationSection.
     *
     * <p>Use {@link MagicConfigurable#configure(ConfigurationSection)} instead.
     */
    @Deprecated
    boolean configure(Map<String, Object> properties);

    @Deprecated
    boolean isSoul();

    WandAction getDropAction();
    WandAction getRightClickAction();
    WandAction getLeftClickAction();
    WandAction getSwapAction();
    boolean performAction(WandAction action);
    @Nonnull
    WandEffectContext getEffectContext();
    boolean isEnchantable();
    void setEnchantments(Map<Enchantment, Integer> enchants);
}

package com.elmakers.mine.bukkit.action.builtin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.elmakers.mine.bukkit.action.CompoundAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.action.GUIAction;
import com.elmakers.mine.bukkit.api.item.ItemData;
import com.elmakers.mine.bukkit.api.magic.CasterProperties;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageClass;
import com.elmakers.mine.bukkit.api.magic.MageClassTemplate;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.magic.MagicAttribute;
import com.elmakers.mine.bukkit.api.magic.MagicProperties;
import com.elmakers.mine.bukkit.api.magic.MagicPropertyType;
import com.elmakers.mine.bukkit.api.magic.ProgressionPath;
import com.elmakers.mine.bukkit.api.requirements.Requirement;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.spell.SpellTemplate;
import com.elmakers.mine.bukkit.api.wand.Wand;
import com.elmakers.mine.bukkit.item.Cost;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.InventoryUtils;

import de.slikey.effectlib.math.EquationStore;
import de.slikey.effectlib.math.EquationTransform;

public class SelectorAction extends CompoundAction implements GUIAction
{
    private static final String[] _DEFAULT_COST_FALLBACKS = {"currency", "item"};
    private static final List<String> DEFAULT_COST_FALLBACKS = Arrays.asList(_DEFAULT_COST_FALLBACKS);
    private static final int MAX_INVENTORY_SLOTS = 6 * 9;
    protected SelectorConfiguration defaultConfiguration;
    protected ItemData confirmFillMaterial;
    protected CastContext context;
    private Map<Integer, SelectorOption> showingItems;
    private int itemCount;
    private int numSlots;
    private int has = 0;
    private String title;
    private String confirmTitle;
    private String confirmUnlockTitle;
    private String titleKey;
    private String confirmTitleKey;
    private String confirmUnlockTitleKey;

    // State
    private boolean isActive = false;
    private SpellResult finalResult = null;
    private Inventory displayInventory = null;

    protected class RequirementsResult {
        public final SpellResult result;
        public final String message;

        public RequirementsResult(SpellResult result, String message) {
            this.result = result;
            this.message = message;
        }

        public RequirementsResult(SpellResult result) {
            this(result, context.getMessage(result.name().toLowerCase()));
        }

        @Override
        public String toString() {
            return result.toString() + " " + message;
        }
    }

    protected enum ModifierType { WAND, MAGE, CLASS, ATTRIBUTE }

    protected class CostModifier {
        private ModifierType type;
        private String equation;
        private String property;
        private double defaultValue;

        public CostModifier(ConfigurationSection configuration) {
            String typeString = configuration.getString("type");
            try {
                type = ModifierType.valueOf(typeString.toUpperCase());
            } catch (Exception ex) {
                context.getLogger().warning("Invalid modifier type in selector config: " + typeString);
                type = null;
                return;
            }

            defaultValue = configuration.getDouble("default");
            equation = configuration.getString("scale");
            if (type == ModifierType.ATTRIBUTE) {
                property = configuration.getString("attribute");
            } else {
                property = configuration.getString("property");
            }
        }

        public void modify(Cost cost) {
            if (type == null) return;

            Mage mage = context.getMage();
            double value = defaultValue;
            switch (type) {
                case MAGE:
                    value = mage.getProperties().getProperty(property, value);
                    break;
                case ATTRIBUTE:
                    Double attribute = mage.getAttribute(property);
                    if (attribute != null) {
                        value = attribute;
                    }
                    break;
                case CLASS:
                    CasterProperties activeClass = mage.getActiveClass();
                    if (activeClass != null) {
                        value = activeClass.getProperty(property, value);
                    }
                    break;
                case WAND:
                    Wand wand = mage.getActiveWand();
                    if (wand != null) {
                        value = wand.getProperty(property, value);
                    }
                    break;
            }
            EquationTransform transform = EquationStore.getInstance().getTransform(equation);
            transform.setVariable("x", value);
            double scale = transform.get();
            cost.scale(scale);
        }
    }

    // This is mainly here for MagicMeta interrogation
    public SelectorConfiguration getSelectorOption(ConfigurationSection section) {
        return new SelectorConfiguration(section);
    }

    protected class SelectorConfiguration {
        protected @Nullable ItemStack icon;
        protected @Nullable String iconKey;
        protected @Nullable String iconDisabledKey;
        protected @Nullable String iconPlaceholderKey;
        protected @Nullable List<ItemStack> items;
        protected @Nullable List<Cost> costs = null;
        protected @Nonnull String costType = "currency";
        protected @Nonnull String earnType = "currency";
        protected @Nullable String costOverride = null;
        protected @Nonnull List<String> costTypeFallbacks = DEFAULT_COST_FALLBACKS;
        protected @Nullable String castSpell = null;
        protected @Nullable ConfigurationSection castSpellParameters = null;
        protected @Nullable String unlockClass = null;
        protected @Nullable String lockClass = null;
        protected @Nullable String selectedMessage = null;
        protected @Nullable String selectedFreeMessage = null;
        protected @Nullable String unlockKey = null;
        protected @Nullable String actions = null;
        protected @Nonnull String unlockSection = "unlocked";
        protected @Nullable Collection<Requirement> requirements;
        protected @Nullable List<String> commands;
        protected @Nullable List<CostModifier> costModifiers;
        protected @Nullable List<CostModifier> earnModifiers;
        protected @Nullable List<Cost> earns = null;
        protected @Nullable Map<String,String> alternateSpellTags;
        protected @Nonnull String effects = "selected";
        protected @Nullable String attributeKey = null;
        protected boolean allowAttributeReduction = false;
        protected int attributeAmount = 0;
        protected boolean applyToWand = false;
        protected boolean applyToCaster = false;
        protected MagicPropertyType applyTo = null;
        protected @Nullable String applyToClass = null;
        protected boolean showConfirmation = false;
        protected boolean showUnavailable = false;
        protected boolean switchClass = false;
        protected boolean putInHand = true;
        protected boolean free = false;
        protected boolean applyLoreToItem = false;
        protected boolean applyNameToItem = false;
        protected boolean nameIcon = true;
        protected boolean allowDroppedItems = true;
        protected double costScale = 1;
        protected double earnScale = 1;
        protected boolean autoClose = true;

        protected int limit = 0;

        protected @Nonnull String[] allCostTypes = null;
        protected @Nonnull String[] fallbackCostTypes = null;

        public SelectorConfiguration(ConfigurationSection configuration) {
            parse(configuration);
        }

        protected SelectorConfiguration() {
        }

        protected void parse(ConfigurationSection configuration) {
            applyToWand = configuration.getBoolean("apply_to_wand", applyToWand);
            applyToCaster = configuration.getBoolean("apply_to_caster", applyToCaster);
            applyToClass = configuration.getString("apply_to_class", applyToClass);
            putInHand = configuration.getBoolean("put_in_hand", putInHand);
            castSpell = configuration.getString("cast_spell", castSpell);
            castSpellParameters = configuration.isConfigurationSection("cast_spell_parameters")
                ? configuration.getConfigurationSection("cast_spell_parameters") : castSpellParameters;
            unlockClass = configuration.getString("unlock_class", unlockClass);
            lockClass = configuration.getString("lock_class", lockClass);
            nameIcon = configuration.getBoolean("apply_name_to_icon", nameIcon);
            autoClose = configuration.getBoolean("auto_close", autoClose);
            allowAttributeReduction = configuration.getBoolean("allow_attribute_reduction", allowAttributeReduction);
            if (configuration.contains("switch_class")) {
                switchClass = true;
                unlockClass = configuration.getString("switch_class");
            }
            String applyToString = configuration.getString("apply_to", applyTo == null ? null : applyTo.name());
            if (applyToString != null && !applyToString.isEmpty()) {
                try {
                    applyTo = MagicPropertyType.valueOf(applyToString.toUpperCase());
                } catch (Exception ex) {
                    context.getLogger().warning("Invalid apply_to: " + applyToString);
                }
            } else {
                applyTo = null;
            }
            attributeAmount = configuration.getInt("attribute_amount", attributeAmount);
            attributeKey = configuration.getString("attribute", attributeKey);
            limit = configuration.getInt("limit", limit);
            unlockKey = configuration.getString("unlock", unlockKey);
            unlockSection = configuration.getString("unlock_section", unlockSection);
            showConfirmation = configuration.getBoolean("confirm", showConfirmation);
            costType = configuration.getString("cost_type", costType);
            costOverride = configuration.getString("cost_override", costOverride);
            earnType = configuration.getString("earn_type", earnType);
            // Legacy option was singular
            allCostTypes = null;
            costTypeFallbacks = ConfigurationUtils.getStringList(configuration, "cost_type_fallback", costTypeFallbacks);
            costTypeFallbacks = ConfigurationUtils.getStringList(configuration, "cost_type_fallbacks", costTypeFallbacks);
            actions = configuration.getString("actions", actions);
            showUnavailable = configuration.getBoolean("show_unavailable", showUnavailable);
            commands = ConfigurationUtils.getStringList(configuration, "commands");
            String command = configuration.getString("command");
            if (command != null && !command.isEmpty()) {
                if (commands == null) commands = new ArrayList<>();
                commands.add(command);
            }
            free = configuration.getBoolean("free", free);
            costScale = configuration.getDouble("scale", costScale);
            if (configuration.contains("earn_scale")) {
                earnScale = configuration.getDouble("earn_scale", earnScale);
            } else if (configuration.contains("scale")) {
                earnScale = costScale;
            }
            effects = configuration.getString("effects", effects);
            applyLoreToItem = configuration.getBoolean("apply_lore_to_item", applyLoreToItem);
            applyNameToItem = configuration.getBoolean("apply_name_to_item", applyNameToItem);
            allowDroppedItems = configuration.getBoolean("allow_dropped_items", allowDroppedItems);

            if (costType.isEmpty() || costType.equalsIgnoreCase("none")) {
                free = true;
            }

            selectedMessage = configuration.getString("selected", selectedMessage);
            selectedFreeMessage = configuration.getString("selected_free", selectedFreeMessage);

            Collection<ConfigurationSection> requirementConfigurations = ConfigurationUtils.getNodeList(configuration, "requirements");
            if (requirementConfigurations != null) {
                requirements = new ArrayList<>();
                for (ConfigurationSection requirementConfiguration : requirementConfigurations) {
                    requirements.add(new Requirement(requirementConfiguration));
                }
            }

            if (configuration.contains("item")) {
                items = new ArrayList<>();
                ItemStack item = parseItem(configuration.getString("item"));
                if (item != null) {
                    items.add(item);
                }
            }
            if (configuration.contains("items")) {
                List<String> itemList = configuration.getStringList("items");
                if (itemList.size() > 0) {
                    items = new ArrayList<>();
                    for (String itemKey : itemList) {
                        items.add(parseItem(itemKey));
                    }
                }
            }

            ConfigurationSection altTags = ConfigurationUtils.getConfigurationSection(configuration, "cast_for_tags");
            if (altTags != null) {
                alternateSpellTags = new HashMap<>();
                for (String key : altTags.getKeys(false)) {
                    alternateSpellTags.put(key, altTags.getString(key));
                }
            }

            if (actions != null && !actions.isEmpty()) {
                addHandler(context.getSpell(), actions);
            }

            // Prevent out of bounds exceptions later
            if (items != null && items.isEmpty()) {
                items = null;
            }

            MageController controller = context.getController();
            iconPlaceholderKey = configuration.getString("placeholder_icon");
            iconKey = configuration.getString("icon");
            iconDisabledKey = configuration.getString("icon_disabled");
            costModifiers = parseCostModifiers(configuration, "cost_modifiers");
            earnModifiers = parseCostModifiers(configuration, "earn_modifiers");
            if (!free) {
                costs = parseCosts(ConfigurationUtils.getConfigurationSection(configuration, "costs"));
                double cost = configuration.getDouble("cost");
                if (cost > 0) {
                    if (costs == null) {
                        costs = new ArrayList<>();
                    }
                    Cost optionCost = new com.elmakers.mine.bukkit.item.Cost(context.getController(), costType, cost);
                    if (costOverride != null) {
                        optionCost.convert(controller, costOverride);
                    }
                    optionCost.checkSupported(controller, getCostTypeFallbacks());
                    optionCost.scale(controller.getWorthBase());
                    optionCost.scale(costScale);
                    costs.add(optionCost);
                } else if (configuration.isString("cost")) {
                    if (costs == null) {
                        costs = new ArrayList<>();
                    }
                    costs.add(new com.elmakers.mine.bukkit.item.Cost(context.getController(), configuration.getString("cost"), 1));
                }

                if (costs == null && items != null) {
                    costs = new ArrayList<>();
                    for (ItemStack item : items) {
                        Cost itemCost = null;
                        String spellKey = controller.getSpell(item);
                        if (spellKey == null) {
                            Double worth = controller.getWorth(item);
                            if (worth != null && worth > 0) {
                                itemCost = new com.elmakers.mine.bukkit.item.Cost(context.getController(), costType, worth);
                            }
                        } else {
                            SpellTemplate spell = controller.getSpellTemplate(spellKey);
                            itemCost = (Cost)spell.getCost();
                        }
                        if (itemCost != null) {
                            if (costOverride != null) {
                                itemCost.convert(controller, costOverride);
                                itemCost.checkSupported(controller, getCostTypeFallbacks());
                            } else {
                                itemCost.checkSupported(controller, getAllCostTypes());
                            }
                            itemCost.scale(controller.getWorthBase());
                            itemCost.scale(costScale);
                            costs.add(itemCost);
                        }
                    }
                }
            }

            if ((applyNameToItem || applyLoreToItem) && items != null) {
                for (ItemStack item : items) {
                    ItemMeta meta = item.getItemMeta();
                    String customName = configuration.getString("name");
                    if (applyNameToItem && customName != null  && !customName.isEmpty()) {
                        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', customName));
                    }
                    List<String> lore = configuration.contains("lore") ? configuration.getStringList("lore") : null;
                    if (applyLoreToItem && lore != null) {
                        List<String> translated = new ArrayList<>();
                        for (String line : lore) {
                            translated.add(ChatColor.translateAlternateColorCodes('&', line));
                        }
                        meta.setLore(translated);
                    }
                    item.setItemMeta(meta);
                }
            }

            earns = parseCosts(ConfigurationUtils.getConfigurationSection(configuration, "earns"));
            double earn = configuration.getDouble("earn");
            if (earn > 0) {
                if (earns == null) {
                    earns = new ArrayList<>();
                }

                Cost earnCost = new com.elmakers.mine.bukkit.item.Cost(context.getController(), earnType, earn);
                earnCost.checkSupported(controller, getAllCostTypes());
                earnCost.scale(controller.getWorthBase());
                earnCost.scale(earnScale);
                earns.add(earnCost);
            }
        }

        @Nullable
        protected List<Cost> parseCosts(ConfigurationSection node) {
            return Cost.parseCosts(node, context.getController());
        }

        @Nullable
        protected List<CostModifier> parseCostModifiers(ConfigurationSection configuration, String section) {
            Collection<ConfigurationSection> modifierConfigs = ConfigurationUtils.getNodeList(configuration, section);
            if (modifierConfigs == null) {
                return null;
            }
            List<CostModifier> modifiers = new ArrayList<>();
            for (ConfigurationSection modifierConfig : modifierConfigs) {
                modifiers.add(new CostModifier(modifierConfig));
            }

            return modifiers;
        }

        public boolean hasLimit() {
            return limit > 0;
        }

        public String getCostType() {
            return costType;
        }

        public String[] getAllCostTypes() {
            if (allCostTypes == null) {
                allCostTypes = new String[costTypeFallbacks.size() + 1];
                for (int i = 0; i < costTypeFallbacks.size(); i++) {
                    allCostTypes[i + 1] = costTypeFallbacks.get(i);
                }
            }
            allCostTypes[0] = costType;
            return allCostTypes;
        }

        public String[] getCostTypeFallbacks() {
            if (fallbackCostTypes == null) {
                fallbackCostTypes = new String[costTypeFallbacks.size()];
                fallbackCostTypes = costTypeFallbacks.toArray(fallbackCostTypes);
            }
            return fallbackCostTypes;
        }

        public boolean has(CastContext context) {
            Mage mage = context.getMage();
            if (unlockClass != null && !unlockClass.isEmpty()) {
                if (mage.hasClassUnlocked(unlockClass)) {
                    return true;
                }
            }

            return false;
        }

        public RequirementsResult checkRequirements(CastContext context) {
            MageController controller = context.getController();
            Mage mage = context.getMage();
            Player player = mage.getPlayer();
            if (player == null) {
                return new SelectorAction.RequirementsResult(SpellResult.PLAYER_REQUIRED);
            }

            if (limit > 0 && has >= limit) {
                return new RequirementsResult(SpellResult.NO_TARGET, getMessage("at_limit").replace("$limit", Integer.toString(limit)));
            }

            if (unlockClass != null && !unlockClass.isEmpty()) {
                if (mage.hasClassUnlocked(unlockClass)) {
                    return new RequirementsResult(SpellResult.NO_TARGET, getMessage("has_class").replace("$class", unlockClass));
                }
            }

            if (requirements != null) {
                String message = controller.checkRequirements(context, requirements);
                if (message != null) {
                    return new RequirementsResult(SpellResult.NO_TARGET, message);
                }
            }

            return new RequirementsResult(SpellResult.CAST);
        }

        public boolean isUnlock() {
            return unlockKey != null && !unlockKey.isEmpty();
        }

        public boolean showIfUnavailable() {
            return showUnavailable;
        }
    }

    protected class SelectorOption extends SelectorConfiguration {
        protected Integer slot = null;
        protected String name = null;
        protected String description = null;
        protected List<String> lore = null;
        protected String unavailableMessage;
        protected boolean placeholder;
        protected boolean unavailable;
        protected SelectorConfiguration defaults;
        protected Double startingAttributeValue;

        public SelectorOption(SelectorConfiguration defaults, ConfigurationSection configuration, CastContext context) {
            super();

            this.defaults = defaults;
            this.selectedMessage = defaults.selectedMessage;
            this.selectedFreeMessage = defaults.selectedFreeMessage;
            this.items = defaults.items;
            this.costs = defaults.costs;
            this.earnScale = defaults.earnScale;
            this.costScale = defaults.costScale;
            this.castSpell = defaults.castSpell;
            this.castSpellParameters = defaults.castSpellParameters;
            this.applyToWand = defaults.applyToWand;
            this.applyToCaster = defaults.applyToCaster;
            this.applyTo = defaults.applyTo;
            this.applyToClass = defaults.applyToClass;
            this.attributeKey = defaults.attributeKey;
            this.attributeAmount = defaults.attributeAmount;
            this.allowAttributeReduction = defaults.allowAttributeReduction;
            this.unlockClass = defaults.unlockClass;
            this.lockClass = defaults.lockClass;
            this.switchClass = defaults.switchClass;
            this.putInHand = defaults.putInHand;
            this.limit = defaults.limit;
            this.unlockKey = defaults.unlockKey;
            this.unlockSection = defaults.unlockSection;
            this.showConfirmation = defaults.showConfirmation;
            this.costType = defaults.costType;
            this.costTypeFallbacks = defaults.costTypeFallbacks;
            this.earnType = defaults.earnType;
            this.showUnavailable = defaults.showUnavailable;
            this.commands = defaults.commands;
            this.actions = defaults.actions;
            this.free = defaults.free;
            this.costOverride = defaults.costOverride;
            this.effects = defaults.effects;
            this.applyLoreToItem = defaults.applyLoreToItem;
            this.applyNameToItem = defaults.applyNameToItem;
            this.nameIcon = defaults.nameIcon;
            this.allowDroppedItems = defaults.allowDroppedItems;
            this.iconKey = defaults.iconKey;
            this.iconPlaceholderKey = defaults.iconPlaceholderKey;
            this.iconDisabledKey = defaults.iconDisabledKey;
            this.autoClose = defaults.autoClose;
            this.lore = configuration.contains("lore") ? configuration.getStringList("lore") : new ArrayList<>();

            placeholder = configuration.getBoolean("placeholder") || configuration.getString("item", "").equals("none");
            if (placeholder) {
                this.icon = parseItem(iconPlaceholderKey);
                if (icon == null) {
                    this.icon = new ItemStack(Material.AIR);
                } else {
                    icon = InventoryUtils.makeReal(icon);
                    InventoryUtils.makeUnbreakable(icon);
                    InventoryUtils.hideFlags(icon, 63);
                    ItemMeta meta = icon.getItemMeta();
                    meta.setDisplayName(" ");
                    icon.setItemMeta(meta);
                }
                return;
            }

            parse(configuration);

            if (defaults.requirements != null) {
                if (requirements == null) {
                    requirements = defaults.requirements;
                } else {
                    requirements.addAll(defaults.requirements);
                }
            }

            if (configuration.contains("slot")) {
                slot = configuration.getInt("slot");
            }
            name = configuration.getString("name", "");

            MageController controller = context.getController();
            if (name.isEmpty() && unlockClass != null && !unlockClass.isEmpty()) {
                MageClassTemplate mageClass = controller.getMageClassTemplate(unlockClass);
                name = getMessage("unlock_class");
                if (mageClass != null) {
                    name = name.replace("$class", mageClass.getName());
                } else {
                    controller.getLogger().warning("Unknown class in selector config: " + unlockClass);
                }
            }
            String castSpell = getCastSpell(context.getWand());
            if (name.isEmpty() && castSpell != null && !castSpell.isEmpty()) {
                SpellTemplate spell = controller.getSpellTemplate(castSpell);
                name = getMessage("cast_spell");
                if (spell != null) {
                    name = name.replace("$spell", spell.getName());
                } else {
                    controller.getLogger().warning("Unknown spell in selector config: " + castSpell);
                }
            }

            MagicAttribute attribute = attributeKey == null ? null : controller.getAttribute(attributeKey);
            if (name.isEmpty() && attribute != null) {
                name = attribute.getName(controller.getMessages());
                if (attributeAmount != 0) {
                    String template = attributeAmount < 0 ? getMessage("decrease_attribute") : getMessage("increase_attribute");
                    name = template.replace("$attribute", name)
                        .replace("$amount", Integer.toString(Math.abs(attributeAmount)));
                }
            }
            if (attributeKey != null) {
                startingAttributeValue = context.getMage().getAttribute(attributeKey);
            }

            if (name.isEmpty() && items != null) {
                ItemStack item = items.get(0);
                name = controller.describeItem(item);
                if (item.getAmount() > 1) {
                    String template = getMessage("item_amount");
                    name = template.replace("$name", name).replace("$amount", Integer.toString(item.getAmount()));
                }
            }

            if (name.isEmpty() && iconKey != null) {
                ItemStack icon = parseItem(iconKey);
                name = controller.describeItem(icon);
                if (icon.getAmount() > 1) {
                    String template = getMessage("item_amount");
                    name = template.replace("$name", name).replace("$amount", Integer.toString(icon.getAmount()));
                }
            }

            name = ChatColor.translateAlternateColorCodes('&', name);
            description = configuration.getString("description");
            if (description == null) {
                if (unlockClass != null && !unlockClass.isEmpty()) {
                    MageClassTemplate mageClass = controller.getMageClassTemplate(unlockClass);
                    if (mageClass != null) {
                        description = mageClass.getDescription();
                    } else {
                        controller.getLogger().warning("Unknown class in selector config: " + unlockClass);
                    }
                } else if (castSpell != null && !castSpell.isEmpty()) {
                    SpellTemplate spell = controller.getSpellTemplate(castSpell);
                    if (spell == null) {
                        controller.getLogger().warning("Unknown spell in selector config: " + castSpell);
                    } else {
                        description = spell.getDescription();
                    }
                } else if (attribute != null && attributeAmount == 0) {
                    description = attribute.getDescription(controller.getMessages());
                }
            }

            updateIcon(context);
        }

        public void updateIcon(CastContext context) {
            MageController controller = context.getController();

            unavailable = false;
            icon = parseItem(iconKey);
            if (icon != null && icon.hasItemMeta()) {
                ItemMeta meta = icon.getItemMeta();
                meta.setLore(null);
                icon.setItemMeta(meta);
            }

            List<String> lore = new ArrayList<>();
            if (this.lore != null) {
                lore.addAll(this.lore);
            }

            if (description != null && !description.isEmpty()) {
                InventoryUtils.wrapText(description, lore);
            }

            boolean unlocked = false;
            if (unlockKey != null && !unlockKey.isEmpty()) {
                Mage mage = context.getMage();
                ConfigurationSection unlocks = mage.getData().getConfigurationSection(unlockSection);
                if (unlocks != null && unlocks.getBoolean(unlockKey, false)) {
                    unlocked = true;
                    costs = null;
                    showConfirmation = false;
                    String unlockedMessage = getMessage("unlocked_lore");
                    InventoryUtils.wrapText(unlockedMessage, lore);
                }
            }

            // Unlocked options skip requirements and costs
            if (!unlocked) {
                RequirementsResult check = checkRequirements(context);
                if (!check.result.isSuccess() && !hasAltTags(context.getWand())) {
                    unavailable = true;
                    unavailableMessage = check.message;
                    if (unavailableMessage != null && !unavailableMessage.isEmpty()) {
                        InventoryUtils.wrapText(check.message, lore);
                    }
                }
            }

            // Don't show costs if unavailable
            if (costs != null && !unavailable) {
                String costHeading = getMessage("cost_heading");
                if (!costHeading.isEmpty()) {
                    InventoryUtils.wrapText(costHeading, lore);
                }

                String costKey = unlockKey != null && !unlockKey.isEmpty() ? "unlock_cost_lore" : "cost_lore";
                String requiredKey = unlockKey != null && !unlockKey.isEmpty() ? "required_unlock_cost_lore" : "required_cost_lore";
                String costString = getMessage(costKey);
                String requiredCostString = getMessage(requiredKey);
                for (Cost cost : costs) {
                    if (costModifiers != null) {
                        for (CostModifier modifier : costModifiers) {
                            modifier.modify(cost);
                        }
                    }

                    String costDescription = cost.has(context.getMage(), context.getWand()) ? costString : requiredCostString;
                    costDescription = costDescription.replace("$cost", cost.getFullDescription(context.getController().getMessages()));
                    InventoryUtils.wrapText(costDescription, lore);
                }
            } else if (unlockKey != null && !unlockKey.isEmpty() && !unlocked) {
                unavailable = true;
                String lockedMessage = getMessage("locked");
                if (!lockedMessage.isEmpty()) {
                    InventoryUtils.wrapText(lockedMessage, lore);
                    if (unavailableMessage == null) {
                        unavailableMessage = lockedMessage;
                    }
                }
            }

            // Add earn lore
            if (earns != null) {
                String costHeading = getMessage("earn_heading");
                if (!costHeading.isEmpty()) {
                    InventoryUtils.wrapText(costHeading, lore);
                }

                String earnString = getMessage("earn_lore");
                for (Cost earn : earns) {
                    if (earnModifiers != null) {
                        for (CostModifier modifier : earnModifiers) {
                            modifier.modify(earn);
                        }
                    }

                    earnString = earnString.replace("$earn", earn.getFullDescription(context.getController().getMessages()));
                    InventoryUtils.wrapText(earnString, lore);
                }
            }

            // Choose icon if none was set in config
            if (icon == null && items != null) {
                icon = InventoryUtils.getCopy(items.get(0));
                // This prevents getting two copies of the lore
                // Only do this if lore was actually provided, since this setting is on by default for the Shop action
                if (applyLoreToItem && this.lore != null && !this.lore.isEmpty()) {
                    ItemMeta meta = icon.getItemMeta();
                    meta.setLore(null);
                    icon.setItemMeta(meta);
                } else if ((applyToWand || applyToCaster) && controller.isWandUpgrade(icon)) {
                    // This is a bit of a hack to get rid of the upgrade_item_description lore
                    ItemMeta meta = icon.getItemMeta();
                    List<String> iconLore = meta.getLore();
                    if (iconLore != null && !iconLore.isEmpty()) {
                        iconLore.remove(iconLore.size() - 1);
                        meta.setLore(iconLore);
                        icon.setItemMeta(meta);
                    }
                }
            }

            if (icon == null && castSpell != null && !castSpell.isEmpty()) {
                String spellToCast = getCastSpell(context.getWand());
                SpellTemplate spellTemplate = context.getController().getSpellTemplate(spellToCast);
                if (spellTemplate != null) {
                    if (unavailable && spellTemplate.getDisabledIcon() != null) {
                        icon = spellTemplate.getDisabledIcon().getItemStack(1);
                    }
                    if (icon == null && spellTemplate.getIcon() != null) {
                        icon = spellTemplate.getIcon().getItemStack(1);
                    }
                    if (icon == null && unavailable && spellTemplate.getDisabledIconURL() != null) {
                        icon = controller.getURLSkull(spellTemplate.getDisabledIconURL());
                    }
                    if (icon == null && spellTemplate.getIconURL() != null) {
                        icon = controller.getURLSkull(spellTemplate.getIconURL());
                    }
                }
            }

            MagicAttribute attribute = attributeKey == null ? null : controller.getAttribute(attributeKey);
            if (icon == null && attribute != null) {
                if (iconDisabledKey == null) {
                    iconDisabledKey = attribute.getIconDisabledKey();
                }
                String iconKey = attribute.getIconKey();
                if (iconKey != null && !iconKey.isEmpty()) {
                    ItemData iconData = controller.getOrCreateItem(iconKey);
                    if (iconData != null) {
                        icon = iconData.getItemStack();
                    }
                }
            }

            if (icon != null && attribute != null) {
                int amount = 1;
                CasterProperties caster = getCaster(context);
                Double currentAmount = caster.getAttribute(attributeKey);
                if (currentAmount != null) {
                    if (attributeAmount == 0) {
                        amount = (int)Math.floor(currentAmount);
                    } else {
                        double newValue = attributeAmount + currentAmount;
                        boolean allowed = attribute == null || attribute.inRange(newValue);
                        if (!allowAttributeReduction && startingAttributeValue != null && newValue < startingAttributeValue) {
                            allowed = false;
                        }
                        if (!allowed) {
                            unavailable = true;
                            String template = attributeAmount < 0 ? getMessage("attribute_min") : getMessage("attribute_max");
                            unavailableMessage = template.replace("$attribute", attribute.getName(context.getController().getMessages()));
                        }
                    }
                }
                icon.setAmount(Math.max(1, amount));
            }

            if (icon == null && unlockClass != null && !unlockClass.isEmpty()) {
                MageClassTemplate mageClass = controller.getMageClassTemplate(unlockClass);
                if (mageClass != null) {
                    if (iconDisabledKey == null) {
                        iconDisabledKey = mageClass.getIconDisabledKey();
                    }
                    String iconKey = mageClass.getIconKey();
                    if (iconKey != null && !iconKey.isEmpty()) {
                        ItemData iconData = controller.getOrCreateItem(iconKey);
                        if (iconData != null) {
                            icon = iconData.getItemStack();
                        }
                    }
                }
            }

            if (unavailable && iconDisabledKey != null) {
                ItemData iconDisabled = controller.getItem(iconDisabledKey);
                if (iconDisabled != null) {
                    icon = iconDisabled.getItemStack();
                }
            }

            if (icon == null && defaults.icon != null) {
                this.icon = InventoryUtils.getCopy(defaults.icon);
            }

            ItemMeta meta = icon == null ? null : icon.getItemMeta();
            if (icon == null || meta == null) {
                // Show a question mark if nothing else worked
                this.icon = controller.getURLSkull("http://textures.minecraft.net/texture/1adaf6e6e387bc18567671bb82e948488bbacff97763ee5985442814989f5d");
                meta = icon.getItemMeta();
                if (meta == null) {
                    this.icon = new ItemStack(com.elmakers.mine.bukkit.wand.Wand.DefaultUpgradeMaterial);
                    meta = this.icon.getItemMeta();
                }
            }

            // Prepare icon
            String name = this.name;
            if (attributeKey != null && attributeAmount == 0) {
                Double value = context.getMage().getAttribute(attributeKey);
                if (value != null) {
                    String template = getMessage("attribute");
                    name = template.replace("$attribute", name)
                        .replace("$amount", Integer.toString((int)(double)value));
                }
            }
            if (nameIcon) {
                meta.setDisplayName(name);
            }
            if (!lore.isEmpty()) {
                List<String> itemLore = meta.getLore();
                if (itemLore == null) {
                    itemLore = new ArrayList<>();
                }
                for (String line : lore) {
                    itemLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(itemLore);
            }
            icon.setItemMeta(meta);
            icon = InventoryUtils.makeReal(icon);

            InventoryUtils.makeUnbreakable(icon);
            InventoryUtils.hideFlags(icon, 63);

            if (unavailable) {
                if (unavailableMessage != null && !unavailableMessage.isEmpty()) {
                    InventoryUtils.setMeta(icon, "unpurchasable", unavailableMessage);
                } else {
                    // We're not going to show unavailable items without a reason.
                    showUnavailable = false;
                }
            }

            if (showConfirmation) {
                InventoryUtils.setMeta(icon, "confirm", "true");
            }
        }

        @Nullable
        protected String getCastSpell(Wand wand) {
            if (alternateSpellTags != null && wand != null) {
                for (String key : alternateSpellTags.keySet()) {
                    if (wand.hasTag(key)) {
                        return alternateSpellTags.get(key);
                    }
                }
            }
            return castSpell;
        }

        protected boolean hasAltTags(Wand wand) {
            if (alternateSpellTags == null || wand == null) return false;
            for (String key : alternateSpellTags.keySet()) {
                if (wand.hasTag(key)) return true;
            }
            return false;
        }

        @Nullable
        protected Cost takeCosts(CastContext context) {
            Cost required = getRequiredCost(context);
            if (required != null) {
                return required;
            }
            if (costs != null) {
                for (Cost cost : costs) {
                    cost.deduct(context.getMage(), context.getWand());
                }
            }

            return null;
        }

        @Nullable
        public Cost getRequiredCost(CastContext context) {
            if (costs != null) {
                for (Cost cost : costs) {
                    if (!cost.has(context.getMage(), context.getWand())) {
                        return cost;
                    }
                }
            }

            return null;
        }

        public SpellResult give(CastContext context) {
            Mage mage = context.getMage();
            Wand wand = context.getWand();

            if (placeholder) {
                return SpellResult.NO_ACTION;
            }

            if (unlockClass != null && !unlockClass.isEmpty()) {
                if (mage.hasClassUnlocked(unlockClass)) {
                    String hasClassMessage = getMessage("has_class").replace("$class", name);
                    context.showMessage(hasClassMessage);
                    return SpellResult.NO_TARGET;
                }
                MageClass activeClass = mage.getActiveClass();
                if (switchClass && activeClass != null) {
                    mage.lockClass(activeClass.getKey());
                }
                mage.unlockClass(unlockClass);
                if (lockClass != null && !lockClass.isEmpty()) {
                    mage.lockClass(lockClass);
                }
                if (switchClass) {
                    mage.setActiveClass(unlockClass);

                    // This is here to force reload any changes made to wands
                    // If this becomes an issue, maybe make it optional
                    wand = actionContext.checkWand();
                }
            }

            CasterProperties caster = getCaster(context);
            if (applyToWand && caster == null) {
                context.showMessage("no_wand", getDefaultMessage(context, "no_wand"));
                return SpellResult.NO_TARGET;
            }

            if (caster != null && items != null) {
                boolean anyApplied = false;
                for (ItemStack item : items) {
                    anyApplied = caster.addItem(item) || anyApplied;
                }
                if (!anyApplied) {
                    String inapplicable = getMessage("not_applicable").replace("$item", name);
                    context.showMessage(inapplicable);
                    return SpellResult.NO_TARGET;
                }
            }

            if (caster != null && attributeKey != null && !attributeKey.isEmpty()) {
                MagicAttribute attributeDefinition = context.getController().getAttribute(attributeKey);
                Double amount = caster.getAttribute(attributeKey);
                if (amount != null && attributeDefinition != null) {
                    double newValue = amount + attributeAmount;
                    if (!attributeDefinition.inRange(newValue)) {
                        return SpellResult.NO_TARGET;
                    }
                    caster.setAttribute(attributeKey, newValue);
                    if (!autoClose && displayInventory != null) {
                        for (Map.Entry<Integer, SelectorOption> entry : showingItems.entrySet()) {
                            SelectorOption option = entry.getValue();
                            if (option.isPlaceholder()) continue;

                            option.updateIcon(context);
                            ItemStack icon = option.getIcon();
                            InventoryUtils.setMeta(icon, "slot", Integer.toString(entry.getKey()));
                            displayInventory.setItem(entry.getKey(), icon);
                        }
                    }
                } else {
                    context.getLogger().warning("Invalid attribute: " + attributeKey);
                }
            }

            MageController controller = context.getController();
            String castSpell = getCastSpell(context.getWand());
            if (castSpell != null && !castSpell.isEmpty()) {
                Spell spell = null;
                spell = mage.getSpell(castSpell);

                // Close before casting, to support sub-menus
                if (autoClose) {
                    mage.deactivateGUI();
                }

                if (spell == null || !spell.cast(castSpellParameters)) {
                    context.showMessage("cast_fail", getDefaultMessage(context, "cast_fail"));
                    return SpellResult.NO_TARGET;
                }
            }

            if (unlockKey != null && !unlockKey.isEmpty()) {
                ConfigurationSection unlocks = mage.getData().getConfigurationSection(unlockSection);
                if (unlocks != null && !unlocks.getBoolean(unlockKey)) {
                    String unlockMessage = getMessage("unlocked");
                    context.showMessage(getCostsMessage(unlockMessage));
                }
                if (unlocks == null) {
                    unlocks = mage.getData().createSection(unlockSection);
                }
                unlocks.set(unlockKey, true);
            }

            if (items != null && caster == null) {
                boolean gave = false;
                for (ItemStack item : items) {
                    ItemStack copy = InventoryUtils.getCopy(item);
                    if (allowDroppedItems) {
                        mage.giveItem(copy, putInHand);
                        gave = true;
                    } else {
                        gave = mage.tryGiveItem(copy, putInHand) || gave;
                    }
                }

                if (!gave) {
                    context.showMessage(getMessage("full"));
                    return SpellResult.NO_TARGET;
                }
            }

            if (commands != null && !commands.isEmpty()) {
                for (String command : commands) {
                    String execute = context.parameterize(command);
                    controller.getPlugin().getServer().dispatchCommand(Bukkit.getConsoleSender(), execute);
                }
            }

            if (earns != null) {
                boolean givenAny = false;
                for (Cost cost : earns) {
                    givenAny = cost.give(mage, wand) || givenAny;
                }
                if (!givenAny) {
                    return SpellResult.NO_TARGET;
                }
            }

            if (actions != null) {
                startActions(actions);
            }

            Cost required = takeCosts(context);
            if (required != null) {
                String baseMessage = getMessage("insufficient");
                String costDescription = required.getFullDescription(controller.getMessages());
                costDescription = baseMessage.replace("$cost", costDescription);
                context.showMessage(costDescription);
                return SpellResult.INSUFFICIENT_RESOURCES;
            }

            if (!effects.isEmpty()) {
                context.playEffects(effects);
            }

            return SpellResult.CAST;
        }

        @Nullable
        public CasterProperties getCaster(CastContext context) {
            Mage mage = context.getMage();
            Wand wand = context.getWand();
            CasterProperties caster = null;
            if (applyTo != null) {
                MagicProperties properties = mage.getProperties().getStorage(applyTo);
                if (properties instanceof CasterProperties) {
                    caster = (CasterProperties)properties;
                }
            } else if (applyToClass != null && !applyToClass.isEmpty()) {
                caster = mage.getClass(applyToClass);
            } else if (applyToWand) {
                caster = wand;
            } else if (applyToCaster) {
                caster = mage.getActiveProperties();
            }
            return caster;
        }

        public Integer getSlot() {
            return slot;
        }

        public boolean isPlaceholder() {
            return placeholder;
        }

        public boolean isUnavailable() {
            return unavailable;
        }

        @Nullable
        public ItemStack getIcon() {
            return icon;
        }

        public String getName() {
            return name;
        }

        public String getSelectedMessage() {
            String message = selectedMessage;
            if (costs == null) {
                if (selectedFreeMessage != null) {
                    message = selectedFreeMessage;
                } else if (message == null) {
                    message = getMessage("selected_free");
                }
            } else if (message == null) {
                message = getMessage("selected");
            }
            return getCostsMessage(message);
        }

        public String getCostsMessage(String baseMessage) {
            String costString = "";

            if (costs != null) {
                for (Cost cost : costs) {
                    if (!costString.isEmpty()) {
                        costString += ", ";
                    }

                    costString += cost.getFullDescription(context.getController().getMessages());
                }
            }

            if (costString.isEmpty()) {
                costString = getMessage("nothing");
            }

            String earnString = "";
            if (earns != null) {
                for (Cost earn : earns) {
                    if (!earnString.isEmpty()) {
                        earnString += ", ";
                    }

                    earnString += earn.getFullDescription(context.getController().getMessages());
                }
            }

            if (earnString.isEmpty()) {
                earnString = getMessage("nothing");
            }
            return baseMessage.replace("$item", name)
                .replace("$name", name)
                .replace("$cost", costString)
                .replace("$earn", earnString);
        }
    }

    @Override
    public void deactivated() {
        // Check for shop items glitched into the player's inventory
        if (context != null) {
            context.getMage().removeItemsWithTag("slot");
        }
        isActive = false;
    }

    @Override
    public void dragged(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    protected String getMessage(String key) {
        return context.getMessage(key, getDefaultMessage(context, key));
    }

    protected String getDefaultMessage(CastContext context, String key) {
        return context.getController().getMessages().get("shops." + key);
    }

    @Override
    public void clicked(InventoryClickEvent event)
    {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        Mage mage = context.getMage();
        if (item == null || !InventoryUtils.hasMeta(item, "slot")) {
            if (defaultConfiguration.autoClose) {
                mage.deactivateGUI();
            }
            return;
        }

        int slotIndex = Integer.parseInt(InventoryUtils.getMetaString(item, "slot"));
        MageController controller = context.getController();

        SelectorOption option = showingItems.get(slotIndex);
        if (option == null || option.isPlaceholder()) {
            return;
        }

        String unpurchasableMessage = InventoryUtils.getMetaString(item, "unpurchasable");
        if (unpurchasableMessage != null && !unpurchasableMessage.isEmpty()) {
            context.showMessage(unpurchasableMessage);
            if (option.autoClose) {
                mage.deactivateGUI();
            }
            return;
        }

        Cost required = option.getRequiredCost(context);
        if (required != null) {
            String baseMessage = getMessage("insufficient");
            String costDescription = required.getFullDescription(controller.getMessages());
            costDescription = baseMessage.replace("$cost", costDescription);
            context.showMessage(costDescription);
        } else {
            String itemName = option.getName();
            if (InventoryUtils.hasMeta(item, "confirm")) {
                String inventoryTitle = getConfirmTitle(option).replace("$item", itemName);
                Inventory confirmInventory = CompatibilityUtils.createInventory(null, 9, inventoryTitle);
                InventoryUtils.removeMeta(item, "confirm");
                for (int i = 0; i < 9; i++)
                {
                    if (i != 4) {
                        ItemStack filler = confirmFillMaterial == null ? null : confirmFillMaterial.getItemStack(1);
                        if (filler == null) {
                            filler = new ItemStack(Material.AIR);
                        }
                        ItemMeta meta = filler.getItemMeta();
                        if (meta != null)
                        {
                            meta.setDisplayName(ChatColor.DARK_GRAY + (i < 4 ? "-->" : "<--"));
                            filler.setItemMeta(meta);
                        }
                        confirmInventory.setItem(i, filler);
                    } else {
                        confirmInventory.setItem(i, item);
                    }
                }
                mage.deactivateGUI();
                isActive = true;
                mage.activateGUI(this, confirmInventory);
                return;
            }

            finalResult = option.give(context);
            if (finalResult.isSuccess() && finalResult != SpellResult.NO_TARGET) {
                context.showMessage(option.getSelectedMessage());
            }
        }
        if (option.autoClose || finalResult != SpellResult.CAST) {
            if (isActive) {
                mage.deactivateGUI();
            }
        } else {
            // update title
            mage.continueGUI(this, getInventory(context));
        }
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        this.context = context;

        defaultConfiguration = new SelectorConfiguration(parameters);
        String fillerKey = parameters.getString("confirm_filler");
        if (fillerKey != null && !fillerKey.isEmpty()) {
            confirmFillMaterial = context.getController().getItem(fillerKey);
        }
        title = parameters.getString("title");
        confirmTitle = parameters.getString("confirm_title");
        confirmUnlockTitle = parameters.getString("unlock_confirm_title");
        titleKey = parameters.getString("title_key", "title");
        confirmTitleKey = parameters.getString("confirm_title_key", "confirm_title");
        confirmUnlockTitleKey = parameters.getString("unlock_confirm_title_key", "unlock_confirm_title");
        finalResult = null;
        isActive = false;

        numSlots = 0;
        showingItems = new HashMap<>();
        has = 0;
        Collection<ConfigurationSection> optionConfigs = ConfigurationUtils.getNodeList(parameters, "options");
        if (optionConfigs != null) {
            loadOptions(optionConfigs);
        }

        // Have to do this after adding options since options may register action handlers
        super.prepare(context, parameters);
    }

    protected void loadOptions(Collection<ConfigurationSection> optionConfigs) {
        // Gather list of selector options first, to compute limits
        List<SelectorOption> options = new ArrayList<>();

        for (ConfigurationSection option : optionConfigs) {
            SelectorOption newOption = new SelectorOption(defaultConfiguration, option, context);
            if (newOption.hasLimit() && newOption.has(context)) {
                has++;
            }
            options.add(newOption);
        }
        for (SelectorOption option : options) {
            if (option.isUnavailable() && !option.showIfUnavailable()) {
                continue;
            }

            Integer targetSlot = option.getSlot();
            int slot = targetSlot == null ? numSlots : targetSlot;
            if (slot >= MAX_INVENTORY_SLOTS) continue;
            if (!option.isPlaceholder()) itemCount++;
            showingItems.put(slot, option);
            numSlots = Math.max(slot + 1, numSlots);
        }
    }

    public SpellResult showItems(CastContext context) {
        Mage mage = context.getMage();
        Player player = mage.getPlayer();
        if (player == null) {
            return SpellResult.PLAYER_REQUIRED;
        }

        isActive = true;
        finalResult = SpellResult.NO_ACTION;
        Inventory displayInventory = getInventory(context);
        mage.activateGUI(this, displayInventory);

        return SpellResult.CAST;
    }

    protected String getInventoryTitle()
    {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return getMessage(titleKey);
    }

    protected String getConfirmTitle(SelectorOption option)
    {
        if (option.isUnlock()) {
            if (confirmUnlockTitle != null && !confirmUnlockTitle.isEmpty()) {
                return confirmUnlockTitle;
            }
            return getMessage(confirmUnlockTitleKey);
        }
        if (confirmTitle != null && !confirmTitle.isEmpty()) {
            return confirmTitle;
        }
        return getMessage(confirmTitleKey);
    }

    protected String getBalanceDescription(CastContext context) {
        Mage mage = context.getMage();
        if (defaultConfiguration.free) {
            return "";
        }
        String costType = defaultConfiguration.getCostType();
        com.elmakers.mine.bukkit.item.Cost cost = new com.elmakers.mine.bukkit.item.Cost(context.getController(), costType, 1);
        cost.checkSupported(context.getController(), defaultConfiguration.getCostTypeFallbacks());
        cost.setAmount(cost.getBalance(mage, context.getWand()));
        return cost.getFullDescription(context.getController().getMessages());
    }

    protected Inventory getInventory(CastContext context)
    {
        String inventoryTitle = getInventoryTitle();
        String balanceDescription = getBalanceDescription(context);
        inventoryTitle = inventoryTitle.replace("$balance", balanceDescription);
        inventoryTitle = context.parameterize(inventoryTitle);

        ProgressionPath path = context.getMage().getActiveProperties().getPath();
        String pathName = (path == null ? null : path.getName());
        if (pathName == null) {
            pathName = "";
        }
        inventoryTitle = inventoryTitle.replace("$path", pathName);

        int invSize = (int)Math.ceil(numSlots / 9.0f) * 9;
        displayInventory = CompatibilityUtils.createInventory(null, invSize, inventoryTitle);
        for (Map.Entry<Integer, SelectorOption> entry : showingItems.entrySet()) {
            ItemStack icon = entry.getValue().getIcon();
            InventoryUtils.setMeta(icon, "slot", Integer.toString(entry.getKey()));
            displayInventory.setItem(entry.getKey(), icon);
        }

        return displayInventory;
    }

    @Override
    public void getParameterNames(Spell spell, Collection<String> parameters)
    {
        super.getParameterNames(spell, parameters);
        parameters.add("confirm");
        parameters.add("path");
        parameters.add("path_end");
        parameters.add("path_exact");
        parameters.add("auto_upgrade");
        parameters.add("require_wand");
        parameters.add("permission");
    }

    @Override
    public void getParameterOptions(Spell spell, String parameterKey, Collection<String> examples)
    {
        MageController controller = spell.getController();
        if (parameterKey.equals("path") || parameterKey.equals("path_exact") || parameterKey.equals("path_end")) {
            examples.addAll(controller.getWandPathKeys());
        } else if (parameterKey.equals("require_wand") || parameterKey.equals("confirm") || parameterKey.equals("auto_upgrade")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_BOOLEANS));
        } else {
            super.getParameterOptions(spell, parameterKey, examples);
        }
    }

    @Override
    public void finish(CastContext context) {
        isActive = false;
        finalResult = null;
    }

    @Override
    public void reset(CastContext context) {
        super.reset(context);
        isActive = false;
        finalResult = null;
    }

    public RequirementsResult checkDefaultRequirements(CastContext context) {
        return defaultConfiguration.checkRequirements(context);
    }

    @Override
    public SpellResult start(CastContext context) {
        RequirementsResult check = checkDefaultRequirements(context);
        if (!check.result.isSuccess()) {
            context.sendMessageKey(check.result.name(), check.message);
            return check.result;
        }

        if (itemCount == 0) {
            context.showMessage("no_items", getDefaultMessage(context, "no_items"));
            return SpellResult.NO_ACTION;
        }
        return showItems(context);
    }

    @Override
    public SpellResult step(CastContext context) {
        this.context = context;
        if (isActive) {
            return SpellResult.PENDING;
        }

        return finalResult == null ? SpellResult.NO_ACTION : finalResult;
    }

    @Nullable
    protected ItemStack parseItem(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || itemKey.equalsIgnoreCase("none"))
        {
            return null;
        }

        ItemStack item = context.getController().createItem(itemKey);
        if (item == null) {
           context.getLogger().warning("Failed to create item in selector: " + itemKey);
        }
        return item;
    }

    protected int getNumSlots() {
        return numSlots;
    }

    @Override
    protected void addHandlers(Spell spell, ConfigurationSection parameters) {
        // We've done something weird with the Selector action, making "actions" not an action handler
        // nor a reference to one, but a default value for options to use for referencing an action
        // handler.
        // This means it's important we don't remove it from the configuration, and so should not
        // try to process it like a default action handler.
        // This is why addHandlers is overridden and does not call the super method.
    }
}

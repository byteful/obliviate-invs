package mc.obliviate.inventory.configurable.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.base.Preconditions;
import mc.obliviate.inventory.configurable.GuiConfigurationTable;
import mc.obliviate.util.placeholder.PlaceholderUtil;
import mc.obliviate.util.string.StringUtil;
import mc.obliviate.util.versiondetection.ServerVersionController;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.SkullUtils;
import org.bukkit.Material;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ItemStackSerializer {

    /**
     * This method matches material and material name. Uses <a href="https://github.com/CryptoMorin/XSeries/blob/master/src/main/java/com/cryptomorin/xseries/XMaterial.java">XMaterial</a>
     * at backend.
     * <p>
     * Legacy versions needs material data (aka damage, durability)
     * to recognize some materials. Ex: white wool and red wool are
     * same material for 1.8 servers. That is why this method
     * returns itemstack instead of material.
     *
     * @param section YAML configuration section of item stack
     * @return raw item stack
     */
    @Nonnull
    public static ItemStack deserializeMaterial(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeMaterial(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    /**
     * This method matches material and material name. Uses <a href="https://github.com/CryptoMorin/XSeries/blob/master/src/main/java/com/cryptomorin/xseries/XMaterial.java">XMaterial</a>
     * at backend.
     * <p>
     * Legacy versions needs material data (aka damage, durability)
     * to recognize some materials. Ex: white wool and red wool are
     * same material for 1.8 servers. That is why this method
     * returns itemstack instead of material.
     *
     * @param section YAML configuration section of item stack
     * @param table   table to find section names
     * @return raw item stack
     */
    @Nonnull
    public static ItemStack deserializeMaterial(@Nonnull ConfigurationSection section, GuiConfigurationTable table) {
        final String materialName = section.getString(table.getMaterialSectionName());
        if (materialName == null) throw new IllegalArgumentException("material section could not find");

        final Optional<XMaterial> xmaterial = XMaterial.matchXMaterial(materialName);
        if (!xmaterial.isPresent()) {
            throw new IllegalArgumentException("Material could not found: " + materialName);
        }

        ItemStack item = xmaterial.get().parseItem();
        if (item == null) {
            throw new IllegalArgumentException("Material could not parsed as item stack: " + materialName);
        }
        return item;
    }

    /**
     * This method deserialize a configuration as an item stack.
     * This method parses item type, name, lore, amount, durability,
     * enchantment, item flags, custom model data and unbreakability.
     * <p>
     * However, this method does not parse placeholders because this
     * type of itemstack must be raw to caching itemstacks and
     * applying placeholders at runtime.
     *
     * @param section YAML configuration section of item stack
     * @return deserialized item stack.
     */
    @Nonnull
    public static ItemStack deserializeItemStack(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeItemStack(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    /**
     * This method deserialize a configuration as an item stack.
     * This method parses item type, name, lore, amount, durability,
     * enchantment, item flags, custom model data and unbreakability.
     * <p>
     * However, this method does not parse placeholders because this
     * type of itemstack must be raw to caching itemstacks and
     * applying placeholders at runtime.
     *
     * @param section YAML configuration section of item stack
     * @param table   table to find section names
     * @return deserialized item stack.
     */
    @Nonnull
    public static ItemStack deserializeItemStack(@Nonnull ConfigurationSection section, @Nullable GuiConfigurationTable table) {
        if (table == null) table = GuiConfigurationTable.getDefaultConfigurationTable();
        Preconditions.checkNotNull(table, "param table and default table cannot be null at same time.");

        if (section.getBoolean("bukkit-serializing", false)) {
            ItemStack item = section.getItemStack("item");
            Preconditions.checkNotNull(item, "bukkit serializing could not applied to item: " + section.getName());
            return item;
        }

        final ItemStack item = deserializeMaterial(section, table);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(section.getString(table.getDisplayNameSectionName()));
        meta.setLore(section.getStringList(table.getLoreSectionName()));
        item.setItemMeta(meta);

        parseColorOfItemStack(item);
        applyEnchantmentsToItemStack(item, deserializeEnchantments(section, table));

        meta = item.getItemMeta();
        if (section.isSet(table.getCustomModelDataSectionName()) && ServerVersionController.isServerVersionAtLeast(ServerVersionController.V1_14))
            meta.setCustomModelData(section.getInt(table.getCustomModelDataSectionName()));
        if (section.getBoolean(table.getUnbreakableSectionName()) && ServerVersionController.isServerVersionAtLeast(ServerVersionController.V1_11))
            meta.setUnbreakable(true);
        if (section.isSet(table.getDurabilitySectionName()))
            item.setDurability((short) section.getInt(table.getDurabilitySectionName()));
        if (section.getBoolean(table.getGlowSectionName())) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            if (meta.getEnchants().isEmpty()) {
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            }
        }
        if (item.getType() == Material.PLAYER_HEAD && section.contains("texture")) {
          PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
          profile.clearProperties();
          profile.getProperties().add(new ProfileProperty("textures", section.getString("texture")));
          ((SkullMeta) meta).setPlayerProfile(profile);
        }
        item.setItemMeta(meta);

        applyItemFlagsToItemStacks(item, deserializeItemFlags(section, table));
        item.setAmount(section.getInt(table.getAmountSectionName(), 1));


        return item;
    }

    public static void applyItemFlagsToItemStacks(@Nonnull ItemStack item, @Nonnull ItemFlag[] itemFlags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (itemFlags.length == 0) return;

        for (ItemFlag itemFlag : itemFlags) {
            if (itemFlag == null) continue;
            meta.addItemFlags(itemFlag);
        }
        item.setItemMeta(meta);
    }

    public static ItemFlag[] deserializeItemFlags(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeItemFlags(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    public static ItemFlag[] deserializeItemFlags(@Nonnull ConfigurationSection section, @Nullable GuiConfigurationTable table) {
        if (table == null) table = GuiConfigurationTable.getDefaultConfigurationTable();
        Preconditions.checkNotNull(table, "param table and default table cannot be null at same time.");

        ItemFlag[] itemFlags = new ItemFlag[ItemFlag.values().length];

        List<String> serializedItemFlags = section.getStringList(table.getItemFlagsSectionName());
        if (serializedItemFlags.isEmpty()) return itemFlags;
        if (serializedItemFlags.contains("*")) return ItemFlag.values();

        int index = 0;
        for (String serializedItemFlag : serializedItemFlags) {
            try {
                ItemFlag itemFlag = ItemFlag.valueOf(serializedItemFlag);
                Preconditions.checkNotNull(itemFlag);
                itemFlags[index++] = itemFlag;
            } catch (Exception e) {
                throw new IllegalArgumentException("item flag could not find: " + serializedItemFlag);
            }
        }

        return itemFlags;
    }

    public static void applyEnchantmentsToItemStack(ItemStack item, @Nonnull Map<Enchantment, Integer> enchantments) {
        if (item == null) return;
        if (enchantments.isEmpty()) return;
        if (item.getType().equals(XMaterial.ENCHANTED_BOOK.parseMaterial())) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta == null) return;

            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }

            item.setItemMeta(meta);
        } else {
            item.addUnsafeEnchantments(enchantments);
        }
    }

    public static Map<Enchantment, Integer> deserializeEnchantments(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeEnchantments(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    public static Map<Enchantment, Integer> deserializeEnchantments(@Nonnull ConfigurationSection section, @Nonnull GuiConfigurationTable table) {
        if (!section.isSet(table.getEnchantmentsSectionName())) return new HashMap<>();
        Map<Enchantment, Integer> map = new HashMap<>();
        for (final String serializedEnchantment : section.getStringList(table.getEnchantmentsSectionName())) {
            final Map.Entry<Enchantment, Integer> enchantmentValue = deserializeEnchantment(serializedEnchantment);
            map.put(enchantmentValue.getKey(), enchantmentValue.getValue());
        }
        return map;
    }

    public static Map.Entry<Enchantment, Integer> deserializeEnchantment(@Nonnull String serializedEnchantment) {
        Preconditions.checkNotNull(serializedEnchantment, "serialized enchantment cannot be null");
        String[] datas = serializedEnchantment.split(":");
        Preconditions.checkArgument(datas.length == 2, "Enchantment could not deserialized: " + serializedEnchantment);
        Enchantment enchantment;
        int value;
        try {
            enchantment = Enchantment.getByName(datas[0]);
            value = Integer.parseInt(datas[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Enchantment or its Value could not resolved: " + datas[0]);
        }
        Preconditions.checkArgument(enchantment != null, "Enchantment could not find: " + datas[0]);
        return new Map.Entry<Enchantment, Integer>() {
            @Override
            public Enchantment getKey() {
                return enchantment;
            }

            @Override
            public Integer getValue() {
                return value;
            }

            @Override
            public Integer setValue(Integer value) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static void serializeItemStack(@Nullable ItemStack item, @Nonnull ConfigurationSection section) {
        serializeItemStack(item, section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    public static void serializeItemStack(@Nullable ItemStack item, @Nonnull ConfigurationSection section, @Nonnull GuiConfigurationTable table) {
        if (item == null || item.getType().equals(XMaterial.AIR.parseMaterial())) {
            section.set(table.getMaterialSectionName(), XMaterial.AIR.name());
            return;
        }

        if (item.getItemMeta() instanceof PotionMeta ||
                item.getItemMeta() instanceof EnchantmentStorageMeta ||
                item.getItemMeta() instanceof FireworkMeta ||
                item.getItemMeta() instanceof BookMeta ||
                item.getItemMeta() instanceof BannerMeta ||
                item.getItemMeta() instanceof MapMeta ||
                item.getItemMeta() instanceof LeatherArmorMeta ||
                item.getItemMeta() instanceof SkullMeta ||
                item.getItemMeta() instanceof FireworkEffectMeta) {
            section.set("bukkit-serializing", true);
            section.set("item", item);
            return;
        }

        section.set(table.getMaterialSectionName(), XMaterial.matchXMaterial(item).name());
        if (item.getDurability() != 0) {
            section.set(table.getDurabilitySectionName(), item.getDurability());
        }
        if (item.getAmount() != 1) {
            section.set(table.getAmountSectionName(), item.getAmount());
        }
        if (!item.getEnchantments().isEmpty()) {
            section.set(table.getEnchantmentsSectionName(), deserializeEnchantments(item.getEnchantments()));
        }

        if (item.getItemMeta() != null) {
            section.set(table.getDisplayNameSectionName(), item.getItemMeta().getDisplayName());
            if (item.getItemMeta().getLore() != null && !item.getItemMeta().getLore().isEmpty()) {
                section.set(table.getLoreSectionName(), item.getItemMeta().getLore());
            }
            if (!item.getItemMeta().getItemFlags().isEmpty()) {
                section.set(table.getItemFlagsSectionName(), deserializeItemFlags(item.getItemMeta().getItemFlags()));
            }
            if (ServerVersionController.isServerVersionAtLeast(ServerVersionController.V1_11)) {
                section.set(table.getUnbreakableSectionName(), item.getItemMeta().isUnbreakable());
            }
            if (ServerVersionController.isServerVersionAtLeast(ServerVersionController.V1_14) && item.getItemMeta().hasCustomModelData()) {
                section.set(table.getCustomModelDataSectionName(), item.getItemMeta().getCustomModelData());
            }
        }
    }

    public static List<String> deserializeEnchantments(@Nonnull Map<Enchantment, Integer> enchantments) {
        final List<String> results = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            results.add(deserializeEnchantment(entry.getKey(), entry.getValue()));
        }
        return results;
    }

    public static String deserializeEnchantment(@Nonnull Enchantment enchantment, @Nonnegative int level) {
        return enchantment.getName() + ":" + level;
    }

    private static List<String> deserializeItemFlags(@Nonnull Set<ItemFlag> flags) {
        final List<String> results = new ArrayList<>();
        for (ItemFlag flag : flags) {
            results.add(flag.name());
        }
        return results;
    }

    public static void applyPlaceholdersToItemStack(ItemStack item, PlaceholderUtil placeholderUtil) {
        if (item == null) return;
        if (placeholderUtil == null) return;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(placeholderUtil.apply(meta.getDisplayName()));
        meta.setLore(placeholderUtil.apply(meta.getLore()));
        item.setItemMeta(meta);
    }

    public static void parseColorOfItemStack(ItemStack item) {
        if (item == null) return;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(StringUtil.parseColor(meta.getDisplayName()));
        meta.setLore(StringUtil.parseColor(meta.getLore()));
        item.setItemMeta(meta);
    }
}

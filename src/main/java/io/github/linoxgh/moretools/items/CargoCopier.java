package io.github.linoxgh.moretools.items;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.linoxgh.moretools.Messages;
import io.github.linoxgh.moretools.MoreTools;
import io.github.linoxgh.moretools.handlers.ItemInteractHandler;
import io.github.thebusybiscuit.slimefun4.core.attributes.DamageableItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunPlugin;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.cargo.CargoInputNode;
import io.github.thebusybiscuit.slimefun4.implementation.items.cargo.CargoOutputNode;
import me.mrCookieSlime.Slimefun.Lists.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.Category;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.SlimefunItemStack;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.cscorelib2.protection.ProtectableAction;

/**
 * A {@link CargoCopier} is a {@link SlimefunItem} which allows you to copy the settings of a cargo node
 * with a single left click and save these settings to a cargo node with a right click.
 *
 * @author Linox
 *
 * @see ItemInteractHandler
 *
 */
public class CargoCopier extends SimpleSlimefunItem<ItemInteractHandler> implements DamageableItem {

    private final boolean damageable;
    private final int cooldown;

    private final HashMap<UUID, Long> lastUses = new HashMap<>();
    private final NamespacedKey copy = new NamespacedKey(MoreTools.getInstance(), "cargo-settings");

    private Method input = null;
    private Method output = null;
    private Method advOutput = null;

    public CargoCopier(@NotNull Category category, @NotNull SlimefunItemStack item, @NotNull RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        addItemHandler(onBreak());
        FileConfiguration cfg = MoreTools.getInstance().getConfig();
        damageable = cfg.getBoolean("item-settings.cargo-copier.damageable");
        cooldown = cfg.getInt("item-settings.cargo-copier.cooldown");
    }

    @Override
    public @NotNull ItemInteractHandler getItemHandler() {
        return (e, sfItem) -> {
            ItemStack item = e.getItem();
            if (!sfItem.getId().equals(getId()) || item == null) {
                return;
            }
            e.setCancelled(true);

            Block b = e.getClickedBlock();
            if (b != null) {
                Player p = e.getPlayer();
                if (SlimefunPlugin.getProtectionManager().hasPermission(p, b.getLocation(), ProtectableAction.BREAK_BLOCK)) {

                    Long lastUse = lastUses.get(p.getUniqueId());
                    if (lastUse != null) {
                        if ((System.currentTimeMillis() - lastUse) < cooldown) {
                            p.sendMessage(
                                    Messages.CARGOCOPIER_COOLDOWN.getMessage().replaceAll(
                                            "\\{left-cooldown}",
                                            String.valueOf(cooldown - (System.currentTimeMillis() - lastUse)))
                            );
                            return;
                        }
                    }
                    lastUses.put(p.getUniqueId(), System.currentTimeMillis());

                    switch (e.getAction()) {
                        case RIGHT_CLICK_BLOCK:
                            saveCargoNode(b, p, item);
                            break;

                        case LEFT_CLICK_BLOCK:
                            copyCargoNode(b, p, item);
                            break;

                        default:
                            break;
                    }
                }
            }
        };
    }

    private void copyCargoNode(@NotNull Block b, @NotNull Player p, @NotNull ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            p.sendMessage(Messages.CARGOCOPIER_COPYFAIL.getMessage());
            return;
        }

        SlimefunItem node = BlockStorage.check(b);
        if (node == null) {
            p.sendMessage(Messages.CARGOCOPIER_WRONGBLOCK.getMessage());
            return;
        }

        StringBuilder builder = new StringBuilder("{");
        if (node.getId().equals("CARGO_NODE_INPUT")) {

            builder.append("round-robin:").append(BlockStorage.getLocationInfo(b.getLocation(), "round-robin")).append("}/{");
            builder.append("frequency:").append(BlockStorage.getLocationInfo(b.getLocation(), "frequency")).append("}/{");
            builder.append("filter-type:").append(BlockStorage.getLocationInfo(b.getLocation(), "filter-type")).append("}/{");
            builder.append("filter-lore:").append(BlockStorage.getLocationInfo(b.getLocation(), "filter-lore")).append("}/{");
            builder.append("filter-durability:").append(BlockStorage.getLocationInfo(b.getLocation(), "filter-durability")).append("}/{");
            builder.append("index:").append(BlockStorage.getLocationInfo(b.getLocation(), "index")).append("}");

        } else if (node.getId().equals("CARGO_NODE_OUTPUT_ADVANCED")) {

            builder.append("frequency:").append(BlockStorage.getLocationInfo(b.getLocation(), "frequency")).append("}/{");
            builder.append("filter-type:").append(BlockStorage.getLocationInfo(b.getLocation(), "filter-type")).append("}/{");
            builder.append("filter-lore:").append(BlockStorage.getLocationInfo(b.getLocation(), "filter-lore")).append("}/{");
            builder.append("filter-durability:").append(BlockStorage.getLocationInfo(b.getLocation(), "filter-durability")).append("}/{");
            builder.append("index:").append(BlockStorage.getLocationInfo(b.getLocation(), "index")).append("}");

        } else if (node.getId().equals("CARGO_NODE_OUTPUT")) {

            builder.append("frequency:").append(BlockStorage.getLocationInfo(b.getLocation(), "frequency")).append("}");

        } else {
            p.sendMessage(Messages.CARGOCOPIER_WRONGBLOCK.getMessage());
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(copy, PersistentDataType.STRING, builder.toString());

        item.setItemMeta(meta);
        p.sendMessage(Messages.CARGOCOPIER_COPYSUCCESS.getMessage());
    }

    private <AbstractFilterNode> void saveCargoNode(@NotNull Block b, @NotNull Player p, @NotNull ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            p.sendMessage(Messages.CARGOCOPIER_SAVEFAIL.getMessage());
            return;
        }

        SlimefunItem node = BlockStorage.check(b);
        if (node == null) {
            p.sendMessage(Messages.CARGOCOPIER_WRONGBLOCK.getMessage());
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String settings = pdc.get(copy, PersistentDataType.STRING);
        if (settings == null) {
            p.sendMessage(Messages.CARGOCOPIER_SAVEFAIL.getMessage());
            return;
        }
        String[] settingsArr = settings.split("/");

        HashMap<String, String> map = new HashMap<>();
        for (String setting : settingsArr) {
            String[] subSetting = setting.substring(1, setting.length() - 1).split(":");
            for (String s : subSetting) {
                System.out.println(s);
            }
            map.put(subSetting[0], subSetting[1]);
        }

        if (node.getId().equals("CARGO_NODE_INPUT")) {

            for (Map.Entry<String, String> entry : map.entrySet()) {
                BlockStorage.addBlockInfo(b, entry.getKey(), entry.getValue());
            }

            try {
                CargoInputNode inputNode = (CargoInputNode) node;
                if (input == null) {
                    input = inputNode.getClass().getDeclaredMethod("updateBlockMenu", BlockMenu.class, Block.class);
                    input.setAccessible(true);
                }
                input.invoke(inputNode, BlockStorage.getInventory(b), b);

            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                MoreTools.getInstance().getLogger().log(Level.SEVERE, "Could not find 'updateBlockMenu' method.");
                MoreTools.getInstance().getLogger().log(Level.SEVERE, "Please report this to:");
                MoreTools.getInstance().getLogger().log(Level.SEVERE, MoreTools.getInstance().getBugTrackerURL());
                e.printStackTrace();
            }

        } else if (node.getId().equals("CARGO_NODE_OUTPUT")) {

            for (Map.Entry<String, String> entry : map.entrySet()) {
                BlockStorage.addBlockInfo(b, entry.getKey(), entry.getValue());
            }

            try {
                CargoOutputNode outputNode = (CargoOutputNode) node;
                if (output == null) {
                    output = outputNode.getClass().getDeclaredMethod("updateBlockMenu", BlockMenu.class, Block.class);
                    output.setAccessible(true);
                }
                output.invoke(outputNode, BlockStorage.getInventory(b), b);

            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                MoreTools.getInstance().getLogger().log(Level.SEVERE, "Could not find 'updateBlockMenu' method.");
                MoreTools.getInstance().getLogger().log(Level.SEVERE, "Please report this to:");
                MoreTools.getInstance().getLogger().log(Level.SEVERE, MoreTools.getInstance().getBugTrackerURL());
                e.printStackTrace();
            }

        } else if (node.getId().equals("CARGO_NODE_OUTPUT_ADVANCED")) {

            for (Map.Entry<String, String> entry : map.entrySet()) {
                BlockStorage.addBlockInfo(b, entry.getKey(), entry.getValue());
            }

            try {
                SlimefunItem advOutputNode =  node;
                if (advOutput == null) {
                    advOutput = advOutputNode.getClass().getDeclaredMethod("updateBlockMenu", BlockMenu.class, Block.class);
                    advOutput.setAccessible(true);
                }
                advOutput.invoke(advOutputNode, BlockStorage.getInventory(b), b);

            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                MoreTools.getInstance().getLogger().log(Level.SEVERE, "Could not find 'updateBlockMenu' method.");
                MoreTools.getInstance().getLogger().log(Level.SEVERE, "Please report this to:");
                MoreTools.getInstance().getLogger().log(Level.SEVERE, MoreTools.getInstance().getBugTrackerURL());
                e.printStackTrace();
            }

        } else {
            p.sendMessage(Messages.CARGOCOPIER_WRONGBLOCK.getMessage());
            return;
        }

        p.sendMessage(Messages.CARGOCOPIER_SAVESUCCESS.getMessage());
    }

    private BlockBreakHandler onBreak() {
    	return new SimpleBlockBreakHandler() {

    		public void onBlockBreak(Block b) {
                if (isItem(getItem())) {
                    Player p = Bukkit.getPlayer(UUID.fromString(BlockStorage.getLocationInfo(b.getLocation(), "owner")));
                    p.sendMessage(Messages.CARGOCOPIER_BLOCKBREAKING.getMessage());
                }
            }

            @Override
            public boolean isPrivate() {
                return false;
            }
        };
    }

    @Override
    public void preRegister() {
        super.preRegister();
        addItemHandler(onBreak());
    }

    @Override
    public boolean isDamageable() {
        return damageable;
    }

}

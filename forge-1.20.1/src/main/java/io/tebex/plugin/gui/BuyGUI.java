package io.tebex.plugin.gui;

import io.tebex.minecraft.shop.ShopPresenter;
import io.tebex.plugin.ForgePluginPlatform;
import io.tebex.plugin.ItemUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Native container renderer. Navigation and commerce are shared. */
public final class BuyGUI {
  private final ForgePluginPlatform platform;

  public BuyGUI(ForgePluginPlatform platform) {
    this.platform = platform;
  }

  public void open(ServerPlayer player) {
    new ShopPresenter(platform)
        .open(
            player.getName().getString(),
            menu -> {
              ListingGui gui = new ListingGui(menu.rows, type(menu.rows), player);
              gui.setTitle(menu.title);
              menu.items.forEach(
                  (slot, spec) -> {
                    Item fallback = ItemUtil.fromString(spec.fallback).orElse(Items.BOOK);
                    Item item =
                        spec.material == null
                            ? fallback
                            : ItemUtil.fromString(spec.material).orElse(fallback);
                    TebexItemBuilder builder =
                        TebexItemBuilder.from(item).hideFlags().name(spec.name).lore(spec.lore);
                    if (spec.enchanted) builder.enchant();
                    gui.addItem(
                        slot,
                        builder.asGuiItem(
                            action -> {
                              action.setCancelled(true);
                              spec.action.run();
                            }));
                  });
              platform.executeBlocking(gui::open);
            },
            player::closeContainer);
  }

  private MenuType<ChestMenu> type(int rows) {
    return switch (rows) {
      case 1 -> MenuType.GENERIC_9x1;
      case 2 -> MenuType.GENERIC_9x2;
      case 3 -> MenuType.GENERIC_9x3;
      case 4 -> MenuType.GENERIC_9x4;
      case 5 -> MenuType.GENERIC_9x5;
      default -> MenuType.GENERIC_9x6;
    };
  }
}

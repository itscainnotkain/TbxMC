package io.tebex.minecraft.bukkit.gui;

import io.tebex.minecraft.bukkit.BukkitPlatform;
import io.tebex.minecraft.bukkit.util.MaterialUtil;
import io.tebex.minecraft.shop.ShopPresenter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

/** Bukkit inventory renderer for the shared shop presenter. */
public final class BuyGUI {
  private final BukkitPlatform platform;

  public BuyGUI(BukkitPlatform platform) {
    this.platform = platform;
  }

  public void open(Player player) {
    new ShopPresenter(platform)
        .open(
            player.getName(),
            menu -> {
              ListingGui gui = new ListingGui().title(menu.title).rows(menu.rows).create();
              menu.items.forEach(
                  (slot, spec) -> {
                    Material fallback =
                        MaterialUtil.fromString(spec.fallback).orElse(Material.BOOK);
                    Material material =
                        spec.material == null
                            ? fallback
                            : MaterialUtil.fromString(spec.material).orElse(fallback);
                    TebexItemBuilder builder =
                        TebexItemBuilder.from(material)
                            .flags(
                                ItemFlag.HIDE_ATTRIBUTES,
                                ItemFlag.HIDE_ENCHANTS,
                                ItemFlag.HIDE_UNBREAKABLE)
                            .name(spec.name)
                            .lore(spec.lore);
                    if (spec.enchanted) builder.enchant();
                    gui.addItem(
                        slot,
                        builder.asGuiItem(
                            action -> {
                              action.setCancelled(true);
                              spec.action.run();
                            }));
                  });
              platform.executeForPlayer(player.getUniqueId(), () -> gui.open(player), () -> {});
            },
            player::closeInventory);
  }
}

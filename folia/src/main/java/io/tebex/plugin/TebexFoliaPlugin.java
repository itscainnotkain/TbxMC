package io.tebex.plugin;

import io.tebex.minecraft.bukkit.BukkitBootstrap;
import io.tebex.minecraft.bukkit.BukkitPlatform;

public final class TebexFoliaPlugin extends BukkitBootstrap {
  @Override
  protected BukkitPlatform createPlatform() {
    return new FoliaPluginPlatform(this);
  }
}

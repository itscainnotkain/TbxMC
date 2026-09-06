package io.tebex.plugin;

import java.util.*;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;

public class TebexBungeePlugin extends Plugin {
  private BungeePluginPlatform platform;

  @Override
  public void onEnable() {
    platform = new BungeePluginPlatform(this);

    platform.loadPlatformConfig(); // loads the platform configuration

    platform.initStore(); // Used loaded key to set current store and cache available packages

    // Bungee-specific registration
    platform
        .getCommands()
        .setRestrictedToCommands("help", "forcecheck", "reload", "secret", "debug");
    TebexCommandExecutor tebexCommand = new TebexCommandExecutor(platform);
    PluginManager pluginManager = platform.getPlugin().getProxy().getPluginManager();
    pluginManager.registerCommand(platform.getPlugin(), tebexCommand);

    getProxy().getPluginManager().registerListener(this, new JoinListener(platform));
  }

  private ProxiedPlayer getPlayer(Object player) {
    if (player == null) return null;

    if (platform.isOnlineMode() && !platform.isGeyser() && player instanceof UUID) {
      return getProxy().getPlayer((UUID) player);
    }

    return getProxy().getPlayer((String) player);
  }

  @Override
  public void onDisable() {
    if (platform != null) platform.shutdown();
  }
}

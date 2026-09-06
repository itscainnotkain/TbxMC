package io.tebex.plugin;

import io.tebex.minecraft.bukkit.BukkitPlatform;
import io.tebex.minecraft.platform.PlatformType;
import org.bukkit.entity.Player;

/**
 * Folia ownership differs from Bukkit; shared bindings never call Bukkit's legacy scheduler here.
 */
public final class FoliaPluginPlatform extends BukkitPlatform {
  public FoliaPluginPlatform(TebexFoliaPlugin plugin) {
    super(plugin);
  }

  @Override
  public PlatformType getType() {
    return PlatformType.FOLIA;
  }

  @Override
  public void executeBlocking(Runnable action) {
    if (!isStopped() && getPlugin().isEnabled())
      getPlugin().getServer().getGlobalRegionScheduler().execute(getPlugin(), action);
  }

  @Override
  public void executeForPlayer(Object id, Runnable action, Runnable retired) {
    if (isStopped() || !getPlugin().isEnabled()) {
      retired.run();
      return;
    }
    Player player = getPlayer(id);
    if (player == null) {
      retired.run();
      return;
    }
    player.getScheduler().execute(getPlugin(), action, retired, 1L);
  }
}

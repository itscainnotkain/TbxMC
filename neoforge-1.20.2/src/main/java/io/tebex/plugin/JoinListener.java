package io.tebex.plugin;

import net.minecraft.server.level.ServerPlayer;

public class JoinListener {
  private final TebexNeoForgePlugin plugin;

  public JoinListener(TebexNeoForgePlugin plugin) {
    this.plugin = plugin;
  }

  public void onPlayerJoin(ServerPlayer player) {
    Object playerId =
        plugin.getPlatform().getPlayerId(player.getName().getString(), player.getUUID());
    plugin
        .getPlatform()
        .createJoinEvent(
            player.getUUID().toString(), player.getName().getString(), player.getIpAddress());

    if (!plugin.getPlatform().getQueuedPlayers().containsKey(playerId)) {
      return;
    }

    plugin.getPlatform().performCheck();
  }
}

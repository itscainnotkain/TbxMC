package io.tebex.minecraft.bukkit.event;

import io.tebex.minecraft.bukkit.BukkitPlatform;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
  private final BukkitPlatform platform;

  public PlayerJoinListener(BukkitPlatform platform) {
    this.platform = platform;
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    Object playerId = platform.getPlayerId(player.getName(), player.getUniqueId());
    platform.createJoinEvent(
        player.getUniqueId().toString(),
        player.getName(),
        player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress());

    if (platform.getQueuedPlayers().containsKey(playerId)) platform.performCheck();
  }
}

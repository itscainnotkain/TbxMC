package io.tebex.minecraft.bukkit;

import dev.dejvokep.boostedyaml.YamlDocument;
import io.tebex.minecraft.bukkit.gui.BuyGUI;
import io.tebex.minecraft.platform.BasePluginPlatform;
import io.tebex.minecraft.platform.PlatformTelemetry;
import io.tebex.minecraft.platform.PlatformType;
import io.tebex.minecraft.platform.config.ServerPlatformConfig;
import io.tebex.minecraft.util.CommandResult;
import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BukkitPlatform extends BasePluginPlatform {
  private BuyGUI buyGUI;
  private final org.bukkit.plugin.java.JavaPlugin plugin;

  public BukkitPlatform(org.bukkit.plugin.java.JavaPlugin plugin) {
    this.plugin = plugin;
    this.buyGUI = new BuyGUI(this);
  }

  @Override
  public int getFreeSlots(Object playerId) {
    Player player = getPlayer(playerId);
    if (player == null) return -1;

    ItemStack[] inv = player.getInventory().getContents();

    // Only get the first 36 slots
    inv = Arrays.copyOfRange(inv, 0, 36);

    return (int)
        Arrays.stream(inv).filter(item -> item == null || item.getType() == Material.AIR).count();
  }

  @Override
  public File getRunningDirectory() {
    return plugin.getDataFolder();
  }

  @Override
  public boolean isOnlineMode() {
    return Bukkit.getServer().getOnlineMode() || config.isProxyMode();
  }

  @Override
  public CommandResult dispatchCommand(String command) {
    if (!plugin.isEnabled()) return CommandResult.from(false).withMessage("Store is not enabled.");
    try {
      boolean success = Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
      return CommandResult.from(success);
    } catch (CommandException bukkitCommandException) {
      return CommandResult.from(false)
          .withMessage(bukkitCommandException.getMessage())
          .withException(bukkitCommandException);
    }
  }

  @Override
  public void executeBlocking(Runnable runnable) {
    if (!plugin.isEnabled() || isStopped()) return;
    if (Bukkit.isPrimaryThread()) runnable.run();
    else Bukkit.getServer().getScheduler().runTask(plugin, runnable);
  }

  @Override
  public <T> T getPlayer(Object uuidOrUsername) {
    if (uuidOrUsername == null) return null;

    if (uuidOrUsername instanceof UUID) {
      return (T) Bukkit.getServer().getPlayer((UUID) uuidOrUsername);
    }

    if (uuidOrUsername instanceof String) {
      return (T) Bukkit.getServer().getPlayerExact((String) uuidOrUsername);
    }

    return null;
  }

  @Override
  public boolean isPlayerOnline(Object playerId) {
    Player player = getPlayer(playerId);
    return player != null && player.isOnline();
  }

  @Override
  public void log(Level level, String message) {
    plugin.getLogger().log(level, message);
  }

  @Override
  public String getPluginVersion() {
    return plugin.getDescription().getVersion();
  }

  @Override
  public PlatformTelemetry getTelemetry() {
    String serverVersion = plugin.getServer().getVersion();

    Pattern pattern = Pattern.compile("MC: (\\d+\\.\\d+\\.\\d+)");
    Matcher matcher = pattern.matcher(serverVersion);
    if (matcher.find()) {
      serverVersion = matcher.group(1);
    }

    return new PlatformTelemetry(
        getPluginVersion(),
        plugin.getServer().getName(),
        serverVersion,
        System.getProperty("java.version"),
        System.getProperty("os.arch"),
        Bukkit.getServer().getOnlineMode());
  }

  @Override
  public void sendPlayerMessage(String playerName, String message) {
    Player player = getPlayer(playerName);
    if (player == null) return;
    player.sendMessage(message);
  }

  @Override
  public UUID getPlayerUniqueId(String playerName) {
    Player player = getPlayer(playerName);
    return player == null ? null : player.getUniqueId();
  }

  @Override
  public boolean hasPermission(String username, String permission) {
    Player player = getPlayer(username);
    return player != null && player.hasPermission(permission);
  }

  @Override
  public PlatformType getType() {
    return PlatformType.BUKKIT;
  }

  public void setPlatformConfigYaml(YamlDocument configYaml) {
    this.configYaml = configYaml;
  }

  public void setConfig(ServerPlatformConfig serverPlatformConfig) {
    this.config = serverPlatformConfig;
  }

  public org.bukkit.plugin.java.JavaPlugin getPlugin() {
    return this.plugin;
  }
}

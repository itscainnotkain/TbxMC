package io.tebex.plugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import io.tebex.minecraft.platform.BasePluginPlatform;
import io.tebex.minecraft.platform.PlatformTelemetry;
import io.tebex.minecraft.platform.PlatformType;
import io.tebex.minecraft.util.CommandResult;
import io.tebex.plugin.event.JoinListener;
import io.tebex.plugin.manager.CommandManager;
import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(
    id = "tebex",
    name = "Tebex",
    version = Constants.VERSION,
    description = "The Velocity plugin for Tebex.",
    url = "https://tebex.io",
    authors = {"Tebex"})
public class TebexVelocityPlugin extends BasePluginPlatform {

  private final ProxyServer proxy;
  private final Logger logger;
  private final Path dataDirectory;

  @Inject
  public TebexVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
    this.proxy = proxy;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  public ProxyServer getProxy() {
    return proxy;
  }

  @Subscribe
  public void onEnable(ProxyInitializeEvent event) {

    loadPlatformConfig(); // load config file for the platform

    initStore(); // use loaded key to set current store

    // Velocity specific
    new CommandManager(this).register();
    proxy.getEventManager().register(this, new JoinListener(this));
  }

  @Override
  public PlatformType getType() {
    return PlatformType.VELOCITY;
  }

  @Override
  public File getRunningDirectory() {
    return dataDirectory.toFile();
  }

  @Override
  public boolean isOnlineMode() {
    return proxy.getConfiguration().isOnlineMode();
  }

  @Override
  public CommandResult dispatchCommand(String command) {
    throw new UnsupportedOperationException(
        "Velocity dispatch is asynchronous; use dispatchAsync.");
  }

  @Override
  public CompletableFuture<CommandResult> dispatchAsync(
      String command, java.util.function.Supplier<Boolean> active) {
    if (!active.get()) return CompletableFuture.completedFuture(null);
    return proxy
        .getCommandManager()
        .executeAsync(proxy.getConsoleCommandSource(), command)
        .handle(
            (success, error) ->
                error == null
                    ? CommandResult.from(Boolean.TRUE.equals(success))
                    : CommandResult.from(false)
                        .withException(error)
                        .withMessage(error.getMessage()));
  }

  @Subscribe
  public void onDisable(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
    shutdown();
  }

  @Override
  public void executeAsync(Runnable runnable) {
    proxy.getScheduler().buildTask(this, runnable).schedule();
  }

  @Override
  public void executeAsyncLater(Runnable runnable, long time, TimeUnit unit) {
    proxy.getScheduler().buildTask(this, runnable).delay(time, unit).schedule();
  }

  @Override
  public void executeBlocking(Runnable runnable) {
    // Velocity has no concept of "blocking"
    executeAsync(runnable);
  }

  @Override
  public void executeBlockingLater(Runnable runnable, long time, TimeUnit unit) {
    // Velocity has no concept of "blocking"
    executeAsyncLater(runnable, time, unit);
  }

  @Override
  public <T> T getPlayer(Object uuidOrUsername) {
    if (uuidOrUsername == null) return null;

    if (isOnlineMode() && !isGeyser() && uuidOrUsername instanceof UUID) {
      return (T) proxy.getPlayer(uuidOrUsername.toString());
    }

    return (T) proxy.getPlayer((String) uuidOrUsername);
  }

  @Override
  public int getFreeSlots(Object player) {
    // Bungee has no concept of an inventory
    return 0;
  }

  @Override
  public String getPluginVersion() {
    return Constants.VERSION;
  }

  @Override
  public void log(Level level, String message) {
    logger.atLevel(convertLevel(level)).log(message);
  }

  private org.slf4j.event.Level convertLevel(Level level) {
    if (level == Level.SEVERE) return org.slf4j.event.Level.ERROR;
    else if (level == Level.WARNING) return org.slf4j.event.Level.WARN;
    else if (level == Level.INFO) return org.slf4j.event.Level.INFO;
    else if (level == Level.CONFIG || level == Level.FINE) return org.slf4j.event.Level.DEBUG;
    else return org.slf4j.event.Level.TRACE;
  }

  @Override
  public PlatformTelemetry getTelemetry() {
    ProxyVersion proxyVersion = proxy.getVersion();
    String serverVersion = proxyVersion.getVersion();

    Pattern pattern = Pattern.compile("MC: (\\d+\\.\\d+\\.\\d+)");
    Matcher matcher = pattern.matcher(serverVersion);
    if (matcher.find()) {
      serverVersion = matcher.group(1);
    }

    return new PlatformTelemetry(
        getPluginVersion(),
        proxyVersion.getName(),
        serverVersion,
        System.getProperty("java.version"),
        System.getProperty("os.arch"),
        proxy.getConfiguration().isOnlineMode());
  }

  @Override
  public void sendPlayerMessage(String playerName, String message) {
    Player player = getPlayer(playerName);
    if (player != null) {
      player.sendMessage(Component.text(message));
    }
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
}

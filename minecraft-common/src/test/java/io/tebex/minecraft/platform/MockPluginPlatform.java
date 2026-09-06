package io.tebex.minecraft.platform;

import io.tebex.minecraft.platform.config.ServerPlatformConfig;
import io.tebex.minecraft.util.CommandResult;
import java.io.File;
import java.util.logging.Level;

public class MockPluginPlatform extends BasePluginPlatform {
  public MockPluginPlatform() {
    super();
    config = new ServerPlatformConfig(1);
    config.setVerbose(true);
  }

  @Override
  public String getPluginVersion() {
    return "1.0.0";
  }

  @Override
  public PlatformType getType() {
    return PlatformType.BUKKIT;
  }

  @Override
  public File getRunningDirectory() {
    return null;
  }

  @Override
  public void log(Level level, String message) {}

  @Override
  public CommandResult dispatchCommand(String command) {
    return null;
  }

  @Override
  public boolean isOnlineMode() {
    return false;
  }

  @Override
  public <T> T getPlayer(Object uuidOrUsername) {
    return null;
  }

  @Override
  public int getFreeSlots(Object player) {
    return 0;
  }

  @Override
  public PlatformTelemetry getTelemetry() {
    return new PlatformTelemetry("1.0.0", "mock", "1.0.0", "18", "x64", true);
  }

  @Override
  public void sendPlayerMessage(String playerName, String message) {}

  @Override
  public boolean hasPermission(String username, String permission) {
    return false;
  }

  @Override
  public void executeBlocking(Runnable action) {
    action.run();
  }
}

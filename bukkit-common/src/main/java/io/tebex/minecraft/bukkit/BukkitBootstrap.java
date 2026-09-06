package io.tebex.minecraft.bukkit;

import dev.dejvokep.boostedyaml.YamlDocument;
import io.tebex.minecraft.bukkit.command.BuyCommand;
import io.tebex.minecraft.bukkit.command.TebexCommandExecutor;
import io.tebex.minecraft.bukkit.event.InventoryClickListener;
import io.tebex.minecraft.bukkit.event.PlayerJoinListener;
import io.tebex.minecraft.platform.config.ServerPlatformConfig;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** The Bukkit platform. */
public abstract class BukkitBootstrap extends JavaPlugin {
  private BukkitPlatform platform;

  protected abstract BukkitPlatform createPlatform();

  public BukkitPlatform getPlatform() {
    return platform;
  }

  /** Starts the Bukkit platform. */
  @Override
  public void onEnable() {
    platform = createPlatform();

    migrateConfig(); // Migrate old config from BuycraftX

    platform.loadPlatformConfig(); // loads the configuration file for the platform

    platform.initStore(); // uses loaded key to set current store and cache the available packages

    // Bukkit specific registration
    TebexCommandExecutor tebexCommands = new TebexCommandExecutor(platform);
    PluginCommand pluginCommand = platform.getPlugin().getCommand("tebex");
    if (pluginCommand == null) {
      throw new RuntimeException("Tebex command not found.");
    }
    pluginCommand.setExecutor(tebexCommands);
    pluginCommand.setTabCompleter(tebexCommands);

    registerBuyCommandIfEnabled();
    registerEvents(new PlayerJoinListener(platform));
    registerEvents(new InventoryClickListener());
  }

  /**
   * Registers the specified listener with the plugin manager.
   *
   * @param l the listener to register
   */
  public <T extends Listener> void registerEvents(T l) {
    getServer().getPluginManager().registerEvents(l, this);
  }

  public void migrateConfig() {
    if (new File(getDataFolder(), "config.yml").exists()) return;
    File oldPluginDir = new File("plugins/BuycraftX");
    if (!oldPluginDir.exists()) return;

    File oldConfigFile = new File(oldPluginDir, "config.properties");
    if (!oldConfigFile.exists()) return;

    platform.info("Detected legacy BuycraftX configuration. Attempting to migrate...");

    try {
      // Load old properties
      Properties properties = new Properties();
      try (java.io.InputStream input = Files.newInputStream(oldConfigFile.toPath())) {
        properties.load(input);
      }

      String secretKey = properties.getProperty("server-key", null);
      secretKey = !Objects.equals(secretKey, "INVALID") ? secretKey : null;

      if (secretKey != null) {
        YamlDocument configYaml = platform.initPlatformConfig();
        // Migrate their existing config.
        configYaml.set("buy-command.name", properties.getProperty("buy-command-name", "buy"));
        configYaml.set(
            "buy-command.enabled",
            !Boolean.parseBoolean(properties.getProperty("disable-buy-command", null)));

        configYaml.set(
            "check-for-updates",
            Boolean.parseBoolean(properties.getProperty("check-for-updates", "true")));
        configYaml.set("verbose", Boolean.parseBoolean(properties.getProperty("verbose", "false")));

        configYaml.set(
            "server.proxy", Boolean.parseBoolean(properties.getProperty("is-bungeecord", "false")));
        configYaml.set("server.secret-key", secretKey);

        // Save new config
        configYaml.save();

        platform.setPlatformConfigYaml(configYaml);
        platform.setConfig(platform.loadServerPlatformConfig(configYaml));

        platform.info("Successfully migrated your config from BuycraftX.");
      }

    } catch (IOException e) {
      platform.warning(
          "Failed to migrate BuycraftX configuration: " + e.getMessage(),
          "Please set your secret key with /tebex secret <key> to enable your store.");
    }
  }

  public void registerBuyCommandIfEnabled() {
    try {
      final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");

      bukkitCommandMap.setAccessible(true);
      CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

      ServerPlatformConfig config = (ServerPlatformConfig) platform.getPlatformConfig();
      if (config.isBuyCommandEnabled()) {
        commandMap.register(
            config.getBuyCommandName(), new BuyCommand(config.getBuyCommandName(), platform));
      }
    } catch (Throwable e) {
      platform.error("Failed to register buy command: " + e.getMessage(), e);
    }
  }

  @Override
  public void onDisable() {
    if (platform != null) platform.shutdown();
  }
}

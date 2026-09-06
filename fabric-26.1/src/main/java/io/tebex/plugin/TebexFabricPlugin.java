package io.tebex.plugin;

import io.tebex.plugin.command.TebexCommandExecutor;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class TebexFabricPlugin implements DedicatedServerModInitializer {
  private FabricPluginPlatform platform;

  public FabricPluginPlatform getPlatform() {
    return platform;
  }

  /** Starts the Fabric platform. */
  @Override
  public void onInitializeServer() {
    platform = new FabricPluginPlatform(this);
    platform.loadPlatformConfig(); // loads the configuration file for the platform

    // Register event hooks
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          platform.setMinecraftServer(server);
          onEnableFabric();
        });

    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          platform.shutdown();
        });

    // Register commands
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, dedicated, environment) -> {
          platform.debug("registering commands");
          new TebexCommandExecutor(platform).register(dispatcher);
        });
  }

  private void onEnableFabric() {
    platform.initStore(); // uses loaded key to set current store and cache the available packages

    // Fabric specific registration
    platform.initBuyGui();
    new JoinListener(this);
  }
}

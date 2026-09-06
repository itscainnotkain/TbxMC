package io.tebex.plugin;

import io.tebex.plugin.command.TebexCommandExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(TebexForgePlugin.MOD_ID)
public final class TebexForgePlugin {
  public static final String MOD_ID = "tebex";

  private final ForgePluginPlatform platform;

  public TebexForgePlugin() {
    platform = new ForgePluginPlatform(this);
    platform.loadPlatformConfig();

    MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
    MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    ForgePermissionNodes.register();
  }

  public ForgePluginPlatform getPlatform() {
    return platform;
  }

  public void onRegisterCommands(RegisterCommandsEvent event) {
    platform.debug("registering commands");
    new TebexCommandExecutor(platform).register(event.getDispatcher());
  }

  public void onServerStarted(ServerStartedEvent event) {
    MinecraftServer server = event.getServer();
    platform.setMinecraftServer(server);
    onEnableForge();
  }

  public void onServerStopping(ServerStoppingEvent event) {
    if (platform.getClient() != null) {
      platform.shutdown();
    }
  }

  private void onEnableForge() {
    platform.initStore();
    platform.initBuyGui();
    JoinListener.register(this);
  }
}

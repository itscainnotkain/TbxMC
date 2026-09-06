package io.tebex.plugin;

import io.tebex.plugin.command.TebexCommandExecutor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@Mod(TebexNeoForgePlugin.MOD_ID)
public class TebexNeoForgePlugin {
  public static final String MOD_ID = "tebex";

  private final NeoForgePluginPlatform platform;
  private final JoinListener joinListener;

  public TebexNeoForgePlugin(IEventBus modEventBus) {
    platform = new NeoForgePluginPlatform(this);
    joinListener = new JoinListener(this);
    platform.loadPlatformConfig();

    NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
    NeoForge.EVENT_BUS.addListener(this::onRegisterPermissionNodes);
  }

  public NeoForgePluginPlatform getPlatform() {
    return platform;
  }

  private void onServerStarted(ServerStartedEvent event) {
    platform.setMinecraftServer(event.getServer());
    onEnableNeoForge();
  }

  private void onServerStopping(ServerStoppingEvent event) {
    platform.shutdown();
  }

  private void onRegisterCommands(RegisterCommandsEvent event) {
    platform.debug("registering commands");
    new TebexCommandExecutor(platform).register(event.getDispatcher());
  }

  private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      joinListener.onPlayerJoin(player);
    }
  }

  private void onRegisterPermissionNodes(PermissionGatherEvent.Nodes event) {
    NeoForgePermissionNodes.register(event);
  }

  private void onEnableNeoForge() {
    platform.initStore(); // uses loaded key to set current store and cache the available packages

    platform.initBuyGui();
  }
}

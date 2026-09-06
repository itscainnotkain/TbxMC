package io.tebex.plugin.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import io.tebex.minecraft.commands.BrigadierBridge;
import io.tebex.plugin.FabricPluginPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Native source conversion and buy registration; the command tree is shared. */
public final class TebexCommandExecutor {
  private final FabricPluginPlatform platform;

  public TebexCommandExecutor(FabricPluginPlatform platform) {
    this.platform = platform;
  }

  public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    io.tebex.minecraft.platform.config.ServerPlatformConfig config =
        (io.tebex.minecraft.platform.config.ServerPlatformConfig) platform.getPlatformConfig();
    if (config.isBuyCommandEnabled())
      dispatcher.register(
          literal(config.getBuyCommandName()).executes(new BuyCommand(platform)::execute));
    new BrigadierBridge<CommandSourceStack>(
            platform,
            source ->
                new BrigadierBridge.Sender(
                    source.getEntity() == null,
                    source.getTextName(),
                    source.getEntity() == null ? null : source.getEntity().getUUID()),
            (source, lines) -> {
              for (String line : lines) source.sendSystemMessage(Component.literal(line));
            })
        .register(dispatcher);
  }
}

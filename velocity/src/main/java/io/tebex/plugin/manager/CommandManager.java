package io.tebex.plugin.manager;

import com.velocitypowered.api.command.CommandMeta;
import io.tebex.plugin.TebexVelocityPlugin;
import io.tebex.plugin.command.TebexCommand;

/** Native registration only; command metadata and handlers live in minecraft-common. */
public final class CommandManager {
  private final TebexVelocityPlugin platform;

  public CommandManager(TebexVelocityPlugin platform) {
    this.platform = platform;
  }

  public void register() {
    platform.getCommands().useOptionalBanArguments();
    platform
        .getCommands()
        .setRestrictedToCommands("secret", "reload", "forcecheck", "help", "ban", "debug");
    CommandMeta meta =
        platform
            .getProxy()
            .getCommandManager()
            .metaBuilder("tebex")
            .aliases("tbx", "buycraft")
            .plugin(platform)
            .build();
    platform.getProxy().getCommandManager().register(meta, new TebexCommand(this));
  }

  public TebexVelocityPlugin getPlatform() {
    return platform;
  }
}

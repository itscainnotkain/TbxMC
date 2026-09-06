package io.tebex.minecraft.bukkit.gui;

import org.bukkit.event.Event;

public interface TebexGuiAction<T extends Event> {
  void execute(final T event);
}

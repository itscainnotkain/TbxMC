package io.tebex.minecraft.runtime;

import io.tebex.model.QueuedCommand;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Session-local delivery ledger. Only completed dispatch attempts become acknowledgements. */
public final class DeliveryQueue {
  private final LongSupplier clock;
  private final Function<QueuedCommand, CompletableFuture<Boolean>> dispatch;
  private final Map<Integer, Entry> pending = new LinkedHashMap<>();
  private final Set<Integer> dispatched = new HashSet<>();
  private final Set<Integer> acknowledgements = new LinkedHashSet<>();
  private boolean running;
  private boolean closed;
  private long sequence;

  public DeliveryQueue(
      LongSupplier clock, Function<QueuedCommand, CompletableFuture<Boolean>> dispatch) {
    this.clock = clock;
    this.dispatch = dispatch;
  }

  public synchronized void offer(QueuedCommand command) {
    if (closed || pending.containsKey(command.getId()) || dispatched.contains(command.getId()))
      return;
    pending.put(
        command.getId(),
        new Entry(
            command, clock.getAsLong() + Math.max(0L, command.getDelay()) * 1000L, sequence++));
  }

  /**
   * Starts at most one dispatch. Completion, including asynchronous proxy dispatch, preserves
   * ordering.
   */
  public synchronized void pump() {
    if (closed || running) return;
    Entry next =
        pending.values().stream()
            .filter(e -> e.due <= clock.getAsLong())
            .min(Comparator.comparingLong((Entry e) -> e.due).thenComparingLong(e -> e.sequence))
            .orElse(null);
    if (next == null) return;
    running = true;
    CompletableFuture<Boolean> result;
    try {
      result = dispatch.apply(next.command);
    } catch (Exception failure) {
      result = new CompletableFuture<>();
      result.completeExceptionally(failure);
    }
    result.whenComplete(
        (attempted, error) -> {
          synchronized (DeliveryQueue.this) {
            pending.remove(next.command.getId());
            if (!closed && error == null && Boolean.TRUE.equals(attempted)) {
              dispatched.add(next.command.getId());
              acknowledgements.add(next.command.getId());
            }
            running = false;
          }
        });
  }

  public synchronized List<Integer> acknowledgements() {
    return new ArrayList<>(acknowledgements);
  }

  public synchronized void acknowledged(List<Integer> ids) {
    acknowledgements.removeAll(ids);
  }

  public synchronized void close() {
    closed = true;
    pending.clear();
    acknowledgements.clear();
    dispatched.clear();
  }

  private static final class Entry {
    final QueuedCommand command;
    final long due;
    final long sequence;

    Entry(QueuedCommand command, long due, long sequence) {
      this.command = command;
      this.due = due;
      this.sequence = sequence;
    }
  }
}

package io.tebex.minecraft;

import io.tebex.http.HeadlessApi;
import io.tebex.http.PluginApi;
import io.tebex.minecraft.platform.BasePluginPlatform;
import io.tebex.model.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * An authenticated Minecraft connection. Transport and wire models belong to the SDK; this class
 * binds credentials and rejects callbacks from retired connections.
 */
public final class TebexClient {
  private final BasePluginPlatform platform;
  private final String secretKey;
  private final PluginApi api;
  private final HeadlessApi headless = new HeadlessApi();
  private volatile boolean closed;
  private final Set<CompletableFuture<?>> pending =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  public TebexClient(BasePluginPlatform platform, String secretKey) {
    this(platform, secretKey, new PluginApi());
  }

  public TebexClient(BasePluginPlatform platform, String secretKey, PluginApi api) {
    this.platform = platform;
    this.secretKey = secretKey == null ? "" : secretKey;
    this.api = api;
  }

  public PluginApi api() {
    return api;
  }

  public HeadlessApi headless() {
    return headless;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public boolean isClosed() {
    return closed;
  }

  public void close() {
    closed = true;
    pending.forEach(
        future ->
            future.completeExceptionally(
                new java.util.concurrent.CancellationException("Connection retired")));
  }

  public void shutdown() {
    platform.shutdown();
  }

  public boolean isSetup() {
    return !closed && platform.isSetup();
  }

  private <T> CompletableFuture<T> active(
      java.util.function.Supplier<CompletableFuture<T>> request) {
    if (closed) {
      CompletableFuture<T> rejected = new CompletableFuture<>();
      rejected.completeExceptionally(
          new java.util.concurrent.CancellationException("Connection retired"));
      return rejected;
    }
    CompletableFuture<T> response =
        request
            .get()
            .thenApply(
                value -> {
                  if (closed)
                    throw new java.util.concurrent.CancellationException("Connection retired");
                  return value;
                });
    pending.add(response);
    response.whenComplete((value, error) -> pending.remove(response));
    if (closed)
      response.completeExceptionally(
          new java.util.concurrent.CancellationException("Connection retired"));
    return response;
  }

  public CompletableFuture<ServerInformation> getServerInformation() {
    return active(() -> api.getServerInformation(secretKey))
        .thenApply(
            info -> {
              if (info == null
                  || info.getAccount() == null
                  || info.getServer() == null
                  || info.getAccount().getCurrency() == null) {
                throw new IllegalStateException("Incomplete Tebex server information");
              }
              headless.setToken(info.getPublicToken());
              return info;
            });
  }

  public CompletableFuture<DuePlayersResponse> getDuePlayers() {
    return active(() -> api.getDuePlayers(secretKey));
  }

  public CompletableFuture<OfflineCommandsResponse> getOfflineCommands() {
    return active(() -> api.getOfflineCommands(secretKey));
  }

  public CompletableFuture<List<QueuedCommand>> getOnlineCommands(QueuedPlayer player) {
    return active(() -> api.getOnlineCommands(secretKey, player));
  }

  public CompletableFuture<Boolean> deleteCommands(List<Integer> ids) {
    return active(() -> api.deleteCommands(secretKey, ids));
  }

  public CompletableFuture<List<Category>> getListing() {
    return active(() -> api.getListing(secretKey));
  }

  public CompletableFuture<CheckoutUrl> createCheckoutUrl(int id, String username) {
    return active(() -> api.createCheckoutUrl(secretKey, id, username));
  }

  public CompletableFuture<List<CommunityGoal>> getCommunityGoals() {
    return active(() -> api.getCommunityGoals(secretKey));
  }

  public CompletableFuture<Boolean> createBan(String username, String ip, String reason) {
    return active(() -> api.createBan(secretKey, username, ip, reason));
  }

  public CompletableFuture<PlayerLookupInfo> getPlayerLookupInfo(String username) {
    return active(() -> api.getPlayerLookupInfo(secretKey, username));
  }

  public CompletableFuture<Boolean> sendJoinEvents(List<ServerEvent> events) {
    return active(() -> api.sendJoinEvents(secretKey, events));
  }

  public CompletableFuture<Boolean> sendTelemetry() {
    return active(
        () ->
            api.sendTelemetry(
                secretKey,
                new StartupTelemetry(
                    platform.getType().name(),
                    platform.getTelemetry().getServerVersion(),
                    platform.isOnlineMode(),
                    platform.getPluginVersion())));
  }

  public CompletableFuture<Boolean> sendPluginEvents() {
    List<PluginEvent> batch = platform.snapshotPluginEvents();
    if (batch.isEmpty()) return CompletableFuture.completedFuture(true);
    return active(() -> api.sendPluginEvents(batch))
        .thenApply(
            sent -> {
              if (Boolean.TRUE.equals(sent)) platform.removePluginEvents(batch);
              return sent;
            });
  }
}

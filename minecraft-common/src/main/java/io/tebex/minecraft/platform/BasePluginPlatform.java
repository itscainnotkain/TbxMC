package io.tebex.minecraft.platform;

import static io.tebex.minecraft.util.ResourceUtil.getBundledFile;

import dev.dejvokep.boostedyaml.YamlDocument;
import io.tebex.minecraft.TebexClient;
import io.tebex.minecraft.commands.TebexCommands;
import io.tebex.minecraft.platform.config.*;
import io.tebex.minecraft.runtime.DeliveryQueue;
import io.tebex.minecraft.util.*;
import io.tebex.model.*;
import io.tebex.model.PluginEvent.Level;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.LinkedPlayer;

/** Shared Minecraft lifecycle and orchestration. All Tebex transport belongs to the SDK. */
public abstract class BasePluginPlatform implements PluginPlatform {
  protected volatile TebexClient sdk;
  protected volatile ServerPlatformConfig config;
  protected YamlDocument configYaml;
  protected volatile boolean setup;
  protected volatile ServerInformation storeInformation;
  protected volatile List<Category> storeCategories = Collections.emptyList();
  private final TebexCommands commands = new TebexCommands(this);
  private final List<ServerEvent> serverEvents = Collections.synchronizedList(new ArrayList<>());
  private final List<PluginEvent> pluginEvents = Collections.synchronizedList(new ArrayList<>());
  private final Map<Object, Integer> queuedPlayers = new ConcurrentHashMap<>();
  private final Map<String, UUID> resolvedFloodgateIds = new ConcurrentHashMap<>();
  private final AtomicBoolean floodgateWarningLogged = new AtomicBoolean();
  private final ScheduledExecutorService worker =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "tebex-minecraft");
            t.setDaemon(true);
            return t;
          });
  private final AtomicBoolean started = new AtomicBoolean();
  private volatile boolean stopped;
  private volatile long generation;
  private volatile long nextCheck;
  private volatile long nextRefresh;
  private volatile long nextEvents;
  private volatile long nextLogs;
  private volatile long nextAck;
  private CompletableFuture<String[]> checking;
  private volatile boolean refreshing, sendingEvents, sendingLogs, acknowledging;
  private final Set<CompletableFuture<?>> outstanding = ConcurrentHashMap.newKeySet();
  private volatile DeliveryQueue deliveries;

  protected TebexClient createClient(String key) {
    return new TebexClient(this, key);
  }

  private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
    outstanding.add(future);
    future.whenComplete((value, error) -> outstanding.remove(future));
    if (stopped) future.completeExceptionally(new CancellationException("Plugin stopped"));
    return future;
  }

  public final TebexCommands getCommands() {
    return commands;
  }

  public final void initStore() {
    if (sdk == null) sdk = createClient("");
    if (started.compareAndSet(false, true))
      worker.scheduleWithFixedDelay(this::tickSafely, 0, 50, TimeUnit.MILLISECONDS);
    if (config != null
        && config.getSecretKey() != null
        && !config.getSecretKey().trim().isEmpty()) {
      connect(config.getSecretKey(), false)
          .exceptionally(
              error -> {
                warning("Failed to connect to Tebex.", "Check the configured secret key.", error);
                return null;
              });
    } else info("Welcome to Tebex! Set your key with /tebex secret <key>.");
  }

  /**
   * Validate before adoption; invalidate old callbacks and pending deliveries as one lifecycle
   * transition.
   */
  public CompletableFuture<ServerInformation> connect(String key, boolean persist) {
    if (stopped) {
      CompletableFuture<ServerInformation> rejected = new CompletableFuture<>();
      rejected.completeExceptionally(new CancellationException("Plugin stopped"));
      return rejected;
    }
    TebexClient candidate = createClient(key);
    final long requestGeneration;
    synchronized (this) {
      requestGeneration = ++generation;
    }
    return track(
        candidate
            .getServerInformation()
            .thenCompose(
                info -> {
                  CompletableFuture<ServerInformation> result = track(new CompletableFuture<>());
                  executeBlocking(
                      () -> {
                        synchronized (BasePluginPlatform.this) {
                          try {
                            if (stopped || generation != requestGeneration) {
                              candidate.close();
                              result.completeExceptionally(
                                  new CancellationException("Connection superseded"));
                              return;
                            }
                            if (persist) {
                              String oldKey = config.getSecretKey();
                              try {
                                config.setSecretKey(key);
                                saveConfig(config);
                              } catch (RuntimeException failure) {
                                config.setSecretKey(oldKey);
                                throw failure;
                              }
                            }
                            // Reloading the same credentials must not discard the
                            // dispatch/acknowledgement ledger.
                            if (current(sdk)
                                && sdk.getSecretKey().equals(candidate.getSecretKey())) {
                              candidate.close();
                              storeInformation = info;
                              nextRefresh = 0;
                              result.complete(info);
                              return;
                            }
                            if (sdk != null) sdk.close();
                            if (deliveries != null) deliveries.close();
                            sdk = candidate;
                            storeInformation = info;
                            storeCategories = Collections.emptyList();
                            resolvedFloodgateIds.clear();
                            queuedPlayers.clear();
                            serverEvents.clear();
                            pluginEvents.clear();
                            deliveries =
                                new DeliveryQueue(
                                    this::nowMillis, command -> deliver(candidate, command));
                            setup = true;
                            nextCheck = nextRefresh = nextAck = 0;
                            checking = null;
                            refreshing = sendingEvents = sendingLogs = acknowledging = false;
                            nextEvents = nowMillis() + 60000;
                            nextLogs = nowMillis() + 600000;
                            info(
                                "Connected to "
                                    + info.getAccount().getName()
                                    + " as "
                                    + info.getServer().getName());
                            try {
                              candidate
                                  .sendTelemetry()
                                  .exceptionally(
                                      error -> {
                                        debug("Telemetry could not be sent.");
                                        return false;
                                      });
                            } catch (RuntimeException telemetryError) {
                              debug("Telemetry could not be sent.");
                            }
                            result.complete(info);
                          } catch (Exception failure) {
                            candidate.close();
                            result.completeExceptionally(failure);
                          }
                        }
                      });
                  return result;
                })
            .whenComplete(
                (info, error) -> {
                  if (error != null) candidate.close();
                }));
  }

  protected long nowMillis() {
    return System.currentTimeMillis();
  }

  void tickSafely() {
    try {
      if (stopped || !setup) return;
      DeliveryQueue queue = deliveries;
      if (queue != null) queue.pump();
      long now = nowMillis();
      if (now >= nextCheck) checkCommandQueue(true);
      if (now >= nextRefresh && !refreshing) refreshListings();
      if (now >= nextEvents && !sendingEvents) flushEvents();
      if (now >= nextLogs && !sendingLogs) flushLogs();
      if (now >= nextAck && !acknowledging && queue != null) acknowledge(queue);
    } catch (Exception error) {
      error("Tebex background task failed.", error);
    }
  }

  public final void performCheck() {
    checkCommandQueue(false);
  }

  public synchronized CompletableFuture<String[]> checkCommandQueue(boolean scheduled) {
    if (!isSetup())
      return CompletableFuture.completedFuture(new String[] {"Tebex is not connected."});
    if (checking != null && !checking.isDone()) return checking;
    final TebexClient connection = sdk;
    final DeliveryQueue queue = deliveries;
    nextCheck = nowMillis() + 120000;
    checking =
        connection
            .getDuePlayers()
            .thenCompose(
                due -> {
                  if (!current(connection)) return CompletableFuture.completedFuture(null);
                  nextCheck = nowMillis() + Math.max(1, due.getNextCheck()) * 1000L;
                  List<CompletableFuture<?>> requests = new ArrayList<>();
                  if (due.isExecuteOffline())
                    requests.add(
                        connection
                            .getOfflineCommands()
                            .thenAccept(
                                response -> {
                                  if (current(connection))
                                    response.getCommands().forEach(queue::offer);
                                }));
                  queuedPlayers.clear();
                  for (QueuedPlayer player : due.getPlayers()) {
                    Object playerId =
                        getPlayerId(player.getName(), PlayerIdentity.uuid(player.getUuid()));
                    queuedPlayers.put(playerId, player.getId());
                    requests.add(
                        callPlayer(playerId, () -> isPlayerOnline(playerId))
                            .handle((online, error) -> error == null && Boolean.TRUE.equals(online))
                            .thenCompose(
                                online -> {
                                  if (!online || !current(connection))
                                    return CompletableFuture.completedFuture(null);
                                  return connection
                                      .getOnlineCommands(player)
                                      .thenAccept(
                                          list -> {
                                            if (current(connection)) list.forEach(queue::offer);
                                          });
                                }));
                  }
                  return CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]));
                })
            .handle(
                (ignored, error) -> {
                  if (error != null && current(connection)) {
                    String message = String.valueOf(error.getMessage());
                    nextCheck =
                        nowMillis()
                            + (message.contains("429")
                                ? 300000L
                                : message.contains("403") ? 1800000L : 60000L);
                    warning("Queue check failed.", "It will be retried automatically.", error);
                    return new String[] {"Queue check failed; retry scheduled."};
                  }
                  return new String[] {"Queue check completed."};
                });
    return checking;
  }

  private boolean current(TebexClient connection) {
    return connection != null && !stopped && setup && sdk == connection && !connection.isClosed();
  }

  private CompletableFuture<Boolean> deliver(TebexClient connection, QueuedCommand command) {
    if (!current(connection)) return CompletableFuture.completedFuture(false);
    QueuedPlayer player = command.getPlayer();
    Object id =
        player == null ? "" : getPlayerId(player.getName(), PlayerIdentity.uuid(player.getUuid()));
    String parsed = PlayerIdentity.command(command, this);
    CompletableFuture<Boolean> eligible =
        command.isOnline()
            ? callPlayer(
                id,
                () ->
                    player != null
                        && (!isOnlineMode() || isGeyser() || PlayerIdentity.hasUuid(player))
                        && isPlayerOnline(id)
                        && getFreeSlots(id) >= command.getRequiredSlots())
            : CompletableFuture.completedFuture(true);
    return eligible
        .thenCompose(
            allowed -> {
              if (!allowed || !current(connection)) return CompletableFuture.completedFuture(false);
              return dispatchAsync(parsed, () -> current(connection))
                  .thenApply(
                      result -> {
                        if (result == null) return false;
                        if (!result.isSuccess())
                          warning(
                              "Command failed: " + result.getMessage(),
                              "Check the command syntax in the store.");
                        return true;
                      });
            })
        .exceptionally(
            error -> {
              if (current(connection))
                warning("Delivery deferred.", "It will be retried on a later queue check.", error);
              return false;
            });
  }

  /** The future completes only after the native command dispatcher finishes. */
  public CompletableFuture<CommandResult> dispatchAsync(String command, Supplier<Boolean> active) {
    CompletableFuture<CommandResult> result = track(new CompletableFuture<>());
    if (stopped) return result;
    executeBlocking(
        () -> {
          try {
            result.complete(active.get() ? dispatchCommand(command) : null);
          } catch (Exception error) {
            result.completeExceptionally(error);
          }
        });
    return result;
  }

  public <T> CompletableFuture<T> callPlayer(Object player, Supplier<T> action) {
    CompletableFuture<T> result = track(new CompletableFuture<>());
    if (stopped) return result;
    executeForPlayer(
        player,
        () -> {
          try {
            if (stopped) throw new CancellationException("Plugin stopped");
            result.complete(action.get());
          } catch (Exception error) {
            result.completeExceptionally(error);
          }
        },
        () -> result.completeExceptionally(new CancellationException("Player disconnected")));
    return result;
  }

  public void executeForPlayer(Object player, Runnable action, Runnable retired) {
    executeBlocking(action);
  }

  private void acknowledge(DeliveryQueue queue) {
    List<Integer> ids = queue.acknowledgements();
    nextAck = nowMillis() + 5000;
    if (ids.isEmpty()) return;
    acknowledging = true;
    TebexClient connection = sdk;
    connection
        .deleteCommands(ids)
        .whenComplete(
            (success, error) -> {
              if (current(connection) && Boolean.TRUE.equals(success)) queue.acknowledged(ids);
              if (current(connection)) acknowledging = false;
            });
  }

  public synchronized void refreshListings() {
    if (!isSetup() || refreshing) return;
    refreshing = true;
    nextRefresh = nowMillis() + 300000;
    TebexClient connection = sdk;
    connection
        .getListing()
        .whenComplete(
            (categories, error) -> {
              if (current(connection) && error == null)
                storeCategories = Collections.unmodifiableList(new ArrayList<>(categories));
              if (current(connection)) refreshing = false;
            });
  }

  private void flushEvents() {
    nextEvents = nowMillis() + 60000;
    List<ServerEvent> batch;
    synchronized (serverEvents) {
      batch = new ArrayList<>(serverEvents.subList(0, Math.min(750, serverEvents.size())));
    }
    if (batch.isEmpty()) return;
    sendingEvents = true;
    TebexClient connection = sdk;
    connection
        .sendJoinEvents(batch)
        .whenComplete(
            (sent, error) -> {
              if (current(connection) && Boolean.TRUE.equals(sent)) serverEvents.removeAll(batch);
              if (current(connection)) sendingEvents = false;
            });
  }

  private void flushLogs() {
    nextLogs = nowMillis() + 600000;
    if (!reportingEnabled()) {
      pluginEvents.clear();
      return;
    }
    sendingLogs = true;
    TebexClient connection = sdk;
    connection
        .sendPluginEvents()
        .whenComplete(
            (sent, error) -> {
              if (current(connection)) sendingLogs = false;
            });
  }

  public synchronized void shutdown() {
    stopped = true;
    setup = false;
    generation++;
    if (sdk != null) sdk.close();
    if (deliveries != null) deliveries.close();
    worker.shutdownNow();
    outstanding.forEach(
        future -> future.completeExceptionally(new CancellationException("Plugin stopped")));
    serverEvents.clear();
    pluginEvents.clear();
  }

  public final void halt() {
    shutdown();
  }

  public boolean isStopped() {
    return stopped;
  }

  public final boolean isSetup() {
    return setup && !stopped;
  }

  public final TebexClient getClient() {
    return sdk;
  }

  public final void setSetup(boolean value) {
    setup = value;
  }

  public final void configure() {
    setup = true;
  }

  public final Map<Object, Integer> getQueuedPlayers() {
    return queuedPlayers;
  }

  public final List<Category> getStoreCategories() {
    return storeCategories;
  }

  public final void setStoreCategories(List<Category> categories) {
    storeCategories = categories;
  }

  public final ServerInformation getStoreInformation() {
    return storeInformation;
  }

  public final ServerInformation.Account getAccount() {
    return storeInformation == null ? null : storeInformation.getAccount();
  }

  public final ServerInformation.Server getStoreServer() {
    return storeInformation == null ? null : storeInformation.getServer();
  }

  public final void setStoreInfo(ServerInformation info) {
    storeInformation = info;
  }

  public void setServerInformation(ServerInformation info) {
    storeInformation = info;
  }

  public final String getStoreType() {
    return getAccount() == null ? "" : getAccount().getGameType();
  }

  public final boolean isGeyser() {
    return getStoreType() != null && getStoreType().toLowerCase(Locale.ROOT).contains("geyser");
  }

  public final Object getPlayerId(String name, UUID uuid) {
    return isOnlineMode() && !isGeyser() && uuid != null && !uuid.equals(UUIDUtil.EMPTY_UUID)
        ? uuid
        : name == null ? "" : name;
  }

  public String resolveCommandPlayerId(QueuedPlayer player) {
    if (player == null) return "";
    if (PlayerIdentity.hasUuid(player)) return PlayerIdentity.defaultIdentifier(player);
    if (PlayerIdentity.hasXuid(player)) {
      UUID id =
          resolvedFloodgateIds.computeIfAbsent(
              player.getXuid(), ignored -> resolveFloodgateUniqueId(player));
      if (id != null) return id.toString();
    }
    return PlayerIdentity.defaultIdentifier(player);
  }

  public void sendCheckoutLink(String name, String url) {
    TebexClient connection = sdk;
    callPlayer(
        name,
        () -> {
          if (!current(connection)) return null;
          if (isOnlineFloodgatePlayer(name))
            sendPlayerMessage(name, "Checkout started! Type this link into your browser: " + url);
          else sendJavaCheckoutLink(name, url);
          return null;
        });
  }

  protected void sendJavaCheckoutLink(String name, String url) {
    sendPlayerMessage(name, "Checkout started! Complete payment here: " + url);
  }

  private UUID resolveFloodgateUniqueId(QueuedPlayer player) {
    if (!isGeyser()) {
      return null;
    }

    Long xuid = io.tebex.minecraft.util.PlayerIdentity.xuid(player);
    if (xuid == null) {
      debug(
          "Unable to parse Bedrock XUID '"
              + player.getXuid()
              + "' for player '"
              + player.getName()
              + "'.");
      return null;
    }

    try {
      FloodgateApi api = FloodgateApi.getInstance();
      UUID bedrockId = api.createJavaPlayerId(xuid);

      if (bedrockId == null || UUIDUtil.EMPTY_UUID.equals(bedrockId)) {
        return null;
      }

      FloodgatePlayer onlinePlayer = api.getPlayer(bedrockId);
      if (onlinePlayer != null && onlinePlayer.getCorrectUniqueId() != null) {
        return onlinePlayer.getCorrectUniqueId();
      }

      if (api.getPlayerLink() != null) {
        try {
          LinkedPlayer linkedPlayer =
              api.getPlayerLink().getLinkedPlayer(bedrockId).get(2, TimeUnit.SECONDS);
          if (linkedPlayer != null && linkedPlayer.getJavaUniqueId() != null) {
            return linkedPlayer.getJavaUniqueId();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          debug(
              "Interrupted while resolving Floodgate UUID for player '" + player.getName() + "'.");
        } catch (ExecutionException | TimeoutException e) {
          debug(
              "Failed to resolve Floodgate link data for player '"
                  + player.getName()
                  + "': "
                  + e.getMessage());
        }
      }

      return bedrockId;
    } catch (IllegalStateException | NoClassDefFoundError e) {
      warnMissingFloodgateApi();
    }

    return null;
  }

  private boolean isOnlineFloodgatePlayer(String playerName) {
    UUID playerUniqueId = getPlayerUniqueId(playerName);
    if (playerUniqueId == null) {
      return false;
    }

    try {
      return FloodgateApi.getInstance().isFloodgatePlayer(playerUniqueId);
    } catch (IllegalStateException | NoClassDefFoundError e) {
      warnMissingFloodgateApi();
      return false;
    }
  }

  private void warnMissingFloodgateApi() {
    if (!floodgateWarningLogged.compareAndSet(false, true)) {
      return;
    }

    warning(
        "Received a Bedrock XUID for command placeholder resolution, but the Floodgate API is unavailable.",
        "Install Floodgate on the same server or proxy running Tebex so {id}/{uuid} placeholders can be resolved for Bedrock players.");
  }

  public final void info(String message) {
    log(java.util.logging.Level.INFO, message);
  }

  public final void debug(String message) {
    if (config != null && config.isVerbose()) info("[DEBUG] " + message);
  }

  public final void warning(String message, String solution) {
    log(java.util.logging.Level.WARNING, message + " " + solution);
    createPluginEvent(Level.WARNING, message);
  }

  public final void warning(String message, String solution, Throwable error) {
    warning(message, solution);
  }

  public final void error(String message) {
    error(message, null);
  }

  public final void error(String message, Throwable error) {
    log(java.util.logging.Level.SEVERE, message);
    createPluginEvent(Level.ERROR, message, error);
  }

  public final int getVersionNumber() {
    return Integer.parseInt(getPluginVersion().replace(".", ""));
  }

  public final void createPluginEvent(Level level, String message) {
    createPluginEvent(level, message, null);
  }

  private boolean reportingEnabled() {
    return isSetup()
        && config != null
        && config.isAutoReportEnabled()
        && getAccount() != null
        && getAccount().isLogEvents();
  }

  public final void createPluginEvent(Level level, String message, Throwable error) {
    if (!reportingEnabled()) return;
    PlatformTelemetry telemetry = getTelemetry();
    PluginEvent event =
        new PluginEvent(level, message)
            .onStore(getAccount())
            .onServer(getStoreServer())
            .onPlatform(
                telemetry.getServerSoftware(),
                telemetry.getServerVersion(),
                telemetry.getJavaVersion(),
                getPluginVersion());
    if (error != null) event.withTrace(error);
    synchronized (pluginEvents) {
      if (pluginEvents.size() < 1000) pluginEvents.add(event);
    }
  }

  public List<PluginEvent> snapshotPluginEvents() {
    if (!reportingEnabled()) return Collections.emptyList();
    synchronized (pluginEvents) {
      return new ArrayList<>(pluginEvents.subList(0, Math.min(750, pluginEvents.size())));
    }
  }

  public void removePluginEvents(List<PluginEvent> events) {
    pluginEvents.removeAll(events);
  }

  public final void clearPluginEvents() {
    pluginEvents.clear();
  }

  public final List<ServerEvent> getJoinEvents() {
    return serverEvents;
  }

  public void clearSelectedPluginEvents(List<ServerEvent> events) {
    serverEvents.removeAll(events);
  }

  public void createJoinEvent(String uuid, String username, String ip) {
    synchronized (serverEvents) {
      if (!stopped && serverEvents.size() < 1000)
        serverEvents.add(new ServerEvent(uuid, username, ip, ServerEvent.Type.JOIN));
    }
  }

  public final YamlDocument initPlatformConfig() throws IOException {
    return YamlDocument.create(getBundledFile(this, getRunningDirectory(), "config.yml"));
  }

  public final ServerPlatformConfig loadServerPlatformConfig(YamlDocument yaml) {
    ServerPlatformConfig value = new ServerPlatformConfig(yaml.getInt("config-version", 2));
    value.setYamlDocument(yaml);
    value.setSecretKey(yaml.getString("server.secret-key", ""));
    value.setBuyCommandName(yaml.getString("buy-command.name", "buy"));
    value.setBuyCommandEnabled(yaml.getBoolean("buy-command.enabled", true));
    value.setCheckForUpdates(yaml.getBoolean("check-for-updates", true));
    value.setVerbose(yaml.getBoolean("verbose", false));
    value.setProxyMode(yaml.getBoolean("server.proxy", false));
    value.setAutoReportEnabled(yaml.getBoolean("auto-report-enabled", true));
    return value;
  }

  public IPlatformConfig getPlatformConfig() {
    return config;
  }

  public void loadPlatformConfig() {
    try {
      configYaml = initPlatformConfig();
      config = loadServerPlatformConfig(configYaml);
      String env = System.getenv("TEBEX_SECRET_KEY");
      if (env != null && !env.isEmpty()) config.setSecretKey(env);
    } catch (IOException error) {
      throw new IllegalStateException("Cannot load Tebex configuration", error);
    }
  }

  public void reloadConfig() {
    loadPlatformConfig();
    if (config.getSecretKey() != null && !config.getSecretKey().isEmpty())
      connect(config.getSecretKey(), false);
  }

  public void saveConfig(IPlatformConfig value) {
    try {
      config = (ServerPlatformConfig) value;
      config.getYamlDocument().set("server.secret-key", config.getSecretKey());
      config.getYamlDocument().set("verbose", config.isVerbose());
      config.getYamlDocument().save();
    } catch (IOException error) {
      throw new IllegalStateException("Cannot save Tebex configuration", error);
    }
  }

  public void executeAsync(Runnable action) {
    if (!stopped) worker.execute(action);
  }

  public void executeAsyncLater(Runnable action, long delay, TimeUnit unit) {
    if (!stopped) worker.schedule(action, delay, unit);
  }

  public abstract void executeBlocking(Runnable action);

  public void executeBlockingLater(Runnable action, long delay, TimeUnit unit) {
    executeAsyncLater(() -> executeBlocking(action), delay, unit);
  }
}

package io.tebex.minecraft;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import io.tebex.http.PluginApi;
import io.tebex.minecraft.platform.MockPluginPlatform;
import io.tebex.model.ServerInformation;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TebexClientTest {
  @Test
  void usesSdkTransportCredentialsAndModelsAgainstLocalServer() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> key = new AtomicReference<>();
    server.createContext(
        "/information",
        exchange -> {
          key.set(exchange.getRequestHeaders().getFirst("X-Tebex-Secret"));
          byte[] body =
              "{\"account\":{\"id\":1,\"name\":\"Fixture\",\"currency\":{\"iso_4217\":\"USD\"}},\"server\":{\"id\":2},\"public_token\":\"public\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    MockPluginPlatform host = new MockPluginPlatform();
    try {
      TebexClient client =
          new TebexClient(
              host,
              "test-secret",
              new PluginApi("http://127.0.0.1:" + server.getAddress().getPort()));
      ServerInformation info = client.getServerInformation().get(5, TimeUnit.SECONDS);
      assertEquals("test-secret", key.get());
      assertEquals("Fixture", info.getAccount().getName());
      assertNotNull(client.headless().Headless);
      assertNotNull(client.headless().Baskets);
    } finally {
      server.stop(0);
      host.shutdown();
    }
  }

  @Test
  void retiredConnectionDoesNotStartAnotherRequest() {
    MockPluginPlatform host = new MockPluginPlatform();
    PluginApi api = org.mockito.Mockito.mock(PluginApi.class);
    TebexClient client = new TebexClient(host, "old", api);
    client.close();
    assertTrue(client.getDuePlayers().isCompletedExceptionally());
    org.mockito.Mockito.verifyNoInteractions(api);
    host.shutdown();
  }

  @Test
  void retiredConnectionRejectsLateApiResponse() {
    CompletableFuture<ServerInformation> response = new CompletableFuture<>();
    MockPluginPlatform host = new MockPluginPlatform();
    PluginApi api = org.mockito.Mockito.mock(PluginApi.class);
    org.mockito.Mockito.when(api.getServerInformation("old")).thenReturn(response);
    TebexClient client = new TebexClient(host, "old", api);
    CompletableFuture<ServerInformation> result = client.getServerInformation();
    client.close();
    response.complete(new Gson().fromJson("{\"account\":{}}", ServerInformation.class));
    assertTrue(result.isCompletedExceptionally());
    host.shutdown();
  }
}

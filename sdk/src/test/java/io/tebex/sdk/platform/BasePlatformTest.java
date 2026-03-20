package io.tebex.sdk.platform;

import io.tebex.sdk.obj.QueuedPlayer;
import io.tebex.sdk.request.response.ServerInformation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasePlatformTest {
    @Test
    void detectsMinecraftGeyserStoreType() {
        MockPluginPlatform platform = new MockPluginPlatform();
        platform.setSetup(true);
        platform.setServerInformation(serverInformation("Minecraft Geyser"));

        assertTrue(platform.isGeyser());
    }

    @Test
    void nonGeyserStoreTypesAreIgnored() {
        MockPluginPlatform platform = new MockPluginPlatform();
        platform.setSetup(true);
        platform.setServerInformation(serverInformation("Minecraft Java"));

        assertFalse(platform.isGeyser());
    }

    @Test
    void onlineCommandsPreferTheLiveServerUuid() {
        UUID liveUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        TestPluginPlatform platform = new TestPluginPlatform(new PlayerStub(liveUuid));
        platform.setSetup(true);
        platform.setServerInformation(serverInformation("Minecraft Geyser"));

        QueuedPlayer resolved = platform.resolveOnlineQueuedPlayer("BedrockSteve", new QueuedPlayer(1, "BedrockSteve", "12345"));

        assertEquals(liveUuid.toString(), resolved.getUuid());
    }

    private ServerInformation serverInformation(String gameType) {
        return new ServerInformation(
                new ServerInformation.Store(
                        1,
                        "example.tebex.io",
                        "Example Store",
                        new ServerInformation.Store.Currency("GBP", "£"),
                        true,
                        gameType,
                        false
                ),
                new ServerInformation.Server(1, "Example Server")
        );
    }

    private static final class TestPluginPlatform extends MockPluginPlatform {
        private final Object player;

        private TestPluginPlatform(Object player) {
            this.player = player;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getPlayer(Object uuidOrUsername) {
            return (T) player;
        }
    }

    private static final class PlayerStub {
        private final UUID uniqueId;

        private PlayerStub(UUID uniqueId) {
            this.uniqueId = uniqueId;
        }

        public UUID getUniqueId() {
            return uniqueId;
        }
    }
}

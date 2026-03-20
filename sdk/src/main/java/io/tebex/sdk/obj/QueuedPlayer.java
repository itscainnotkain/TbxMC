package io.tebex.sdk.obj;

import com.google.gson.JsonObject;
import io.tebex.sdk.util.UUIDUtil;
import lombok.Data;

@Data
public class QueuedPlayer {
    private final int id;
    private final String name;
    private final String uuid;

    /**
     * Constructs a Player instance.
     *
     * @param id The Tebex player ID.
     * @param name The player name.
     * @param uuid The player UUID/XUID. Mojang UUIDs are normalized to Java UUID format and XUIDs are translated
     *             into Floodgate UUIDs.
     */
    public QueuedPlayer(int id, String name, String uuid) {
        this.id = id;
        this.name = name;
        this.uuid = String.valueOf(UUIDUtil.mojangIdToJavaId(uuid));
    }

    public static QueuedPlayer fromJson(JsonObject object) {
        return new QueuedPlayer(
                object.get("id").getAsInt(),
                object.get("name").getAsString(),
                !object.get("uuid").isJsonNull() ? object.get("uuid").getAsString() : null
        );
    }

    public QueuedPlayer withUuid(String uuid) {
        return new QueuedPlayer(id, name, uuid);
    }
}

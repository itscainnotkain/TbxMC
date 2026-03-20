package io.tebex.sdk.util;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class UUIDUtil {
    public static final UUID EMPTY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Translates a Tebex player identifier into a Java UUID. Tebex returns Mojang-style UUIDs for Java players
     * and Floodgate/XUID identifiers for Bedrock players.
     *
     * @param id the Tebex identifier to use
     * @return the Java UUID, or {@link #EMPTY_UUID} if the identifier is missing or invalid
     */
    public static UUID mojangIdToJavaId(String id) {
        if (id == null) {
            return EMPTY_UUID; // empty uuid
        }

        String trimmed = id.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")) {
            return EMPTY_UUID;
        }

        if (isXuid(trimmed)) {
            return xuidToJavaId(trimmed);
        }

        try {
            return UUID.fromString(trimmed.replaceFirst(
                    "^([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})$",
                    "$1-$2-$3-$4-$5"
            ));
        } catch (IllegalArgumentException ignored) {
            return EMPTY_UUID;
        }
    }

    public static boolean isXuid(String id) {
        return id != null && id.length() <= 19 && id.matches("^\\d+$");
    }

    public static UUID xuidToJavaId(String xuid) {
        if (!isXuid(xuid)) {
            return EMPTY_UUID;
        }

        try {
            return new UUID(0L, Long.parseLong(xuid.trim()));
        } catch (NumberFormatException ignored) {
            return EMPTY_UUID;
        }
    }

    public static UUID extractUuid(Object player) {
        Object unwrapped = unwrapOptional(player);
        if (unwrapped == null) {
            return EMPTY_UUID;
        }

        if (unwrapped instanceof UUID) {
            return (UUID) unwrapped;
        }

        for (String methodName : new String[]{"getUniqueId", "getUuid"}) {
            try {
                Method method = unwrapped.getClass().getMethod(methodName);
                Object value = method.invoke(unwrapped);
                if (value instanceof UUID) {
                    return (UUID) value;
                }
                if (value instanceof String) {
                    return mojangIdToJavaId((String) value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor name
            }
        }

        return EMPTY_UUID;
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional) {
            return ((Optional<?>) value).orElse(null);
        }

        return value;
    }
}

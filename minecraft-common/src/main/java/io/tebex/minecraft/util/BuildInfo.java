package io.tebex.minecraft.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Version supplied by the shared Gradle resource convention. */
public final class BuildInfo {
  private BuildInfo() {}

  public static String version() {
    Properties properties = new Properties();
    try (InputStream input =
        BuildInfo.class.getClassLoader().getResourceAsStream("tebex-build.properties")) {
      if (input != null) properties.load(input);
    } catch (IOException ignored) {
    }
    return properties.getProperty("version", "unknown");
  }
}

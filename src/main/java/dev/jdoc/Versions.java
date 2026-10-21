package dev.jdoc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Versions {

  private Versions() {
    throw new AssertionError("Utility Class cannot be instantiated.");
  }

  static final List<String> SUPPORTED = Arrays.asList("17", "21", "25");

  static String base(String version) {
    if (!SUPPORTED.contains(version)) {
      throw new IllegalArgumentException("Unsupported documentation version: " + version);
    }
    return "https://docs.oracle.com/en/java/javase/" + version + "/docs/api/";
  }

  static String typeIndex(String version) {
    return base(version) + "type-search-index.js";
  }

  static String memberIndex(String version) {
    return base(version) + "member-search-index.js";
  }

  static String packageIndex(String version) {
    return base(version) + "package-search-index.js";
  }

  static String supportedFor(int major) {
    if (major < 17) {
      throw new IllegalArgumentException("Project targets Java " + major
          + "; jDoc supports documentation releases 17, 21, and 25."
          + " Use --docVersion to override the project target.");
    }
    return closest(major);
  }

  static Selection detect(String javaHome, String runtimeVersion) {
    if (javaHome != null && !javaHome.isBlank()) {
      try {
        Path home = Path.of(javaHome);
        if (Files.isRegularFile(home.resolve("bin/java"))) {
          Properties release = new Properties();
          try (InputStream input = Files.newInputStream(home.resolve("release"))) {
            release.load(input);
          }
          int major = major(release.getProperty("JAVA_VERSION"));
          if (major >= 17) {
            return new Selection(major, closest(major), "JAVA_HOME");
          }
        }
      } catch (IOException | IllegalArgumentException ignored) {
        // An unusable JAVA_HOME should not prevent the running JVM from working.
      }
    }
    int major = major(runtimeVersion);
    if (major < 17) {
      throw new IllegalArgumentException("Java 17 or newer is required to select documentation.");
    }
    return new Selection(major, closest(major), "running Java");
  }

  private static String closest(int major) {
    String selected = SUPPORTED.get(0);
    for (String version : SUPPORTED) {
      if (Integer.parseInt(version) <= major) {
        selected = version;
      }
    }
    return selected;
  }

  private static int major(String value) {
    Matcher match = Pattern.compile("^(?:1\\.)?(\\d+)(?:[.+_\\-].*)?$")
        .matcher(value == null ? "" : value.replace("\"", "").trim());
    if (!match.matches()) {
      throw new IllegalArgumentException("Cannot determine Java version: " + value);
    }
    return Integer.parseInt(match.group(1));
  }

  static final class Selection {
    final int detected;
    final String version;
    final String source;

    Selection(int detected, String version, String source) {
      this.detected = detected;
      this.version = version;
      this.source = source;
    }

    String notice() {
      return detected == Integer.parseInt(version)
          ? "Using Java " + version + " documentation (detected from " + source + ")."
          : source + " is Java " + detected + "; using Java " + version + " documentation.";
    }
  }
}

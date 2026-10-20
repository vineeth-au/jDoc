package dev.jdoc;

import java.util.Arrays;
import java.util.List;

final class Versions {

  private Versions() {
    throw new AssertionError("Utility Class cannot be instantiated.");
  }

  static final List<String> SUPPORTED = Arrays.asList("8", "11", "17", "21", "25");

  static String base(String version) {
    if (!SUPPORTED.contains(version)) {
      throw new IllegalArgumentException("Unsupported documentation version: " + version);
    }
    return version.equals("8") ? "https://docs.oracle.com/javase/8/docs/api/"
        : "https://docs.oracle.com/en/java/javase/" + version + "/docs/api/";
  }

  static String index(String version) {
    return base(version) + (version.equals("8") ? "allclasses-noframe.html"
        : "allclasses-index.html");
  }
}

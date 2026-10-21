package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionsTest {
  @TempDir Path temp;

  @Test
  void selectsClosestSupportedReleaseFromJavaHome() throws IOException {
    Path home = temp.resolve("jdk");
    Files.createDirectories(home.resolve("bin"));
    Files.createFile(home.resolve("bin/java"));
    Files.writeString(home.resolve("release"), "JAVA_VERSION=\"22.0.2\"\n");

    Versions.Selection selection = Versions.detect(home.toString(), "17");
    assertEquals("21", selection.version);
    assertEquals("JAVA_HOME", selection.source);
    assertEquals("JAVA_HOME is Java 22; using Java 21 documentation.", selection.notice());
  }

  @Test
  void fallsBackToRunningJavaWhenHomeIsMissingOrTooOld() throws IOException {
    assertEquals("25", Versions.detect(temp.resolve("missing").toString(), "26").version);
    assertEquals("running Java", Versions.detect(null, "21").source);

    Path home = temp.resolve("old-jdk");
    Files.createDirectories(home.resolve("bin"));
    Files.createFile(home.resolve("bin/java"));
    Files.writeString(home.resolve("release"), "JAVA_VERSION=\"11.0.26\"\n");
    assertEquals("17", Versions.detect(home.toString(), "17").version);
    assertEquals("running Java", Versions.detect(home.toString(), "17").source);
  }

  @Test
  void onlyModernReleasesAreSupported() {
    assertEquals("https://docs.oracle.com/en/java/javase/17/docs/api/type-search-index.js",
        Versions.typeIndex("17"));
    assertThrows(IllegalArgumentException.class, () -> Versions.typeIndex("8"));
    assertThrows(IllegalArgumentException.class, () -> Versions.typeIndex("11"));
    assertThrows(IllegalArgumentException.class, () -> Versions.detect(null, "11"));
    assertEquals("21", Versions.supportedFor(22));
    assertThrows(IllegalArgumentException.class, () -> Versions.supportedFor(11));
  }
}

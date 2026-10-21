package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvailabilityTest {
  @TempDir Path cache;

  private void cache21Indexes() throws IOException {
    Path dir = cache.resolve("21");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("type-search-index.json"), "[{\"p\":\"java.util\",\"l\":\"List\"},"
        + "{\"p\":\"java.util\",\"l\":\"SequencedCollection\"}]");
    Files.writeString(dir.resolve("package-search-index.json"),
        "[{\"m\":\"java.base\",\"l\":\"java.util\"}]");
  }

  @Test
  void reportsExactDeclaringTypeFromCachedEffectiveClassPage() throws IOException {
    cache21Indexes();
    Path file = cache.resolve("21/classes/java.util.List.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{\"schemaVersion\":4,\"name\":\"java.util.List\","
        + "\"methods\":[],\"constructors\":[],\"fields\":[],\"inherited\":[{"
        + "\"name\":\"getFirst\",\"signature\":\"getFirst()\",\"kind\":\"method\","
        + "\"declaringType\":\"java.util.SequencedCollection\",\"since\":\"21\","
        + "\"parameterTypes\":[]}]}" );
    Availability available = new Availability(new Documentation(cache));
    String report = available.memberReport("java.util.List", new String[]{"-getFirst"}, "17");
    assertTrue(report.contains("Added in Java 21: getFirst documented on "
        + "java.util.List, declared in java.util.SequencedCollection"));
    assertTrue(report.contains("Cannot determine availability for Java 25"));
  }

  @Test
  void indexHitWithoutClassPageDoesNotInventDeclaringType() throws IOException {
    cache21Indexes();
    Files.writeString(cache.resolve("21/member-search-index.json"),
        "[{\"p\":\"java.util\",\"c\":\"List\",\"l\":\"getFirst()\"}]");
    String report = new Availability(new Documentation(cache))
        .memberReport("java.util.List", new String[]{"-getFirst"}, "17");
    assertTrue(report.contains("Java 21: getFirst() documented on java.util.List"));
    assertTrue(report.contains("declaring type unverified"));
    assertFalse(report.contains("declared in java.util.List"));
  }

  @Test
  void unknownReleasesStayUnknownAndCachedTypesCanBeReported() throws IOException {
    cache21Indexes();
    String report = new Availability(new Documentation(cache))
        .classReport("SequencedCollection", "17");
    assertTrue(report.contains("Java 21: java.util.SequencedCollection"));
    assertTrue(report.contains("Cannot determine availability for Java 25"));
  }
}

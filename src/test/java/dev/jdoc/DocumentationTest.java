package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationTest {
  @TempDir Path cache;

  @Test
  void stalePageRemainsReadableWhenOffline() throws IOException {
    Path path = cache.resolve("17/classes/demo.Child.json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, "{\"schemaVersion\":2,\"name\":\"demo.Child\","
        + "\"methods\":[],\"constructors\":[]}");
    DocModel doc = new Documentation(cache).classDoc("17", "demo.Child",
        "http://127.0.0.1:1/Child.html");
    assertTrue(doc.stale);
    assertEquals(2, doc.schemaVersion);
    assertTrue(doc.inherited.isEmpty());
  }
}

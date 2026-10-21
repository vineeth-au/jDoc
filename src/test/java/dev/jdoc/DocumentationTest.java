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

  @Test
  void previousMemberCacheRemainsReadableWhenOffline() throws IOException {
    Path path = cache.resolve("17/classes/demo.Child.json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, "{\"schemaVersion\":3,\"name\":\"demo.Child\","
        + "\"methods\":[],\"constructors\":[],\"inherited\":[],\"fields\":[]}");
    DocModel doc = new Documentation(cache).classDoc("17", "demo.Child",
        "http://127.0.0.1:1/Child.html");
    assertTrue(doc.stale);
    assertEquals(3, doc.schemaVersion);
  }

  @Test
  void structuredPageIsReadFromCacheWithoutNetwork() throws IOException {
    Path path = cache.resolve("17/classes/demo.Child.json");
    Files.createDirectories(path.getParent());
    DocModel fresh = new DocModel();
    fresh.schemaVersion = 4;
    fresh.name = "demo.Child";
    DocModel.Method method = new DocModel.Method();
    method.name = "sample";
    method.signature = "void sample()";
    method.since = "17";
    DocModel.Block block = new DocModel.Block();
    block.kind = "code";
    block.code = "first();\n  second();";
    method.body.add(block);
    fresh.methods.add(method);
    Files.writeString(path, new com.google.gson.Gson().toJson(fresh));

    DocModel loaded = new Documentation(cache).classDoc("17", "demo.Child",
        "http://127.0.0.1:1/Child.html");
    assertEquals("17", loaded.methods.get(0).since);
    assertEquals("first();\n  second();", loaded.methods.get(0).body.get(0).code);
    assertTrue(!loaded.stale);
  }
}

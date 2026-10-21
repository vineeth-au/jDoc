package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Optional compatibility checks against official pages; no Oracle HTML is committed. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "JDOC_LIVE_TEST", matches = "true")
class JavadocLiveTest {
  private static final String[] TYPES = {
      "java.base/java/lang/String.html", "java.base/java/util/ArrayList.html",
      "java.base/java/lang/Exception.html", "java.base/java/util/concurrent/TimeUnit.html",
      "java.base/java/util/Map.html"
  };

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void officialPagesRetainExpectedStructure(String release) throws IOException {
    for (String path : TYPES) {
      String url = Versions.base(release) + path;
      Document page = Jsoup.connect(url).timeout(20000).get();
      String name = path.substring(path.indexOf('/') + 1, path.length() - 5).replace('/', '.');
      DocModel doc = JavadocParser.parse(page, name, url, release);
      assertEquals(name, doc.name);
      assertFalse(doc.description.isBlank(), url);
      if (name.equals("java.lang.String")) {
        assertTrue(doc.methods.stream().anyMatch(m -> m.name.equals("valueOf")), url);
      } else if (name.equals("java.util.ArrayList")) {
        assertTrue(doc.methods.stream().anyMatch(m -> m.name.equals("add")), url);
        assertTrue(doc.inherited.stream().anyMatch(m -> m.name.equals("stream")
            && m.declaringType.equals("java.util.Collection")), url);
        assertEquals(1, doc.inherited.stream().filter(m -> m.name.equals("containsAll")).count(), url);
        assertTrue(doc.inherited.stream().anyMatch(m -> m.name.equals("containsAll")
            && m.declaringType.equals("java.util.AbstractCollection")), url);
      } else if (name.equals("java.lang.Exception")) {
        assertTrue(doc.methods.isEmpty(), url);
        assertEquals(5, doc.constructors.size(), url);
      } else if (name.equals("java.util.concurrent.TimeUnit")) {
        assertTrue(doc.enumConstants.stream().anyMatch(m -> m.name.equals("SECONDS")), url);
        assertTrue(doc.methods.stream().anyMatch(m -> m.name.equals("valueOf")), url);
      } else if (name.equals("java.util.Map")) {
        assertTrue(doc.methods.stream().map(m -> m.name)
            .toList().containsAll(List.of("getOrDefault", "of")), url);
        assertTrue(doc.nestedTypes.stream().anyMatch(m -> m.name.equals("Map.Entry")), url);
      }
    }
  }
}

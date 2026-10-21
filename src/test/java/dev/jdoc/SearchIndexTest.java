package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchIndexTest {
  private static String fixture(String release, String file) throws IOException {
    String path = "fixtures/" + release + "/search/" + file + "-search-index.js";
    try (InputStream in = SearchIndexTest.class.getClassLoader().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void supportedReleasesExposeTypedSearchIndexes(String release) throws IOException {
    List<SearchIndex.Type> types = SearchIndex.types(fixture(release, "type"));
    List<SearchIndex.Package> packages = SearchIndex.packages(fixture(release, "package"));
    List<SearchIndex.Member> members = SearchIndex.members(fixture(release, "member"));
    Map<String, String> urls = SearchIndex.classUrls(release, types, packages);
    assertEquals(Versions.base(release) + "java.base/java/util/Map.Entry.html",
        urls.get("java.util.Map.Entry"));
    assertTrue(SearchIndex.namedMethods(members, "computeIfAbsent").stream()
        .anyMatch(m -> m.className().equals("java.util.Map")));
    assertTrue(SearchIndex.namedMethods(members, "List").isEmpty());
    assertEquals(!release.equals("17"), SearchIndex.namedMethods(members, "getFirst")
        .stream().anyMatch(m -> m.className().equals("java.util.List")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void unexpectedJavascriptIsNeverExecuted(String release) {
    assertThrows(IOException.class, () -> SearchIndex.types("alert('hi');"));
  }
}

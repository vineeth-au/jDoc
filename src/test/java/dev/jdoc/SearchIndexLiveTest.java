package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Network compatibility check against each supported release's published index format. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "JDOC_LIVE_TEST", matches = "true")
class SearchIndexLiveTest {
  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void officialSearchIndexesCanBeParsedAndResolved(String release) throws IOException {
    List<SearchIndex.Type> types = SearchIndex.types(fetch(Versions.typeIndex(release)));
    List<SearchIndex.Package> packages = SearchIndex.packages(fetch(Versions.packageIndex(release)));
    List<SearchIndex.Member> members = SearchIndex.members(fetch(Versions.memberIndex(release)));
    Map<String, String> urls = SearchIndex.classUrls(release, types, packages);
    assertTrue(urls.get("java.util.List").endsWith("/java.base/java/util/List.html"));
    assertFalse(SearchIndex.namedMethods(members, "computeIfAbsent").isEmpty());
    assertTrue(release.equals("17") || SearchIndex.namedMethods(members, "getFirst")
        .stream().anyMatch(m -> m.className().equals("java.util.List")));
  }

  private static String fetch(String url) throws IOException {
    try (InputStream input = new URL(url).openStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}

package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReleaseReportTest {
  @TempDir Path cache;

  private static org.jsoup.nodes.Document fixture(String release, String name) throws IOException {
    String path = "fixtures/" + release + "/reports/" + name + "-list.html";
    try (InputStream in = ReleaseReportTest.class.getClassLoader().getResourceAsStream(path)) {
      return Jsoup.parse(in, "UTF-8", Versions.base(release) + name + "-list.html");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void newReportFiltersCumulativePageToRequestedRelease(String release) throws IOException {
    ReleaseReport report = ReleaseReport.parse(fixture(release, "new"), release, false);
    assertTrue(report.entries.stream().allMatch(e -> e.since.equals(release)));
    assertFalse(report.render().contains("String.strip()"));
    assertTrue(report.render().contains("java.lang") || report.render().contains("java.util.random"));
    if (release.equals("21")) {
      assertTrue(report.render().contains("StringBuilder.repeat(int, int)"));
      assertTrue(report.render().contains("StringTemplate [Preview]"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void removalReportIncludesOnlyTerminallyDeprecatedSection(String release) throws IOException {
    ReleaseReport report = ReleaseReport.parse(fixture(release, "deprecated"), release, true);
    assertTrue(report.render().contains("SecurityManager"));
    assertFalse(report.render().contains("String.old()"));
    assertFalse(report.render().contains("java.lang.Thread"));
    if (!release.equals("17")) assertTrue(report.render().contains("deprecated in 17"));
    if (release.equals("25")) {
      assertTrue(report.render().contains("JDIPermission (deprecated in 25)"));
    }
  }

  @Test
  void cachedReportIsReadableOffline() throws IOException {
    Path file = cache.resolve("21/reports/new-v1.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{\"release\":\"21\",\"kind\":\"new\",\"entries\":[{"
        + "\"packageName\":\"java.lang\",\"kind\":\"method\","
        + "\"label\":\"java.lang.StringBuilder.repeat(int,int)\",\"since\":\"21\"}]}");
    ReleaseReport report = new Documentation(cache).report("21", false);
    assertEquals(1, report.entries.size());
    assertTrue(report.render().contains("StringBuilder.repeat(int,int)"));
  }
}

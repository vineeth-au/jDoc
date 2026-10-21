package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "JDOC_LIVE_TEST", matches = "true")
class ReleaseReportLiveTest {
  @Test
  void realBuilderDiffAndPreviewTypeRemovalAreDistinct() throws IOException {
    String builder = "java.base/java/lang/StringBuilder.html";
    DocModel old = JavadocParser.parse(Jsoup.connect(Versions.base("17") + builder)
        .timeout(20000).get(), "java.lang.StringBuilder", Versions.base("17") + builder, "17");
    DocModel current = JavadocParser.parse(Jsoup.connect(Versions.base("21") + builder)
        .timeout(20000).get(), "java.lang.StringBuilder", Versions.base("21") + builder, "21");
    String diff = ApiDiff.compare("java.lang.StringBuilder", "17", old, "21", current);
    assertTrue(diff.contains("+ repeat(int codePoint, int count)"));
    assertTrue(diff.contains("+ repeat(CharSequence cs, int count)"));
    assertFalse(diff.contains("Constructors\n"));

    String template = "java.base/java/lang/StringTemplate.html";
    DocModel preview = JavadocParser.parse(Jsoup.connect(Versions.base("21") + template)
        .timeout(20000).get(), "java.lang.StringTemplate", Versions.base("21") + template, "21");
    assertTrue(preview.preview);
    assertTrue(ApiDiff.compare("java.lang.StringTemplate", "21", preview, "25", null)
        .contains("Removed type (Preview)"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void officialReleaseSummariesRemainReadable(String release) throws IOException {
    String base = Versions.base(release);
    ReleaseReport added = ReleaseReport.parse(
        Jsoup.connect(base + "new-list.html").timeout(20000).get(), release, false);
    ReleaseReport removal = ReleaseReport.parse(
        Jsoup.connect(base + "deprecated-list.html").timeout(20000).get(), release, true);
    assertFalse(added.entries.isEmpty());
    assertTrue(added.entries.stream().allMatch(e -> release.equals(e.since)));
    assertFalse(removal.entries.isEmpty());
    if (release.equals("21")) {
      assertTrue(added.entries.stream().anyMatch(e -> e.label.contains("StringBuilder.repeat")));
      assertTrue(added.entries.stream().anyMatch(e -> e.label.contains("StringTemplate")
          && e.preview));
    }
  }
}

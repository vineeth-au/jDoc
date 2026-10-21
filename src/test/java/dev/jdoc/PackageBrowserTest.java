package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PackageBrowserTest {
  private static PackageBrowser picker() {
    return new PackageBrowser("17", Map.of(
        "java.lang.String", "https://example.test/java.base/java/lang/String.html",
        "java.util.List", "https://example.test/java.base/java/util/List.html",
        "java.awt.List", "https://example.test/java.desktop/java/awt/List.html",
        "java.util.Map.Entry", "https://example.test/java.base/java/util/Map.Entry.html"));
  }

  @Test
  void homeShowsFlatQualifiedPackagesAndRetainsSearchWhenReturning() {
    PackageBrowser browser = picker();
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("java.util", PackageBrowser.packageOf("java.util.Map.Entry",
        "https://example.test/java.base/java/util/Map.Entry.html"));
    assertEquals(3, browser.packages.matches.size());
    browser.packages.filter("util");
    assertEquals("java.util", browser.packages.selected());
    assertNull(browser.handleKey(1005, 24));
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    for (char c : "Entry".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.util.Map.Entry", browser.selection().selected());
    assertEquals("java.util.Map.Entry", browser.handleKey(1005, 24));
    browser.classes();
    assertEquals("Entry", browser.selection().query);
    browser.handleKey(1004, 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("util", browser.packages.query);
    browser.handleKey(1005, 24);
    assertEquals("Entry", browser.selection().query);
  }

  @Test
  void escapeReturnsHomeAndEmptySearchCannotOpenAnything() {
    PackageBrowser browser = picker();
    browser.handleKey(1005, 24);
    browser.selection().filter("not-here");
    assertNull(browser.handleKey(1005, 24));
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    browser.handleKey(27, 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    browser.handleKey('q', 24);
    assertTrue(browser.quit);
  }

  @Test
  void directClassLookupCanReturnToItsPackageAndDuplicateNamesStayDistinct() {
    PackageBrowser browser = picker();
    browser.focusClass("java.awt.List", "https://example.test/java.desktop/java/awt/List.html");
    assertEquals("java.awt", browser.packageName);
    assertEquals("java.awt.List", browser.selection().selected());
    browser.handleKey(1004, 24);
    assertEquals("java.awt", browser.packages.selected());
    browser.packages.filter("java.util");
    browser.handleKey(1005, 24);
    browser.selection().filter("List");
    assertEquals("java.util.List", browser.selection().selected());
  }

  @Test
  void typingStringAtHomeFindsTheClassAndBackPreservesTheSearch() {
    PackageBrowser browser = picker();
    for (char c : "String".toCharArray()) browser.handleKey(c, 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("java.lang.String", browser.selection().selected());
    assertEquals("java.lang.String", browser.handleKey(1005, 24));
    browser.backFromMembers();
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("String", browser.packages.query);
    assertEquals("java.lang.String", browser.packages.selected());
    for (int i = 0; i < 6; i++) browser.handleKey(127, 24);
    assertEquals("", browser.packages.query);
    assertEquals(3, browser.packages.matches.size());
  }

  @Test
  void duplicateSimpleNamesAreShownWithDistinctPackages() {
    PackageBrowser browser = picker();
    for (char c : "List".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.awt.List", browser.packages.matches.get(0));
    assertEquals("java.util.List", browser.packages.matches.get(1));
    browser.handleKey(1001, 24);
    assertEquals("java.util.List", browser.handleKey(13, 24));
  }
}

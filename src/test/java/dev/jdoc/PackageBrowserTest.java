package dev.jdoc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageBrowserTest {
  @TempDir Path cache;

  private static final Map<String, String> INDEX = Map.of(
      "java.lang.String", "https://example.test/java.base/java/lang/String.html",
      "java.util.List", "https://example.test/java.base/java/util/List.html",
      "java.util.concurrent.TimeoutException", "https://example.test/java.base/java/util/concurrent/TimeoutException.html",
      "java.awt.List", "https://example.test/java.desktop/java/awt/List.html",
      "java.io.IOException", "https://example.test/java.base/java/io/IOException.html");

  private static PackageBrowser picker() { return new PackageBrowser("17", INDEX); }

  @Test
  void homeStartsWithTwoSegmentGroupsAndCanCollapseOneSegmentFurther() {
    PackageBrowser browser = picker();
    assertEquals(List.of("java.awt", "java.io", "java.lang", "java.util"),
        browser.packages.matches);
    browser.handleKey(27, 24);
    browser.handleKey(1001, 24);
    browser.handleKey(1001, 24);
    browser.handleKey(1001, 24);
    assertEquals("java.util", browser.packages.selected());
    browser.handleKey('c', 24);
    assertEquals(List.of("java"), browser.packages.matches);
    browser.handleKey('d', 24);
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    assertEquals(List.of("java.awt", "java.io", "java.lang", "java.util"),
        browser.selection().matches);
  }

  @Test
  void packageViewContainsImmediateChildrenAndDirectClasses() {
    PackageBrowser browser = picker();
    browser.handleKey(27, 24);
    for (int i = 0; i < 3; i++) browser.handleKey(1001, 24);
    browser.handleKey('d', 24);
    assertEquals("java.util", browser.packageName);
    assertEquals(List.of("java.util.concurrent", "java.util.List"), browser.selection().matches);
    assertTrue(browser.selection().searching);
    browser.handleKey(1005, 24);
    assertEquals("java.util.concurrent", browser.packageName);
    assertEquals("java.util.concurrent.TimeoutException", browser.selection().selected());
    browser.handleKey(1004, 24);
    assertEquals("java.util", browser.packageName);
    assertEquals("java.util.concurrent", browser.selection().selected());
    browser.handleKey(1004, 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
  }

  @Test
  void escapeClearsInitialHomeSearchAndOnlySlashReactivatesIt() {
    PackageBrowser browser = picker();
    for (char c : "String".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.lang.String", browser.selection().selected());
    browser.handleKey(27, 24);
    assertEquals("", browser.packages.query);
    assertEquals(List.of("java.awt", "java.io", "java.lang", "java.util"),
        browser.packages.matches);
    browser.handleKey('H', 24);
    assertEquals("", browser.packages.query);
    browser.handleKey('x', 24);
    assertEquals("", browser.packages.query);
    browser.handleKey('/', 24);
    browser.handleKey('x', 24);
    assertEquals("x", browser.packages.query);
  }

  @Test
  void typingRestartsSearchInsideAPackageAfterEscape() {
    PackageBrowser browser = picker();
    browser.handleKey(27, 24);
    browser.handleKey(1005, 24); // java.awt
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    browser.handleKey('L', 24);
    assertEquals("L", browser.selection().query);
    browser.handleKey(27, 24);
    assertEquals("java.awt.List", browser.selection().selected());
    browser.handleKey('L', 24);
    assertEquals("L", browser.selection().query);
    assertTrue(browser.selection().searching);
    browser.handleKey(27, 24);
    browser.handleKey('/', 24);
    browser.handleKey('L', 24);
    assertEquals("L", browser.selection().query);
  }

  @Test
  void exceptionSearchRanksExactNameBeforeCompoundNames() {
    PackageBrowser browser = new PackageBrowser("17", Map.of(
        "java.util.concurrent.TimeoutException", INDEX.get("java.util.concurrent.TimeoutException"),
        "java.lang.Exception", "https://example.test/java.base/java/lang/Exception.html",
        "java.io.IOException", INDEX.get("java.io.IOException"),
        "java.io.EOFException", "https://example.test/java.base/java/io/EOFException.html"));
    for (char c : "Exception".toCharArray()) browser.handleKey(c, 24);
    assertEquals(List.of("java.lang.Exception", "java.io.EOFException", "java.io.IOException",
        "java.util.concurrent.TimeoutException"), browser.packages.matches);
  }

  @Test
  void listSearchRanksExactPrefixAndCompoundNamesWithoutListenerSubstring() {
    Map<String, String> index = Map.of(
        "java.awt.List", INDEX.get("java.awt.List"),
        "java.util.List", INDEX.get("java.util.List"),
        "java.util.ListIterator", "https://example.test/java.base/java/util/ListIterator.html",
        "java.util.ArrayList", "https://example.test/java.base/java/util/ArrayList.html",
        "java.util.LinkedList", "https://example.test/java.base/java/util/LinkedList.html",
        "java.awt.event.TextListener", "https://example.test/java.desktop/java/awt/event/TextListener.html");
    PackageBrowser browser = new PackageBrowser("17", index);
    for (char c : "List".toCharArray()) browser.handleKey(c, 24);
    assertEquals(List.of("java.awt.List", "java.util.List", "java.util.ListIterator",
        "java.util.ArrayList", "java.util.LinkedList"), browser.packages.matches);

    browser.handleKey(27, 24);
    browser.handleKey('/', 24);
    for (char c : "java.util.List".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.util.List", browser.packages.selected());

    browser.handleKey(27, 24);
    browser.handleKey(1004, 24); // Group java.awt and java.util under java.
    browser.handleKey(13, 24);
    for (char c : "List".toCharArray()) browser.handleKey(c, 24);
    assertEquals(List.of("java.awt.List", "java.util.List", "java.util.ListIterator",
        "java.util.ArrayList", "java.util.LinkedList"), browser.selection().matches);
  }

  @Test
  void homeScopeToggleFiltersBothGroupedAndSearchedViewsWithoutHidingOtherPackages() {
    Map<String, String> index = Map.ofEntries(
        Map.entry("java.awt.List", INDEX.get("java.awt.List")),
        Map.entry("java.util.List", INDEX.get("java.util.List")),
        Map.entry("java.util.ArrayList", "https://example.test/java.base/java/util/ArrayList.html"),
        Map.entry("java.util.ListIterator", "https://example.test/java.base/java/util/ListIterator.html"),
        Map.entry("java.util.ListResourceBundle", "https://example.test/java.base/java/util/ListResourceBundle.html"),
        Map.entry("java.util.concurrent.ConcurrentSkipListMap", "https://example.test/java.base/java/util/concurrent/ConcurrentSkipListMap.html"),
        Map.entry("java.net.http.WebSocket.Listener", "https://example.test/java.net.http/java/net/http/WebSocket.Listener.html"),
        Map.entry("java.awt.event.TextListener", "https://example.test/java.desktop/java/awt/event/TextListener.html"),
        Map.entry("javax.swing.ListModel", "https://example.test/java.desktop/javax/swing/ListModel.html"),
        Map.entry("javax.swing.JList", "https://example.test/java.desktop/javax/swing/JList.html"),
        Map.entry("com.sun.jdi.connect.ListeningConnector", "https://example.test/jdk.jdi/com/sun/jdi/connect/ListeningConnector.html"));
    PackageBrowser browser = new PackageBrowser("17", index);
    assertEquals(List.of("java.awt", "java.net", "java.util"), browser.packages.matches);

    for (char c : "Lis".toCharArray()) browser.handleKey(c, 24);
    assertTrue(browser.packages.matches.contains("java.util.List"));
    browser.handleKey('t', 24);
    assertEquals("List", browser.packages.query);
    assertEquals(List.of("java.awt.List", "java.util.List", "java.util.ListIterator",
        "java.util.ListResourceBundle", "java.util.ArrayList",
        "java.util.concurrent.ConcurrentSkipListMap"), browser.packages.matches);

    browser.handleKey(9, 24);
    assertEquals("List", browser.packages.query);
    assertEquals("java.awt.List", browser.packages.selected());
    assertTrue(browser.packages.matches.contains("javax.swing.ListModel"));
    assertTrue(browser.packages.matches.contains("javax.swing.JList"));
    assertFalse(browser.packages.matches.contains("com.sun.jdi.connect.ListeningConnector"));
    assertFalse(browser.packages.matches.contains("java.net.http.WebSocket.Listener"));

    browser.handleKey(27, 24);
    assertTrue(browser.packages.matches.contains("javax.swing"));
    assertTrue(browser.packages.matches.contains("com.sun"));
    browser.handleKey(9, 24);
    assertEquals(List.of("java.awt", "java.net", "java.util"), browser.packages.matches);
    assertEquals(List.of("java.awt", "java.net", "java.util"),
        new PackageBrowser("17", index).packages.matches);
  }

  @Test
  void lowercaseBookmarkAndPackagePreferencesCanBeReopened() throws IOException {
    NavigationStore store = new NavigationStore(cache);
    store.toggleBookmark("17", "java.util.List");
    PackageBrowser browser = new PackageBrowser("17", INDEX, store);
    browser.handleKey(27, 24);
    browser.handleKey('b', 24);
    assertEquals(PackageBrowser.Level.BOOKMARKS, browser.level);
    browser.handleKey('c', 24);
    browser.handleKey('p', 24);
    assertEquals(PackageBrowser.Level.VISIBILITY, browser.level);
    browser.handleKey('p', 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    browser.handleKey('p', 24);
    assertEquals(PackageBrowser.Level.VISIBILITY, browser.level);
  }

  @Test
  void hidingAParentAlsoHidesItsSubpackagesWhileDirectClassFocusStillWorks() throws IOException {
    NavigationStore store = new NavigationStore(cache);
    PackageBrowser browser = new PackageBrowser("17", INDEX, store);
    browser.handleKey(27, 24);
    browser.handleKey('p', 24);
    browser.handleKey('/', 24);
    for (char c : "java.util".toCharArray()) browser.handleKey(c, 24);
    browser.handleKey(27, 24);
    assertEquals("java.util", browser.selection().selected());
    browser.handleKey(' ', 24);
    assertTrue(store.isHidden("java.util.concurrent"));
    browser.handleKey('h', 24);
    assertFalse(browser.packages.matches.contains("java.util"));
    browser.focusClass("java.util.List", INDEX.get("java.util.List"));
    assertEquals("java.util.List", browser.selection().selected());
    assertTrue(new NavigationStore(cache).isHidden("java.util.List"));
  }

  @Test
  void arrayListSearchFollowsClassPackageAndHomeBreadcrumb() {
    Map<String, String> index = Map.of(
        "java.util.ArrayList", "https://example.test/java.base/java/util/ArrayList.html",
        "java.awt.Button", "https://example.test/java.desktop/java/awt/Button.html");
    PackageBrowser browser = new PackageBrowser("17", index);
    for (char c : "ArrayList".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.util.ArrayList", browser.selection().selected());
    browser.handleKey(27, 24);
    assertEquals("", browser.packages.query);
    assertEquals(List.of("java.awt", "java.util"), browser.packages.matches);

    browser.handleKey('/', 24);
    for (char c : "ArrayList".toCharArray()) browser.handleKey(c, 24);
    browser.handleKey(1004, 24);
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    assertEquals("java.util", browser.packageName);
    assertEquals("java.util.ArrayList", browser.selection().selected());
    browser.handleKey(1004, 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("java.util", browser.packages.selected());

    browser.handleKey('/', 24);
    for (char c : "ArrayList".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.util.ArrayList", browser.handleKey(1005, 24));
    browser.backFromMembers();
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    assertEquals("java.util.ArrayList", browser.selection().selected());
    browser.handleKey(1004, 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("", browser.packages.query);
    assertEquals("java.util", browser.packages.selected());
    assertEquals(List.of("java.awt", "java.util"), browser.packages.matches);
  }

  @Test
  void collapsingFromAClassListDoesNotRestoreEarlierSearches() {
    Map<String, String> index = Map.of(
        "java.util.ArrayList", "https://example.test/java.base/java/util/ArrayList.html",
        "java.util.concurrent.TimeoutException", "https://example.test/java.base/java/util/concurrent/TimeoutException.html");
    PackageBrowser browser = new PackageBrowser("17", index);
    browser.handleKey(27, 24);
    browser.handleKey('/', 24);
    for (char c : "java.util".toCharArray()) browser.handleKey(c, 24);
    browser.handleKey(13, 24); // Open the exact package match.
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    browser.handleKey('/', 24);
    for (char c : "ArrayList".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.util.ArrayList", browser.handleKey(13, 24));
    browser.backFromMembers();
    assertEquals("", browser.selection().query);
    assertEquals("java.util.ArrayList", browser.selection().selected());
    browser.handleKey(27, 24);
    browser.handleKey('c', 24);
    assertEquals(PackageBrowser.Level.PACKAGES, browser.level);
    assertEquals("", browser.packages.query);
    assertEquals(List.of("java.util"), browser.packages.matches);
  }

  @Test
  void searchingJavaGroupFindsListInDescendantPackages() {
    PackageBrowser browser = picker();
    browser.handleKey(27, 24);
    browser.handleKey(1004, 24); // Collapse java.awt to the shared java group.
    assertEquals(List.of("java"), browser.packages.matches);
    browser.handleKey(1005, 24);
    assertEquals("java", browser.packageName);
    browser.handleKey(27, 24); // The user left search before typing in this package.
    for (char c : "List".toCharArray()) browser.handleKey(c, 24);
    assertEquals(List.of("java.awt.List", "java.util.List"), browser.selection().matches);
    assertEquals("java.awt.List", browser.selection().selected());
    browser.handleKey(1001, 24);
    assertEquals("java.util.List", browser.handleKey(1005, 24));
    assertTrue(browser.takeOpenedInSearch());
  }

  @Test
  void activeStartupSearchContinuesAfterOpeningPackageUntilEscape() {
    PackageBrowser browser = picker();
    for (char c : "java.util".toCharArray()) browser.handleKey(c, 24);
    assertEquals("java.util", browser.selection().selected());
    browser.handleKey(13, 24);
    assertEquals(PackageBrowser.Level.CLASSES, browser.level);
    assertTrue(browser.selection().searching);
    for (char c : "List".toCharArray()) browser.handleKey(c, 24);
    assertEquals("List", browser.selection().query);
    assertEquals("java.util.List", browser.selection().selected());
    assertEquals("java.util.List", browser.handleKey(13, 24));
    assertTrue(browser.takeOpenedInSearch());
    browser.backFromMembers();
    assertEquals("", browser.selection().query);
    assertTrue(browser.selection().searching);
    browser.handleKey('L', 24);
    assertEquals("L", browser.selection().query);
    browser.handleKey(27, 24);
    browser.handleKey('L', 24);
    assertEquals("L", browser.selection().query);
  }
}

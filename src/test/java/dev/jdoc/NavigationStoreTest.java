package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NavigationStoreTest {
  @TempDir Path cache;

  @Test
  void bookmarksAndRecentClassesPersistByRelease() throws IOException {
    NavigationStore store = new NavigationStore(cache);
    assertTrue(store.toggleBookmark("17", "java.util.List"));
    assertFalse(store.toggleBookmark("17", "java.util.List"));
    assertTrue(store.toggleBookmark("21", "java.util.List"));
    for (int i = 0; i < 22; i++) store.visited("17", "example.Type" + i);
    store.visited("17", "example.Type20");
    NavigationStore restored = new NavigationStore(cache);
    assertFalse(restored.isBookmarked("17", "java.util.List"));
    assertTrue(restored.isBookmarked("21", "java.util.List"));
    assertEquals(20, restored.recentNames("17").size());
    assertEquals("example.Type20", restored.recentNames("17").get(0));
    assertTrue(restored.recentNames("21").isEmpty());
  }

  @Test
  void hidingAParentPackageIncludesSubpackagesButNotSimilarPrefixes() throws IOException {
    NavigationStore store = new NavigationStore(cache);
    assertTrue(store.togglePackage("java.util").startsWith("Hidden"));
    NavigationStore restored = new NavigationStore(cache);
    assertTrue(restored.isHidden("java.util"));
    assertTrue(restored.isHidden("java.util.concurrent"));
    assertFalse(restored.isHidden("java.utility"));
    assertTrue(restored.togglePackage("java.util.concurrent").contains("hidden by java.util"));
    assertTrue(restored.togglePackage("java.util").startsWith("Showing"));
    assertFalse(new NavigationStore(cache).isHidden("java.util.concurrent"));
  }
}

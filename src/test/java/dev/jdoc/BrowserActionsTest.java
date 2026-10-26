package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserActionsTest {
  @TempDir Path cache;

  @Test
  void browserClipboardAndBookmarkActionsAreContextual() throws IOException {
    class Fake implements BrowserActions.Runner {
      String url;
      String copied;
      public void open(String url) { this.url = url; }
      public void copy(String text) { copied = text; }
    }
    Fake fake = new Fake();
    BrowserActions actions = new BrowserActions(new NavigationStore(cache), fake);
    DocModel doc = new DocModel();
    doc.name = "java.util.Map";
    doc.release = "21";
    doc.sourceUrl = "https://example.test/Map.html";
    DocModel.Method method = new DocModel.Method();
    method.signature = "void put(String key)";
    method.anchor = "put(java.lang.String)";
    assertEquals("Opened source in browser", actions.open(doc, method));
    assertEquals("https://example.test/Map.html#put%28java.lang.String%29", fake.url);
    actions.copySignature(doc, method);
    assertEquals("void put(String key)", fake.copied);
    actions.copyImport(doc, method);
    assertEquals("import java.util.Map;", fake.copied);
    method.kind = "nested type";
    method.name = "Map.Entry";
    actions.copyImport(doc, method);
    assertEquals("import java.util.Map.Entry;", fake.copied);
    assertTrue(actions.toggleBookmark(doc).contains("Bookmarked"));
    assertTrue(actions.bookmarked(doc));
  }
}

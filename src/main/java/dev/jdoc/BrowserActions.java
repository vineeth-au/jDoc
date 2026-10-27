package dev.jdoc;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class BrowserActions {
  interface Runner {
    void open(String url) throws IOException;
    void copy(String value) throws IOException;
  }

  private final NavigationStore store;
  private final Runner runner;

  BrowserActions(NavigationStore store) {
    this(store, new Runner() {
      public void open(String url) throws IOException {
        execute(new ProcessBuilder("open", url));
      }

      public void copy(String value) throws IOException {
        Process process = new ProcessBuilder("pbcopy").start();
        try (java.io.OutputStream output = process.getOutputStream()) {
          output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        finish(process);
      }
    });
  }

  BrowserActions(NavigationStore store, Runner runner) {
    this.store = store;
    this.runner = runner;
  }

  String open(DocModel doc, DocModel.Method method) throws IOException {
    String base = method == null || method.sourceUrl == null ? doc.sourceUrl : method.sourceUrl;
    if (base == null || base.isBlank()) throw new IOException("No source URL for this page");
    String anchor = method == null || method.anchor == null ? "" : "#"
        + URLEncoder.encode(method.anchor, StandardCharsets.UTF_8).replace("+", "%20");
    runner.open(base + anchor);
    return "Opened source in browser";
  }

  String copySignature(DocModel doc, DocModel.Method method) throws IOException {
    String text = method == null ? doc.name : method.signature;
    runner.copy(text);
    return "Copied signature";
  }

  String copyImport(DocModel doc, DocModel.Method method) throws IOException {
    String name = doc.name;
    if (method != null && "nested type".equals(method.kind)) {
      name = doc.name.substring(0, doc.name.lastIndexOf('.') + 1) + method.name;
    }
    runner.copy("import " + name + ";");
    return "Copied import";
  }

  String toggleBookmark(DocModel doc) throws IOException {
    return store.toggleBookmark(doc.release, doc.name)
        ? "Bookmarked " + doc.name : "Removed bookmark for " + doc.name;
  }

  boolean bookmarked(DocModel doc) { return store.isBookmarked(doc.release, doc.name); }

  private static void execute(ProcessBuilder builder) throws IOException {
    finish(builder.start());
  }

  private static void finish(Process process) throws IOException {
    try {
      if (process.waitFor() != 0) throw new IOException("macOS action failed");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("macOS action interrupted", ex);
    }
  }
}

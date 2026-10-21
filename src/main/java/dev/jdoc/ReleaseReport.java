package dev.jdoc;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** The release-filtered New API or terminally deprecated Javadoc summary page. */
final class ReleaseReport {
  String release;
  String kind;
  List<Entry> entries = new ArrayList<>();

  static ReleaseReport parse(Document page, String release, boolean removal) throws IOException {
    ReleaseReport report = new ReleaseReport();
    report.release = release;
    report.kind = removal ? "removal" : "new";
    String selector = removal ? "div#for-removal .col-summary-item-name"
        : "main div[id]:has(> .table-tabs) .col-summary-item-name";
    Map<String, Entry> unique = new LinkedHashMap<>();
    for (Element cell : page.select(selector)) {
      Element link = cell.selectFirst("> a[href]");
      Element added = cell.nextElementSibling();
      if (link == null || added == null) continue;
      Element group = cell.closest("div[id]:has(> .table-tabs)");
      if (group == null) continue;
      String since = added.hasClass("col-second") ? added.text().trim() : null;
      if (!removal && since == null) {
        String tab = null;
        for (Element button : group.select(".table-tabs button[id]")) {
          if (button.text().trim().equals("Added in " + release)) tab = button.id();
        }
        if (tab == null || !cell.hasClass(tab)) continue;
        since = release;
      }
      if (!removal && !release.equals(since)) continue;
      Entry entry = new Entry();
      entry.label = link.text().trim();
      entry.url = link.attr("abs:href");
      entry.packageName = packageOf(entry.url, release);
      entry.kind = removal ? "for removal" : group.id();
      entry.since = since;
      entry.preview = cell.select("sup").stream().anyMatch(s -> s.text().contains("PREVIEW"));
      if (!entry.label.isEmpty() && entry.packageName != null) {
        unique.putIfAbsent(entry.kind + "|" + entry.url, entry);
      }
    }
    report.entries.addAll(unique.values());
    report.entries.sort(Comparator.comparing((Entry e) -> e.packageName)
        .thenComparing(e -> e.kind).thenComparing(e -> e.label));
    if (report.entries.isEmpty()) {
      throw new IOException("No " + report.kind + " entries found in Java " + release + " report");
    }
    return report;
  }

  private static String packageOf(String url, String release) {
    if (!url.startsWith(Versions.base(release))) return null;
    String relative = URI.create(url).getPath();
    String root = URI.create(Versions.base(release)).getPath();
    relative = relative.substring(root.length());
    String[] parts = relative.split("/");
    if (parts.length < 2) return "(modules)";
    if (parts.length == 2 && parts[1].equals("module-summary.html")) return "(modules)";
    StringBuilder result = new StringBuilder();
    for (int i = 1; i < parts.length - 1; i++) {
      if (result.length() > 0) result.append('.');
      result.append(parts[i]);
    }
    return result.length() == 0 ? "(modules)" : result.toString();
  }

  String render() {
    StringBuilder out = new StringBuilder(kind.equals("new") ? "New API in Java " + release
        : "APIs flagged for removal as of Java " + release);
    out.append(" (" + entries.size() + ")\n");
    if (kind.equals("removal")) {
      out.append("Deprecated-in is not necessarily when forRemoval was first set.\n");
    }
    String lastPackage = null;
    String lastKind = null;
    for (Entry entry : entries) {
      if (!entry.packageName.equals(lastPackage)) {
        out.append("\n").append(entry.packageName).append("\n");
        lastPackage = entry.packageName;
        lastKind = null;
      }
      if (!entry.kind.equals(lastKind)) {
        out.append("  ").append(entry.kind).append("\n");
        lastKind = entry.kind;
      }
      String label = entry.label.startsWith(entry.packageName + ".")
          ? entry.label.substring(entry.packageName.length() + 1) : entry.label;
      out.append("    ").append(label);
      if (entry.preview) out.append(" [Preview]");
      if (kind.equals("removal") && entry.since != null) {
        out.append(" (deprecated in ").append(entry.since).append(')');
      }
      out.append('\n');
    }
    return out.toString();
  }

  static final class Entry {
    String packageName;
    String kind;
    String label;
    String url;
    String since;
    boolean preview;
  }
}

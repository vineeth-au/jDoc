package dev.jdoc;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.NonBlockingReader;

// TODO: This entire class is shit and needs to be re-written
final class MethodBrowser {

  private static final AttributedStyle CYAN = AttributedStyle.DEFAULT.foreground(
      AttributedStyle.CYAN).bold();
  private static final AttributedStyle GREEN = AttributedStyle.DEFAULT.foreground(
      AttributedStyle.GREEN).bold();
  private static final AttributedStyle YELLOW = AttributedStyle.DEFAULT.foreground(
      AttributedStyle.YELLOW).bold();
  private static final AttributedStyle MAGENTA = AttributedStyle.DEFAULT.foreground(
      AttributedStyle.MAGENTA).bold();
  private static final AttributedStyle MUTED = AttributedStyle.DEFAULT.foreground(
      AttributedStyle.WHITE).faint();
  private static final AttributedStyle SELECTED = AttributedStyle.DEFAULT.foreground(
          AttributedStyle.WHITE)
      .background(AttributedStyle.BLUE).bold();

  private final DocModel doc;
  private final Documentation docs;
  private final BrowserActions actions;
  private final boolean colors;
  private final MemberCatalog catalog;
  private DocModel.Method opened;
  private DocModel.Method selectedRef;
  private String unavailable;
  private String actionStatus;
  private int detailScroll;
  private boolean detail;
  private boolean searching;

  MethodBrowser(DocModel doc) {
    this(doc, new Documentation());
  }

  MethodBrowser(DocModel doc, Documentation docs) {
    this(doc, docs, new BrowserActions(new NavigationStore(docs.versionPath(""))));
  }

  MethodBrowser(DocModel doc, Documentation docs, BrowserActions actions) {
    this.doc = doc;
    this.docs = docs;
    this.actions = actions;
    this.colors = System.getenv("NO_COLOR") == null;
    this.catalog = new MemberCatalog(doc);
  }

  enum Navigation { STAY, BACK, HOME, BOOKMARKS, QUIT }

  String className() { return doc.name; }
  void beginSearch() { searching = true; }

  Navigation handleKey(int key, int height) {
    if (key == 27) {
      if (!detail && searching) {
        searching = false;
        catalog.filter("");
      }
      return Navigation.STAY;
    }
    if (key == 'q' && (detail || !searching)) return Navigation.QUIT;
    if (key == 'h' && (detail || !searching)) return Navigation.HOME;
    if (key == 'b' && (detail || !searching)) return Navigation.BOOKMARKS;
    if (detail) {
      if (key == 1004 || key == 'c') {
        detail = false;
        actionStatus = null;
      } else if (key == 'o' || key == 'y' || key == 'm' || key == 's') {
        try {
          actionStatus = switch (key) {
            case 'o' -> actions.open(doc, opened);
            case 'y' -> actions.copySignature(doc, opened);
            case 'm' -> actions.copyImport(doc, opened);
            default -> actions.toggleBookmark(doc);
          };
        } catch (IOException ex) {
          actionStatus = "Action failed: " + ex.getMessage();
        }
      } else {
        int page = Math.max(1, height - 5);
        if (key == 1000) detailScroll = Math.max(0, detailScroll - 1);
        if (key == 1001) detailScroll++;
        if (key == 1002) detailScroll = Math.max(0, detailScroll - page);
        if (key == 1003) detailScroll += page;
      }
      return Navigation.STAY;
    }
    MemberCatalog.View view = catalog.view();
    if (key == 1004 || key == 'c' && !searching) {
      MemberCatalog.Row selected = view.selected();
      if (selected != null && selected.groupKey != null
          && (!selected.group || selected.expanded)) {
        catalog.collapse(selected);
        return Navigation.STAY;
      }
      return Navigation.BACK;
    }
    if (key == 'i' && !searching && catalog.tab() == MemberCatalog.Tab.METHODS) {
      catalog.toggleInherited();
    } else if (key == 9 && !searching) {
      catalog.nextTab();
    } else if (key >= '1' && key <= '5' && !searching) {
      catalog.selectTab(key - '1');
    } else if (key == '/') {
      searching = true;
    } else if (key == 1000) {
      view.move(-1);
    } else if (key == 1001) {
      view.move(1);
    } else if (key == 1002) {
      view.move(-Math.max(1, height - 6));
    } else if (key == 1003) {
      view.move(Math.max(1, height - 6));
    } else if (key == 'd' && !searching || key == 1005 || key == 10 || key == 13) {
      MemberCatalog.Row selected = view.selected();
      if (selected != null && selected.group) {
        catalog.expand(selected);
      } else if (selected != null) {
        unavailable = null;
        selectedRef = selected.member;
        opened = selectedRef;
        if (opened != null) {
          try {
            opened = docs.detail(doc.release, opened);
          } catch (IOException ex) {
            unavailable = "Details unavailable (not cached or offline): " + ex.getMessage();
          }
        }
        detail = true;
        detailScroll = 0;
      }
    } else if (key == 8 || key == 127) {
      if (searching && !view.query.isEmpty()) catalog.filter(view.query.substring(0, view.query.length() - 1));
    } else if (searching && key >= 32 && key != 127 && !Character.isISOControl(key)) {
      catalog.filter(view.query + new String(Character.toChars(key)));
    }
    return Navigation.STAY;
  }

  void render(Terminal terminal) {
    PrintWriter out = terminal.writer();
    int width = Math.max(40, terminal.getColumns());
    int height = Math.max(10, terminal.getRows());
    List<String> classLines = doc.methods.isEmpty() ? DocRenderer.classLines(doc, width - 1)
        : List.of();
    int overviewRows = detail ? 0 : Math.min(3, classLines.size());
    int viewport = height - (detail ? 5 : 6 + overviewRows);
    out.print("\033[H\033[2J");
    line(out, terminal, doc.name + "  ·  Java " + doc.release + " API"
        + (doc.stale ? "  ·  older cache; reconnect to refresh" : ""), CYAN, width);
    if (detail) {
      List<String> lines = detailLines(opened, width);
      detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, lines.size() - viewport)));
      line(out, terminal, opened == null ? "Class documentation"
          : opened.kind + " " + (catalog.view().cursor + 1) + "/"
              + catalog.view().rows.size(), YELLOW, width);
      rule(out, terminal, width);
      for (int i = 0; i < viewport; i++) {
        int index = detailScroll + i;
        if (index >= lines.size()) {
          out.print("\033[K\n");
          continue;
        }
        String value = lines.get(index);
        AttributedStyle style = value.startsWith("  ") ? AttributedStyle.DEFAULT
            : value.endsWith(":") ? MAGENTA : index == 0 ? GREEN : MUTED;
        line(out, terminal, value, style, width);
      }
      rule(out, terminal, width);
      line(out, terminal, actionStatus == null
          ? "o Open  y Signature  m Import  s Bookmark  c Back  h Home  q Quit"
          : actionStatus + "  ·  c Back  h Home  q Quit", MUTED, width);
    } else {
      for (int i = 0; i < overviewRows; i++) {
        line(out, terminal, i == overviewRows - 1 && classLines.size() > overviewRows
            ? "…  (select Class description for the full text)" : classLines.get(i), MUTED, width);
      }
      StringBuilder tabs = new StringBuilder();
      String[] names = {"Methods", "Ctors", "Fields", "Enums", "Nested"};
      for (int i = 0; i < names.length; i++) {
        if (i > 0) tabs.append("  ");
        tabs.append(catalog.tab().ordinal() == i ? "*" : " ").append(i + 1).append(' ')
            .append(names[i]).append(' ').append(catalog.count(MemberCatalog.Tab.values()[i]));
      }
      line(out, terminal, tabs.toString(), CYAN, width);
      MemberCatalog.View view = catalog.view();
      line(out, terminal,
          "Search members: " + view.query
              + (searching ? "_" : "  (/ to search)"), YELLOW, width);
      rule(out, terminal, width);
      view.keepVisible(viewport);
      int signatureWidth = Math.max(16, width / 2);
      for (int i = 0; i < viewport; i++) {
        int index = view.top + i;
        if (index >= view.rows.size()) {
          out.print("\033[K\n");
          continue;
        }
        MemberCatalog.Row selected = view.rows.get(index);
        DocModel.Method method = selected.member;
        String label = selected.description() ? "Class description"
            : selected.group ? (selected.expanded ? "▾ " : "▸ ")
            + selected.name() + " (" + selected.count + " overloads)"
            : (selected.groupKey != null ? "  " : "") + "[" + method.kind + "] "
                + listSignature(method);
        String signature = shorten(label,
            signatureWidth - 3);
        String summary = selected.description() ? doc.description : method.inherited
            ? "Inherited from " + method.declaringType : method.description;
        String labels = selected.description() ? "" : DocRenderer.summaryLabels(method);
        if (!labels.isEmpty()) summary = labels + (summary == null || summary.isEmpty() ? "" : "  " + summary);
        String description = shorten(summary, Math.max(0, width - signatureWidth - 4));
        String row = String.format("%-" + signatureWidth + "s  %s", signature, description);
        lineHighlighted(out, terminal, (index == view.cursor ? "❯ " : "  ") + row,
            index == view.cursor ? SELECTED : GREEN, width, view.query);
      }
      rule(out, terminal, width);
      line(out, terminal,
          view.error != null ? view.error : "i Inherited (" + catalog.inheritedCount()
              + (catalog.inherited() ? " shown" : " collapsed") + ")  ·  "
              + (view.rows.isEmpty() ? 0 : view.cursor + 1) + "/" + view.rows.size()
              + "  Tab Kinds  d/→ Open  c/← Collapse  h Home  q Quit",
          MUTED, width);
    }
    out.flush();
  }

  private List<String> detailLines(DocModel.Method method, int width) {
    if (method == null) {
      List<String> lines = DocRenderer.classLines(doc, width - 2);
      lines.add("");
      wrap(lines, "Source: " + doc.sourceUrl, width - 2, "");
      return lines;
    }
    List<String> lines = DocRenderer.methodLines(method, width - 2, doc.sourceUrl);
    if (selectedRef != null && selectedRef.inherited) {
      lines.add(1, "Inherited from " + selectedRef.declaringType);
    }
    if (unavailable != null) {
      lines.add("");
      wrap(lines, unavailable, width - 2, "");
    }
    return lines;
  }

  static void wrap(List<String> lines, String text, int width, String indent) {
    if (text == null || text.isEmpty()) {
      return;
    }
    StringBuilder current = new StringBuilder(indent);
    for (String word : text.split("\\s+")) {
      while (word.length() > width - indent.length()) {
        if (current.length() > indent.length()) {
          lines.add(current.toString());
          current = new StringBuilder(indent);
        }
        lines.add(indent + word.substring(0, width - indent.length()));
        word = word.substring(width - indent.length());
      }
      if (current.length() > indent.length() && current.length() + word.length() + 1 > width) {
        lines.add(current.toString());
        current = new StringBuilder(indent);
      }
      if (current.length() > indent.length()) {
        current.append(' ');
      }
      current.append(word);
    }
    lines.add(current.toString());
  }

  static String listSignature(DocModel.Method method) {
    if (!"method".equals(method.kind) && !"constructor".equals(method.kind)) {
      return method.name;
    }
    int open = method.signature.indexOf('(');
    int close = method.signature.indexOf(')', open);
    return open >= 0 && close >= 0 ? method.name + method.signature.substring(open, close + 1)
        : method.signature;
  }

  private void rule(PrintWriter out, Terminal terminal, int width) {
    line(out, terminal, "─".repeat(width - 1), MUTED, width);
  }

  private void line(PrintWriter out, Terminal terminal, String text, AttributedStyle style,
      int width) {
    String clipped = shorten(text, width - 1);
    out.print(colors ? new AttributedString(clipped, style).toAnsi(terminal) : clipped);
    out.print("\033[K\n");
  }

  private void lineHighlighted(PrintWriter out, Terminal terminal, String text,
      AttributedStyle style,
      int width, String query) {
    String clipped = shorten(text, width - 1);
    int start = query.isEmpty() ? -1
        : clipped.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
    if (!colors || start < 0) {
      line(out, terminal, clipped, style, width);
      return;
    }
    AttributedStringBuilder result = new AttributedStringBuilder();
    result.styled(style, clipped.substring(0, start));
    result.styled(style.foreground(AttributedStyle.YELLOW).bold(),
        clipped.substring(start, start + query.length()));
    result.styled(style, clipped.substring(start + query.length()));
    out.print(result.toAnsi(terminal));
    out.print("\033[K\n");
  }

  static String shorten(String text, int max) {
    if (text == null || max <= 0) {
      return "";
    }
    if (text.length() <= max) {
      return text;
    }
    return max > 1 ? text.substring(0, max - 1) + "…" : "…";
  }

  static int readKey(NonBlockingReader input) throws IOException {
    int first = input.read();
    if (first != 27) {
      return first;
    }
    if (input.peek(70) != '[' && input.peek(70) != 'O') {
      return 27;
    }
    input.read();
    int code = input.read(70);
    StringBuilder sequence = new StringBuilder();
    for (int i = 0; i < 16 && code >= 0; i++) {
      sequence.append((char) code);
      if (code >= 0x40 && code <= 0x7e) {
        break;
      }
      code = input.read(70);
    }
    return switch (sequence.toString()) {
      case "A" -> 1000;
      case "B" -> 1001;
      case "5~" -> 1002;
      case "6~" -> 1003;
      case "D" -> 1004;
      case "C" -> 1005;
      default -> -2;
    };
  }

}

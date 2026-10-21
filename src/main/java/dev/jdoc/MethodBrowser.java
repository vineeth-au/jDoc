package dev.jdoc;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
  private final boolean colors;
  private State state;
  private boolean showInherited;
  private DocModel.Method opened;
  private String unavailable;
  private int detailScroll;
  private boolean detail;
  private boolean searching;

  MethodBrowser(DocModel doc) {
    this(doc, new Documentation());
  }

  MethodBrowser(DocModel doc, Documentation docs) {
    this.doc = doc;
    this.docs = docs;
    this.colors = System.getenv("NO_COLOR") == null;
    this.state = new State(doc.declaredMembers());
  }

  enum Navigation { STAY, BACK, HOME, QUIT }

  Navigation handleKey(int key, int height) {
    if (key == 27) {
      return Navigation.HOME;
    }
    if (key == 'q' && (detail || !searching)) {
      return Navigation.QUIT;
    }
    if (detail) {
      if (key == 1004 || key == 'b') {
        detail = false;
      } else {
        int page = Math.max(1, height - 5);
        if (key == 1000) detailScroll = Math.max(0, detailScroll - 1);
        if (key == 1001) detailScroll++;
        if (key == 1002) detailScroll = Math.max(0, detailScroll - page);
        if (key == 1003) detailScroll += page;
      }
      return Navigation.STAY;
    }
    if (key == 1004 || key == 'b' && !searching) {
      return Navigation.BACK;
    }
    if (key == 'i' && !searching && state.query.isEmpty()) {
      showInherited = !showInherited;
      List<DocModel.Method> visible = doc.declaredMembers();
      if (showInherited) visible.addAll(doc.inherited);
      state = new State(visible);
    } else if (key == '/') {
      searching = true;
    } else if (key == 1000) {
      state.move(-1);
    } else if (key == 1001) {
      state.move(1);
    } else if (key == 1002) {
      state.move(-Math.max(1, height - 5));
    } else if (key == 1003) {
      state.move(Math.max(1, height - 5));
    } else if (key == 1005 || key == 10 || key == 13) {
      if (state.selected() != null) {
        unavailable = null;
        opened = state.selected();
        try {
          opened = docs.detail(doc.release, opened);
        } catch (IOException ex) {
          unavailable = "Details unavailable (not cached or offline): " + ex.getMessage();
        }
        detail = true;
        detailScroll = 0;
        searching = false;
      }
    } else if (key == 8 || key == 127) {
      if (!state.query.isEmpty()) state.filter(state.query.substring(0, state.query.length() - 1));
    } else if (key >= 32 && key != 127 && !Character.isISOControl(key)) {
      state.filter(state.query + new String(Character.toChars(key)));
      searching = true;
    }
    return Navigation.STAY;
  }

  void render(Terminal terminal) {
    PrintWriter out = terminal.writer();
    int width = Math.max(40, terminal.getColumns());
    int height = Math.max(10, terminal.getRows());
    boolean overview =
        doc.methods.isEmpty() && doc.description != null && !doc.description.isEmpty();
    int viewport = height - (overview && !detail ? 6 : 5);
    out.print("\033[H\033[2J");
    line(out, terminal, doc.name + "  ·  Java " + doc.release + " API"
        + (doc.stale ? "  ·  older cache; reconnect to refresh" : ""), CYAN, width);
    if (detail) {
      List<String> lines = detailLines(opened, width);
      detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, lines.size() - viewport)));
      line(out, terminal, (opened == null ? "Member" : opened.kind) + " " + (state.cursor + 1) + "/"
          + state.matches.size(), YELLOW, width);
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
      line(out, terminal, "↑↓ Scroll  PgUp/PgDn Page  ← Back  Esc Home  q Quit", MUTED, width);
    } else {
      if (overview) {
        line(out, terminal, doc.description, MUTED, width);
      }
      line(out, terminal,
          "Search members: " + state.query
              + (searching ? "_" : "  (type / to search)"), YELLOW, width);
      rule(out, terminal, width);
      state.keepVisible(viewport);
      int signatureWidth = Math.max(16, width / 2);
      for (int i = 0; i < viewport; i++) {
        int index = state.top + i;
        if (index >= state.matches.size()) {
          out.print("\033[K\n");
          continue;
        }
        DocModel.Method method = state.matches.get(index);
        boolean groupStart = method.inherited && (index == 0
            || !state.matches.get(index - 1).inherited
            || !method.declaringType.equals(state.matches.get(index - 1).declaringType));
        String label = groupStart ? "[Inherited from "
            + method.declaringType.substring(method.declaringType.lastIndexOf('.') + 1) + "] "
            : "[" + method.kind + "] ";
        String signature = shorten(label + listSignature(method),
            signatureWidth - 3);
        String description = shorten(method.inherited ? "Inherited from " + method.declaringType
            : method.description, Math.max(0, width - signatureWidth - 4));
        String row = String.format("%-" + signatureWidth + "s  %s", signature, description);
        lineHighlighted(out, terminal, (index == state.cursor ? "❯ " : "  ") + row,
            index == state.cursor ? SELECTED : GREEN, width, state.query);
      }
      rule(out, terminal, width);
      line(out, terminal,
          "i Inherited (" + doc.inherited.size() + (showInherited ? " shown" : " collapsed")
              + ")  ·  " + (state.matches.isEmpty() ? 0 : state.cursor + 1)
              + "/" + state.matches.size() + "  ← Classes  → Read  Esc Home  q Quit",
          MUTED, width);
    }
    out.flush();
  }

  private List<String> detailLines(DocModel.Method method, int width) {
    List<String> lines = new ArrayList<>();
    if (method == null) {
      return lines;
    }
    wrap(lines, method.signature, width - 2, "");
    if (state.selected() != null && state.selected().inherited) {
      wrap(lines, "Inherited from " + state.selected().declaringType, width - 2, "");
    }
    lines.add("");
    wrap(lines, method.description, width - 2, "");
    lines.add("");
    for (String entry : (method.details == null ? "" : method.details).split("\n")) {
      if (entry.startsWith("  ")) {
        wrap(lines, entry.trim(), width - 4, "  ");
      } else {
        lines.add("");
        lines.add(entry);
      }
    }
    lines.add("");
    if (unavailable != null) {
      wrap(lines, unavailable, width - 2, "");
    }
    String source = method.sourceUrl == null ? doc.sourceUrl : method.sourceUrl;
    wrap(lines, "Source: " + source + (method.anchor == null ? "" : "#" + method.anchor),
        width - 2, "");
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
      default -> -2; // An arrow/unknown sequence is not a standalone Escape.
    };
  }

  static final class State {

    final List<DocModel.Method> all;
    List<DocModel.Method> matches;
    String query = "";
    int cursor;
    int top;

    State(List<DocModel.Method> methods) {
      all = methods;
      matches = new ArrayList<>(methods);
    }

    void filter(String value) {
      query = value;
      String needle = value.toLowerCase(Locale.ROOT);
      matches = new ArrayList<>();
      for (DocModel.Method method : all) {
        if (method.signature.toLowerCase(Locale.ROOT).contains(needle)) {
          matches.add(method);
        }
      }
      cursor = 0;
      top = 0;
    }

    DocModel.Method selected() {
      return matches.isEmpty() ? null : matches.get(cursor);
    }

    void move(int delta) {
      if (!matches.isEmpty()) {
        cursor = Math.max(0, Math.min(matches.size() - 1, cursor + delta));
      }
    }

    void keepVisible(int page) {
      if (cursor < top) {
        top = cursor;
      }
      if (cursor >= top + page) {
        top = cursor - page + 1;
      }
    }
  }
}

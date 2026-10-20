package dev.jdoc;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
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
  private final boolean colors;
  private final State state;
  private final boolean constructors;
  private int detailScroll;
  private boolean detail;
  private boolean searching;

  MethodBrowser(DocModel doc) {
    this.doc = doc;
    this.colors = System.getenv("NO_COLOR") == null;
    this.constructors = doc.methods.isEmpty() && !doc.constructors.isEmpty();
    this.state = new State(constructors ? doc.constructors : doc.methods);
  }

  void run() throws IOException {
    try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
      Attributes original = terminal.enterRawMode();
      PrintWriter writer = terminal.writer();
      writer.print("\033[?1049h\033[?25l"); // Alternate screen, hidden cursor.
      writer.flush();
      try {
        for (; ; ) {
          render(terminal);
          int key = readKey(terminal.reader());
          if (key == -1 || key == 3) {
            break;
          }
          if (!detail && !searching && key == 'q') {
            break;
          }
          if (detail) {
            if (key == 'q') {
              break;
            }
            if (key == 'b' || key == 27) {
              detail = false;
              continue;
            }
            int page = Math.max(1, terminal.getHeight() - 5);
            if (key == 1000) {
              detailScroll = Math.max(0, detailScroll - 1);
            }
            if (key == 1001) {
              detailScroll++;
            }
            if (key == 1002) {
              detailScroll = Math.max(0, detailScroll - page);
            }
            if (key == 1003) {
              detailScroll += page;
            }
            continue;
          }
          if (key == 27) {
            searching = false;
            continue;
          }
          if (key == '/') {
            searching = true;
            continue;
          }
          if (key == 1000) {
            state.move(-1);
            continue;
          }
          if (key == 1001) {
            state.move(1);
            continue;
          }
          if (key == 1002) {
            state.move(-Math.max(1, terminal.getHeight() - 5));
            continue;
          }
          if (key == 1003) {
            state.move(Math.max(1, terminal.getHeight() - 5));
            continue;
          }
          if (key == 10 || key == 13) {
            if (state.selected() != null) {
              detail = true;
              detailScroll = 0;
              searching = false;
            }
            continue;
          }
          if (key == 8 || key == 127) {
            if (!state.query.isEmpty()) {
              state.filter(state.query.substring(0, state.query.length() - 1));
            }
            continue;
          }
          if (key >= 32 && key != 127 && !Character.isISOControl(key)) {
            state.filter(state.query + new String(Character.toChars(key)));
            searching = true;
          }
        }
      } finally {
        writer.print("\033[0m\033[?25h\033[?1049l");
        writer.flush();
        terminal.setAttributes(original);
      }
    }
  }

  private void render(Terminal terminal) {
    PrintWriter out = terminal.writer();
    int width = Math.max(40, terminal.getColumns());
    int height = Math.max(10, terminal.getRows());
    boolean overview =
        doc.methods.isEmpty() && doc.description != null && !doc.description.isEmpty();
    int viewport = height - (overview && !detail ? 6 : 5);
    out.print("\033[H\033[2J");
    line(out, terminal, doc.name + "  ·  Java " + doc.release + " API", CYAN, width);
    if (detail) {
      List<String> lines = detailLines(state.selected(), width);
      detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, lines.size() - viewport)));
      line(out, terminal, (constructors ? "Constructor " : "Method ") + (state.cursor + 1) + "/"
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
      line(out, terminal, "↑↓ Scroll  PgUp/PgDn Page  b Back  q Quit", MUTED, width);
    } else {
      if (overview) {
        line(out, terminal, doc.description, MUTED, width);
      }
      line(out, terminal,
          "Search " + (constructors ? "constructors" : "methods") + ": " + state.query
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
        String signature = shorten(listSignature(method), signatureWidth - 3);
        String description = shorten(method.description, Math.max(0, width - signatureWidth - 4));
        String row = String.format("%-" + signatureWidth + "s  %s", signature, description);
        lineHighlighted(out, terminal, (index == state.cursor ? "❯ " : "  ") + row,
            index == state.cursor ? SELECTED : GREEN, width, state.query);
      }
      rule(out, terminal, width);
      line(out, terminal,
          state.matches.size() + (constructors ? " constructors" : " methods") + "  ·  " + (
              state.matches.isEmpty() ? 0 : state.cursor + 1)
              + "/" + state.matches.size() + "     ↑↓ Navigate  PgUp/PgDn Page  Enter Read  q Quit",
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
    lines.add("");
    wrap(lines, method.description, width - 2, "");
    lines.add("");
    for (String entry : method.details.split("\n")) {
      if (entry.startsWith("  ")) {
        wrap(lines, entry.trim(), width - 4, "  ");
      } else {
        lines.add("");
        lines.add(entry);
      }
    }
    lines.add("");
    wrap(lines, "Source: " + doc.sourceUrl + "#" + method.anchor, width - 2, "");
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

  private static int readKey(NonBlockingReader input) throws IOException {
    int first = input.read();
    if (first != 27) {
      return first;
    }
    if (input.peek(70) != '[' && input.peek(70) != 'O') {
      return 27;
    }
    input.read();
    int code = input.read(70);
    if (code == 'A') {
      return 1000;
    }
    if (code == 'B') {
      return 1001;
    }
    if (code == '5' || code == '6') {
      if (input.peek(70) == '~') {
        input.read();
      }
      return code == '5' ? 1002 : 1003;
    }
    return 27;
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

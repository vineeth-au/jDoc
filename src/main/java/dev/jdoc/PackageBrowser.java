package dev.jdoc;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/** Package and class levels of the documentation browser. No class pages are fetched here. */
final class PackageBrowser {
  enum Level { PACKAGES, CLASSES }

  private final String release;
  private final Map<String, String> index;
  private final List<String> allClasses;
  private final Map<String, List<String>> classes = new TreeMap<>();
  private final Map<String, Selection> classSelections = new TreeMap<>();
  final Selection packages;
  Level level = Level.PACKAGES;
  String packageName;
  String error;
  boolean quit;
  private boolean openedFromHomeSearch;

  PackageBrowser(String release, Map<String, String> index) {
    this.release = release;
    this.index = index;
    this.allClasses = new ArrayList<>(index.keySet());
    allClasses.sort(String::compareTo);
    for (Map.Entry<String, String> entry : index.entrySet()) {
      classes.computeIfAbsent(packageOf(entry.getKey(), entry.getValue()), ignored -> new ArrayList<>())
          .add(entry.getKey());
    }
    classes.values().forEach(list -> list.sort(String::compareTo));
    packages = new Selection(new ArrayList<>(classes.keySet()));
    packages.searching = true;
  }

  static String packageOf(String name, String url) {
    String filename = url.substring(url.lastIndexOf('/') + 1).replaceFirst("\\.html$", "");
    return name.substring(0, name.length() - filename.length() - 1);
  }

  void focusClass(String name, String url) {
    packageName = packageOf(name, url);
    packages.select(packageName);
    classSelections.computeIfAbsent(packageName, p -> new Selection(classes.get(p))).select(name);
    level = Level.CLASSES;
    openedFromHomeSearch = false;
  }

  void home() {
    level = Level.PACKAGES;
    packages.searching = false;
  }

  void classes() {
    level = Level.CLASSES;
    selection().searching = false;
  }

  void backFromMembers() {
    if (openedFromHomeSearch) home();
    else classes();
    openedFromHomeSearch = false;
  }

  Selection selection() {
    return level == Level.PACKAGES ? packages
        : classSelections.computeIfAbsent(packageName, p -> new Selection(classes.get(p)));
  }

  /** Returns a class name when a class result is opened; otherwise null. */
  String handleKey(int key, int height) {
    if (key == 27) {
      if (selection().searching) {
        selection().searching = false;
        error = null;
        return null;
      }
      home();
      error = null;
      return null;
    }
    Selection state = selection();
    if (key == 'q' && !state.searching) {
      quit = true;
    } else if (key == 1004) {
      if (level == Level.CLASSES) home();
    } else if (key == 1005 || key == 10 || key == 13) {
      if (state.selected() != null) {
        state.searching = false;
        if (level == Level.CLASSES) return state.selected();
        if (index.containsKey(state.selected())) {
          String name = state.selected();
          focusClass(name, index.get(name));
          openedFromHomeSearch = true;
          return name;
        }
        packageName = state.selected();
        openedFromHomeSearch = false;
        classes();
      }
    } else if (key == '/') {
      state.searching = true;
    } else if (key == 1000) {
      state.move(-1);
    } else if (key == 1001) {
      state.move(1);
    } else if (key == 1002) {
      state.move(-Math.max(1, height - 5));
    } else if (key == 1003) {
      state.move(Math.max(1, height - 5));
    } else if (key == 8 || key == 127) {
      if (!state.query.isEmpty()) filter(state.query.substring(0, state.query.length() - 1));
    } else if (key >= 32 && !Character.isISOControl(key)) {
      filter(state.query + new String(Character.toChars(key)));
      state.searching = true;
    }
    error = null;
    return null;
  }

  private void filter(String query) {
    if (level == Level.CLASSES || query.isEmpty()) {
      selection().filter(query);
      return;
    }
    String needle = query.toLowerCase(Locale.ROOT);
    List<String> results = new ArrayList<>();
    for (String pkg : classes.keySet()) {
      if (pkg.toLowerCase(Locale.ROOT).contains(needle)) results.add(pkg);
    }
    for (String name : allClasses) {
      if (name.toLowerCase(Locale.ROOT).contains(needle)) results.add(name);
    }
    results.sort(Comparator.comparingInt((String name) -> rank(name, needle))
        .thenComparing(String::compareTo));
    packages.query = query;
    packages.matches = results;
    packages.cursor = 0;
    packages.top = 0;
  }

  private int rank(String name, String needle) {
    String simple = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (name.equalsIgnoreCase(needle)) return 0;
    if (index.containsKey(name) && simple.equals(needle)) return 1;
    if (simple.startsWith(needle)) return 2;
    if (name.toLowerCase(Locale.ROOT).startsWith(needle)) return 3;
    return 4;
  }

  void render(Terminal terminal) {
    PrintWriter out = terminal.writer();
    int width = Math.max(40, terminal.getColumns());
    int height = Math.max(10, terminal.getRows());
    Selection state = selection();
    int viewport = height - 5;
    out.print("\033[H\033[2J");
    line(out, terminal, level == Level.PACKAGES ? "Packages  ·  Java " + release + " API"
        : "Classes in " + packageName + "  ·  Java " + release + " API", AttributedStyle.CYAN,
        width);
    line(out, terminal, "Search " + (level == Level.PACKAGES ? "packages or classes" : "classes")
        + ": " + state.query + (state.searching ? "_" : "  (/ to search)"),
        AttributedStyle.YELLOW, width);
    line(out, terminal, "─".repeat(width - 1), AttributedStyle.WHITE, width);
    state.keepVisible(viewport);
    for (int i = 0; i < viewport; i++) {
      int index = state.top + i;
      if (index >= state.matches.size()) {
        out.print("\033[K\n");
      } else {
        String name = state.matches.get(index);
        String row = (index == state.cursor ? "❯ " : "  ")
            + (level == Level.PACKAGES && !state.query.isEmpty()
                ? (indexContainsClass(name) ? "[class] " : "[package] ") : "") + name;
        line(out, terminal, row, index == state.cursor ? AttributedStyle.WHITE
            : AttributedStyle.GREEN, width, index == state.cursor);
      }
    }
    line(out, terminal, "─".repeat(width - 1), AttributedStyle.WHITE, width);
    line(out, terminal, error != null ? error
        : (state.matches.isEmpty() ? "No matches  ·  " : state.matches.size() + " matches  ·  ")
            + "↑↓ Navigate  ← Back  → Open  Esc Search/Home  q Quit", AttributedStyle.WHITE, width);
    out.flush();
  }

  private boolean indexContainsClass(String name) {
    return index.containsKey(name);
  }

  private static void line(PrintWriter out, Terminal terminal, String text, int color, int width) {
    line(out, terminal, text, color, width, false);
  }

  private static void line(PrintWriter out, Terminal terminal, String text, int color, int width,
      boolean selected) {
    AttributedStyle style = AttributedStyle.DEFAULT.foreground(color);
    if (selected) style = style.background(AttributedStyle.BLUE).bold();
    String clipped = MethodBrowser.shorten(text, width - 1);
    out.print(System.getenv("NO_COLOR") == null
        ? new AttributedString(clipped, style).toAnsi(terminal) : clipped);
    out.print("\033[K\n");
  }

  static final class Selection {
    final List<String> all;
    List<String> matches;
    String query = "";
    int cursor;
    int top;
    boolean searching;

    Selection(List<String> values) {
      all = values;
      matches = new ArrayList<>(values);
    }

    void filter(String value) {
      query = value;
      String needle = value.toLowerCase(Locale.ROOT);
      matches = all.stream().filter(item -> item.toLowerCase(Locale.ROOT).contains(needle)).toList();
      cursor = 0;
      top = 0;
    }

    void select(String value) {
      int index = matches.indexOf(value);
      if (index >= 0) cursor = index;
    }

    String selected() {
      return matches.isEmpty() ? null : matches.get(cursor);
    }

    void move(int delta) {
      if (!matches.isEmpty()) cursor = Math.max(0, Math.min(matches.size() - 1, cursor + delta));
    }

    void keepVisible(int viewport) {
      if (cursor < top) top = cursor;
      if (cursor >= top + viewport) top = cursor - viewport + 1;
    }
  }
}

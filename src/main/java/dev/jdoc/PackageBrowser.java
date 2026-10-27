package dev.jdoc;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/** Package and class levels of the documentation browser. No class pages are fetched here. */
final class PackageBrowser {
  enum Level { PACKAGES, CLASSES, RECENTS, BOOKMARKS, VISIBILITY }

  private final String release;
  private final Map<String, String> index;
  private final List<String> allClasses;
  private final Map<String, String> classPackages = new TreeMap<>();
  private final Set<String> nameComponents = new HashSet<>();
  private final NavigationStore history;
  private final Map<String, List<String>> classes = new TreeMap<>();
  private final Map<String, Selection> classSelections = new TreeMap<>();
  private final Set<String> collapsedRoots = new HashSet<>();
  final Selection packages;
  private Selection recents;
  private Selection bookmarks;
  private Selection visibility;
  Level level = Level.PACKAGES;
  String packageName;
  String error;
  boolean quit;
  private boolean allPackages;
  private boolean openedInSearch;
  private Level returnLevel = Level.CLASSES;
  private final Deque<Overlay> overlays = new ArrayDeque<>();

  private record Overlay(Level level, String packageName) {}

  PackageBrowser(String release, Map<String, String> index) {
    this(release, index, null);
  }

  PackageBrowser(String release, Map<String, String> index, NavigationStore history) {
    this.release = release;
    this.index = index;
    this.history = history;
    this.allClasses = new ArrayList<>(index.keySet());
    allClasses.sort(String::compareTo);
    for (Map.Entry<String, String> entry : index.entrySet()) {
      String pkg = packageOf(entry.getKey(), entry.getValue());
      classPackages.put(entry.getKey(), pkg);
      String typeName = entry.getKey().substring(pkg.length() + 1);
      for (String component : typeName.split("(?=[A-Z])|\\.")) {
        if (!component.isEmpty()) nameComponents.add(component.toLowerCase(Locale.ROOT));
      }
      classes.computeIfAbsent(pkg, ignored -> new ArrayList<>())
          .add(entry.getKey());
    }
    classes.values().forEach(list -> list.sort(String::compareTo));
    packages = new Selection(new ArrayList<>());
    packages.searching = true;
    filterHome("");
  }

  static String packageOf(String name, String url) {
    String filename = url.substring(url.lastIndexOf('/') + 1).replaceFirst("\\.html$", "");
    return name.substring(0, name.length() - filename.length() - 1);
  }

  void focusClass(String name, String url) {
    packageName = packageOf(name, url);
    packages.select(packageName);
    Selection state = classSelections.computeIfAbsent(packageName,
        p -> new Selection(contents(p)));
    if (!state.all.contains(name)) state = new Selection(new ArrayList<>(classes.get(packageName)));
    classSelections.put(packageName, state);
    state.filter("");
    state.select(name);
    level = Level.CLASSES;
    returnLevel = Level.CLASSES;
  }

  void home() {
    level = Level.PACKAGES;
    packages.searching = false;
    overlays.clear();
    refreshHome();
  }

  void resetHome() {
    collapsedRoots.clear();
    packages.query = "";
    home();
  }

  void classes() {
    level = Level.CLASSES;
    Selection state = selection();
    state.filter("");
    state.searching = true;
  }

  boolean takeOpenedInSearch() {
    boolean active = openedInSearch;
    openedInSearch = false;
    return active;
  }

  void openBookmarks() {
    if (level == Level.BOOKMARKS) return;
    saveOverlayOrigin();
    bookmarks = new Selection(history.bookmarkedNames(release).stream()
        .filter(this::visibleClass).toList());
    level = Level.BOOKMARKS;
  }

  void leaveBookmarksToMembers() {
    restoreOverlayOrigin();
    if (packageName != null) selection().searching = false;
  }

  private void saveOverlayOrigin() {
    overlays.push(new Overlay(level, packageName));
  }

  private void restoreOverlayOrigin() {
    Overlay origin = overlays.isEmpty() ? new Overlay(Level.PACKAGES, null) : overlays.pop();
    level = origin.level();
    packageName = origin.packageName();
  }

  void backFromMembers() {
    level = returnLevel == Level.PACKAGES ? Level.CLASSES : returnLevel;
    Selection state = selection();
    String selected = state.selected();
    state.filter("");
    if (selected != null) state.select(selected);
    state.searching = level == Level.CLASSES;
    returnLevel = Level.CLASSES;
  }

  Selection selection() {
    return switch (level) {
      case PACKAGES -> packages;
      case CLASSES -> classSelections.computeIfAbsent(packageName, p -> new Selection(contents(p)));
      case RECENTS -> recents;
      case BOOKMARKS -> bookmarks;
      case VISIBILITY -> visibility;
    };
  }

  String handleKey(int key, int height) {
    if (key == 27) {
      if (selection().searching) {
        clearSearch();
        error = null;
      }
      return null;
    }
    Selection state = selection();
    if (key == 9 && level == Level.PACKAGES) {
      allPackages = !allPackages;
      refreshHome();
    } else if (key == 'q' && !state.searching) {
      quit = true;
    } else if (key == 'h' && !state.searching) {
      resetHome();
    } else if (key == 'b' && !state.searching && history != null) {
      openBookmarks();
    } else if (key == 'p' && !state.searching && history != null) {
      if (level == Level.VISIBILITY) collapse();
      else {
        saveOverlayOrigin();
        visibility = new Selection(new ArrayList<>(classes.keySet()));
        level = Level.VISIBILITY;
      }
    } else if (key == 'c' && !state.searching || key == 1004) {
      collapse();
    } else if (level == Level.VISIBILITY && (key == ' ' || key == 1005
        || key == 10 || key == 13)) {
      if (state.selected() != null && history != null) {
        try {
          error = history.togglePackage(state.selected());
          classSelections.clear();
          refreshHome();
        } catch (java.io.IOException ex) {
          error = "Could not save visibility: " + ex.getMessage();
        }
      }
      return null;
    } else if (key == 'd' && !state.searching || key == 1005 || key == 10 || key == 13) {
      if (state.selected() != null) {
        boolean activeSearch = state.searching;
        if (level == Level.CLASSES) {
          if (index.containsKey(state.selected())) {
            openedInSearch = activeSearch;
            return state.selected();
          }
          packageName = state.selected();
          classes();
          return null;
        }
        if (index.containsKey(state.selected())) {
          Level origin = level;
          String name = state.selected();
          focusClass(name, index.get(name));
          returnLevel = origin;
          openedInSearch = activeSearch;
          return name;
        }
        packageName = state.selected();
        returnLevel = Level.CLASSES;
        classes();
      }
    } else if (key == 'r' && !state.searching && history != null) {
      saveOverlayOrigin();
      recents = new Selection(history.recentNames(release).stream().filter(this::visibleClass).toList());
      level = Level.RECENTS;
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
      if (state.searching && !state.query.isEmpty()) {
        filter(state.query.substring(0, state.query.length() - 1));
      }
    } else if (level == Level.CLASSES && !state.searching && key >= 32
        && !Character.isISOControl(key)) {
      state.searching = true;
      filter(new String(Character.toChars(key)));
    } else if (state.searching && key >= 32 && !Character.isISOControl(key)) {
      filter(state.query + new String(Character.toChars(key)));
    }
    error = null;
    return null;
  }

  private void filter(String query) {
    if (level == Level.CLASSES) {
      filterClassSubtree(query);
    } else if (level != Level.PACKAGES) {
      selection().filter(query);
      return;
    } else filterHome(query);
  }

  private void filterClassSubtree(String query) {
    Selection state = selection();
    if (query.isEmpty()) {
      state.filter("");
      return;
    }
    List<String> results = new ArrayList<>();
    for (String pkg : classes.keySet()) {
      if (pkg.startsWith(packageName + ".") && visiblePackage(pkg)
          && searchRank(pkg, query) >= 0) results.add(pkg);
    }
    for (String name : allClasses) {
      String pkg = classPackages.get(name);
      if ((pkg.equals(packageName) || pkg.startsWith(packageName + "."))
          && visibleClass(name) && searchRank(name, query) >= 0) {
        results.add(name);
      }
    }
    results.sort(Comparator.comparingInt((String name) -> searchRank(name, query))
        .thenComparing((String name) ->
            index.containsKey(name) ? classPackages.get(name) : name)
        .thenComparingInt(name -> index.containsKey(name) ? 1 : 0)
        .thenComparing(String::compareTo));
    state.query = query;
    state.matches = results;
    state.cursor = 0;
    state.top = 0;
  }

  private void clearSearch() {
    Selection state = selection();
    String selected = state.selected();
    if (level == Level.PACKAGES) filterHome("");
    else {
      state.filter("");
      if (selected != null) state.select(selected);
    }
    state.searching = false;
  }

  private void refreshHome() {
    String selected = packages.selected();
    filterHome(packages.query);
    if (selected != null) packages.select(selected);
  }

  private boolean visibleClass(String name) {
    return index.containsKey(name) && visiblePackage(classPackages.get(name));
  }

  private boolean visiblePackage(String pkg) {
    return history == null || !history.isHidden(pkg);
  }

  private void filterHome(String query) {
    List<String> results = new ArrayList<>();
    if (query.isEmpty()) {
      Set<String> groups = new HashSet<>();
      for (String pkg : classes.keySet()) {
        if (visibleHomePackage(pkg)) groups.add(homeGroup(pkg));
      }
      results.addAll(groups);
    } else {
      for (String pkg : classes.keySet()) {
        if (visibleHomePackage(pkg) && searchRank(pkg, query) >= 0) results.add(pkg);
      }
      for (String name : allClasses) {
        if (visibleHomeClass(name) && searchRank(name, query) >= 0) results.add(name);
      }
    }
    results.sort(Comparator.comparingInt((String name) -> searchRank(name, query))
        .thenComparing((String name) ->
            index.containsKey(name) ? classPackages.get(name) : name)
        .thenComparingInt(name -> index.containsKey(name) ? 1 : 0)
        .thenComparing(name -> index.containsKey(name)
            ? name.substring(name.lastIndexOf('.') + 1) : name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(String::compareTo));
    packages.query = query;
    packages.matches = results;
    packages.cursor = 0;
    packages.top = 0;
  }

  private boolean visibleHomePackage(String pkg) {
    return (allPackages || pkg.startsWith("java.")) && visiblePackage(pkg);
  }

  private boolean visibleHomeClass(String name) {
    String pkg = classPackages.get(name);
    return visibleHomePackage(pkg) && name.substring(pkg.length() + 1).indexOf('.') < 0;
  }

  private int searchRank(String name, String query) {
    boolean isClass = index.containsKey(name);
    String candidate = isClass && !query.contains(".")
        ? name.substring(name.lastIndexOf('.') + 1) : name;
    String value = candidate.toLowerCase(Locale.ROOT);
    String needle = query.toLowerCase(Locale.ROOT);
    if (value.equals(needle)) return 0;
    if (value.startsWith(needle) && (!isClass || query.contains(".")
        || !nameComponents.contains(needle) || needle.length() == candidate.length()
        || Character.isUpperCase(candidate.charAt(needle.length())))) return 1;
    if (!isClass || query.contains(".")) return value.contains(needle) ? 2 : -1;
    for (int at = value.indexOf(needle, 1); at >= 0; at = value.indexOf(needle, at + 1)) {
      int end = at + needle.length();
      if (Character.isUpperCase(candidate.charAt(at))
          && (!nameComponents.contains(needle) || end == candidate.length()
              || Character.isUpperCase(candidate.charAt(end)))) return 2;
    }
    return -1;
  }

  private String homeGroup(String pkg) {
    String[] parts = pkg.split("\\.");
    if (parts.length == 1 || collapsedRoots.contains(parts[0])) return parts[0];
    return parts[0] + "." + parts[1];
  }

  private List<String> contents(String parent) {
    Set<String> children = new HashSet<>();
    for (String pkg : classes.keySet()) {
      if (!visiblePackage(pkg) || !pkg.startsWith(parent + ".")) continue;
      String rest = pkg.substring(parent.length() + 1);
      children.add(parent + "." + rest.split("\\.", 2)[0]);
    }
    List<String> result = new ArrayList<>(children);
    result.sort(String::compareTo);
    if (visiblePackage(parent)) result.addAll(classes.getOrDefault(parent, List.of()));
    return result;
  }

  private void collapse() {
    Selection state = selection();
    if (level == Level.CLASSES) {
      int dot = packageName.lastIndexOf('.');
      if (dot >= 0 && packageName.substring(0, dot).contains(".")) {
        String child = packageName;
        packageName = packageName.substring(0, dot);
        Selection parent = classSelections.computeIfAbsent(packageName,
            p -> new Selection(contents(p)));
        parent.filter("");
        parent.select(child);
        parent.searching = true;
      } else {
        String parent = packageName;
        resetHome();
        packages.select(homeGroup(parent));
      }
    } else if (level == Level.PACKAGES && state.selected() != null) {
      String selected = state.selected();
      if (index.containsKey(selected)) {
        packageName = classPackages.get(selected);
        classes();
        selection().select(selected);
        returnLevel = Level.CLASSES;
      } else if (selected.contains(".")) {
        String parent = selected.substring(0, selected.indexOf('.'));
        collapsedRoots.add(parent);
        filterHome("");
        packages.select(parent);
      }
    } else if (level == Level.RECENTS || level == Level.BOOKMARKS
        || level == Level.VISIBILITY) {
      restoreOverlayOrigin();
      selection().searching = false;
    }
  }

  void render(Terminal terminal) {
    PrintWriter out = terminal.writer();
    int width = Math.max(40, terminal.getColumns());
    int height = Math.max(10, terminal.getRows());
    Selection state = selection();
    int viewport = height - 5;
    out.print("\033[H\033[2J");
    line(out, terminal, (switch (level) {
      case PACKAGES -> "Packages [" + (allPackages ? "all packages" : "java.* only") + "]"
          + (history != null && history.hiddenCount() > 0
          ? " (" + history.hiddenCount() + " hidden roots)" : "");
      case CLASSES -> "Classes and packages in " + packageName;
      case RECENTS -> "Recent classes";
      case BOOKMARKS -> "Bookmarked classes";
      case VISIBILITY -> "Manage hidden packages";
    }) + "  ·  Java " + release + " API", AttributedStyle.CYAN,
        width);
    line(out, terminal, "Search " + (level == Level.PACKAGES ? "packages or classes"
        : level == Level.VISIBILITY ? "packages" : "classes")
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
            + (level == Level.VISIBILITY ? visibilityLabel(name)
                : level == Level.CLASSES ? (indexContainsClass(name) ? "[class] " : "[package] ")
                : level == Level.PACKAGES && !state.query.isEmpty()
                    ? (indexContainsClass(name) ? "[" + classPackages.get(name) + "] "
                        : "[package] ") : "") + name;
        line(out, terminal, row, index == state.cursor ? AttributedStyle.WHITE
            : AttributedStyle.GREEN, width, index == state.cursor);
      }
    }
    line(out, terminal, "─".repeat(width - 1), AttributedStyle.WHITE, width);
    line(out, terminal, error != null ? error
        : (state.matches.isEmpty() ? "No matches  ·  " : state.matches.size() + " matches  ·  ")
            + (level == Level.PACKAGES ? "Tab Scope  b Bookmarks  p Hidden  r Recent  "
                : level == Level.VISIBILITY ? "Space/Enter Toggle  " : "")
            + "←/c Collapse  →/d Open  h Home  q Quit", AttributedStyle.WHITE, width);
    out.flush();
  }

  private boolean indexContainsClass(String name) {
    return index.containsKey(name);
  }

  private String visibilityLabel(String pkg) {
    String root = history == null ? null : history.hiddenBy(pkg);
    if (root == null) return "[shown] ";
    return root.equals(pkg) ? "[hidden] " : "[hidden via " + root + "] ";
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

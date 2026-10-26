package dev.jdoc;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** User-specific browser history and bookmarks, separate from release page caches. */
final class NavigationStore {
  private final Path root;
  private final Gson gson = new Gson();
  private final List<Entry> bookmarks;
  private final List<Entry> recents;
  private final List<String> hiddenPackages;

  NavigationStore(Path root) {
    this.root = root;
    bookmarks = read("bookmarks.json");
    recents = read("recents.json");
    hiddenPackages = readHidden();
  }

  String hiddenBy(String packageName) {
    return hiddenPackages.stream()
        .filter(root -> packageName.equals(root) || packageName.startsWith(root + "."))
        .min(java.util.Comparator.comparingInt(String::length)).orElse(null);
  }

  boolean isHidden(String packageName) { return hiddenBy(packageName) != null; }

  int hiddenCount() { return hiddenPackages.size(); }

  String togglePackage(String packageName) throws IOException {
    List<String> next = new ArrayList<>(hiddenPackages);
    if (next.remove(packageName)) {
      write("hidden-packages.json", next);
      hiddenPackages.clear();
      hiddenPackages.addAll(next);
      String parent = hiddenBy(packageName);
      return parent == null ? "Showing " + packageName
          : packageName + " is still hidden by " + parent;
    }
    String parent = hiddenBy(packageName);
    if (parent != null) return packageName + " is hidden by " + parent + "; show it first";
    next.add(packageName);
    next.sort(String::compareTo);
    write("hidden-packages.json", next);
    hiddenPackages.clear();
    hiddenPackages.addAll(next);
    return "Hidden " + packageName + " and its subpackages";
  }

  boolean isBookmarked(String release, String name) {
    return bookmarks.stream().anyMatch(e -> e.release.equals(release) && e.className.equals(name));
  }

  boolean toggleBookmark(String release, String name) throws IOException {
    boolean remove = isBookmarked(release, name);
    List<Entry> next = new ArrayList<>(bookmarks);
    if (remove) next.removeIf(e -> e.release.equals(release) && e.className.equals(name));
    else next.add(new Entry(release, name));
    write("bookmarks.json", next);
    bookmarks.clear();
    bookmarks.addAll(next);
    return !remove;
  }

  void visited(String release, String name) throws IOException {
    List<Entry> next = new ArrayList<>(recents);
    next.removeIf(e -> e.release.equals(release) && e.className.equals(name));
    next.add(0, new Entry(release, name));
    if (next.size() > 20) next = new ArrayList<>(next.subList(0, 20));
    write("recents.json", next);
    recents.clear();
    recents.addAll(next);
  }

  List<String> recentNames(String release) { return names(recents, release); }
  List<String> bookmarkedNames(String release) { return names(bookmarks, release); }

  private static List<String> names(List<Entry> entries, String release) {
    return entries.stream().filter(e -> e.release.equals(release)).map(e -> e.className).toList();
  }

  private List<Entry> read(String filename) {
    Path file = root.resolve(filename);
    if (!Files.isRegularFile(file)) return new ArrayList<>();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Entry[] loaded = gson.fromJson(reader, Entry[].class);
      return loaded == null ? new ArrayList<>() : new ArrayList<>(List.of(loaded));
    } catch (IOException | RuntimeException ignored) {
      // A damaged optional history file must not prevent API browsing.
      return new ArrayList<>();
    }
  }

  private List<String> readHidden() {
    Path file = root.resolve("hidden-packages.json");
    if (!Files.isRegularFile(file)) return new ArrayList<>();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String[] loaded = gson.fromJson(reader, String[].class);
      if (loaded == null) return new ArrayList<>();
      List<String> names = new ArrayList<>();
      for (String name : loaded) {
        if (name != null && !name.isBlank() && !names.contains(name)) names.add(name);
      }
      return names;
    } catch (IOException | RuntimeException ignored) {
      return new ArrayList<>();
    }
  }

  private void write(String filename, Object entries) throws IOException {
    Files.createDirectories(root);
    Path temporary = Files.createTempFile(root, "jdoc-navigation-", ".tmp");
    try {
      try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        gson.toJson(entries, writer);
      }
      Files.move(temporary, root.resolve(filename), StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static final class Entry {
    String release;
    String className;

    Entry(String release, String className) {
      this.release = release;
      this.className = className;
    }
  }
}

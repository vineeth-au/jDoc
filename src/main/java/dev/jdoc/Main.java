package dev.jdoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

public final class Main {

  private static final String APP_VERSION = "0.1.0";
  private final Documentation docs = new Documentation();
  private final Path selected = docs.versionPath("selected-version.txt");

  public static void main(String[] args) {
    try {
      new Main().run(args);
    } catch (IllegalArgumentException | IOException error) {
      System.err.println("jDoc: " + error.getMessage());
      System.exit(1);
    }
  }

  private void run(String[] args) throws IOException {
    if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
      help();
      return;
    }
    if (args.length == 0 && System.console() == null) {
      System.out.println("An interactive terminal is required to browse packages. "
          + "Use jDoc <Class> or jDoc --help.");
      return;
    }
    if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
      requireLength(args, 1);
      System.out.println("jDoc " + APP_VERSION);
      return;
    }
    if (args.length > 0 && args[0].equals("--versions")) {
      requireLength(args, 1);
      String current = selection();
      boolean automatic = current == null;
      if (automatic) {
        current = automaticSelection().version;
      }
      System.out.println("Java documentation releases:");
      for (String version : Versions.SUPPORTED) {
        System.out.printf("  %-3s %-9s index: %-6s cached classes: %d%n", version,
            version.equals(current) ? (automatic ? "(auto)" : "(selected)") : "",
            docs.indexed(version) ? "yes" : "no",
            docs.cachedCount(version));
      }
      return;
    }
    if (args.length > 0 && (args[0].equals("--docVersion") || args[0].equals("--doc-version"))) {
      requireLength(args, 2);
      Versions.base(args[1]);
      Files.createDirectories(selected.getParent());
      Files.write(selected, Arrays.asList(args[1]), StandardCharsets.UTF_8);
      System.out.println(
          "Selected Java " + args[1] + " documentation. Pages will be fetched on demand.");
      return;
    }
    if (args.length > 0 && args[0].equals("--java")) {
      throw new IllegalArgumentException("Use --docVersion <release> to select documentation.");
    }
    String version = selection();
    if (version == null) {
      Versions.Selection automatic = automaticSelection();
      version = automatic.version;
      System.err.println("jDoc: " + automatic.notice() + " Change with --docVersion <release>.");
    }
    Map<String, String> index;
    try {
      index = docs.index(version);
    } catch (IOException ex) {
      throw new IOException(
          "Could not load the Java " + version + " class index (network required on first use): "
              + ex.getMessage(), ex);
    }
    if (args.length == 0) {
      new BrowserSession(docs, index, version, null).run();
      return;
    }
    String requested = args[0];
    String[] memberArgs = Arrays.copyOfRange(args, 1, args.length);
    final String lookup = requested;
    if (!index.containsKey(requested) && requested.contains(".")
        && index.keySet().stream().noneMatch(s -> s.endsWith("." + lookup))) {
      int dot = requested.lastIndexOf('.');
      String prefix = requested.substring(0, dot);
      if (index.containsKey(prefix) || index.keySet().stream()
          .anyMatch(s -> s.endsWith("." + prefix))) {
        memberArgs = new String[args.length];
        memberArgs[0] = requested.substring(dot + 1);
        System.arraycopy(args, 1, memberArgs, 1, args.length - 1);
        requested = prefix;
      }
    }
    String name = resolve(index, requested);
    if (name == null) {
      return;
    }
    DocModel doc;
    try {
      doc = docs.classDoc(version, name, index.get(name));
    } catch (IOException ex) {
      throw new IOException(
          "Could not load " + name + " (network required if uncached): " + ex.getMessage(), ex);
    }
    if (doc.stale) {
      System.err.println("jDoc: Using older cached documentation; reconnect to refresh "
          + "member coverage, formatting, links, and metadata.");
    }
    if (memberArgs.length == 0 && System.console() != null) {
      new BrowserSession(docs, index, version, doc).run();
      return;
    }
    List<DocModel.Method> methods = MemberLookup.select(doc, memberArgs);
    if (methods.isEmpty() && memberArgs.length > 0) {
      if (doc.stale) {
        throw new IllegalArgumentException("This cached page predates member lookup; reconnect "
            + "to refresh before deciding the member is absent.");
      }
      String requestedMethod = memberArgs[0].startsWith("-")
          ? memberArgs[0].substring(1) : memberArgs[0];
      String alternatives = doc.declaredMembers().stream()
          .filter(m -> m.name.equals(requestedMethod))
          .map(m -> "\n  " + m.signature).collect(Collectors.joining());
      throw new IllegalArgumentException(
          "No matching documented method on " + name + " in Java " + version + "."
              + (alternatives.isEmpty() ? "" : " Available overloads:" + alternatives));
    }
    StringBuilder out = new StringBuilder(
        name + " — Java " + version + " documentation\n" + doc.sourceUrl + "\n\n");
    if (memberArgs.length == 0 && doc.methods.isEmpty() && doc.description != null
        && !doc.description.isEmpty()) {
      out.append(String.join("\n", DocRenderer.classLines(doc, 100))).append("\n\n");
    }
    if (methods.isEmpty()) {
      out.append("No documented members.\n");
    }
    List<String> tokens = Arrays.stream(memberArgs).filter(t -> !t.equals("-i")).toList();
    boolean namedLookup = !tokens.isEmpty() && !tokens.get(0).equals("-p")
        && !tokens.get(0).equals("--params");
    for (DocModel.Method method : methods) {
      DocModel.Method displayed = method;
      String unavailable = null;
      if ((method.inherited || method.kind.equals("nested type"))
          && namedLookup) {
        try {
          displayed = docs.detail(version, method);
        } catch (IOException ex) {
          unavailable = "Details unavailable (not cached or offline): " + ex.getMessage();
        }
      }
      if (method.inherited) {
        out.append("Inherited from ").append(method.declaringType).append('\n');
      }
      List<String> lines = DocRenderer.methodLines(displayed, 100, doc.sourceUrl);
      if (!lines.isEmpty()) {
        out.append(displayed.kind).append(": ").append(lines.get(0)).append('\n');
        for (int i = 1; i < lines.size(); i++) out.append(lines.get(i)).append('\n');
      }
      if (unavailable != null) {
        out.append(unavailable).append('\n');
      }
      out.append('\n');
    }
    present(out.toString());
  }

  private String resolve(Map<String, String> index, String requested) {
    if (index.containsKey(requested)) {
      return requested;
    }
    List<String> matches = index.keySet().stream()
        .filter(s -> s.endsWith("." + requested))
        .sorted().collect(Collectors.toList());
    if (matches.size() == 1) {
      return matches.get(0);
    }
    if (matches.size() > 1) {
      return choose("Multiple classes named " + requested + ":", matches);
    }
    List<String> suggestions = index.keySet().stream()
        .filter(s ->
            distance(requested.toLowerCase(), s.substring(s.lastIndexOf('.') + 1).toLowerCase())
                <= 2)
        .sorted(Comparator.comparingInt((String s) -> distance(requested.toLowerCase(),
                s.substring(s.lastIndexOf('.') + 1).toLowerCase()))
            .thenComparing(s -> s))
        .limit(5).collect(Collectors.toList());
    if (suggestions.size() == 1) {
      String suggestion = suggestions.get(0);
      if (System.console() != null) {
        System.err.print(
            "Did you mean " + suggestion.substring(suggestion.lastIndexOf('.') + 1) + " ("
                + suggestion + ")? [y/N] ");
        if (new Scanner(System.in).nextLine().trim().equalsIgnoreCase("y")) {
          return suggestion;
        }
      }
      throw new IllegalArgumentException(
          "Class not found: " + requested + ". Suggested: " + suggestion);
    }
    if (!suggestions.isEmpty()) {
      return choose("Class not found: " + requested + ". Did you mean:", suggestions);
    }
    throw new IllegalArgumentException("Class not found: " + requested);
  }

  private String choose(String prompt, List<String> choices) {
    System.err.println(prompt);
    for (int i = 0; i < choices.size(); i++) {
      System.err.println("  " + (i + 1) + ". " + choices.get(i));
    }
    if (System.console() == null) {
      throw new IllegalArgumentException("Specify a fully qualified class name.");
    }
    System.err.print("Choose a number (Enter to cancel): ");
    String input = new Scanner(System.in).nextLine().trim();
    if (input.matches("[0-9]+")) {
      int index = Integer.parseInt(input) - 1;
      if (index >= 0 && index < choices.size()) {
        return choices.get(index);
      }
    }
    return null;
  }

  private static int distance(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      d[0][j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
            d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
        if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(
            j - 1)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }
    return d[a.length()][b.length()];
  }

  private String selection() throws IOException {
    if (!Files.exists(selected)) {
      return null;
    }
    String value = new String(Files.readAllBytes(selected), StandardCharsets.UTF_8).trim();
    return Versions.SUPPORTED.contains(value) ? value : null;
  }

  private static Versions.Selection automaticSelection() {
    return Versions.detect(System.getenv("JAVA_HOME"),
        System.getProperty("java.specification.version"));
  }

  private void present(String text) throws IOException {
    if (System.console() != null && System.getenv("NO_COLOR") == null) {
      text = colorize(text);
    }
    if (System.console() != null && text.split("\n").length > 35) {
      Process process = new ProcessBuilder("less", "-R")
          .redirectOutput(ProcessBuilder.Redirect.INHERIT)
          .redirectError(ProcessBuilder.Redirect.INHERIT).start();
      try (java.io.OutputStream output = process.getOutputStream()) {
        output.write(text.getBytes(StandardCharsets.UTF_8));
      }
      try {
        process.waitFor();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    } else {
      System.out.print(text);
    }
  }

  static String colorize(String text) {
    StringBuilder colored = new StringBuilder();
    String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      AttributedStyle style =
          i == 0 ? AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold()
              : line.startsWith("http") ? AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE)
                  .faint()
                  : line.matches("^(Parameters|Returns|Throws|Since|See Also):.*")
                      ? AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold()
                      : !line.startsWith(" ") && line.contains("(")
                          ? AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold()
                          : AttributedStyle.DEFAULT;
      colored.append(new AttributedString(line, style).toAnsi());
      if (i < lines.length - 1) {
        colored.append('\n');
      }
    }
    return colored.toString();
  }

  private static void requireLength(String[] args, int expected) {
    if (args.length != expected) {
      throw new IllegalArgumentException("Unexpected arguments. See jDoc --help.");
    }
  }

  private static void help() {
    System.out.println("""
        jDoc — terminal Java API documentation
        Usage:
          jDoc --docVersion <release>     Select the default documentation release.
                                          Supported versions: 17, 21, 25
          jDoc --versions                 Show supported releases and cached status
          jDoc --version | -v             Show the application version
          jDoc --help                     Show this help
          jDoc                            Search packages or classes (terminal only)
          jDoc <Class>                    Browse methods, constructors, fields and types
          jDoc <Class> -<member>          Look up a method or member (inherited too)
          jDoc <Class> -p <count> [-i]    List methods by arity; -i includes inherited
          jDoc <Class> <method> -p <count|types>  Filter declared and inherited methods

        Browser: Packages → Classes → Members → Documentation.
          From home, type String to find a class across all packages.
          Type to filter (/ starts search); Backspace edits the query.
          ↑↓ Navigate  PgUp/PgDn Page  →/Enter Open  ← Back one level
          Esc leaves search; otherwise goes to package home. q quits when not typing.
          i toggles inherited methods.
          C reads the class description from the member list.

        Colors are disabled by NO_COLOR or when output is redirected.
        A number after -p/--params is a parameter COUNT, not an overload number.

        Types require a method name. Use char-array for char[]. Put -p last.
        Examples: jDoc String -repeat
                  jDoc String -p 1
                  jDoc String valueOf -p 3
                  jDoc String valueOf -p char-array, int, int

        Without a saved selection, JAVA_HOME (or the running Java) selects the
        closest supported documentation release no newer than that Java version.
        Pages and the class index are fetched on demand and cached locally.""");
  }
}

package dev.jdoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
      help();
      return;
    }
    if (args[0].equals("--version") || args[0].equals("-v")) {
      requireLength(args, 1);
      System.out.println("jDoc " + APP_VERSION);
      return;
    }
    if (args[0].equals("--versions")) {
      requireLength(args, 1);
      String current = selection();
      System.out.println("Java documentation releases:");
      for (String version : Versions.SUPPORTED) {
        System.out.printf("  %-3s %-9s index: %-6s cached classes: %d%n", version,
            version.equals(current) ? "(selected)" : "", docs.indexed(version) ? "yes" : "no",
            docs.cachedCount(version));
      }
      return;
    }
    if (args[0].equals("--docVersion") || args[0].equals("--doc-version")) {
      requireLength(args, 2);
      Versions.base(args[1]);
      Files.createDirectories(selected.getParent());
      Files.write(selected, Arrays.asList(args[1]), StandardCharsets.UTF_8);
      System.out.println(
          "Selected Java " + args[1] + " documentation. Pages will be fetched on demand.");
      return;
    }
    if (args[0].equals("--java")) {
      throw new IllegalArgumentException("Use --docVersion <release> to select documentation.");
    }
    String version = selection();
    if (version == null) {
      throw new IllegalArgumentException(
          "No documentation version selected. Run jDoc --versions, then jDoc --docVersion 17.");
    }
    Map<String, String> index;
    try {
      index = docs.index(version);
    } catch (IOException ex) {
      throw new IOException(
          "Could not load the Java " + version + " class index (network required on first use): "
              + ex.getMessage(), ex);
    }
    String name = resolve(index, args[0]);
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
    if (args.length == 1 && System.console() != null) {
      new MethodBrowser(doc).run();
      return;
    }
    List<DocModel.Method> declared = doc.methods.isEmpty() ? doc.constructors : doc.methods;
    List<DocModel.Method> methods = new ArrayList<>(declared);
    if (args.length > 1) {
      methods = filter(methods, Arrays.copyOfRange(args, 1, args.length));
    }
    if (methods.isEmpty() && args.length > 1) {
      String requestedMethod = args[1].startsWith("-") ? args[1].substring(1) : args[1];
      String alternatives = declared.stream().filter(m -> m.name.equals(requestedMethod))
          .map(m -> "\n  " + m.signature).collect(Collectors.joining());
      throw new IllegalArgumentException(
          "No matching documented method on " + name + " in Java " + version + "."
              + (alternatives.isEmpty() ? "" : " Available overloads:" + alternatives));
    }
    StringBuilder out = new StringBuilder(
        name + " — Java " + version + " documentation\n" + doc.sourceUrl + "\n\n");
    if (args.length == 1 && doc.methods.isEmpty() && doc.description != null
        && !doc.description.isEmpty()) {
      out.append(doc.description).append("\n\n");
    }
    if (methods.isEmpty()) {
      out.append("No directly documented methods or constructors.\n");
    }
    for (DocModel.Method method : methods) {
      out.append(method.signature).append('\n');
      if (!method.description.isEmpty()) {
        out.append("  ").append(method.description).append('\n');
      }
      if (!method.details.isEmpty()) {
        out.append(method.details).append('\n');
      }
      out.append('\n');
    }
    present(out.toString());
  }

  private List<DocModel.Method> filter(List<DocModel.Method> methods, String[] args) {
    String methodName = null;
    int position = 0;
    if (!args[0].equals("-p") && !args[0].equals("--params")) {
      methodName = args[0].startsWith("-") ? args[0].substring(1) : args[0];
      position = 1;
    }
    final String named = methodName;
    if (named != null) {
      methods = methods.stream().filter(m -> m.name.equals(named)).collect(Collectors.toList());
    }
    if (position == args.length) {
      return methods.isEmpty() ? methods : methods.subList(0, 1);
    }
    if (!args[position].equals("-p") && !args[position].equals("--params")) {
      throw new IllegalArgumentException(
          "Expected -p/--params after the method name. See jDoc --help.");
    }
    if (position + 1 >= args.length) {
      throw new IllegalArgumentException("-p requires a parameter count or parameter types.");
    }
    String value = String.join("", Arrays.copyOfRange(args, position + 1, args.length)).trim();
    if (value.matches("[0-9]+")) {
      int count = Integer.parseInt(value);
      return methods.stream().filter(m -> m.parameterTypes.size() == count)
          .collect(Collectors.toList());
    }
    if (named == null) {
      throw new IllegalArgumentException(
          "Parameter types require a method name; use e.g. jDoc String valueOf -p char-array,int,int.");
    }
    List<String> types = Arrays.stream(value.split(",", -1)).map(JavadocParser::normalize)
        .collect(Collectors.toList());
    return methods.stream().filter(m -> m.parameterTypes.equals(types))
        .collect(Collectors.toList());
  }

  private String resolve(Map<String, String> index, String requested) {
    if (index.containsKey(requested)) {
      return requested;
    }
    List<String> matches = index.keySet().stream()
        .filter(s -> s.substring(s.lastIndexOf('.') + 1).equals(requested))
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
          jDoc --docVersion               Select the default documentation release.
                                          Currently Supported versions are 8,11,17,21 and 25
          jDoc --versions                 Show supported releases and cached status
          jDoc --version | -v             Show the application version
          jDoc --help                     Show this help
          jDoc <Class>                    Browse direct methods (constructors when none)
          jDoc <Class> -<method>          Show first overload in Javadoc summary order
          jDoc <Class> -p <count>         Show methods with this parameter count
          jDoc <Class> <method> -p <count|types>  Filter named method

        Class browser: type to filter (/ starts search); Backspace edits.
          ↑↓ Navigate  PgUp/PgDn Page  Enter Read  b Back  q Quit

        Colors are disabled by NO_COLOR or when output is redirected.
        A number after -p/--params is a parameter COUNT, not an overload number.

        Types require a method name. Use char-array for char[]. Put -p last.
        Examples: jDoc String -repeat
                  jDoc String -p 1
                  jDoc String valueOf -p 3
                  jDoc String valueOf -p char-array, int, int

        Pages and the class index are fetched on demand and cached locally.""");
  }
}

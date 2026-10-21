package dev.jdoc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Reads literal build settings; never evaluates Maven or Gradle build logic. */
final class ProjectTarget {
  final int major;
  final String source;
  final boolean apiRestricted;

  ProjectTarget(int major, String source, boolean apiRestricted) {
    this.major = major;
    this.source = source;
    this.apiRestricted = apiRestricted;
  }

  static ProjectTarget detect(Path cwd) {
    for (Path dir = cwd.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
      Path pom = dir.resolve("pom.xml");
      if (Files.isRegularFile(pom)) {
        ProjectTarget target = maven(pom);
        if (target != null) return target;
      }
      for (String filename : new String[]{"build.gradle", "build.gradle.kts"}) {
        Path build = dir.resolve(filename);
        if (Files.isRegularFile(build)) {
          ProjectTarget target = gradle(build);
          if (target != null) return target;
        }
      }
    }
    return null;
  }

  private static ProjectTarget maven(Path pom) {
    try {
      MavenConfig config = readPom(pom, new HashSet<>(), 0);
      if (config == null) return null;
      for (String[] candidate : new String[][]{
          {config.release, "maven-compiler-plugin release", "true"},
          {config.properties.get("maven.compiler.release"), "maven.compiler.release", "true"},
          {config.toolchain, "JDK toolchain", "false"},
          {config.target, "maven-compiler-plugin target", "false"},
          {config.properties.get("maven.compiler.target"), "maven.compiler.target", "false"},
          {config.properties.get("maven.compiler.source"), "maven.compiler.source", "false"},
          {config.properties.get("java.version"), "java.version convention", "false"}}) {
        Integer major = number(candidate[0], config.properties);
        if (major != null) {
          return new ProjectTarget(major, pom.getFileName() + " (" + candidate[1] + ")",
              Boolean.parseBoolean(candidate[2]));
        }
      }
    } catch (IOException | ParserConfigurationException | SAXException ignored) {
      // A dynamic or malformed build file is not evidence of a target release.
    }
    return null;
  }

  private static MavenConfig readPom(Path path, Set<Path> visited, int depth)
      throws IOException, ParserConfigurationException, SAXException {
    Path normalized = path.toAbsolutePath().normalize();
    if (depth > 5 || !Files.isRegularFile(normalized) || !visited.add(normalized)) return null;
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    Element project;
    try (InputStream in = Files.newInputStream(normalized)) {
      project = factory.newDocumentBuilder().parse(in).getDocumentElement();
    }
    MavenConfig config = new MavenConfig();
    Element parent = child(project, "parent");
    if (parent != null) {
      String relative = text(parent, "relativePath");
      if (relative == null) relative = "../pom.xml";
      if (!relative.isBlank()) {
        Path parentPath = normalized.getParent().resolve(relative).normalize();
        if (Files.isDirectory(parentPath)) parentPath = parentPath.resolve("pom.xml");
        MavenConfig inherited = readPom(parentPath, visited, depth + 1);
        if (inherited != null) config = inherited;
      }
    }
    Element props = child(project, "properties");
    if (props != null) {
      for (Node node = props.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element entry) {
          config.properties.put(entry.getTagName(), entry.getTextContent().trim());
        }
      }
    }
    Element build = child(project, "build");
    if (build != null) {
      String release = compilerSetting(build, "release");
      if (release != null) config.release = release;
      String target = compilerSetting(build, "target");
      if (target != null) config.target = target;
      String toolchain = compilerToolchain(build);
      if (toolchain != null) config.toolchain = toolchain;
    }
    return config;
  }

  private static String compilerSetting(Element build, String key) {
    for (String location : new String[]{"plugins", "pluginManagement"}) {
      Element plugins = child(build, location);
      if (location.equals("pluginManagement")) plugins = child(plugins, "plugins");
      if (plugins == null) continue;
      for (Node node = plugins.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element plugin && "plugin".equals(plugin.getTagName())
            && "maven-compiler-plugin".equals(text(plugin, "artifactId"))) {
          String value = text(child(plugin, "configuration"), key);
          if (value != null) return value;
        }
      }
    }
    return null;
  }

  private static String compilerToolchain(Element build) {
    for (String location : new String[]{"plugins", "pluginManagement"}) {
      Element plugins = child(build, location);
      if (location.equals("pluginManagement")) plugins = child(plugins, "plugins");
      if (plugins == null) continue;
      for (Node node = plugins.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element plugin && "plugin".equals(plugin.getTagName())) {
          Element configuration = child(plugin, "configuration");
          if ("maven-compiler-plugin".equals(text(plugin, "artifactId"))) {
            String value = text(child(configuration, "jdkToolchain"), "version");
            if (value != null) return value;
          }
          if ("maven-toolchains-plugin".equals(text(plugin, "artifactId"))) {
            String value = text(child(child(configuration, "toolchains"), "jdk"), "version");
            if (value != null) return value;
          }
        }
      }
    }
    return null;
  }

  private static Element child(Element parent, String tag) {
    if (parent == null) return null;
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && tag.equals(element.getTagName())) return element;
    }
    return null;
  }

  private static String text(Element parent, String tag) {
    Element element = child(parent, tag);
    return element == null ? null : element.getTextContent().trim();
  }

  private static ProjectTarget gradle(Path build) {
    try {
      String script = Files.readString(build);
      script = script.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
      Map<String, String> properties = new HashMap<>();
      Path props = build.getParent().resolve("gradle.properties");
      if (Files.isRegularFile(props)) {
        for (String line : Files.readAllLines(props)) {
          int equal = line.indexOf('=');
          if (equal > 0 && !line.stripLeading().startsWith("#")) {
            properties.put(line.substring(0, equal).trim(), line.substring(equal + 1).trim());
          }
        }
      }
      for (String[] candidate : new String[][]{
          {"\\boptions\\.release\\s*(?:=|\\.set\\()\\s*([^\\s)]+)", "options.release", "true"},
          {"\\blanguageVersion\\s*(?:=|\\.set\\()\\s*(?:JavaLanguageVersion\\.of\\()?\\s*([^\\s)]+)",
              "java.toolchain.languageVersion", "false"},
          {"\\btargetCompatibility\\s*=\\s*([^\\s;]+)", "targetCompatibility", "false"},
          {"\\bsourceCompatibility\\s*=\\s*([^\\s;]+)", "sourceCompatibility", "false"}}) {
        Matcher match = Pattern.compile(candidate[0]).matcher(script);
        while (match.find()) {
          Integer major = number(match.group(1), properties);
          if (major != null) return new ProjectTarget(major,
              build.getFileName() + " (" + candidate[1] + ")",
              Boolean.parseBoolean(candidate[2]));
        }
      }
    } catch (IOException ignored) {
      // Ignore unreadable builds and fall back to the installed JDK.
    }
    return null;
  }

  private static Integer number(String raw, Map<String, String> properties) {
    if (raw == null) return null;
    String value = raw.trim();
    for (int i = 0; i < 8 && value.contains("${"); i++) {
      Matcher match = Pattern.compile("\\$\\{([^}]+)}").matcher(value);
      if (!match.find()) break;
      String replacement = properties.get(match.group(1));
      if (replacement == null) return null;
      value = value.substring(0, match.start()) + replacement + value.substring(match.end());
    }
    if (value.startsWith("$") && !value.startsWith("${")) {
      value = properties.getOrDefault(value.substring(1), value);
    }
    Matcher match = Pattern.compile("(?:JavaVersion\\.VERSION_|VERSION_)?(\\d+)")
        .matcher(value.replace("\"", "").replace("'", ""));
    return match.matches() ? Integer.parseInt(match.group(1)) : null;
  }

  private static final class MavenConfig {
    final Map<String, String> properties = new HashMap<>();
    String release;
    String toolchain;
    String target;
  }
}

package dev.jdoc;

import com.google.gson.Gson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Documentation {

  private final Path root;
  private final Gson gson = new Gson();

  public Documentation() {
    String override = System.getenv("JDOC_CACHE");
    root = override == null || override.isEmpty()
        ? Paths.get(System.getProperty("user.home"), "Library", "Caches", "jDoc")
        : Paths.get(override);
  }

  Path versionPath(String version) {
    return root.resolve(version);
  }

  boolean indexed(String version) {
    return Files.exists(versionPath(version).resolve("index.json"));
  }

  long cachedCount(String version) throws IOException {
    Path folder = versionPath(version).resolve("classes");
    if (!Files.isDirectory(folder)) {
      return 0;
    }
    try (java.util.stream.Stream<Path> files = Files.list(folder)) {
      return files.filter(p -> p.toString().endsWith(".json")).count();
    }
  }

  Map<String, String> index(String version) throws IOException {
    Path path = versionPath(version).resolve("index.json");
    if (Files.exists(path)) {
      try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        Map<String, String> index = gson.fromJson(in, Map.class);
        if (index != null && !index.isEmpty()) {
          return index;
        }
      }
    }
    Document page = fetch(Versions.index(version));
    Map<String, String> index = new LinkedHashMap<>();
    for (Element link : page.select("a[href$=.html]")) {
      String href = link.attr("href");
      if (href.contains("#") || href.contains("?") || href.startsWith("http")) {
        continue;
      }
      String relative = href.replaceFirst("^\\./", "");
      String[] parts = relative.split("/");
      if (parts.length < 2) {
        continue;
      }
      int offset = 1;
      if (parts.length < offset + 2) {
        continue;
      }
      String file = parts[parts.length - 1];
      String simple = file.substring(0, file.length() - 5);
      if (simple.contains("-") || simple.equals("package-summary") || simple.equals(
          "module-summary")) {
        continue;
      }
      List<String> names = new ArrayList<>();
      for (int i = offset; i < parts.length - 1; i++) {
        names.add(parts[i]);
      }
      names.add(simple);
      index.put(String.join(".", names), Versions.base(version) + relative);
    }
    if (index.isEmpty()) {
      throw new IOException("No classes found in the Java " + version + " index");
    }
    write(path, index);
    return index;
  }

  DocModel classDoc(String version, String name, String url) throws IOException {
    Path path = versionPath(version).resolve("classes").resolve(name + ".json");
    if (Files.exists(path)) {
      try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        DocModel doc = gson.fromJson(in, DocModel.class);
        if (doc != null && doc.schemaVersion == 2 && doc.methods != null
            && doc.constructors != null) {
          return doc;
        }
      }
    }
    DocModel doc = JavadocParser.parse(fetch(url), name, url, version);
    write(path, doc);
    return doc;
  }

  private Document fetch(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestProperty("User-Agent", "jDoc/0.1 (terminal Javadoc reader)");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(20000);
    try {
      int status = connection.getResponseCode();
      if (status != 200) {
        throw new IOException("HTTP " + status + " at " + url);
      }
      try (java.io.InputStream stream = connection.getInputStream()) {
        return Jsoup.parse(stream, "UTF-8", url);
      }
    } finally {
      connection.disconnect();
    }
  }

  private void write(Path path, Object object) throws IOException {
    Files.createDirectories(path.getParent());
    Path temporary = Files.createTempFile(path.getParent(), "jdoc-", ".tmp");
    try {
      try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        gson.toJson(object, out);
      }
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}

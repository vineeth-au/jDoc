package dev.jdoc;

import com.google.gson.Gson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class Documentation {

  private final Path root;
  private final Gson gson = new Gson();

  public Documentation() {
    this(defaultRoot());
  }

  private static Path defaultRoot() {
    String override = System.getenv("JDOC_CACHE");
    return override == null || override.isEmpty()
        ? Paths.get(System.getProperty("user.home"), "Library", "Caches", "jDoc")
        : Paths.get(override);
  }

  Documentation(Path root) {
    this.root = root;
  }

  Path versionPath(String version) {
    return root.resolve(version);
  }

  boolean indexed(String version) {
    return Files.exists(versionPath(version).resolve("type-search-index.json"))
        && Files.exists(versionPath(version).resolve("package-search-index.json"))
        || Files.exists(versionPath(version).resolve("index.json"));
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
    try {
      List<SearchIndex.Type> types = cachedTypeSearch(version);
      if (types == null) {
        types = SearchIndex.types(fetchText(Versions.typeIndex(version)));
        write(versionPath(version).resolve("type-search-index.json"), types);
      }
      List<SearchIndex.Package> packages = cachedPackageSearch(version);
      if (packages == null) {
        packages = SearchIndex.packages(fetchText(Versions.packageIndex(version)));
        write(versionPath(version).resolve("package-search-index.json"), packages);
      }
      Map<String, String> result = SearchIndex.classUrls(version, types, packages);
      if (result.isEmpty()) throw new IOException("No classes in Java " + version + " search index");
      if (!Files.exists(path)) write(path, result);
      return result;
    } catch (IOException ex) {
      if (!Files.exists(path)) throw ex;
      try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        Map<String, String> old = gson.fromJson(in, Map.class);
        if (old != null && !old.isEmpty()) return old;
      }
      throw ex;
    }
  }

  List<SearchIndex.Type> cachedTypeSearch(String version) throws IOException {
    Path path = versionPath(version).resolve("type-search-index.json");
    if (!Files.exists(path)) return null;
    try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return Arrays.asList(gson.fromJson(in, SearchIndex.Type[].class));
    }
  }

  List<SearchIndex.Package> cachedPackageSearch(String version) throws IOException {
    Path path = versionPath(version).resolve("package-search-index.json");
    if (!Files.exists(path)) return null;
    try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return Arrays.asList(gson.fromJson(in, SearchIndex.Package[].class));
    }
  }

  List<SearchIndex.Member> cachedMemberSearch(String version) throws IOException {
    Path path = versionPath(version).resolve("member-search-index.json");
    if (!Files.exists(path)) return null;
    try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return Arrays.asList(gson.fromJson(in, SearchIndex.Member[].class));
    }
  }

  List<SearchIndex.Member> memberSearch(String version) throws IOException {
    List<SearchIndex.Member> cached = cachedMemberSearch(version);
    if (cached != null) return cached;
    List<SearchIndex.Member> members = SearchIndex.members(fetchText(Versions.memberIndex(version)));
    write(versionPath(version).resolve("member-search-index.json"), members);
    return members;
  }

  ReleaseReport report(String release, boolean removal) throws IOException {
    Versions.base(release);
    String name = removal ? "removal" : "new";
    Path path = versionPath(release).resolve("reports").resolve(name + "-v1.json");
    if (Files.exists(path)) {
      try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        ReleaseReport report = gson.fromJson(in, ReleaseReport.class);
        if (report != null && release.equals(report.release) && name.equals(report.kind)
            && report.entries != null && !report.entries.isEmpty()) return report;
      }
    }
    String page = removal ? "deprecated-list.html" : "new-list.html";
    ReleaseReport report = ReleaseReport.parse(fetch(Versions.base(release) + page), release, removal);
    write(path, report);
    return report;
  }

  Map<String, String> cachedIndex(String version) throws IOException {
    List<SearchIndex.Type> types = cachedTypeSearch(version);
    List<SearchIndex.Package> packages = cachedPackageSearch(version);
    if (types != null && packages != null) return SearchIndex.classUrls(version, types, packages);
    Path path = versionPath(version).resolve("index.json");
    if (!Files.exists(path)) return null;
    try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return gson.fromJson(in, Map.class);
    }
  }

  DocModel cachedClassDoc(String version, String name) throws IOException {
    Path path = versionPath(version).resolve("classes").resolve(name + ".json");
    if (!Files.exists(path)) return null;
    try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      DocModel doc = gson.fromJson(in, DocModel.class);
      if (doc != null) doc.stale = doc.schemaVersion != 5;
      return doc;
    }
  }

  DocModel classDoc(String version, String name, String url) throws IOException {
    Path path = versionPath(version).resolve("classes").resolve(name + ".json");
    DocModel older = null;
    if (Files.exists(path)) {
      try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        DocModel doc = gson.fromJson(in, DocModel.class);
        if (doc != null && doc.schemaVersion == 5 && doc.methods != null
            && doc.constructors != null && doc.inherited != null && doc.fields != null
            && doc.classBody != null) {
          return doc;
        }
        if (doc != null && (doc.schemaVersion == 2 || doc.schemaVersion == 3
            || doc.schemaVersion == 4)
            && doc.methods != null && doc.constructors != null) {
          older = doc;
        }
      }
    }
    try {
      DocModel doc = JavadocParser.parse(fetch(url), name, url, version);
      write(path, doc);
      return doc;
    } catch (IOException ex) {
      if (older == null) {
        throw ex;
      }
      older.stale = true;
      return older;
    }
  }

  DocModel.Method detail(String release, DocModel.Method member) throws IOException {
    if (!member.inherited && !"nested type".equals(member.kind)) {
      return member;
    }
    String qualified = member.inherited ? member.declaringType
        : member.declaringType.substring(0, member.declaringType.lastIndexOf('.') + 1)
            + member.name;
    DocModel owner = classDoc(release, qualified, member.sourceUrl);
    if ("nested type".equals(member.kind)) {
      DocModel.Method nested = new DocModel.Method();
      nested.name = member.name;
      nested.kind = member.kind;
      nested.signature = member.signature;
      nested.description = owner.description;
      nested.body = owner.classBody;
      nested.since = owner.since;
      nested.deprecated = owner.deprecated;
      nested.deprecatedSince = owner.deprecatedSince;
      nested.preview = owner.preview;
      nested.sourceUrl = owner.sourceUrl;
      nested.declaringType = member.declaringType;
      return nested;
    }
    for (DocModel.Method method : owner.methods) {
      if (method.anchor.equals(member.anchor) ||
          JavadocParser.key(method.name, method.parameterTypes)
              .equals(JavadocParser.key(member.name, member.parameterTypes))) {
        method.inherited = true;
        return method;
      }
    }
    throw new IOException("Documentation for " + member.name + " not found on "
        + member.declaringType);
  }

  private Document fetch(String url) throws IOException {
    HttpURLConnection connection = connection(url);
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

  private String fetchText(String url) throws IOException {
    HttpURLConnection connection = connection(url);
    try {
      int status = connection.getResponseCode();
      if (status != 200) throw new IOException("HTTP " + status + " at " + url);
      try (java.io.InputStream stream = connection.getInputStream()) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } finally {
      connection.disconnect();
    }
  }

  private HttpURLConnection connection(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestProperty("User-Agent", "jDoc/0.1 (terminal Javadoc reader)");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(20000);
    return connection;
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

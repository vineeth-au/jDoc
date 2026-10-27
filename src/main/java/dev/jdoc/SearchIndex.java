package dev.jdoc;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SearchIndex {
  private SearchIndex() {}

  static List<Type> types(String script) throws IOException {
    return Arrays.asList(new Gson().fromJson(array(script, "typeSearchIndex"), Type[].class));
  }

  static List<Package> packages(String script) throws IOException {
    return Arrays.asList(new Gson().fromJson(array(script, "packageSearchIndex"), Package[].class));
  }

  static List<Member> members(String script) throws IOException {
    return Arrays.asList(new Gson().fromJson(array(script, "memberSearchIndex"), Member[].class));
  }

  private static String array(String script, String variable) throws IOException {
    if (script == null || !script.stripLeading().startsWith(variable + " = [")) {
      throw new IOException("Unexpected " + variable + " format");
    }
    int start = script.indexOf('[');
    int end = script.lastIndexOf(']');
    if (end <= start) throw new IOException("Incomplete " + variable + " index");
    String json = script.substring(start, end + 1);
    try {
      JsonParser.parseString(json).getAsJsonArray();
    } catch (RuntimeException ex) {
      throw new IOException("Invalid " + variable + " index", ex);
    }
    return json;
  }

  static Map<String, String> classUrls(String release, List<Type> types, List<Package> packages) {
    Map<String, String> modules = new HashMap<>();
    for (Package pkg : packages) {
      if (pkg.l != null && pkg.m != null) modules.put(pkg.l, pkg.m);
    }
    Map<String, String> urls = new LinkedHashMap<>();
    for (Type type : types) {
      if (type.p == null || type.l == null) continue;
      String module = type.m != null ? type.m : modules.get(type.p);
      if (module == null) continue;
      urls.put(type.p + "." + type.l, Versions.base(release) + module + "/"
          + type.p.replace('.', '/') + "/" + type.l + ".html");
    }
    return urls;
  }

  static List<Member> namedMethods(List<Member> members, String method) {
    List<Member> found = new ArrayList<>();
    for (Member entry : members) {
      if (entry.c == null || entry.p == null || entry.l == null) continue;
      int open = entry.l.indexOf('(');
      if (open < 0 || !entry.l.substring(0, open).equals(method)
          || method.equals(entry.c.substring(entry.c.lastIndexOf('.') + 1))) continue;
      found.add(entry);
    }
    found.sort(Comparator.comparing((Member m) -> m.className())
        .thenComparing(m -> m.l));
    return found;
  }

  static final class Type {
    String p, l, m, u;
  }

  static final class Package {
    String m, l, u;
  }

  static final class Member {
    String p, c, l, u, m;

    String className() {
      return p + "." + c;
    }

    String name() {
      int open = l.indexOf('(');
      return open < 0 ? l : l.substring(0, open);
    }

    String anchor() {
      return u == null ? l : u;
    }
  }
}

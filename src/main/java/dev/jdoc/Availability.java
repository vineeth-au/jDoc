package dev.jdoc;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class Availability {
  private final Documentation docs;

  Availability(Documentation docs) {
    this.docs = docs;
  }

  String classReport(String requested, String selected) throws IOException {
    List<String> found = new ArrayList<>();
    List<String> unknown = new ArrayList<>();
    for (String release : Versions.SUPPORTED) {
      if (release.equals(selected)) continue;
      Map<String, String> index = docs.cachedIndex(release);
      if (index == null) {
        unknown.add(release);
        continue;
      }
      for (String name : index.keySet()) {
        if (name.equals(requested) || name.endsWith("." + requested)) {
          found.add("Java " + release + ": " + name);
        }
      }
    }
    return report(found, unknown);
  }

  String memberReport(String className, String[] arguments, String selected) throws IOException {
    List<String> tokens = new ArrayList<>(Arrays.asList(arguments));
    tokens.removeIf(t -> t.equals("-i"));
    String name = tokens.isEmpty() || tokens.get(0).equals("-p")
        || tokens.get(0).equals("--params") ? null : tokens.get(0).replaceFirst("^-", "");
    List<String> found = new ArrayList<>();
    List<String> unknown = new ArrayList<>();
    for (String release : Versions.SUPPORTED) {
      if (release.equals(selected)) continue;
      Map<String, String> index = docs.cachedIndex(release);
      if (index == null) {
        unknown.add(release);
        continue;
      }
      if (!index.containsKey(className)) continue;
      DocModel cached = docs.cachedClassDoc(release, className);
      if (cached != null && cached.methods != null && cached.inherited != null
          && cached.fields != null) {
        List<DocModel.Method> matches = MemberLookup.select(cached, arguments);
        if (!matches.isEmpty()) {
          DocModel.Method match = matches.get(0);
          String owner = match.declaringType == null ? className : match.declaringType;
          boolean added = match.since != null && match.since.matches("[0-9]+")
              && Integer.parseInt(match.since) > Integer.parseInt(selected);
          String location = added ? "Added in Java " + match.since
              + (match.since.equals(release) ? "" : " (documented in Java " + release + ")")
              : "Java " + release;
          found.add(location + ": " + match.name + " documented on " + className
              + ", declared in " + owner);
          continue;
        }
      }
      List<SearchIndex.Member> members = docs.cachedMemberSearch(release);
      if (members == null || name == null) {
        unknown.add(release);
        continue;
      }
      SearchIndex.Member match = null;
      for (SearchIndex.Member entry : SearchIndex.namedMethods(members, name)) {
        if (entry.className().equals(className) && matchesParameters(entry, tokens)) {
          match = entry;
          break;
        }
      }
      if (match != null) {
        found.add("Java " + release + ": " + match.l + " documented on " + className
            + " (declaring type unverified; cache its class page for details)");
      } else {
        // Member indexes alone cannot rule out inherited members.
        unknown.add(release);
      }
    }
    return report(found, unknown);
  }

  private static boolean matchesParameters(SearchIndex.Member entry, List<String> tokens) {
    int option = tokens.indexOf("-p");
    if (option < 0) option = tokens.indexOf("--params");
    if (option < 0 || option + 1 >= tokens.size()) return true;
    String requested = String.join("", tokens.subList(option + 1, tokens.size()));
    String anchor = URLDecoder.decode(entry.anchor(), StandardCharsets.UTF_8);
    List<String> actual = JavadocParser.anchorTypes(anchor);
    if (requested.matches("[0-9]+")) return actual.size() == Integer.parseInt(requested);
    return actual.equals(Arrays.stream(requested.split(","))
        .map(JavadocParser::erase).toList());
  }

  private static String report(List<String> found, List<String> unknown) {
    StringBuilder result = new StringBuilder();
    if (found.isEmpty() && unknown.isEmpty()) {
      return "\nNot documented in the other cached supported releases.";
    }
    if (!found.isEmpty()) result.append("\nAvailable in other cached releases:");
    for (String value : found) result.append("\n  ").append(value);
    if (!unknown.isEmpty()) {
      result.append("\nCannot determine availability for Java ")
          .append(String.join(", ", unknown))
          .append(" from the local cache. Select a release to cache its API index/page.");
    }
    return result.toString();
  }
}

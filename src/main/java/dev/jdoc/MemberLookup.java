package dev.jdoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Selects documented members without fetching inherited detail pages. */
final class MemberLookup {
  private MemberLookup() {}

  static List<DocModel.Method> select(DocModel doc, String[] args) {
    List<String> tokens = new ArrayList<>(Arrays.asList(args));
    boolean includeInherited = tokens.removeIf(t -> t.equals("-i"));
    List<DocModel.Method> direct = doc.declaredMembers();
    if (tokens.isEmpty()) {
      List<DocModel.Method> result = new ArrayList<>(direct);
      if (includeInherited) {
        result.addAll(doc.inherited);
      }
      return result;
    }
    String name = null;
    if (!tokens.get(0).equals("-p") && !tokens.get(0).equals("--params")) {
      name = tokens.remove(0);
      if (name.startsWith("-")) {
        name = name.substring(1);
      }
    }
    final String requested = name;
    List<DocModel.Method> candidates = direct.stream()
        .filter(m -> requested == null || m.name.equals(requested)
            || (m.kind.equals("nested type") && m.name.endsWith("." + requested)))
        .collect(Collectors.toCollection(ArrayList::new));
    if (name != null || includeInherited) {
      for (DocModel.Method method : doc.inherited) {
        if (name == null || method.name.equals(name)) {
          candidates.add(method);
        }
      }
    }
    if (tokens.isEmpty()) {
      return name == null || candidates.isEmpty() ? candidates : candidates.subList(0, 1);
    }
    if (!tokens.remove(0).matches("-p|--params")) {
      throw new IllegalArgumentException("Expected -p/--params after the method name. See jdoc --help.");
    }
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("-p requires a parameter count or parameter types.");
    }
    String value = String.join("", tokens).trim();
    if (value.matches("[0-9]+")) {
      int count = Integer.parseInt(value);
      return candidates.stream().filter(m -> parameterized(m, doc)
          && m.parameterTypes.size() == count).toList();
    }
    if (name == null) {
      throw new IllegalArgumentException(
          "Parameter types require a method name; use e.g. jdoc String valueOf -p char-array,int,int.");
    }
    List<String> types = Arrays.stream(value.split(",", -1)).map(JavadocParser::erase).toList();
    return candidates.stream().filter(m -> parameterized(m, doc)
        && m.parameterTypes.stream().map(JavadocParser::erase).toList().equals(types)).toList();
  }

  private static boolean parameterized(DocModel.Method member, DocModel doc) {
    return "method".equals(member.kind) || doc.methods.isEmpty()
        && "constructor".equals(member.kind);
  }
}

package dev.jdoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Composable member-list search. Unknown operators are errors, not empty matches. */
final class MemberFilter {
  private final List<Predicate<DocModel.Method>> tests = new ArrayList<>();

  static MemberFilter parse(String query) {
    MemberFilter filter = new MemberFilter();
    if (query.isBlank()) return filter;
    String[] tokens = query.trim().split("\\s+");
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      if (token.equals(":static")) {
        filter.tests.add(m -> m.signature != null && m.signature.matches("(?s).*\\bstatic\\b.*"));
      } else if (token.equals(":deprecated")) {
        filter.tests.add(m -> m.deprecated);
      } else if (token.equals(":since")) {
        if (++i == tokens.length || !tokens[i].matches("[0-9]+")) {
          throw new IllegalArgumentException(":since needs a Java release number");
        }
        int version = Integer.parseInt(tokens[i]);
        filter.tests.add(m -> since(m.since) >= version);
      } else if (token.startsWith("#")) {
        if (!token.substring(1).matches("[0-9]+")) {
          throw new IllegalArgumentException("Use #<count>, for example #2");
        }
        int count = Integer.parseInt(token.substring(1));
        filter.tests.add(m -> (m.kind.equals("method") || m.kind.equals("constructor"))
            && m.parameterTypes.size() == count);
      } else if (token.startsWith("->")) {
        if (token.length() == 2) throw new IllegalArgumentException("Use -><return type>");
        String type = JavadocParser.erase(token.substring(2));
        filter.tests.add(m -> m.returnType != null && JavadocParser.erase(m.returnType).equals(type));
      } else if (token.startsWith(":")) {
        throw new IllegalArgumentException("Unknown filter: " + token);
      } else {
        String text = token.toLowerCase(Locale.ROOT);
        filter.tests.add(m -> m.signature != null
            && m.signature.toLowerCase(Locale.ROOT).contains(text));
      }
    }
    return filter;
  }

  boolean matches(DocModel.Method member) {
    return tests.stream().allMatch(test -> test.test(member));
  }

  private static int since(String value) {
    if (value == null) return -1;
    String major = value.startsWith("1.") ? value.substring(2) : value;
    return major.matches("[0-9]+") ? Integer.parseInt(major) : -1;
  }
}

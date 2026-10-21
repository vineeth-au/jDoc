package dev.jdoc;

import dev.jdoc.DocModel.Method;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavadocParser {

  static DocModel parse(Document page, String name, String url, String release) {
    DocModel doc = new DocModel();
    doc.schemaVersion = 3;
    doc.name = name;
    doc.sourceUrl = url;
    doc.release = release;
    Element classBlock = page.selectFirst("section.class-description div.block");
    doc.description = classBlock == null ? "" : classBlock.text().trim();
    Map<String, DocModel.Method> methodDetails = new LinkedHashMap<>();
    Map<String, DocModel.Method> constructorDetails = new LinkedHashMap<>();
    Map<String, DocModel.Method> fieldDetails = new LinkedHashMap<>();
    Map<String, DocModel.Method> constantDetails = new LinkedHashMap<>();
    String selector = "section.method-details section.detail:has(> h3), "
        + "section.constructor-details section.detail:has(> h3), "
        + "section.field-details section.detail:has(> h3), "
        + "section.constant-details section.detail:has(> h3)";
    for (Element item : page.select(selector)) {
      Element title = item.selectFirst("> h3");
      Element signature = item.selectFirst("div.member-signature");
      if (title == null || signature == null) {
        continue;
      }
      String methodName = title.text().trim();
      String declaration = signature.text().replace('\u00a0', ' ').replace("\u200b", "")
          .replaceAll("\\s+", " ").replaceAll("\\s+\\(", "(").trim();
      String anchor = item.id();
      if (anchor.isEmpty()) {
        continue;
      }
      DocModel.Method method = new Method();
      method.name = methodName;
      method.signature = declaration;
      method.anchor = anchor;
      method.declaringType = name;
      method.sourceUrl = url;
      boolean constructor = item.closest("section.constructor-details") != null;
      boolean field = item.closest("section.field-details") != null;
      boolean constant = item.closest("section.constant-details") != null;
      method.kind = constructor ? "constructor"
          : field ? "field" : constant ? "enum constant" : "method";
      if (constructor || method.kind.equals("method")) {
        method.parameterTypes = parameterTypes(declaration);
      }
      Element block = item.selectFirst("div.block");
      method.description = block == null ? "" : block.text().trim();
      StringBuilder notes = new StringBuilder();
      for (Element tag : item.select("dl > dt")) {
        String label = tag.text().trim();
        if (!label.isEmpty()) {
          notes.append(label).append('\n');
        }
        for (Element next = tag.nextElementSibling(); next != null && next.tagName().equals("dd");
            next = next.nextElementSibling()) {
          notes.append("  ").append(next.text().trim()).append('\n');
        }
      }
      method.details = notes.toString().trim();
      (constructor ? constructorDetails : field ? fieldDetails : constant ? constantDetails
          : methodDetails).put(anchor, method);
    }

    // The detail sections can be in source order. The summary is the user-visible order.
    order(page, "section.method-summary a[href]", methodDetails, doc.methods);
    order(page, "section.constructor-summary a[href]", constructorDetails, doc.constructors);
    order(page, "section.field-summary a[href]", fieldDetails, doc.fields);
    order(page, "section.constants-summary a[href]", constantDetails, doc.enumConstants);
    for (Element link : page.select("section.nested-class-summary a.type-name-link[href]")) {
      DocModel.Method nested = new Method();
      nested.name = link.text().trim();
      nested.signature = "Nested type " + nested.name;
      nested.kind = "nested type";
      nested.declaringType = name;
      nested.sourceUrl = link.attr("abs:href");
      Element summary = link.closest("div.col-second");
      if (summary != null && summary.nextElementSibling() != null) {
        nested.description = summary.nextElementSibling().text().trim();
      }
      doc.nestedTypes.add(nested);
    }
    inherited(page, doc);
    return doc;
  }

  private static void inherited(Document page, DocModel doc) {
    Map<String, Method> seen = new LinkedHashMap<>();
    for (Method declared : doc.methods) {
      seen.put(key(declared.name, declared.parameterTypes), declared);
    }
    List<Element> blocks = new ArrayList<>(page.select("section.method-summary div.inherited-list"));
    // Class declarations win over interface defaults, even if the page changes block order.
    blocks.sort(Comparator.comparingInt(block -> block.selectFirst("h3") != null
        && block.selectFirst("h3").text().contains("interface") ? 1 : 0));
    for (Element block : blocks) {
      Element title = block.selectFirst("h3[id^=methods-inherited-from-class-]");
      if (title == null) {
        continue;
      }
      String owner = title.id().substring("methods-inherited-from-class-".length());
      if (owner.equals("java.lang.Object")) {
        continue;
      }
      for (Element link : block.select("code a[href*='#']")) {
        String href = link.attr("abs:href");
        int hash = href.lastIndexOf('#');
        if (hash < 0) {
          continue;
        }
        String anchor = decode(href.substring(hash + 1));
        int open = anchor.indexOf('(');
        if (open < 0 || !anchor.endsWith(")")) {
          continue;
        }
        Method ref = new Method();
        ref.name = link.text().trim();
        ref.kind = "method";
        ref.inherited = true;
        ref.declaringType = owner;
        ref.anchor = anchor;
        ref.sourceUrl = href.substring(0, hash);
        ref.parameterTypes = anchorTypes(anchor);
        ref.signature = ref.name + "(" + String.join(", ", ref.parameterTypes) + ")";
        String key = key(ref.name, ref.parameterTypes);
        if (!seen.containsKey(key)) {
          seen.put(key, ref);
          doc.inherited.add(ref);
        }
      }
    }
  }

  static String key(String name, List<String> parameters) {
    return name + "(" + String.join(",", parameters.stream().map(JavadocParser::erase).toList())
        + ")";
  }

  static List<String> anchorTypes(String anchor) {
    int start = anchor.indexOf('(');
    if (start < 0 || !anchor.endsWith(")") || start == anchor.length() - 2) {
      return new ArrayList<>();
    }
    List<String> result = new ArrayList<>();
    for (String type : anchor.substring(start + 1, anchor.length() - 1).split(",")) {
      result.add(erase(type));
    }
    return result;
  }

  static String erase(String type) {
    StringBuilder plain = new StringBuilder();
    int depth = 0;
    for (char c : type.toCharArray()) {
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (depth == 0) {
        plain.append(c);
      }
    }
    return normalize(plain.toString());
  }

  private static void order(Document page, String summary, Map<String, DocModel.Method> details,
      List<DocModel.Method> output) {
    Set<String> seen = new HashSet<>();
    for (Element link : page.select(summary)) {
      String href = link.attr("href");
      int hash = href.indexOf('#');
      if (hash < 0) {
        continue;
      }
      String anchor = decode(href.substring(hash + 1));
      DocModel.Method method = details.get(anchor);
      if (method != null && link.text().trim().equals(method.name) && seen.add(anchor)) {
        output.add(method);
      }
    }
    for (Map.Entry<String, DocModel.Method> entry : details.entrySet()) {
      if (seen.add(entry.getKey())) {
        output.add(entry.getValue());
      }
    }
  }

  private static String decode(String anchor) {
    try {
      return URLDecoder.decode(anchor, StandardCharsets.UTF_8.name());
    } catch (java.io.UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }

  static List<String> parameterTypes(String signature) {
    int open = signature.indexOf('(');
    int close = signature.lastIndexOf(')');
    List<String> types = new ArrayList<>();
    if (open < 0 || close <= open + 1) {
      return types;
    }
    String arguments = signature.substring(open + 1, close);
    int generics = 0;
    int start = 0;
    for (int i = 0; i <= arguments.length(); i++) {
      if (i < arguments.length()) {
        char character = arguments.charAt(i);
        if (character == '<') {
          generics++;
        } else if (character == '>') {
          generics--;
        }
        if (character != ',' || generics != 0) {
          continue;
        }
      }
      String parameter = arguments.substring(start, i).trim();
      parameter = parameter.replaceAll("(?:@[\\w.]+\\s+|\\bfinal\\s+)", "");
      parameter = parameter.replaceAll("\\s+[\\w$]+(\\[\\])?$", "$1").trim();
      types.add(normalize(parameter));
      start = i + 1;
    }
    return types;
  }

  static String normalize(String type) {
    return type.trim().replaceAll("\\s+", "")
        .replace("-array", "[]").replace("...", "[]")
        .replaceAll("(?:[\\w$]+\\.)+([\\w$]+)", "$1");
  }

  private JavadocParser() {
  }
}

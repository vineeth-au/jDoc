package dev.jdoc;

import dev.jdoc.DocModel.Method;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavadocParser {

  static DocModel parse(Document page, String name, String url, String release) {
    DocModel doc = new DocModel();
    doc.schemaVersion = 2;
    doc.name = name;
    doc.sourceUrl = url;
    doc.release = release;
    Element classBlock = page.selectFirst(release.equals("8") || release.equals("11")
        ? "div.description div.block" : "section.class-description div.block");
    doc.description = classBlock == null ? "" : classBlock.text().trim();
    Map<String, DocModel.Method> methodDetails = new LinkedHashMap<>();
    Map<String, DocModel.Method> constructorDetails = new LinkedHashMap<>();
    boolean old = release.equals("8") || release.equals("11");
    String selector = old ? "div.details li.blockList:has(> h4):has(> pre)"
        : "section.method-details section.detail:has(> h3), section.constructor-details section.detail:has(> h3)";
    for (Element item : page.select(selector)) {
      Element title = item.selectFirst(old ? "> h4" : "> h3");
      Element signature = item.selectFirst(old ? "> pre" : "div.member-signature");
      if (title == null || signature == null) {
        continue;
      }
      String methodName = title.text().trim();
      String declaration = signature.text().replace('\u00a0', ' ').replace("\u200b", "")
          .replaceAll("\\s+", " ").trim();
      if (!declaration.contains("(")) {
        continue;
      }
      String anchor = old ? anchorFromNamedElement(item) : item.id();
      if (anchor.isEmpty()) {
        continue;
      }
      DocModel.Method method = new Method();
      method.name = methodName;
      method.signature = declaration;
      method.anchor = anchor;
      method.parameterTypes = parameterTypes(declaration);
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
      String simple = name.substring(name.lastIndexOf('.') + 1);
      (methodName.equals(simple) ? constructorDetails : methodDetails).put(anchor, method);
    }

    // The detail sections can be in source order. The summary is the user-visible order.
    order(page, old ? "table.memberSummary a[href]" : "section.method-summary a[href]",
        methodDetails, doc.methods);
    order(page, old ? "table.memberSummary a[href]" : "section.constructor-summary a[href]",
        constructorDetails, doc.constructors);
    return doc;
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

  private static String anchorFromNamedElement(Element item) {
    Element named = item.selectFirst("a[name]");
    if (named != null) {
      return named.attr("name");
    }
    // Javadoc 8/11 place the overload anchor immediately before the method's <ul>.
    Element previous = item.parent() == null ? null : item.parent().previousElementSibling();
    if (previous != null && previous.tagName().equals("a")) {
      return previous.hasAttr("name") ? previous.attr("name") : previous.id();
    }
    return "";
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

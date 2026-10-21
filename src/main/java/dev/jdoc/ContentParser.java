package dev.jdoc;

import dev.jdoc.DocModel.Block;
import dev.jdoc.DocModel.Inline;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** Turns Javadoc prose into paragraphs, code blocks, lists, and typed links. */
final class ContentParser {
  private ContentParser() {}

  static List<Block> parse(Element root, String owner, String release) {
    List<Block> result = new ArrayList<>();
    if (root != null) flow(root.childNodes(), result, owner, release);
    return result;
  }

  static Block label(String text) {
    Block block = new Block();
    block.kind = "label";
    Inline inline = new Inline();
    inline.text = text;
    block.inlines.add(inline);
    return block;
  }

  private static void flow(List<Node> nodes, List<Block> result, String owner, String release) {
    List<Inline> pending = new ArrayList<>();
    for (Node node : nodes) {
      if (node instanceof TextNode text) {
        appendText(pending, text.getWholeText());
      } else if (node instanceof Element element) {
        String tag = element.normalName();
        if (tag.equals("p") || tag.equals("div") || tag.equals("section")) {
          flush(pending, result);
          flow(element.childNodes(), result, owner, release);
        } else if (tag.equals("pre")) {
          flush(pending, result);
          Block code = new Block();
          code.kind = "code";
          code.code = element.wholeText().replace("\r\n", "\n").stripTrailing();
          for (Element link : element.select("a[href]")) {
            Inline ref = new Inline();
            ref.text = link.text();
            target(ref, link.attr("abs:href"), owner, release);
            code.codeLinks.add(ref);
          }
          if (!code.code.isBlank()) result.add(code);
        } else if (tag.equals("ul") || tag.equals("ol")) {
          flush(pending, result);
          Block list = new Block();
          list.kind = tag.equals("ol") ? "ordered-list" : "list";
          for (Element item : element.children()) {
            if (!item.normalName().equals("li")) continue;
            List<Block> contents = new ArrayList<>();
            flow(item.childNodes(), contents, owner, release);
            list.items.add(contents);
          }
          if (!list.items.isEmpty()) result.add(list);
        } else if (tag.equals("br")) {
          appendText(pending, "\n");
        } else {
          inline(element, pending, owner, release);
        }
      }
    }
    flush(pending, result);
  }

  private static void inline(Node node, List<Inline> output, String owner, String release) {
    if (node instanceof TextNode text) {
      appendText(output, text.getWholeText());
    } else if (node instanceof Element element) {
      if (element.normalName().equals("a") && element.hasAttr("href")) {
        Inline link = new Inline();
        link.text = element.text();
        target(link, element.attr("abs:href"), owner, release);
        output.add(link);
      } else {
        for (Node child : element.childNodes()) inline(child, output, owner, release);
      }
    }
  }

  private static void appendText(List<Inline> inlines, String value) {
    String text = value.replace('\u00a0', ' ').replaceAll("[\\t\\r\\n ]+", " ");
    if (text.isEmpty()) return;
    if (!inlines.isEmpty()) {
      Inline last = inlines.get(inlines.size() - 1);
      if (last.targetClass == null && last.externalUrl == null) {
        last.text += text;
        return;
      }
    }
    Inline inline = new Inline();
    inline.text = text;
    inlines.add(inline);
  }

  private static void flush(List<Inline> inlines, List<Block> output) {
    if (inlines.isEmpty()) return;
    inlines.get(0).text = inlines.get(0).text.stripLeading();
    inlines.get(inlines.size() - 1).text = inlines.get(inlines.size() - 1).text.stripTrailing();
    if (inlines.stream().anyMatch(i -> !i.text.isBlank())) {
      Block paragraph = new Block();
      paragraph.kind = "paragraph";
      paragraph.inlines.addAll(inlines);
      output.add(paragraph);
    }
    inlines.clear();
  }

  private static void target(Inline link, String href, String owner, String release) {
    String base = Versions.base(release);
    if (href.isEmpty()) return;
    URI uri;
    try {
      uri = URI.create(href);
    } catch (IllegalArgumentException invalid) {
      return;
    }
    String page = href.split("#", 2)[0];
    String fragment = uri.getRawFragment();
    if (fragment != null) {
      link.targetAnchor = URLDecoder.decode(fragment, StandardCharsets.UTF_8);
    }
    if (page.startsWith(base) && page.endsWith(".html")) {
      String relative = page.substring(base.length());
      String[] parts = relative.split("/");
      if (parts.length > 1 && parts[0].contains(".")) {
        relative = relative.substring(parts[0].length() + 1);
      }
      if (relative.contains("/") && !relative.contains("-summary")
          && !relative.contains("-list") && !relative.startsWith("index")) {
        link.targetClass = relative.substring(0, relative.length() - 5)
            .replace('/', '.');
        return;
      }
    }
    if (page.equals(base + owner.replace('.', '/') + ".html")) {
      link.targetClass = owner;
    } else {
      link.targetAnchor = null;
      link.externalUrl = href;
    }
  }
}

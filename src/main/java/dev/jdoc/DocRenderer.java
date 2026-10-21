package dev.jdoc;

import dev.jdoc.DocModel.Block;
import dev.jdoc.DocModel.Inline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shared plain-text layout for terminal details and redirected output. */
final class DocRenderer {
  private DocRenderer() {}

  static List<String> methodLines(DocModel.Method member, int width, String fallbackSource) {
    List<String> lines = new ArrayList<>();
    MethodBrowser.wrap(lines, member.signature, width, "");
    String labels = labels(member.since, member.deprecated, member.deprecatedSince, member.preview);
    if (!labels.isEmpty()) lines.add(labels);
    lines.add("");
    if (member.body != null && !member.body.isEmpty()) {
      blocks(lines, member.body, width, "");
    } else {
      MethodBrowser.wrap(lines, member.description, width, "");
      if (member.details != null && !member.details.isEmpty()) {
        lines.add("");
        lines.addAll(Arrays.asList(member.details.split("\n")));
      }
    }
    trimEmpty(lines);
    lines.add("");
    String source = member.sourceUrl == null ? fallbackSource : member.sourceUrl;
    if (source != null) {
      String address = "Source: " + source + (member.anchor == null ? "" : "#" + member.anchor);
      if (width >= 100) lines.add(address);
      else MethodBrowser.wrap(lines, address, width, "");
    }
    return lines;
  }

  static List<String> classLines(DocModel doc, int width) {
    List<String> lines = new ArrayList<>();
    String labels = labels(doc.since, doc.deprecated, doc.deprecatedSince, doc.preview);
    if (!labels.isEmpty()) lines.add(labels);
    if (doc.classBody != null && !doc.classBody.isEmpty()) {
      blocks(lines, doc.classBody, width, "");
    } else {
      MethodBrowser.wrap(lines, doc.description, width, "");
    }
    trimEmpty(lines);
    return lines;
  }

  static String summaryLabels(DocModel.Method member) {
    return labels(member.since, member.deprecated, member.deprecatedSince, member.preview);
  }

  private static String labels(String since, boolean deprecated, String deprecatedSince,
      boolean preview) {
    List<String> labels = new ArrayList<>();
    if (since != null && !since.isBlank()) labels.add("Since: " + since);
    if (deprecated) labels.add("Deprecated"
        + (deprecatedSince == null || deprecatedSince.isBlank() ? "" : " since " + deprecatedSince));
    if (preview) labels.add("Preview");
    return String.join("  ·  ", labels);
  }

  private static void blocks(List<String> lines, List<Block> content, int width, String indent) {
    for (Block block : content) {
      if (block == null || block.kind == null) continue;
      switch (block.kind) {
        case "paragraph" -> {
          if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()
              && !lines.get(lines.size() - 1).endsWith(":")) lines.add("");
          MethodBrowser.wrap(lines, inlineText(block.inlines), width, indent);
        }
        case "label" -> {
          if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
          MethodBrowser.wrap(lines, inlineText(block.inlines), width, indent);
        }
        case "note" -> {
          if (!block.items.isEmpty()) blocks(lines, block.items.get(0), width, indent + "  ");
        }
        case "code" -> {
          if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
          if (block.code != null) {
            for (String line : block.code.split("\n", -1)) {
              String display = indent + "  " + line;
              if (width < 100) {
                int lineWidth = Math.max(12, width);
                while (display.length() > lineWidth) {
                  lines.add(display.substring(0, lineWidth - 1) + "↪");
                  display = indent + "    " + display.substring(lineWidth - 1);
                }
              }
              lines.add(display);
            }
          }
        }
        case "list", "ordered-list" -> {
          if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
          for (int i = 0; i < block.items.size(); i++) {
            List<String> item = new ArrayList<>();
            blocks(item, block.items.get(i), Math.max(12, width - indent.length() - 2), "");
            String bullet = block.kind.equals("ordered-list") ? (i + 1) + ". " : "- ";
            if (item.isEmpty()) {
              lines.add(indent + bullet);
            } else {
              lines.add(indent + bullet + item.get(0));
              for (int j = 1; j < item.size(); j++) {
                lines.add(item.get(j).isEmpty() ? "" : indent + "  " + item.get(j));
              }
            }
          }
        }
        default -> { }
      }
    }
  }

  static String inlineText(List<Inline> inlines) {
    if (inlines == null) return "";
    StringBuilder text = new StringBuilder();
    for (Inline part : inlines) {
      if (part == null || part.text == null) continue;
      text.append(part.text);
      if (part.targetClass != null) {
        String target = part.targetClass
            + (part.targetAnchor == null ? "" : "#" + part.targetAnchor);
        if (!part.text.trim().equals(target)) text.append(" [").append(target).append(']');
      } else if (part.externalUrl != null) {
        text.append(" [").append(part.externalUrl).append(']');
      }
    }
    return text.toString().trim();
  }

  private static void trimEmpty(List<String> lines) {
    while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
  }
}

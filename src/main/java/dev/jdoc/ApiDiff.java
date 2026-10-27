package dev.jdoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ApiDiff {
  private static final List<String> KINDS = List.of("method", "constructor", "field",
      "enum constant", "nested type");

  private ApiDiff() {}

  static String compare(String name, String from, DocModel before, String to, DocModel after) {
    StringBuilder out = new StringBuilder(name + " · Java " + from + " → " + to + "\n");
    if (before == null && after == null) {
      return out.append("Type is not documented in either release.\n").toString();
    }
    if (before == null || after == null) {
      DocModel present = before == null ? after : before;
      return out.append(before == null ? "Added type" : "Removed type")
          .append(present.preview ? " (Preview)" : "").append("\n").toString();
    }
    String typeChange = status(before.deprecated, before.preview, before.deprecatedSince,
        after.deprecated, after.preview, after.deprecatedSince);
    if (!typeChange.isEmpty()) out.append("Type status: ").append(typeChange).append("\n");
    Map<String, DocModel.Method> oldMembers = effective(before);
    Map<String, DocModel.Method> newMembers = effective(after);
    boolean changed = !typeChange.isEmpty();
    boolean removed = false;
    for (String kind : KINDS) {
      List<String> lines = new ArrayList<>();
      for (Map.Entry<String, DocModel.Method> entry : newMembers.entrySet()) {
        DocModel.Method member = entry.getValue();
        if (!kind.equals(member.kind)) continue;
        DocModel.Method previous = oldMembers.get(entry.getKey());
        if (previous == null) {
          lines.add("  + " + display(member));
        } else {
          String difference = changes(previous, member);
          if (!difference.isEmpty()) lines.add("  ~ " + display(member) + " — " + difference);
        }
      }
      for (Map.Entry<String, DocModel.Method> entry : oldMembers.entrySet()) {
        if (kind.equals(entry.getValue().kind) && !newMembers.containsKey(entry.getKey())) {
          lines.add("  - " + display(entry.getValue()));
          removed = true;
        }
      }
      if (!lines.isEmpty()) {
        changed = true;
        out.append("\n").append(heading(kind)).append("\n");
        lines.forEach(line -> out.append(line).append('\n'));
      }
    }
    if (!changed) out.append("No API member or status changes.\n");
    else if (!removed) out.append("\nNo members removed.\n");
    return out.toString();
  }

  private static Map<String, DocModel.Method> effective(DocModel doc) {
    Map<String, DocModel.Method> members = new LinkedHashMap<>();
    for (DocModel.Method member : doc.declaredMembers()) members.putIfAbsent(key(member), member);
    for (DocModel.Method member : doc.inherited) members.putIfAbsent(key(member), member);
    return members;
  }

  private static String key(DocModel.Method member) {
    return member.kind + ":" + (member.kind.equals("method")
        || member.kind.equals("constructor")
        ? JavadocParser.key(member.name, member.parameterTypes) : member.name);
  }

  private static String display(DocModel.Method member) {
    StringBuilder value = new StringBuilder(MethodBrowser.listSignature(member));
    if (member.since != null && !member.since.isBlank()) value.append("  Since: ").append(member.since);
    if (member.preview) value.append("  [Preview]");
    if (member.deprecated) value.append("  [Deprecated]");
    if (member.inherited) value.append("  (inherited from ").append(member.declaringType).append(')');
    return value.toString();
  }

  private static String changes(DocModel.Method old, DocModel.Method current) {
    List<String> changed = new ArrayList<>();
    String status = status(old.deprecated, old.preview, old.deprecatedSince,
        current.deprecated, current.preview, current.deprecatedSince);
    if (!status.isEmpty()) changed.add(status);
    if (!Objects.equals(old.declaringType, current.declaringType)) {
      changed.add("declaring type " + old.declaringType + " → " + current.declaringType);
    }
    if (!old.inherited && !current.inherited &&
        !declaration(old).equals(declaration(current))) {
      changed.add("declaration changed");
    }
    return String.join("; ", changed);
  }

  private static String declaration(DocModel.Method member) {
    String signature = member.signature == null ? "" : member.signature;
    if (!member.kind.equals("method") && !member.kind.equals("constructor")) {
      return signature.replaceAll("\\s+", " ").trim();
    }
    int open = signature.indexOf('(');
    int close = signature.lastIndexOf(')');
    if (open < 0 || close < open) return signature;
    return signature.substring(0, open).replaceAll("\\s+", " ").trim()
        + "(" + String.join(",", member.parameterTypes) + ")"
        + signature.substring(close + 1).replaceAll("\\s+", " ").trim();
  }

  private static String status(boolean oldDeprecated, boolean oldPreview, String oldSince,
      boolean newDeprecated, boolean newPreview, String newSince) {
    List<String> changes = new ArrayList<>();
    if (oldPreview != newPreview) changes.add(newPreview ? "now Preview" : "no longer Preview");
    if (oldDeprecated != newDeprecated) {
      changes.add(newDeprecated ? "now Deprecated" : "no longer Deprecated");
    }
    if (!Objects.equals(oldSince, newSince) && (oldSince != null || newSince != null)) {
      changes.add("deprecated-since " + (oldSince == null ? "unknown" : oldSince)
          + " → " + (newSince == null ? "unknown" : newSince));
    }
    return String.join("; ", changes);
  }

  private static String heading(String kind) {
    return switch (kind) {
      case "method" -> "Methods";
      case "constructor" -> "Constructors";
      case "field" -> "Fields";
      case "enum constant" -> "Enum Constants";
      default -> "Nested Types";
    };
  }
}

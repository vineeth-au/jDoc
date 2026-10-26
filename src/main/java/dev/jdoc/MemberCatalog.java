package dev.jdoc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The five member tabs, filtered overload groups, and per-tab selection state. */
final class MemberCatalog {
  enum Tab { METHODS, CONSTRUCTORS, FIELDS, ENUM_CONSTANTS, NESTED_TYPES }

  private final DocModel doc;
  private final Map<Tab, View> views = new EnumMap<>(Tab.class);
  private Tab tab;
  private boolean inherited;

  MemberCatalog(DocModel doc) {
    this.doc = doc;
    for (Tab value : Tab.values()) views.put(value, new View());
    tab = doc.methods.isEmpty() && !doc.constructors.isEmpty() ? Tab.CONSTRUCTORS : Tab.METHODS;
    for (Tab value : Tab.values()) rebuild(value);
  }

  Tab tab() { return tab; }
  View view() { return views.get(tab); }
  boolean inherited() { return inherited; }
  int inheritedCount() { return doc.inherited.size(); }

  void selectTab(int index) {
    if (index >= 0 && index < Tab.values().length) tab = Tab.values()[index];
  }

  void nextTab() { tab = Tab.values()[(tab.ordinal() + 1) % Tab.values().length]; }

  int count(Tab kind) { return members(kind).size(); }

  void toggleInherited() {
    inherited = !inherited;
    rebuild(Tab.METHODS);
  }

  void filter(String query) {
    view().query = query;
    rebuild(tab);
  }

  void expand(Row group) {
    if (group == null || !group.group) return;
    View view = view();
    view.expanded.add(group.groupKey);
    rebuild(tab);
    for (int i = 0; i < view.rows.size(); i++) {
      Row row = view.rows.get(i);
      if (!row.group && group.groupKey.equals(row.groupKey)) {
        view.cursor = i;
        break;
      }
    }
  }

  void collapse(Row row) {
    if (row == null || row.groupKey == null) return;
    View view = view();
    view.expanded.remove(row.groupKey);
    rebuild(tab);
    for (int i = 0; i < view.rows.size(); i++) {
      if (view.rows.get(i).group && row.groupKey.equals(view.rows.get(i).groupKey)) {
        view.cursor = i;
        break;
      }
    }
  }

  private List<DocModel.Method> members(Tab kind) {
    return switch (kind) {
      case METHODS -> {
        List<DocModel.Method> methods = new ArrayList<>(doc.methods);
        if (inherited) methods.addAll(doc.inherited);
        yield methods;
      }
      case CONSTRUCTORS -> doc.constructors;
      case FIELDS -> doc.fields;
      case ENUM_CONSTANTS -> doc.enumConstants;
      case NESTED_TYPES -> doc.nestedTypes;
    };
  }

  private void rebuild(Tab kind) {
    View view = views.get(kind);
    Row previous = view.selected();
    MemberFilter filter;
    try {
      filter = MemberFilter.parse(view.query);
      view.error = null;
    } catch (IllegalArgumentException ex) {
      view.error = ex.getMessage();
      view.rows = new ArrayList<>();
      view.cursor = 0;
      view.top = 0;
      return;
    }
    List<DocModel.Method> matches = members(kind).stream().filter(filter::matches).toList();
    List<Row> rows = new ArrayList<>();
    if (kind == Tab.METHODS && view.query.isEmpty()) {
      rows.add(new Row(null, false, null, 1, false)); // Class description
    }
    if (kind == Tab.METHODS || kind == Tab.CONSTRUCTORS) {
      Map<String, List<DocModel.Method>> grouped = new LinkedHashMap<>();
      for (DocModel.Method method : matches) {
        grouped.computeIfAbsent(method.name, ignored -> new ArrayList<>()).add(method);
      }
      for (Map.Entry<String, List<DocModel.Method>> entry : grouped.entrySet()) {
        String key = kind + ":" + entry.getKey();
        List<DocModel.Method> overloads = entry.getValue();
        if (overloads.size() == 1) {
          rows.add(new Row(overloads.get(0), false, null, 1, false));
        } else {
          boolean expanded = view.expanded.contains(key);
          rows.add(new Row(overloads.get(0), true, key, overloads.size(), expanded));
          if (expanded) {
            for (DocModel.Method method : overloads) {
              rows.add(new Row(method, false, key, 1, false));
            }
          }
        }
      }
    } else {
      for (DocModel.Method method : matches) rows.add(new Row(method, false, null, 1, false));
    }
    view.rows = rows;
    view.cursor = 0;
    view.top = 0;
    if (previous != null) {
      for (int i = 0; i < rows.size(); i++) {
        Row row = rows.get(i);
        if (row.group == previous.group && row.member == previous.member
            && java.util.Objects.equals(row.groupKey, previous.groupKey)) {
          view.cursor = i;
          break;
        }
      }
    }
  }

  static final class View {
    String query = "";
    String error;
    List<Row> rows = new ArrayList<>();
    Set<String> expanded = new HashSet<>();
    int cursor;
    int top;

    Row selected() { return rows.isEmpty() ? null : rows.get(cursor); }
    void move(int delta) {
      if (!rows.isEmpty()) cursor = Math.max(0, Math.min(rows.size() - 1, cursor + delta));
    }
    void keepVisible(int page) {
      if (cursor < top) top = cursor;
      if (cursor >= top + page) top = cursor - page + 1;
    }
  }

  static final class Row {
    final DocModel.Method member;
    final boolean group;
    final String groupKey;
    final int count;
    final boolean expanded;

    Row(DocModel.Method member, boolean group, String groupKey, int count, boolean expanded) {
      this.member = member;
      this.group = group;
      this.groupKey = groupKey;
      this.count = count;
      this.expanded = expanded;
    }

    String name() {
      return description() ? "Class description"
          : group ? groupKey.substring(groupKey.indexOf(':') + 1) : member.name;
    }

    boolean description() { return !group && member == null; }
  }
}

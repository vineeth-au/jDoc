package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MemberCatalogTest {
  private static DocModel.Method method(String name, String signature) {
    DocModel.Method m = new DocModel.Method();
    m.name = name;
    m.signature = signature;
    return m;
  }

  @Test
  void overloadsExpandAndCollapseWithoutLosingSelection() {
    DocModel doc = new DocModel();
    doc.methods.add(method("valueOf", "String valueOf(boolean b)"));
    doc.methods.add(method("repeat", "String repeat(int n)"));
    doc.methods.add(method("valueOf", "String valueOf(char[] chars)"));
    MemberCatalog catalog = new MemberCatalog(doc);
    assertEquals(3, catalog.view().rows.size());
    assertTrue(catalog.view().selected().description());
    catalog.view().move(100);
    assertEquals(2, catalog.view().cursor);
    catalog.view().keepVisible(1);
    assertEquals(2, catalog.view().top);
    catalog.view().move(-100);
    catalog.view().keepVisible(1);
    assertEquals(0, catalog.view().cursor);
    assertEquals(0, catalog.view().top);
    catalog.view().move(1);
    MemberCatalog.Row group = catalog.view().selected();
    assertTrue(group.group);
    assertEquals(2, group.count);
    catalog.expand(group);
    assertEquals(5, catalog.view().rows.size());
    assertEquals("String valueOf(boolean b)", catalog.view().selected().member.signature);
    catalog.collapse(catalog.view().selected());
    assertEquals(3, catalog.view().rows.size());
    assertTrue(catalog.view().selected().group);
  }

  @Test
  void filteringBeforeGroupingRetainsOnlyMatchingOverloadsAndTabQueries() {
    DocModel doc = new DocModel();
    doc.methods.add(method("valueOf", "String valueOf(boolean b)"));
    doc.methods.add(method("valueOf", "String valueOf(char[] chars)"));
    DocModel.Method field = method("COUNT", "static int COUNT");
    field.kind = "field";
    doc.fields.add(field);
    MemberCatalog catalog = new MemberCatalog(doc);
    catalog.filter("char[]");
    assertEquals(1, catalog.view().rows.size());
    assertFalse(catalog.view().selected().group);
    catalog.selectTab(2);
    catalog.filter(":static");
    assertEquals(1, catalog.view().rows.size());
    catalog.selectTab(0);
    assertEquals("char[]", catalog.view().query);
    catalog.filter(":bad");
    assertTrue(catalog.view().rows.isEmpty());
    assertTrue(catalog.view().error.contains("Unknown filter"));
  }

  @Test
  void methodsDefaultToConstructorsWhenNoDirectMethodsAndInheritedIsToggleable() {
    DocModel doc = new DocModel();
    DocModel.Method ctor = method("Exception", "public Exception()");
    ctor.kind = "constructor";
    doc.constructors.add(ctor);
    doc.inherited.add(method("stream", "stream()"));
    MemberCatalog catalog = new MemberCatalog(doc);
    assertEquals(MemberCatalog.Tab.CONSTRUCTORS, catalog.tab());
    catalog.selectTab(0);
    assertTrue(catalog.view().selected().description());
    catalog.toggleInherited();
    catalog.view().move(1);
    assertEquals("stream", catalog.view().selected().member.name);
  }
}

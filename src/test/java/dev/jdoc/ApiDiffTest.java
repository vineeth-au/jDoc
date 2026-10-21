package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiDiffTest {
  private static DocModel.Method member(String kind, String name, String signature, String... types) {
    DocModel.Method m = new DocModel.Method();
    m.kind = kind;
    m.name = name;
    m.signature = signature;
    m.parameterTypes = List.of(types);
    m.declaringType = "demo.Widget";
    return m;
  }

  @Test
  void allMemberKindsAppearOnlyWhenChanged() {
    DocModel before = new DocModel();
    DocModel after = new DocModel();
    before.constructors.add(member("constructor", "Widget", "public Widget()"));
    after.methods.add(member("method", "parse", "public String parse(String input)", "String"));
    after.fields.add(member("field", "DEFAULT_TIMEOUT", "public static final int DEFAULT_TIMEOUT"));
    after.nestedTypes.add(member("nested type", "Widget.Options", "Nested type Widget.Options"));
    String diff = ApiDiff.compare("demo.Widget", "17", before, "21", after);
    assertTrue(diff.contains("Methods\n  + parse(String input)"));
    assertTrue(diff.contains("Constructors\n  - Widget()"));
    assertTrue(diff.contains("Fields\n  + DEFAULT_TIMEOUT"));
    assertTrue(diff.contains("Nested Types\n  + Widget.Options"));
    assertFalse(diff.contains("Enum Constants"));
  }

  @Test
  void erasedIdentityAvoidsFalseAddsAndReportsStatusAndDeclarationChanges() {
    DocModel before = new DocModel();
    DocModel after = new DocModel();
    DocModel.Method old = member("method", "take", "public String take(List<String> values)",
        "List<String>");
    DocModel.Method current = member("method", "take", "public Object take(List<Integer> input)",
        "java.util.List<java.lang.Integer>");
    current.preview = true;
    after.methods.add(current);
    before.methods.add(old);
    String diff = ApiDiff.compare("demo.Widget", "17", before, "21", after);
    assertTrue(diff.contains("~ take(List<Integer> input)"));
    assertTrue(diff.contains("now Preview"));
    assertTrue(diff.contains("declaration changed"));
    assertFalse(diff.contains("+ take("));
    assertFalse(diff.contains("- take("));
  }

  @Test
  void inheritedEffectiveMethodsAndPreviewTypeRemovalAreVisible() {
    DocModel before = new DocModel();
    DocModel after = new DocModel();
    DocModel.Method inherited = member("method", "getFirst", "getFirst()");
    inherited.inherited = true;
    inherited.declaringType = "demo.Sequenced";
    after.inherited.add(inherited);
    assertTrue(ApiDiff.compare("demo.Widget", "17", before, "21", after)
        .contains("+ getFirst()  (inherited from demo.Sequenced)"));
    before.preview = true;
    assertTrue(ApiDiff.compare("demo.PreviewType", "21", before, "25", null)
        .contains("Removed type (Preview)"));
  }

  @Test
  void parameterNamesDoNotCountAsApiChangesButGenericTypesDo() {
    DocModel before = new DocModel();
    DocModel after = new DocModel();
    before.methods.add(member("method", "take", "public void take(List<String> values)",
        "List<String>"));
    after.methods.add(member("method", "take", "public void take(List<String> input)",
        "List<String>"));
    assertTrue(ApiDiff.compare("demo.Widget", "17", before, "21", after)
        .contains("No API member or status changes."));
    after.methods.get(0).parameterTypes = List.of("List<Integer>");
    assertTrue(ApiDiff.compare("demo.Widget", "17", before, "21", after)
        .contains("declaration changed"));
  }
}

package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocRendererTest {
  @Test
  void codeBlocksWrapWithoutDiscardingTextAndLegacyPagesStillRender() {
    DocModel.Method member = new DocModel.Method();
    member.signature = "public void example()";
    member.description = "Older description.";
    member.details = "Returns:\n  a value";
    assertTrue(String.join("\n", DocRenderer.methodLines(member, 60, "url"))
        .contains("Older description."));

    DocModel.Block code = new DocModel.Block();
    code.kind = "code";
    code.code = "if (ready) {\n  veryLongMethodCallWithSeveralArguments(alpha, beta, gamma);\n}";
    member.body = List.of(code);
    List<String> lines = DocRenderer.methodLines(member, 30, "url");
    assertTrue(lines.stream().anyMatch(s -> s.contains("if (ready) {")));
    assertTrue(lines.stream().anyMatch(s -> s.endsWith("↪")));
    assertFalse(lines.stream().anyMatch(s -> s.contains("Older description.")));
  }

  @Test
  void metadataLabelsNeverInventAnUnknownSinceVersion() {
    DocModel.Method member = new DocModel.Method();
    member.signature = "public void example()";
    member.deprecated = true;
    member.deprecatedSince = "21";
    member.preview = true;
    String rendered = String.join("\n", DocRenderer.methodLines(member, 80, "url"));
    assertTrue(rendered.contains("Deprecated since 21"));
    assertTrue(rendered.contains("Preview"));
    assertFalse(rendered.contains("Since: 1.0"));
  }
}

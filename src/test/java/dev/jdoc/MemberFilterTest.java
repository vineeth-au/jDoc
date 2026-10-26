package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MemberFilterTest {
  private static DocModel.Method method() {
    DocModel.Method m = new DocModel.Method();
    m.kind = "method";
    m.signature = "public static String valueOf(char[] chars, int offset)";
    m.returnType = "java.lang.String";
    m.parameterTypes = java.util.List.of("char[]", "int");
    m.since = "21";
    m.deprecated = true;
    return m;
  }

  @Test
  void operatorsComposeWithCaseInsensitiveText() {
    DocModel.Method method = method();
    assertTrue(MemberFilter.parse("VALUEOF :static #2 :since 17 :deprecated ->String")
        .matches(method));
    assertFalse(MemberFilter.parse("#1").matches(method));
    assertFalse(MemberFilter.parse("->int").matches(method));
    assertFalse(MemberFilter.parse(":since 25").matches(method));
  }

  @Test
  void unknownSinceIsNotMistakenForTheFirstRelease() {
    DocModel.Method method = method();
    method.since = null;
    assertFalse(MemberFilter.parse(":since 17").matches(method));
    method.since = "1.8";
    assertFalse(MemberFilter.parse(":since 11").matches(method));
  }

  @Test
  void malformedOperatorsShowAnError() {
    assertThrows(IllegalArgumentException.class, () -> MemberFilter.parse(":unknown"));
    assertThrows(IllegalArgumentException.class, () -> MemberFilter.parse(":since"));
    assertThrows(IllegalArgumentException.class, () -> MemberFilter.parse("#many"));
    assertThrows(IllegalArgumentException.class, () -> MemberFilter.parse("->"));
  }
}

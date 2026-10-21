package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberLookupTest {
  private static DocModel fixture(String release, String type) throws IOException {
    String relative = "fixtures/" + release + "/fixture/api/" + type + ".html";
    try (InputStream input = MemberLookupTest.class.getClassLoader().getResourceAsStream(relative)) {
      String url = Versions.base(release) + "fixture/api/" + type + ".html";
      return JavadocParser.parse(Jsoup.parse(input, "UTF-8", url),
          "fixture.api." + type, url, release);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void namedInheritedLookupWorksWithoutListingInheritedMethods(String release) throws IOException {
    DocModel doc = fixture(release, "Sequence");
    assertEquals("fixture.api.SequenceView",
        MemberLookup.select(doc, new String[]{"-stream"}).get(0).declaringType);
    assertEquals("fixture.api.BaseSequence",
        MemberLookup.select(doc, new String[]{"containsAll", "-p", "Collection"})
            .get(0).declaringType);
    assertEquals(List.of("add"), MemberLookup.select(doc, new String[]{"-p", "1"})
        .stream().map(m -> m.name).toList());
    assertTrue(MemberLookup.select(doc, new String[]{"-p", "1", "-i"})
        .stream().anyMatch(m -> m.name.equals("containsAll")));
    assertFalse(MemberLookup.select(doc, new String[]{})
        .stream().anyMatch(m -> m.inherited));
    assertTrue(MemberLookup.select(doc, new String[]{"-i"})
        .stream().anyMatch(m -> m.inherited));
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void fieldsEnumConstantsNestedTypesAndOverloadsCanBeSelected(String release) throws IOException {
    DocModel text = fixture(release, "Text");
    assertEquals("field", MemberLookup.select(text, new String[]{"MAX_VALUE"}).get(0).kind);
    assertEquals("valueOf(boolean)", MemberLookup.select(text, new String[]{"-valueOf"})
        .get(0).anchor);
    assertEquals("valueOf(char[],int,int)", MemberLookup.select(text,
        new String[]{"valueOf", "-p", "char-array,int,int"}).get(0).anchor);
    assertEquals("enum constant", MemberLookup.select(fixture(release, "Unit"),
        new String[]{"FIRST"}).get(0).kind);
    assertEquals("nested type", MemberLookup.select(fixture(release, "Dictionary"),
        new String[]{"Entry"}).get(0).kind);
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void normalizedInheritedAnchorKeysEraseGenericArguments(String release) throws IOException {
    assertEquals("List", JavadocParser.erase("java.util.List<java.lang.String>"));
    assertEquals("Collection[]", JavadocParser.erase("java.util.Collection<String>..."));
    DocModel doc = fixture(release, "Sequence");
    assertEquals(List.of("Collection"), doc.inherited.stream()
        .filter(m -> m.name.equals("containsAll"))
        .findFirst().orElseThrow().parameterTypes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"17", "21", "25"})
  void namedLookupPrefersDeclaredOverInheritedAndConstructorsRemainSelectable(String release)
      throws IOException {
    DocModel doc = fixture(release, "Sequence");
    DocModel.Method inherited = new DocModel.Method();
    inherited.name = "add";
    inherited.signature = "add(Object)";
    inherited.kind = "method";
    inherited.inherited = true;
    inherited.parameterTypes = List.of("Object");
    doc.inherited.add(inherited);
    assertFalse(MemberLookup.select(doc, new String[]{"-add"}).get(0).inherited);
    assertEquals(inherited, MemberLookup.select(doc, new String[]{"add", "-p", "Object"})
        .get(0));

    DocModel failure = fixture(release, "Failure");
    assertEquals(2, MemberLookup.select(failure, new String[]{"-p", "1"}).size());
    assertEquals("constructor", MemberLookup.select(failure,
        new String[]{"Failure", "-p", "String"}).get(0).kind);
  }
}

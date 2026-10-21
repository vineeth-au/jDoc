package dev.jdoc;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class JavadocParserTest {
    @Test
    void modernPagesFollowSummaryOrderRatherThanDetailOrder() {
        String html = "<section class='method-summary'><a href='#valueOf(boolean)'>valueOf</a>"
                + "<a href='#valueOf(char[],int,int)'>valueOf</a></section>"
                + "<section class='method-details'>"
                + "<section class='detail' id='valueOf(char[],int,int)'><h3>valueOf</h3>"
                + "<div class='horizontal-scroll'><div class='member-signature'>public static String valueOf(char[] data, int offset, int count)</div>"
                + "<div class='block'>Three arguments.</div></div></section>"
                + "<section class='detail' id='valueOf(boolean)'><h3>valueOf</h3>"
                + "<div class='member-signature'>public static String valueOf(boolean b)</div>"
                + "<div class='block'>One argument.</div></section></section>";
        DocModel doc = JavadocParser.parse(Jsoup.parse(html), "java.lang.String", "url", "25");
        assertEquals(2, doc.methods.size());
        assertEquals("One argument.", doc.methods.get(0).description);
        assertEquals(Arrays.asList("char[]", "int", "int"), doc.methods.get(1).parameterTypes);
    }

    @Test
    void modernMethodDetailsIncludeTagsAndNormalizeSignatures() {
        String html = "<section class='method-summary'><a href='#valueOf(char[],int,int)'>valueOf</a></section>"
                + "<section class='method-details'><section class='detail' id='valueOf(char[],int,int)'>"
                + "<h3>valueOf</h3><div class='member-signature'>public static String valueOf\u200b(char[] data, int offset, int count)</div>"
                + "<div class='block'>Creates a string.</div><dl><dt>Returns:</dt><dd>a String</dd></dl>"
                + "</section></section>";
        DocModel doc = JavadocParser.parse(Jsoup.parse(html), "java.lang.String", "url", "21");
        assertEquals(1, doc.methods.size());
        assertEquals(Arrays.asList("char[]", "int", "int"), doc.methods.get(0).parameterTypes);
        assertTrue(doc.methods.get(0).details.contains("Returns:\n  a String"));
        assertFalse(doc.methods.get(0).signature.contains("\u200b"));
    }

    @Test
    void genericCommasDoNotSplitParameterTypes() {
        assertEquals(Arrays.asList("Map<String,Integer>", "int"),
                JavadocParser.parameterTypes("void take(Map<String, Integer> entries, int count)"));
    }

    @Test
    void methodlessExceptionShowsClassDescriptionAndConstructorsInSummaryOrder() {
        String html = "<section class='class-description'><div class='block'>A checked exception.</div></section>"
                + "<section class='constructor-summary'><a href='#%3Cinit%3E()'>Exception</a>"
                + "<a href='#%3Cinit%3E(java.lang.String)'>Exception</a></section>"
                + "<section class='constructor-details'>"
                + "<section class='detail' id='&lt;init&gt;(java.lang.String)'><h3>Exception</h3>"
                + "<div class='member-signature'>public Exception(String message)</div>"
                + "<div class='block'>With a message.</div></section>"
                + "<section class='detail' id='&lt;init&gt;()'><h3>Exception</h3>"
                + "<div class='member-signature'>public Exception()</div>"
                + "<div class='block'>Without a message.</div></section></section>";
        DocModel doc = JavadocParser.parse(Jsoup.parse(html), "java.lang.Exception", "url", "17");
        assertEquals(3, doc.schemaVersion);
        assertTrue(doc.methods.isEmpty());
        assertEquals("A checked exception.", doc.description);
        assertEquals(2, doc.constructors.size());
        assertEquals("public Exception()", doc.constructors.get(0).signature);
        assertEquals(Arrays.asList("String"), doc.constructors.get(1).parameterTypes);
    }

    @Test
    void inheritedAnchorsUseErasedSignaturesAndPreferClassImplementations() {
        String html = "<section class='method-summary'>"
                + "<a href='#take(java.util.Collection)'>take</a>"
                + "<div class='inherited-list'><h3 id='methods-inherited-from-class-demo.View'>"
                + "Methods inherited from interface View</h3><code>"
                + "<a href='View.html#take(java.util.Collection)'>take</a>, "
                + "<a href='View.html#stream()'>stream</a></code></div>"
                + "<div class='inherited-list'><h3 id='methods-inherited-from-class-demo.Base'>"
                + "Methods inherited from class Base</h3><code>"
                + "<a href='Base.html#stream()'>stream</a></code></div></section>"
                + "<section class='method-details'><section class='detail' id='take(java.util.Collection)'>"
                + "<h3>take</h3><div class='member-signature'>public void take(Collection&lt;String&gt; values)</div>"
                + "</section></section>";
        DocModel doc = JavadocParser.parse(Jsoup.parse(html, "https://example.test/demo/Child.html"),
                "demo.Child", "https://example.test/demo/Child.html", "17");
        assertEquals("take(Collection)", JavadocParser.key("take", doc.methods.get(0).parameterTypes));
        assertEquals(1, doc.inherited.size());
        assertEquals("demo.Base", doc.inherited.get(0).declaringType);
        assertEquals("https://example.test/demo/Base.html", doc.inherited.get(0).sourceUrl);
    }

}

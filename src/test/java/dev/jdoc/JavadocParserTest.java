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
    void java8And11AnchorsOutsideDetailListAreResolved() {
        for (String release : Arrays.asList("8", "11")) {
            String attribute = release.equals("8") ? "name" : "id";
            String anchor = release.equals("8") ? "valueOf-char:A-int-int-" : "valueOf(char[],int,int)";
            String html = "<div class='summary'><table class='memberSummary'><tr><th><a href='#" + anchor
                    + "'>valueOf</a></th></tr></table></div>"
                    + "<div class='details'><a " + attribute + "='" + anchor + "'></a><ul><li class='blockList'>"
                    + "<h4>valueOf</h4><pre>public static String valueOf\u200b(char[] data, int offset, int count)</pre>"
                    + "<div class='block'>Creates a string.</div><dl><dt>Returns:</dt><dd>a String</dd></dl>"
                    + "</li></ul></div>";
            DocModel doc = JavadocParser.parse(Jsoup.parse(html), "java.lang.String", "url", release);
            assertEquals(1, doc.methods.size(), release);
            assertEquals(Arrays.asList("char[]", "int", "int"), doc.methods.get(0).parameterTypes);
            assertTrue(doc.methods.get(0).details.contains("Returns:\n  a String"));
            assertFalse(doc.methods.get(0).signature.contains("\u200b"));
        }
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
        assertEquals(2, doc.schemaVersion);
        assertTrue(doc.methods.isEmpty());
        assertEquals("A checked exception.", doc.description);
        assertEquals(2, doc.constructors.size());
        assertEquals("public Exception()", doc.constructors.get(0).signature);
        assertEquals(Arrays.asList("String"), doc.constructors.get(1).parameterTypes);
    }

    @Test
    void legacyConstructorAnchorAndOldCacheAreHandled() {
        String html = "<div class='description'><div class='block'>A checked exception.</div></div>"
                + "<div class='summary'><table class='memberSummary'><a href='#Exception--'>Exception</a></table></div>"
                + "<div class='details'><a name='Exception--'></a><ul><li class='blockList'>"
                + "<h4>Exception</h4><pre>public Exception()</pre><div class='block'>Empty.</div>"
                + "</li></ul></div>";
        DocModel doc = JavadocParser.parse(Jsoup.parse(html), "java.lang.Exception", "url", "8");
        assertEquals(1, doc.constructors.size());
        assertTrue(doc.methods.isEmpty());
        assertEquals("A checked exception.", doc.description);
        assertEquals(0, new com.google.gson.Gson().fromJson("{\"methods\":[]}", DocModel.class).schemaVersion);
    }
}

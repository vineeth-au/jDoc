package dev.jdoc;

import dev.jdoc.DocModel.Method;
import org.junit.jupiter.api.Test;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MethodBrowserTest {
    @Test
    void outputCanBeClippedAndColoredWithoutChangingPlainContent() {
        assertEquals("met…", MethodBrowser.shorten("method", 4));
        DocModel.Method method = method("public boolean add(E element)");
        method.name = "add";
        assertEquals("add(E element)", MethodBrowser.listSignature(method));
        String text = "java.lang.String — Java 17 documentation\npublic String repeat(int count)\nReturns:\n  result\n";
        String colored = Main.colorize(text);
        assertTrue(colored.contains("\u001b["));
        assertEquals(text, org.jline.utils.AttributedString.stripAnsi(colored));
    }

    @Test
    void arrowsHaveDistinctCodesFromEscape() throws IOException {
        assertEquals(27, MethodBrowser.readKey(reader("\033")));
        assertEquals(1005, MethodBrowser.readKey(reader("\033[C")));
        assertEquals(1004, MethodBrowser.readKey(reader("\033[D")));
        assertEquals(-2, MethodBrowser.readKey(reader("\033[1;5C")));
        assertEquals(1000, MethodBrowser.readKey(reader("\033[A")));
        assertEquals(1002, MethodBrowser.readKey(reader("\033[5~")));
    }

    @Test
    void descriptionIsSelectableAndCollapseMovesUpTheTree() {
        DocModel doc = new DocModel();
        doc.name = "java.lang.String";
        doc.release = "17";
        doc.methods.add(method("repeat(int count)"));
        MethodBrowser browser = new MethodBrowser(doc);
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.BACK, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(27, 24));
        assertEquals(MethodBrowser.Navigation.HOME, browser.handleKey('h', 24));
    }

    @Test
    void descriptionKeyOpensClassOrMethodAfterLeavingSearch() {
        DocModel doc = new DocModel();
        doc.name = "java.lang.String";
        doc.release = "17";
        doc.methods.add(method("clear()"));
        MethodBrowser browser = new MethodBrowser(doc);
        browser.beginSearch();
        for (char c : "clear".toCharArray()) browser.handleKey(c, 24);
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(27, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey('d', 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey('c', 24));
        assertEquals(MethodBrowser.Navigation.BACK, browser.handleKey(1004, 24));
    }

    @Test
    void escapeClearsSearchAndOnlySlashCanReactivateIt() {
        DocModel doc = new DocModel();
        doc.name = "java.lang.String";
        doc.methods.add(method("repeat(int count)"));
        MethodBrowser browser = new MethodBrowser(doc);
        browser.beginSearch();
        browser.handleKey('r', 24);
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(27, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(27, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey('d', 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey('c', 24));
        assertEquals(MethodBrowser.Navigation.BACK, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.QUIT, browser.handleKey('q', 24));
        MethodBrowser fresh = new MethodBrowser(doc);
        fresh.handleKey('r', 24);
        assertEquals(MethodBrowser.Navigation.QUIT, fresh.handleKey('q', 24));
        fresh.handleKey('/', 24);
        fresh.handleKey('r', 24);
        assertEquals(MethodBrowser.Navigation.STAY, fresh.handleKey(27, 24));
        assertEquals(MethodBrowser.Navigation.BACK, fresh.handleKey(1004, 24));
    }

    @Test
    void rightExpandsOverloadGroupAndLeftCollapsesBeforeLeavingClass() {
        DocModel doc = new DocModel();
        doc.name = "java.lang.String";
        doc.methods.add(method("valueOf(boolean value)"));
        doc.methods.add(method("valueOf(char[] value)"));
        MethodBrowser browser = new MethodBrowser(doc);
        browser.handleKey(1001, 24); // Select overload group after Class description.
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.BACK, browser.handleKey(1004, 24));
    }

    @Test
    void leftFromActiveMemberSearchReturnsToSelectedClass() {
        DocModel doc = new DocModel();
        doc.name = "java.util.ArrayList";
        doc.methods.add(method("equals(Object other)"));
        MethodBrowser browser = new MethodBrowser(doc);
        browser.beginSearch();
        for (char c : "equals".toCharArray()) browser.handleKey(c, 24);
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.BACK, browser.handleKey(1004, 24));
    }

    @Test
    void lowercaseBookmarksAndHomeKeysAreCommandsOnlyOutsideSearch() {
        DocModel doc = new DocModel();
        doc.methods.add(method("repeat(int count)"));
        MethodBrowser browser = new MethodBrowser(doc);
        assertEquals(MethodBrowser.Navigation.BOOKMARKS, browser.handleKey('b', 24));
        assertEquals(MethodBrowser.Navigation.HOME, browser.handleKey('h', 24));
        browser.beginSearch();
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey('b', 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey('h', 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(27, 24));
        assertEquals(MethodBrowser.Navigation.BOOKMARKS, browser.handleKey('b', 24));
    }

    private static NonBlockingReader reader(String keys) {
        return new NonBlockingReader() {
            int position;

            @Override
            protected int read(long timeout, boolean peek) {
                if (position >= keys.length()) {
                    return -1;
                }
                int character = keys.charAt(position);
                if (!peek) {
                    position++;
                }
                return character;
            }

            @Override
            public int readBuffered(char[] buffer, int offset, int length, long timeout) {
                if (position >= keys.length()) {
                    return -1;
                }
                buffer[offset] = (char) read(timeout, false);
                return 1;
            }
        };
    }

    private static DocModel.Method method(String signature) {
        DocModel.Method method = new Method();
        method.name = signature.substring(0, signature.indexOf('('));
        method.signature = signature;
        return method;
    }
}

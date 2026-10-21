package dev.jdoc;

import dev.jdoc.DocModel.Method;
import org.junit.jupiter.api.Test;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MethodBrowserTest {
    @Test
    void filteringRetainsJavadocOrderAndClampsNavigation() {
        DocModel.Method first = method("add(E e)");
        DocModel.Method other = method("clear()");
        DocModel.Method second = method("add(int index, E element)");
        MethodBrowser.State state = new MethodBrowser.State(Arrays.asList(first, other, second));
        state.filter("ADD");
        assertEquals(Arrays.asList(first, second), state.matches);
        state.move(100);
        assertSame(second, state.selected());
        state.keepVisible(1);
        assertEquals(1, state.top);
        state.move(-100);
        state.keepVisible(1);
        assertSame(first, state.selected());
        assertEquals(0, state.top);
        state.filter("missing");
        assertNull(state.selected());
    }

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
    void leftMovesUpOneLevelAndEscapeGoesHome() {
        DocModel doc = new DocModel();
        doc.name = "java.lang.String";
        doc.release = "17";
        doc.methods.add(method("repeat(int count)"));
        MethodBrowser browser = new MethodBrowser(doc);
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.BACK, browser.handleKey(1004, 24));
        assertEquals(MethodBrowser.Navigation.STAY, browser.handleKey(1005, 24));
        assertEquals(MethodBrowser.Navigation.HOME, browser.handleKey(27, 24));
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

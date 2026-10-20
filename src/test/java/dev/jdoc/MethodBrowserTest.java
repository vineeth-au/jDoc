package dev.jdoc;

import dev.jdoc.DocModel.Method;
import org.junit.jupiter.api.Test;

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

    private static DocModel.Method method(String signature) {
        DocModel.Method method = new Method();
        method.name = signature.substring(0, signature.indexOf('('));
        method.signature = signature;
        return method;
    }
}

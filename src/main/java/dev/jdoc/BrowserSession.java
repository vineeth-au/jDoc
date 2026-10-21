package dev.jdoc;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** Owns the terminal for the entire package → class → member → detail journey. */
final class BrowserSession {
  private final Documentation docs;
  private final Map<String, String> index;
  private final String release;
  private final PackageBrowser picker;
  private MethodBrowser members;

  BrowserSession(Documentation docs, Map<String, String> index, String release, DocModel initial) {
    this.docs = docs;
    this.index = index;
    this.release = release;
    picker = new PackageBrowser(release, index);
    if (initial != null) {
      picker.focusClass(initial.name, initial.sourceUrl);
      members = new MethodBrowser(initial, docs);
    }
  }

  void run() throws IOException {
    try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
      Attributes original = terminal.enterRawMode();
      PrintWriter writer = terminal.writer();
      writer.print("\033[?1049h\033[?25l");
      writer.flush();
      try {
        while (true) {
          if (members == null) picker.render(terminal);
          else members.render(terminal);
          int key = MethodBrowser.readKey(terminal.reader());
          if (key == -1 || key == 3) break;
          if (members != null) {
            MethodBrowser.Navigation action = members.handleKey(key, terminal.getHeight());
            if (action == MethodBrowser.Navigation.QUIT) break;
            if (action == MethodBrowser.Navigation.HOME) {
              members = null;
              picker.home();
            } else if (action == MethodBrowser.Navigation.BACK) {
              members = null;
              picker.backFromMembers();
            }
          } else {
            String name = picker.handleKey(key, terminal.getHeight());
            if (picker.quit) break;
            if (name != null) {
              writer.print("\033[H\033[2JLoading " + name + "…\n");
              writer.flush();
              try {
                members = new MethodBrowser(docs.classDoc(release, name, index.get(name)), docs);
              } catch (IOException ex) {
                picker.error = "Could not load " + name + ": " + ex.getMessage();
              }
            }
          }
        }
      } finally {
        writer.print("\033[0m\033[?25h\033[?1049l");
        writer.flush();
        terminal.setAttributes(original);
      }
    }
  }
}

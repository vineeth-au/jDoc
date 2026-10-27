package dev.jdoc;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

final class BrowserSession {
  private final Documentation docs;
  private final Map<String, String> index;
  private final String release;
  private final PackageBrowser picker;
  private final NavigationStore history;
  private final BrowserActions actions;
  private MethodBrowser members;
  private MethodBrowser suspended;

  BrowserSession(Documentation docs, Map<String, String> index, String release, DocModel initial) {
    this.docs = docs;
    this.index = index;
    this.release = release;
    history = new NavigationStore(docs.versionPath(""));
    actions = new BrowserActions(history);
    picker = new PackageBrowser(release, index, history);
    if (initial != null) {
      picker.focusClass(initial.name, initial.sourceUrl);
      members = new MethodBrowser(initial, docs, actions);
      members.beginSearch();
    }
  }

  void run() throws IOException {
    try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
      Attributes original = terminal.enterRawMode();
      PrintWriter writer = terminal.writer();
      writer.print("\033[?1049h\033[?25l");
      writer.flush();
      try {
        if (members != null) recordVisit(members.className());
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
              picker.resetHome();
            } else if (action == MethodBrowser.Navigation.BACK) {
              members = null;
              picker.backFromMembers();
            } else if (action == MethodBrowser.Navigation.BOOKMARKS) {
              suspended = members;
              members = null;
              picker.openBookmarks();
            }
          } else {
            if (suspended != null && picker.level == PackageBrowser.Level.BOOKMARKS
                && (key == 1004 || key == 'c' && !picker.selection().searching)) {
              members = suspended;
              suspended = null;
              picker.leaveBookmarksToMembers();
              continue;
            }
            String name = picker.handleKey(key, terminal.getHeight());
            if (picker.quit) break;
            if (name != null) {
              suspended = null;
              writer.print("\033[H\033[2JLoading " + name + "…\n");
              writer.flush();
              try {
                members = new MethodBrowser(docs.classDoc(release, name, index.get(name)), docs, actions);
                if (picker.takeOpenedInSearch()) members.beginSearch();
                recordVisit(name);
              } catch (IOException ex) {
                picker.error = "Could not load " + name + ": " + ex.getMessage();
              }
            }
            if (picker.level == PackageBrowser.Level.PACKAGES) suspended = null;
          }
        }
      } finally {
        writer.print("\033[0m\033[?25h\033[?1049l");
        writer.flush();
        terminal.setAttributes(original);
      }
    }
  }

  private void recordVisit(String name) {
    try {
      history.visited(release, name);
    } catch (IOException ex) {
      picker.error = "Could not save recent classes: " + ex.getMessage();
    }
  }
}

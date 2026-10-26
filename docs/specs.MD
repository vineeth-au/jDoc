# jDoc specification

## Purpose and platform

- jDoc is a macOS command-line reader for official Java SE API documentation.
- Java 17 or newer is required to run the application. The documentation release
  is independent of the installed Java runtime.
- The executable is `jdoc`. Users may define `alias j='jdoc'` themselves; the
  installer does not create a `j` command or edit shell configuration.

## Documentation versions and caching

- Support documentation releases 17, 21, and 25.
- `jdoc --docVersion 17` persistently selects the default documentation release.
  `jdoc --docVersion 21 <Class> [member]` overrides it for one lookup.
  Without an explicit choice, read a local Maven or Gradle build setting from
  the current directory or parent project: `--release` settings before JDK
  toolchains, then weaker bytecode target/source settings. Do not execute build
  scripts. Fall back to `JAVA_HOME`, then the running JVM. Select the closest
  supported release no newer than the detected version; report its source and
  distinguish bytecode compatibility from an enforced API release.
- `jdoc --versions` lists supported releases, the current selection, and cache
  status. `jdoc --version` and `jdoc -v` show the application version.
- Fetch the release's type and package search indexes instead of scraping the
  all-classes HTML page. Fetch the larger member index for `-m` reverse lookup.
  Fetch individual requested class pages on demand.
  Cache extracted signatures and documentation by release, not full archives.
  Cached class lookups work offline. Show the selected release and source URL.
- Schema v5 stores class and member prose as paragraphs, code blocks, lists,
  and links with a class name plus anchor (or an external URL), along with
  `since`, deprecation, preview, and parsed return-type metadata. Older cached pages refresh on
  demand; when offline, show the older data with an incompleteness notice.
- `jdoc --java 17 String` is invalid; use `--docVersion` for documentation.

## Commands and lookup

- `jdoc --help` explains all commands, parameter rules, examples and keys.
- `jdoc` without arguments opens the package browser in an interactive terminal.
  Its home starts with two-segment `java.*` package groups from the selected release
  (for example `java.awt`). Tab at home toggles all package families (including
  `javax.*`, `org.*`, and `com.sun.*`) for the current session. It applies to
  grouped home and home search, preserves the current query when toggled, and
  starts at `java.* only` on the next launch. Opening a group lists its immediate
  subpackages and classes directly in that package. `c` on a home group can
  collapse it further to a shared one-segment parent. Typing on the initial
  screen searches packages and top-level classes within the current home scope.
  Sort class matches by fully qualified package and
  then class name within relevance tiers: exact simple names, prefixes, and
  camel-case components. Once a query is a complete name component, require
  a component boundary after it: `List` matches `ListIterator` and `ArrayList`,
  but not `ListeningConnector` or `TextListener`. During partial typing, prefixes
  still match. This matches names rather than interface implementations.
  Display each class's package visibly. Nested types remain accessible via
  package browsing and direct lookups. Qualified-name searches still work for
  top-level types in the selected home scope.
  Opening a package lists its classes; opening a class result directly
  opens its members. Collapsing from a home class result enters its containing
  package with the class selected; collapsing from its members returns to the
  same selected class, then its package, then grouped home.
  Without a terminal, show an instruction to
  supply a class name or use `--help` instead of reading input.
- Browse packages → classes → members → documentation with `d`, Right Arrow,
  or Enter. `c`/Left Arrow collapses one level; `h` resets to the grouped
  package home and `q` quits.
  The initial screen starts in search mode. Escape clears the current query
  and exits search without changing levels; at home this restores grouped
  packages. Outside search, Escape does nothing. Left Arrow from a home search
  class result enters its containing package. Opening a package starts search in its new
  view even after Escape at the parent. Within a package view, Escape turns
  search off, but typing a class name starts it again; `/` starts search
  explicitly when its first character conflicts with a command key. Package-level searches
  include descendant classes under that package, while unfiltered package
  views list only immediate children and classes directly in the package.
  Collapsing back from a
  class opened through home search must not restore the old query. A named
  `jdoc <Class>` lookup opens at members; Left Arrow reaches
  its class in the package browser. Fetch a class page only when it is opened.
- A failed class-page fetch keeps the user at the class list with an error so
  another class can be chosen. Keep one terminal/raw-mode session throughout.
- `p` opens a visibility manager listing all packages,
  including hidden ones. Space/Enter toggles the selected package. Hidden
  package roots apply to the exact package and subpackages on a dot boundary
  (`java.util` hides `java.util.concurrent`, not `java.utility`). Persist the
  roots across releases in `JDOC_CACHE`. Apply them to package/class browsing,
  search, recents, and bookmarks; direct class commands remain available.
  A subpackage hidden by an ancestor cannot be shown without showing the
  ancestor. Preserve the search where possible when a result is hidden.
- `jdoc <Class>` browses directly documented methods, constructors, fields,
  enum constants and nested types. If it declares no methods, show its class
  description and constructors (for example, `jdoc Exception`).
- Inherited methods are indexed from the requested class page and grouped by
  declaring class/interface. Hide `java.lang.Object` methods. Deduplicate
  overridden signatures in favor of the class, then nearest superclass,
  then interfaces. Fetch declaring-class details only when requested.
- Named lookups include inherited methods automatically. A name-only lookup
  chooses a declared overload before an inherited one. `-p` listings remain
  declared-only unless `-i` is supplied; exact named parameter lookup searches
  both. The TUI collapses inherited methods until `i` is pressed.
- If inherited details cannot be fetched offline, identify the declaring type
  and report that details are unavailable rather than denying the method exists.
- `jdoc <Class> -<method>` shows the first matching overload in the selected
  release's Javadoc **method-summary order**.
- `jdoc <Class> -p <number>` shows directly documented methods with that
  parameter count. A number is never an overload position.
- `jdoc <Class> <method> -p <number>` filters named methods by parameter count.
- `jdoc <Class> <method> -p <types>` selects matching parameter types;
  `--params` is an alias for `-p`, and `char-array` means `char[]`. Whitespace
  after commas is accepted when `-p` appears last.
- Parameter-type lookup without a method name is invalid.
- Accept short and fully qualified class names. Show qualified choices when a
  short name is ambiguous. For likely typos such as `ArrryList`, ask
  “Did you mean ArrayList?” in an interactive terminal; never silently replace
  a name. Non-interactive output gives suggestions without prompting.
- Report methods absent in a selected release and list available overloads
  when an exact parameter lookup fails.
- For failed class/member lookups, check only other cached releases. Where a
  class page confirms an effective member, report the declaring type and
  `@since` when known. A member-index hit without the class page can confirm
  that a type documents it but cannot establish the declaring type. If the
  cache cannot determine availability, say so rather than claiming absence.
- `jdoc -m <name>` searches the selected release's member index for method
  names and lists classes that document matching declarations.

## Version comparison and reports

- `jdoc --diff <old> <new> <Class>` compares effective APIs (declared and
  inherited methods plus constructors, fields, enum constants, nested types)
  by kind/name and erased parameter types. Show additions, removals, declaration
  or status changes; omit unchanged groups. A missing class is an added or
  removed type, not a missing-member error. Keep Preview, Deprecated, and
  removed distinct. Do not claim a complete diff from an older-schema cache.
- `jdoc --new <release>` filters that release's cumulative `new-list.html`
  to entries added in the requested release, grouped by package and kind.
  Java 17 uses tab classes for release labels; 21 and 25 use an Added in column.
  Preserve Preview labels such as Java 21 `StringTemplate`.
- `jdoc --removal <release>` reads only the terminally deprecated section of
  `deprecated-list.html` for that release. This is a snapshot of APIs flagged
  for removal *as of* the release, not just newly flagged entries. The
  Deprecated in column is not necessarily the first release with forRemoval.
- Cache both release reports under the release's cache directory. A missing
  cached index, page, or report needs network access; explain incomplete offline
  comparisons rather than silently returning empty results.

## Terminal presentation

- Use JLine 4 with a Java 17-compatible artifact.
- An unfiltered class lookup opens a scrollable, searchable member list in
  Javadoc summary order. Rows show the member kind, signature and description.
- Typing filters the current level; Backspace edits the query. Up/Down and
  Page Up/Down navigate. The initial view starts searching; entering a package
  also enables search and typing there can restart it after Escape. In other
  views, use `/` to start a search. `d`/Right Arrow/Enter opens
  a selected item; `c`/Left Arrow collapses it. Escape clears search and `h`
  returns home. Show a status line
  with these keys.
- Member-kind tabs: Methods, Constructors, Fields, Enum Constants, Nested
  Types. Tab or `1`–`5` switches while not typing, retaining each tab's search
  and selection. Group overloads in summary order; Right Arrow expands a group
  or opens an overload, while Left Arrow collapses it before navigating up.
  The first Methods row selects Class description, even for methodless types;
  `d`/Enter/Right opens its full text. `c` collapses the current detail to a
  member, then the class, package, and outer package in order.
  Apply filters before grouping so a signature search shows only matching
  overloads.
- Composable filters: `:static`, `:since <version>` (at least that version),
  `:deprecated`, `#<parameter count>`, `-><return type>`, and plain text.
  Unknown operators show errors; unknown `@since` does not match a since filter.
- Detail actions: `o` opens the documented source URL, `y` copies a signature,
  `m` copies an import, and `s` bookmarks a class. `b` opens bookmarks from any
  view; `c` returns to the prior view. `r` opens recents. Persist these by
  release in the user cache with atomic writes and cap recents at 20.
- Preserve paragraphs, code indentation and list items in detail views and
  plain output. Display `Since`, `Deprecated`, and `Preview` as text labels when
  present; a missing `@since` is unknown. Select Class description to read
  the full class description. Display resolved link targets without treating
  them as raw HTML hrefs.
- Direct member lookups show their documentation without requiring a menu.
- Use accessible, high-contrast colors: cyan for the class and release heading,
  bright white with a blue selection background for the current method, green
  for signatures, yellow for search matches, muted gray for descriptions and
  source URLs, and magenta for section labels such as Parameters and Returns.
  Keep text labels so color never carries meaning alone.
- Honor `NO_COLOR` by suppressing color. With redirected/piped output or no
  interactive terminal, print ordinary uncolored text and do not capture keys.

## Illustrative interactive view

The color annotations below describe the visual treatment; they are not
characters printed by the application.

```text
java.util.ArrayList                         Java 17 API       [cyan]
Search methods: _                                             [yellow]
──────────────────────────────────────────────────────────────────────
❯ add(int index, E element)   Inserts an element at an index  [blue selection]
  add(E e)                    Appends an element to this list
  addAll(Collection<? extends E> c)  Appends collection elements
  clear()                     Removes all elements
  ...
──────────────────────────────────────────────────────────────────────
↑↓ Navigate  PgUp/PgDn Page  Enter Read  q Quit
```

The detail view emphasizes the signature in green and section labels in
magenta, with `b` to return and `q` to exit.

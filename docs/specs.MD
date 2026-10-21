# jDoc specification

## Purpose and platform

- jDoc is a macOS command-line reader for official Java SE API documentation.
- Java 17 or newer is required to run the application. The documentation release
  is independent of the installed Java runtime.
- The executable is `jDoc`. Users may define `alias j='jDoc'` themselves; the
  installer does not create a `j` command or edit shell configuration.

## Documentation versions and caching

- Support documentation releases 17, 21, and 25.
- `jDoc --docVersion 17` persistently selects the default documentation release.
  Without a saved choice, use `JAVA_HOME` when valid or the running JVM otherwise;
  select the closest supported release no newer than the detected Java version.
  Report the automatic choice on stderr without saving it.
- `jDoc --versions` lists supported releases, the current selection, and cache
  status. `jDoc --version` and `jDoc -v` show the application version.
- Fetch a release's class index and individual requested class pages on demand.
  Cache extracted signatures and documentation by release, not full archives.
  Cached class lookups work offline. Show the selected release and source URL.
- Schema v4 stores class and member prose as paragraphs, code blocks, lists,
  and links with a class name plus anchor (or an external URL), along with
  `since`, deprecation, and preview metadata. Older cached pages refresh on
  demand; when offline, show the older data with an incompleteness notice.
- `jDoc --java 17 String` is invalid; use `--docVersion` for documentation.

## Commands and lookup

- `jDoc --help` explains all commands, parameter rules, examples and keys.
- `jDoc` without arguments opens the package browser in an interactive terminal.
  Its home level is a flat list of fully qualified package names from the
  selected release's class index. Typing at home searches both packages and
  classes across the index; distinguish class results with their qualified
  names. Opening a package lists its classes; opening a class result directly
  opens its members, and Left Arrow returns to those home search results.
  Without a terminal, show an instruction to
  supply a class name or use `--help` instead of reading input.
- Browse packages → classes → members → documentation with Right Arrow or
  Enter. Left Arrow moves up one level; Escape first leaves an active search
  while retaining its results, and otherwise returns to package home;
  `q` quits when not typing. Each level retains its search and selection when
  returning. A named `jDoc <Class>` lookup opens at members; Left Arrow reaches
  its class in the package browser. Fetch a class page only when it is opened.
- A failed class-page fetch keeps the user at the class list with an error so
  another class can be chosen. Keep one terminal/raw-mode session throughout.
- `jDoc <Class>` browses directly documented methods, constructors, fields,
  enum constants and nested types. If it declares no methods, show its class
  description and constructors (for example, `jDoc Exception`).
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
- `jDoc <Class> -<method>` shows the first matching overload in the selected
  release's Javadoc **method-summary order**.
- `jDoc <Class> -p <number>` shows directly documented methods with that
  parameter count. A number is never an overload position.
- `jDoc <Class> <method> -p <number>` filters named methods by parameter count.
- `jDoc <Class> <method> -p <types>` selects matching parameter types;
  `--params` is an alias for `-p`, and `char-array` means `char[]`. Whitespace
  after commas is accepted when `-p` appears last.
- Parameter-type lookup without a method name is invalid.
- Accept short and fully qualified class names. Show qualified choices when a
  short name is ambiguous. For likely typos such as `ArrryList`, ask
  “Did you mean ArrayList?” in an interactive terminal; never silently replace
  a name. Non-interactive output gives suggestions without prompting.
- Report methods absent in a selected release and list available overloads
  when an exact parameter lookup fails.

## Terminal presentation

- Use JLine 4 with a Java 17-compatible artifact.
- An unfiltered class lookup opens a scrollable, searchable member list in
  Javadoc summary order. Rows show the member kind, signature and description.
- Typing filters the current level; Backspace edits the query. Up/Down and
  Page Up/Down navigate. Right Arrow/Enter opens a selected item; Left Arrow
  returns one level and Escape leaves search or returns home. Show a status line
  with these keys.
- Preserve paragraphs, code indentation and list items in detail views and
  plain output. Display `Since`, `Deprecated`, and `Preview` as text labels when
  present; a missing `@since` is unknown. Press `C` in the member list to read
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

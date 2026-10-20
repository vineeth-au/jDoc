# jDoc specification

## Purpose and platform

- jDoc is a macOS command-line reader for official Java SE API documentation.
- Java 17 or newer is required to run the application. The documentation release
  is independent of the installed Java runtime.
- The executable is `jDoc`. Users may define `alias j='jDoc'` themselves; the
  installer does not create a `j` command or edit shell configuration.

## Documentation versions and caching

- Initially support documentation releases 8, 11, 17, 21, and 25.
- `jDoc --docVersion 17` persistently selects the default documentation release.
  Until one is selected, lookups explain how to select one.
- `jDoc --versions` lists supported releases, the current selection, and cache
  status. `jDoc --version` and `jDoc -v` show the application version.
- Fetch a release's class index and individual requested class pages on demand.
  Cache extracted signatures and documentation by release, not full archives.
  Cached class lookups work offline. Show the selected release and source URL.
- `jDoc --java 17 String` is invalid; use `--docVersion` for documentation.

## Commands and lookup

- `jDoc --help` explains all commands, parameter rules, examples and keys.
- `jDoc <Class>` browses the methods directly documented on its class page.
  If the class declares no methods, show its class description and directly
  documented constructors instead (for example, `jDoc Exception`).
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
- Exclude inherited methods. `-i` is reserved for a later release.

## Terminal presentation

- Use JLine 4 with a Java 17-compatible artifact.
- An unfiltered class lookup opens a scrollable, searchable method list in
  Javadoc summary order. Rows show a signature and short description.
- Typing filters methods by name or signature; Backspace edits the query.
  Up/Down and Page Up/Down navigate, Enter reads a method's full documentation,
  `b` returns to the list, and `q` quits. Show a status line with these keys.
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

# jDoc: Richer TUI Navigation Ideas

Ideas 4, 5, 6, and 8 are implemented in P6. The others remain proposals.

Concrete navigation features for jDoc's interactive browser, each shown as what the user does and what they see, plus how it fits the current `MethodBrowser`.

## Design note: separate commands from search

Search starts active on launch and when entering a package. Inside a package,
typing a class name can restart search after Escape; `/` is available when the
first character is a command key. In other views, `/` starts search after
Escape. `q` quits from command mode.

Current lowercase controls: `d` opens, `c` collapses, `h` returns home,
`b` opens bookmarks, and `p` manages hidden packages. Detail actions use
`o`, `y`, `m`, and `s`.

## 1. Fuzzy class picker (`jdoc` with no args)

```text
Find class: hshmp_                                   Java 21
──────────────────────────────────────────────────────────────
❯ java.util.HashMap
  java.util.LinkedHashMap
  java.util.concurrent.ConcurrentHashMap
  javax.print.attribute.HashPrintJobAttributeSet
──────────────────────────────────────────────────────────────
↑↓ Move  Enter Open  Esc Quit                   4 of 4,412
```

- Subsequence matching: "hshmp" matches **H**a**sh**Ma**p**.
- Ranked by match tightness, then by package commonness (`java.util` and `java.lang` first).
- Data source: the existing `index.json`.

## 2. Follow type links, with history

In the detail view, `Tab` cycles through every type in the signature and description, and `Enter` jumps to it:

```text
java.util.HashMap › putAll                           Java 21
──────────────────────────────────────────────────────────────
public void putAll([Map]<? extends K, ? extends V> m)
                    ^^^ Tab-highlighted; Enter opens java.util.Map
Copies all of the mappings from the specified map …
See also: [Map.putAll]
──────────────────────────────────────────────────────────────
Tab Next link  Enter Follow  c Back  q Quit
```

- A breadcrumb grows as you go: `HashMap › putAll › Map › entrySet`.
- `c` pops one level; `b` is reserved for bookmarks.
- Implement as a `Deque<View>`, where each view stores the class, selected method, and scroll offset, so going back restores the exact position.

P3 already stores typed links (target class and anchor) in schema v5. Following
links and maintaining a view-history stack remain proposals.

## 3. Move between methods without going back

- Proposed next/previous-method shortcuts need new keys: `p` now manages hidden packages.
- `]` / `[`: jump between overloads of the same name, for example the nine `String.valueOf` variants.

## 4. Grouped overloads in the list

**Implemented.** Filtering occurs before grouping. Right Arrow expands a
group, and Left Arrow collapses it before navigating to the parent level.

```text
❯ ▸ valueOf             (9 overloads)  Returns the string representation…
  ▾ indexOf             (4 overloads)
      indexOf(int ch)
      indexOf(int ch, int fromIndex)
      indexOf(String str)
      indexOf(String str, int fromIndex)
```

`→` expands a group and `←` collapses it. This keeps long classes like `String` or `Collectors` scannable.

## 5. Member-kind tabs

**Implemented.** The five tabs are Methods, Constructors, Fields, Enum
Constants, and Nested Types. Tab or `1`–`5` switches outside search mode.

```text
 Methods (54) │ Constructors (15) │ Fields (2) │ Nested (0)
```

Switch with `1`–`5`, or `Tab` in the list view. Enum Constants have their own
fifth tab; constructors, fields, and nested types were already parsed in P2.

## 6. Filter operators in search

**Implemented.** Operators compose with text; invalid operators show an error.
The return-type filter uses parsed type data in cache schema v5.

| Typed | Meaning |
| --- | --- |
| `/repl` | Name or signature contains "repl" (current behavior) |
| `/:static` | Static methods only |
| `/:since 11` | Members added in Java 11 or later |
| `/:deprecated` | Deprecated members only |
| `/#2` | Exactly two parameters (mirrors `-p 2`) |
| `/->String` | Returns `String` |

Operators can be combined, for example `/:static ->List`.

## 7. Version switching in place

Press `v` on any method to get a release picker:

```text
String.strip()  — choose release
  17  ✓
  ❯ 21  ✓ (current)
  25  ✓
```

- Choosing a release re-renders the same method from that release's cache.
- A diff shortcut needs a different key: `d` now opens the selected description.
- This is jDoc's differentiator; no IDE does it.

## 8. Quick actions

**Implemented.** Detail actions use `o`, `y`, `m`, and `s`. `r` opens
recent classes and `b` opens bookmarks after leaving search mode. Recents are
capped at 20 and both lists are stored in the user cache.

| Key | Action | macOS implementation |
| --- | --- | --- |
| `o` | Open this member's anchor in the browser | `new ProcessBuilder("open", url + "#" + anchor)` |
| `y` | Copy the signature | Pipe to `pbcopy` |
| `m` | Copy the import line (`import java.util.HashMap;`) | Pipe to `pbcopy` |
| `s` | Star or bookmark the class | Small `bookmarks.json` in the cache dir |
| `r` | Recently viewed classes | Same idea, capped at 20 entries |

## 9. Split pane on wide terminals

When the terminal is at least 140 columns wide, show the list on the left and a live preview of the selected method on the right, updated while arrowing through. Below that width, keep the current two-screen flow.

JLine provides the width through `terminal.getWidth()` on every redraw, so the layout can switch live when the window is resized.

## Recommended first three

1. **Follow type links (2)** — also unlocks the formatting fix in the parser.
2. **n/p navigation (3)** — small change, big reduction in keystrokes.
3. **Version switching (7)** — the feature nobody else has.

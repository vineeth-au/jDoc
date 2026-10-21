# jDoc: Richer TUI Navigation Ideas

Concrete navigation features for jDoc's interactive browser, each shown as what the user does and what they see, plus how it fits the current `MethodBrowser`.

## Design note: separate commands from search

In the list view, typing already filters, and `q` only quits when not searching. New single-letter commands (`o`, `y`, `v`, …) would collide with typing a filter.

The cleanest fix is the less/vim convention: plain keys are commands, `/` enters search mode, and `Esc` or `Enter` leaves it. `MethodBrowser` already has a `searching` flag, so this mostly means making `/` the way into search instead of "any printable key".

## 1. Fuzzy class picker (`jDoc` with no args)

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
Tab Next link  Enter Follow  b Back  q Quit
```

- A breadcrumb grows as you go: `HashMap › putAll › Map › entrySet`.
- `b` pops one level.
- Implement as a `Deque<View>`, where each view stores the class, selected method, and scroll offset, so going back restores the exact position.

**Code change needed:** `JavadocParser` currently flattens everything with `.text()`. Keep the `<a href>` targets, for example as a `List<Link>` on `DocModel.Method` holding the label, target class, and anchor. Bump `schemaVersion` to 3 so old caches refetch.

## 3. Move between methods without going back

- `n` / `p`: jump to the next or previous method in summary order, instead of `b`, arrow down, `Enter`.
- `]` / `[`: jump between overloads of the same name, for example the nine `String.valueOf` variants.

## 4. Grouped overloads in the list

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

```text
 Methods (54) │ Constructors (15) │ Fields (2) │ Nested (0)
```

Switch with `1`–`4`, or `Tab` in the list view. This replaces the current rule that shows constructors only when a class has no methods, and gives fields a home once they are parsed.

## 6. Filter operators in search

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
  8   ✗ not present
  11  ✓ added (@since 11)
  17  ✓
❯ 21  ✓ (current)
```

- Choosing a release re-renders the same method from that release's cache.
- `d` shows a diff against another release, for example a changed signature or new `@throws`.
- This is jDoc's differentiator; no IDE does it.

## 8. Quick actions

| Key | Action | macOS implementation |
| --- | --- | --- |
| `o` | Open this member's anchor in the browser | `new ProcessBuilder("open", url + "#" + anchor)` |
| `y` | Copy the signature | Pipe to `pbcopy` |
| `Y` | Copy the import line (`import java.util.HashMap;`) | Pipe to `pbcopy` |
| `s` | Star or bookmark the class | Small `bookmarks.json` in the cache dir |
| `r` | Recently viewed classes | Same idea, capped at 20 entries |

## 9. Split pane on wide terminals

When the terminal is at least 140 columns wide, show the list on the left and a live preview of the selected method on the right, updated while arrowing through. Below that width, keep the current two-screen flow.

JLine provides the width through `terminal.getWidth()` on every redraw, so the layout can switch live when the window is resized.

## Recommended first three

1. **Follow type links (2)** — also unlocks the formatting fix in the parser.
2. **n/p navigation (3)** — small change, big reduction in keystrokes.
3. **Version switching (7)** — the feature nobody else has.

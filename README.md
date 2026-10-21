# jDoc

jDoc brings Java 17, 21, and 25 API docs to your terminal. Look up JDK classes and members by name, browse their documentation, and choose a documentation release independently of your installed Java version.

## Requirements

- Java 17 or newer (the documentation release can differ from your installed Java)
- Maven to build from source
- Network access for the first lookup of a release or class

## Build and run

From the repository directory:

```sh
mvn package
./jDoc
./jDoc String
```

`./jDoc` opens a package browser in a terminal. Type a package name or a class
name such as `String` at the home screen; a matching class opens directly when
selected. The first launch
downloads the class index; a class page is fetched only when you open that class.
Subsequent lookups of cached classes work offline.

**Supported documentation releases:** Java 17, 21, and 25. Without a saved
selection, jDoc checks Maven `pom.xml` or Gradle `build.gradle[.kts]` in the
current directory and its parents. `maven.compiler.release` and Gradle
`options.release` take priority over toolchain settings. If no static project
target is found, it uses `JAVA_HOME`, then the running Java. It selects the
closest supported release no newer than the detected version (Java 22 selects
Java 21 docs) and reports the source on stderr. `targetCompatibility` and
`maven.compiler.target` are weaker bytecode settings; they do not restrict APIs.
Build scripts are read, never executed. Use `./jDoc --docVersion 21` to save an
explicit preference, or `./jDoc --docVersion 21 java.util.List -getFirst` for one lookup.

## Examples

| Command | Result |
| --- | --- |
| `./jDoc --versions` | List supported documentation releases and cache status |
| `./jDoc` | Search packages or classes, then browse members and documentation |
| `./jDoc --docVersion 21` | Select Java 21 documentation for future lookups |
| `./jDoc String` | Browse methods documented on `String` |
| `./jDoc Exception` | Browse the class description and constructors |
| `./jDoc String -repeat` | Show the `repeat` method |
| `./jDoc String -valueOf` | Show the first `valueOf` overload |
| `./jDoc String valueOf -p char-array,int,int` | Find a specific overload |
| `./jDoc ArrayList -stream` | Show an inherited method and its declaring class |
| `./jDoc ArrayList -p 1 -i` | Include inherited methods in a parameter-count listing |
| `./jDoc Integer.MAX_VALUE` | Show a field |
| `./jDoc TimeUnit.SECONDS` | Show an enum constant |
| `./jDoc Map Entry` | Show a nested type |
| `./jDoc -m computeIfAbsent` | List classes documenting that method |
| `./jDoc --docVersion 21 java.util.List -getFirst` | Use Java 21 docs for one lookup |
| `./jDoc --diff 17 21 StringBuilder` | Compare all changed member kinds; shows two new `repeat` methods |
| `./jDoc --diff 21 25 StringTemplate` | See a Java 21 Preview type absent in Java 25 |
| `./jDoc --new 21` | List APIs introduced in Java 21, grouped by package |
| `./jDoc --removal 25` | List APIs flagged for removal as of Java 25 |
| `./jDoc --help` | Show all options |

In the browser, type to search packages and classes from home, or to filter the
current level. Right Arrow or Enter opens the selection; selecting a package
shows its classes, while selecting a class opens its members. Left Arrow goes
back one level. Escape first leaves an active search (keeping its results);
otherwise it returns to home. `q` quits when you are not typing. Inherited
methods are collapsed by default; press `i` to toggle them. Their full
documentation is fetched only when opened.
Press `C` from a member list to read the full class description. Paragraphs,
code examples, lists, and link targets are preserved in documentation details;
`Since`, `Deprecated`, and `Preview` are shown as text labels when available.
With no terminal, provide a class name for plain-text output. Set `NO_COLOR=1`
to disable color, or `JDOC_CACHE` to choose a different cache directory.

jDoc currently searches JDK documentation, not project dependencies. Methods inherited from `Object` are hidden in the class browser.
If a lookup fails, jDoc checks **other cached releases** for that class or
member and identifies a declaring type when a cached class page confirms it.
An uncached release is reported as unknown rather than incorrectly called absent.
Type and package search indexes are downloaded on first use of a release; the
larger member index is fetched only for reverse member search.
Version diffs fetch and cache both class pages on demand; a complete diff needs
both releases available. New-API and removal reports cache their summary pages
after first use. `--new` filters the cumulative New API page to the requested
release; `--removal` shows the for-removal status in that release, not merely
ordinary deprecation. Preview, deprecation, and removal are separate statuses.

## Contributing

Bug reports and pull requests are welcome. Run `mvn test` before submitting a change.

## License

[MIT](LICENSE).

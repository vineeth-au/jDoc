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
selection, jDoc detects the version in `JAVA_HOME` (or the running Java if
`JAVA_HOME` is unavailable or unusable). It picks the closest supported release
no newer than that version; for example, Java 22 selects Java 21 documentation.
The automatic choice is reported on stderr and is not saved. Use
`./jDoc --docVersion 21` to select a different release persistently.

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

## Contributing

Bug reports and pull requests are welcome. Run `mvn test` before submitting a change.

## License

[MIT](LICENSE).

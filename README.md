# jDoc

jDoc is a macOS command-line (TUI) browser for viewing Java API documentation. Look up JDK classes by short name, browse their documented methods and constructors, and choose a documentation release independently of your installed Java version.

## Requirements

- Java 17 or newer
- Maven to build from source
- Network access for the first lookup of a release or class

## Build and run

From the repository directory:

```sh
mvn package
./jDoc --docVersion 17
./jDoc String
```

The first lookup downloads a class index and documentation page. Subsequent lookups of cached classes work offline.

## Examples

| Command | Result |
| --- | --- |
| `./jDoc --versions` | List supported documentation releases and cache status |
| `./jDoc --docVersion 21` | Select Java 21 documentation for future lookups |
| `./jDoc String` | Browse methods documented on `String` |
| `./jDoc Exception` | Browse the class description and constructors |
| `./jDoc String -repeat` | Show the `repeat` method |
| `./jDoc String -valueOf` | Show the first `valueOf` overload |
| `./jDoc String valueOf -p char-array,int,int` | Find a specific overload |
| `./jDoc --help` | Show all options |

Interactive browsing is available when running in a terminal. Redirected output is plain text. Set `NO_COLOR=1` to disable color, or `JDOC_CACHE` to choose a different cache directory.

jDoc currently searches JDK documentation, not project dependencies or inherited methods.

## Contributing

Bug reports and pull requests are welcome. Run `mvn test` before submitting a change.

## License

[MIT](LICENSE).

# jDoc

Read Java 17, 21, and 25 API documentation in your macOS terminal. Search JDK classes, browse members, and choose a documentation release independently of the Java version running jDoc.

## Get started

You need Java 17 or newer and Maven to build from source. The first lookup of a release or an uncached class requires network access.

From the repository directory:

```sh
mvn package
./jDoc                    # Browse packages and classes interactively
./jDoc String             # Open the String API
./jDoc --docVersion 21    # Save Java 21 as the documentation release
```

Without a saved selection, jDoc detects a release from a local Maven or Gradle project, then `JAVA_HOME`, then the running Java. Run `./jDoc --versions` to see supported releases and cache status. To use a release for one lookup without saving it, run `./jDoc --docVersion 21 String`.

## First steps in the browser

Home starts with `java.*` package groups and search already active. Type a class name such as `ArrayList`, use Up/Down to select a result, and press Enter or Right Arrow to open it. Press Tab on home to include other package families for this session.

| Key | Action |
| --- | --- |
| Enter / Right Arrow | Open the selected package, class, or member |
| Left Arrow | Go back one level; from members to the class's package, then home |
| Escape | Clear the current search and leave search mode |
| `/` | Start a search when the first character conflicts with a shortcut |
| `h` / `q` | Home / quit outside search mode |

Opening a package starts search within it. When you leave search mode with Escape, you can type another class name in that package. Use `./jDoc <Class>` for a direct lookup; with no interactive terminal, supply a class name for plain-text output.

For all commands, member filters, navigation keys, bookmarks, package visibility, version comparisons, and caching behavior, read the **[user guide](docs/User_Guide.md)**. Run `./jDoc --help` for the command-line synopsis. For implementation requirements, see the [project specification](docs/specs.MD).

## Contributing

Bug reports and pull requests are welcome. Run `mvn test` before submitting a change.

## License

[MIT](LICENSE).

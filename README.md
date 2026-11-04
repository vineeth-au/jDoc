# jDoc

Read Java 17, 21, and 25 API documentation in your macOS terminal. Search JDK classes, browse methods, compare APIs across releases, and choose a documentation release independently of the Java version.

## Get started

To install jDoc
```sh
brew install toolfoundrylabs/tap/jdoc
```
> [!IMPORTANT]
> The first lookup of a release needs network access to download its class index and the requested page. Cached pages work offline after that.

## Examples

| Command | Result |
| --- | --- |
| `jdoc` | Browse packages and classes interactively |
| `jdoc String -repeat` | Read a specific method |
| `jdoc String valueOf -p 3` | Filter overloads by parameter count |
| `jdoc Integer.MAX_VALUE` | Read a field |
| `jdoc -m computeIfAbsent` | Find types that declare a method |
| `jdoc --diff 17 21 StringBuilder` | Compare an API across releases |
| `jdoc --new 21` | List APIs introduced in Java 21 |


## Interactive browser

Run `jdoc` with no arguments. Home starts with `java.*` package groups and search already active. Type a class name such as `ArrayList`, use Up/Down to select a result, and press Enter or Right Arrow to open it. Press Tab on home to include other package families for this session.

| Key | Action |
| --- | --- |
| Enter / Right Arrow | Open the selected package, class, or member |
| Left Arrow | Go back one level |
| Escape | Clear the current search and leave search mode |
| `/` | Start a search when the first character conflicts with a shortcut |
| `h` / `q` | Home / quit outside search mode |

Press Escape to leave search mode and use shortcuts. See the [user guide](https://github.com/vineeth-au/jDoc/wiki/Command-Line-Reference) for all keys and member filters. Alternatively, run `jdoc --help` for command-line synopsis.

## Choosing a release

jDoc picks a documentation release from your Maven or Gradle project, then `JAVA_HOME`, then its own runtime, and prints which source it used.

```sh
jdoc --versions                # supported and cached releases
jdoc --docVersion 21           # save Java 21 as the default
jdoc --docVersion 21 String    # use Java 21 for one lookup
```

## Documentation

- [User guide](https://github.com/vineeth-au/jDoc/wiki) covers how to use jDoc effectively.
- [Troubleshooting](https://github.com/vineeth-au/jDoc/wiki/Troubleshooting) Incase you run into issues.

Found a bug? [Open an issue](https://github.com/vineeth-au/jDoc/issues) with the command you ran, its output, and `jdoc --version`.

## Contributing

Bug reports and pull requests are welcome. To build from source, you need Java 17+ and Maven. Refer [Contributing](https://github.com/vineeth-au/jDoc/wiki/Contributing) for more details.

## License

[MIT](LICENSE).

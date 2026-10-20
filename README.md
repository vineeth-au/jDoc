# jDoc

Terminal reader for the official Java SE API documentation on macOS. Select a
documentation release independently of the Java runtime installed on your Mac.
`jDoc` fetches a release's class index and each requested class page on demand,
then caches extracted text and signatures in `~/Library/Caches/jDoc`. A Java 17
or newer runtime is required; a matching JDK and `javap` are not required.
The complete requirements are in [`docs/specs.MD`](docs/specs.MD).

## Build and run

```sh
mvn package
./jDoc --versions
./jDoc --docVersion 17
./jDoc String -repeat
```

The `jDoc` launcher is executable. To invoke it from another directory, add
this repository to your `PATH`, or link the launcher in a directory already on
your `PATH`. If you want the short command, set your own zsh alias:

```sh
alias j='jDoc'
```

Put that line in `~/.zshrc` to make it persistent. The application does not
install or overwrite a `j` command.

## Commands

| Command | Meaning |
| --- | --- |
| `jDoc --help` | Show full usage. |
| `jDoc --version`, `jDoc -v` | Show the application version. |
| `jDoc --versions` | Show supported versions (8, 11, 17, 21, 25), selection, and cache status. |
| `jDoc --docVersion 17` | Set the default documentation version persistently. |
| `jDoc String` | Show signatures and documentation of methods directly on `String`'s page. |
| `jDoc Exception` | Show the class description and constructors when no methods are declared directly. |
| `jDoc String -valueOf` | Show the first overload in that release's Javadoc method-summary order. |
| `jDoc String -p 1` | Show all directly documented methods with one parameter. |
| `jDoc String valueOf -p 3` | Show three-parameter `valueOf` overloads. |
| `jDoc String valueOf -p char-array,int,int` | Select the exact overload. |

`--params` is an alias for `-p`. A **number** following it is a **parameter
count**, never an overload number. Parameter *types* require a method name.
Use `-array` for array types (for example, `char-array` means `char[]`). Spaces
after commas work too: `jDoc String valueOf -p char-array, int, int`. Put `-p`
last in the command. `jDoc String 'valueOf(char[], int, int)'` is not the
preferred syntax; parentheses and spaces require quoting in macOS shells.

Short class names are resolved through the selected release's class index.
Ambiguous names offer qualified choices; close misspellings offer suggestions.
Interactive prompts require a terminal. In scripts and pipes, use qualified
class names or correct spelling instead. Large results are displayed with
the interactive JLine method browser when run in a terminal. Type to filter
signatures (`/` starts a search that can include `q`); use ↑/↓ or Page Up/Down
to move, Enter to read full documentation, `b` to go back, and `q` to quit.
The browser preserves the release's Javadoc method-summary order. Direct
method lookups print documentation with colored headings, signatures and
section labels. Set `NO_COLOR=1` for a monochrome terminal; redirected output
is always plain text.

The first lookup of a release needs network access to fetch its class index;
the first lookup of each class needs network access for that class page. Cached
class lookups work offline. Set `JDOC_CACHE` to override the cache location.
Inherited methods (`-i`), project dependencies, and complete offline archives
are future features.

package fixture.api;

/** An interface with default and static methods, and a nested type. */
public interface Dictionary {
  /** Read a value. */
  String get(String key);

  /** Read a value or use a fallback. */
  default String getOrDefault(String key, String fallback) {
    String value = get(key);
    return value == null ? fallback : value;
  }

  /** Build a dictionary entry. */
  static Entry entry(String key, String value) { return new Entry(key, value); }

  /** A key-value pair. */
  record Entry(String key, String value) {}
}

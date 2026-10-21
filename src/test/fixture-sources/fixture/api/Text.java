package fixture.api;

/** Sample text with overloaded methods and structured documentation. */
public class Text {
  /** Create empty text. */
  public Text() {}

  /** Older constructor.
   * @deprecated Use {@link #Text()} instead.
   */
  @Deprecated(since = "17")
  public Text(char[] characters) {}

  /** Convert a boolean. */
  public static Text valueOf(boolean value) { return new Text(); }

  /** Convert part of an array.
   * @param value the characters
   * @param offset the first position
   * @param count the number of characters
   * @return a new text value
   */
  public static Text valueOf(char[] value, int offset, int count) { return new Text(); }

  /** Format arguments.
   * <p>Example:</p>
   * <pre>{@code Text.format("%s", "hello");}</pre>
   * <ul><li>Retains the first value.</li><li>Accepts more values.</li></ul>
   * @since 17
   */
  public static Text format(String template, Object... values) { return new Text(); }
}

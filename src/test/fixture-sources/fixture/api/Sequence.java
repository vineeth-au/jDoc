package fixture.api;

/** A class with its own methods and inherited class and interface methods. */
public class Sequence extends BaseSequence implements SequenceView {
  /** Add an element. */
  public boolean add(String value) { return true; }
}

/** Shared class implementation. */
class BaseSequence {
  /** Test whether all values are present. */
  public boolean containsAll(java.util.Collection<?> values) { return true; }
}

/** Interface with a default method. */
interface SequenceView {
  /** Create a stream of values. */
  default java.util.stream.Stream<String> stream() { return java.util.stream.Stream.empty(); }
}

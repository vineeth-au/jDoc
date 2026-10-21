package fixture.api;

/** A checked failure whose page documents constructors but no methods. */
public class Failure extends Exception {
  /** Construct a failure. */
  public Failure() {}

  /** Construct with a message. */
  public Failure(String message) { super(message); }

  /** Construct with a cause. */
  public Failure(Throwable cause) { super(cause); }

  /** Construct with a message and cause. */
  public Failure(String message, Throwable cause) { super(message, cause); }

  /** Construct with suppression and stack trace options. */
  protected Failure(String message, Throwable cause, boolean suppression, boolean writable) {
    super(message, cause, suppression, writable);
  }
}

package fixture.api;

/** Units used to characterize enum Javadoc pages. */
public enum Unit {
  /** The first unit. */ FIRST,
  /** The second unit. */ SECOND;

  /** Return a user-facing label. */
  public String label() { return name(); }
}

package starter.loans.domain;

/**
 * Typed identifier of a member (B5): another aggregate is referenced by its id, never by its state,
 * and a MemberId cannot be passed where a different id is expected.
 *
 * @param value the raw identifier
 */
public record MemberId(long value) {}

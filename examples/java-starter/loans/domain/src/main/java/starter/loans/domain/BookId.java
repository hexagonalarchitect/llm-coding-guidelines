package starter.loans.domain;

/**
 * Typed identifier of a book (B5): another aggregate is referenced by its id, never by its state,
 * and a BookId cannot be passed where a different id is expected.
 *
 * @param value the raw identifier
 */
public record BookId(long value) {}

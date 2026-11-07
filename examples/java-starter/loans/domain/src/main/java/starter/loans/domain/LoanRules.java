package starter.loans.domain;

/** Named domain constants for library loans, kept in one rules module (G7). */
public final class LoanRules {

    /** Shortest loan the library grants, in days. */
    public static final int MIN_LOAN_DAYS = 1;

    /** Longest loan the library grants, in days. */
    public static final int MAX_LOAN_DAYS = 30;

    private LoanRules() {}
}

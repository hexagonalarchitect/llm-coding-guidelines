package starter.loans.domain;

/**
 * BREACH of C12-no-default-arm: a default arm over the loan's own states. It compiles, and it
 * silently files any state added later under "not out", where an exhaustive switch would have
 * refused to compile until someone decided.
 */
public final class DefaultHidesACase {

    private DefaultHidesACase() {}

    /**
     * Whether the book is still out.
     *
     * @param state the loan's state
     * @return true while the loan is active
     */
    public static boolean isOut(Loan.State state) {
        return switch (state) {
            case Loan.Active active -> true;
            default -> false;
        };
    }
}

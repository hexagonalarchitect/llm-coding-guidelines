package starter.loans.domain;

import starter.kernel.Result;

/**
 * A loan length in days, bounded to {@code MIN_LOAN_DAYS..MAX_LOAN_DAYS} (C3).
 *
 * <p>The representation is hidden, the constructor is private, and {@link #create} is the only way
 * in: it returns the value or a typed reason, never null and never an exception.
 */
public final class LoanDays implements Comparable<LoanDays> {

    /** Why a raw day count is not a legal {@link LoanDays}. */
    public enum Invalid {
        /** The count is below {@link LoanRules#MIN_LOAN_DAYS}. */
        BELOW_MINIMUM,
        /** The count is above {@link LoanRules#MAX_LOAN_DAYS}. */
        ABOVE_MAXIMUM
    }

    private final int days;

    private LoanDays(int days) {
        this.days = days;
    }

    /**
     * Validates a raw day count.
     *
     * @param raw the requested number of days
     * @return the constrained value, or why it was refused
     */
    public static Result<LoanDays, Invalid> create(int raw) {
        if (raw < LoanRules.MIN_LOAN_DAYS) {
            return Result.err(Invalid.BELOW_MINIMUM);
        }
        if (raw > LoanRules.MAX_LOAN_DAYS) {
            return Result.err(Invalid.ABOVE_MAXIMUM);
        }
        return Result.ok(new LoanDays(raw));
    }

    /**
     * Returns the validated day count.
     *
     * @return the number of days
     */
    public int value() {
        return days;
    }

    @Override
    public int compareTo(LoanDays other) {
        return Integer.compare(days, other.days);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LoanDays that && that.days == days;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(days);
    }

    @Override
    public String toString() {
        return "LoanDays[" + days + "]";
    }
}

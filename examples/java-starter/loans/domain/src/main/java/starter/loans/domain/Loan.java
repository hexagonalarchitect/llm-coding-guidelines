package starter.loans.domain;

import java.time.LocalDate;
import java.util.List;
import starter.kernel.Result;

/**
 * The loan aggregate as a pure decider (B3): {@link #decide}, {@link #evolve} and
 * {@link #isTerminal} over sealed states, commands and events.
 *
 * <p>Every {@code switch} lists every case and has no {@code default} arm (C12), so adding a
 * state, command or event is a compile error until each decision is made. The date is passed in
 * as the leading parameter; the domain never reads a clock (D1). Event lists are immutable (C2).
 */
public final class Loan {

    private Loan() {}

    /** Lifecycle state of a loan. */
    public sealed interface State permits Active, Ended {}

    /**
     * The book is out with a member.
     *
     * @param loanId the loan
     * @param bookId the borrowed book
     * @param memberId the borrowing member
     * @param duration the approved length
     * @param since the date of the checkout or of the latest renewal
     */
    public record Active(LoanId loanId, BookId bookId, MemberId memberId, LoanDays duration, LocalDate since)
            implements State {}

    /**
     * The book is back; the loan has ended and refuses every command.
     *
     * @param loanId the loan
     * @param memberId the member who returned it
     * @param returnedOn the date it came back
     */
    public record Ended(LoanId loanId, MemberId memberId, LocalDate returnedOn) implements State {}

    /** What a member or librarian asks of an existing loan. */
    public sealed interface Command permits Renew, Return {}

    /**
     * Extend the loan to a new, longer duration.
     *
     * @param duration the requested duration
     */
    public record Renew(LoanDays duration) implements Command {}

    /** Record that the book has come back. */
    public record Return() implements Command {}

    /** Past-tense facts; each carries the loan id (B7). */
    public sealed interface Event permits Opened, Renewed, Returned {}

    /**
     * A member checked out a book. Opening is a create step with no prior state, so it is built
     * directly rather than decided; every later command goes through {@link #decide}.
     *
     * @param loanId the loan
     * @param bookId the book
     * @param memberId the member
     * @param duration the approved length
     * @param openedOn the checkout date
     */
    public record Opened(LoanId loanId, BookId bookId, MemberId memberId, LoanDays duration, LocalDate openedOn)
            implements Event {}

    /**
     * An active loan was extended.
     *
     * @param loanId the loan
     * @param duration the new duration
     * @param renewedOn the renewal date
     */
    public record Renewed(LoanId loanId, LoanDays duration, LocalDate renewedOn) implements Event {}

    /**
     * The book came back.
     *
     * @param loanId the loan
     * @param memberId the member who returned it
     * @param returnedOn the return date
     */
    public record Returned(LoanId loanId, MemberId memberId, LocalDate returnedOn) implements Event {}

    /** Why {@link #decide} turned a command down: a refusal, never a silent empty success (B3). */
    public enum Refusal {
        /** The loan has ended, and an ended loan refuses every command. */
        LOAN_ENDED,
        /** A renewal must ask for more days than the loan already has. */
        NOT_AN_EXTENSION
    }

    /**
     * Decides what a command does to a loan; the only place business decisions live (B3).
     *
     * @param today the leading parameter: the business date
     * @param command the command
     * @param state the current state
     * @return the resulting events, or a refusal
     */
    public static Result<List<Event>, Refusal> decide(LocalDate today, Command command, State state) {
        return switch (state) {
            case Active active -> switch (command) {
                case Renew renew -> renew.duration().compareTo(active.duration()) > 0
                        ? Result.ok(List.of(new Renewed(active.loanId(), renew.duration(), today)))
                        : Result.err(Refusal.NOT_AN_EXTENSION);
                case Return ignored -> Result.ok(List.of(new Returned(active.loanId(), active.memberId(), today)));
            };
            case Ended ended -> switch (command) {
                case Renew ignored -> Result.err(Refusal.LOAN_ENDED);
                case Return ignored -> Result.err(Refusal.LOAN_ENDED);
            };
        };
    }

    /**
     * The state right after a loan opens: the start of every fold.
     *
     * @param opened the opening event
     * @return the active loan
     */
    public static State start(Opened opened) {
        return new Active(opened.loanId(), opened.bookId(), opened.memberId(), opened.duration(), opened.openedOn());
    }

    /**
     * Folds one event into the state; total over every (state, event) pair (B3, C6).
     *
     * @param state the current state
     * @param event the event to apply
     * @return the next state
     */
    public static State evolve(State state, Event event) {
        return switch (event) {
            case Opened opened -> start(opened);
            case Renewed renewed -> switch (state) {
                case Active active -> new Active(
                        active.loanId(), active.bookId(), active.memberId(), renewed.duration(), renewed.renewedOn());
                case Ended ended -> ended;
            };
            case Returned returned -> new Ended(returned.loanId(), returned.memberId(), returned.returnedOn());
        };
    }

    /**
     * Whether the loan has reached its end (B3).
     *
     * @param state the state
     * @return true once the book is back
     */
    public static boolean isTerminal(State state) {
        return switch (state) {
            case Active active -> false;
            case Ended ended -> true;
        };
    }
}

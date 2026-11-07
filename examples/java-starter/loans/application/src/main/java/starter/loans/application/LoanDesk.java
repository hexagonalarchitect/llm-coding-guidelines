package starter.loans.application;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import starter.infrastructure.notices.NoticeLog;
import starter.kernel.Result;
import starter.loans.domain.Loan;

/**
 * The loan workflow: ask the decider, fold its events, and tell the member when a book is back.
 *
 * <p>An application service with infrastructure below it is Nullable too (E5): {@link #create}
 * wires the real wrapper, {@link #createNull} the embedded stub. No mocking library is needed.
 */
public final class LoanDesk {

    /** Text key of the notice sent when a book comes back. */
    public static final String RETURN_NOTICE = "loan.returned";

    /** The workflow's error union: a domain refusal or an infrastructure failure (C11). */
    public sealed interface Failure permits Refused, NoticeNotSent {}

    /**
     * The domain refused the command.
     *
     * @param refusal the domain's reason
     */
    public record Refused(Loan.Refusal refusal) implements Failure {}

    /**
     * The command was decided, but the member could not be told.
     *
     * @param failure the wrapper's reason
     */
    public record NoticeNotSent(NoticeLog.Failure failure) implements Failure {}

    private final NoticeLog notices;

    private LoanDesk(NoticeLog notices) {
        this.notices = notices;
    }

    /**
     * The real service.
     *
     * @param noticeFile where notices are written
     * @return the service
     */
    public static LoanDesk create(Path noticeFile) {
        return new LoanDesk(NoticeLog.create(noticeFile));
    }

    /**
     * A Nullable service whose notices always succeed.
     *
     * @return the service
     */
    public static LoanDesk createNull() {
        return new LoanDesk(NoticeLog.createNull());
    }

    /**
     * A Nullable service over a configured Nullable wrapper, whose outputs the caller can read.
     *
     * @param notices a wrapper built with {@link NoticeLog#createNull}
     * @return the service
     */
    public static LoanDesk createNull(NoticeLog notices) {
        return new LoanDesk(notices);
    }

    /**
     * Handles one command against the current state of a loan.
     *
     * @param today the business date, read by the host
     * @param state the loan's current state
     * @param command the command
     * @return the new state, or why nothing changed
     */
    public Result<Loan.State, Failure> handle(LocalDate today, Loan.State state, Loan.Command command) {
        return switch (Loan.decide(today, command, state)) {
            case Result.Err<List<Loan.Event>, Loan.Refusal> refused -> Result.err(new Refused(refused.error()));
            case Result.Ok<List<Loan.Event>, Loan.Refusal> decided -> apply(state, decided.value());
        };
    }

    private Result<Loan.State, Failure> apply(Loan.State state, List<Loan.Event> events) {
        Loan.State next = state;
        for (Loan.Event event : events) {
            next = Loan.evolve(next, event);
            Optional<NoticeLog.Failure> failed = notify(event);
            if (failed.isPresent()) {
                return Result.err(new NoticeNotSent(failed.get()));
            }
        }
        return Result.ok(next);
    }

    /** Sends the notice an event calls for, if any; returns the failure when sending failed. */
    private Optional<NoticeLog.Failure> notify(Loan.Event event) {
        return switch (event) {
            case Loan.Returned returned -> switch (notices.send(returned.memberId().value(), RETURN_NOTICE)) {
                case Result.Ok<NoticeLog.NoticeSent, NoticeLog.Failure> sent -> Optional.empty();
                case Result.Err<NoticeLog.NoticeSent, NoticeLog.Failure> err -> Optional.of(err.error());
            };
            case Loan.Opened opened -> Optional.empty();
            case Loan.Renewed renewed -> Optional.empty();
        };
    }
}

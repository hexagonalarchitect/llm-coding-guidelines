package starter.loans.application;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import starter.infrastructure.notices.NoticeLog;
import starter.kernel.Result;
import starter.loans.domain.BookId;
import starter.loans.domain.Loan;
import starter.loans.domain.LoanDays;
import starter.loans.domain.LoanId;
import starter.loans.domain.MemberId;
import starter.testing.Expect;

/**
 * Scenario tests of the loan workflow over a Nullable wrapper (E1, E5, E9): no mocking library,
 * and the recorded outputs are compared as a whole list against literals.
 */
@Tag("scenario")
class LoanDeskTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    @Test
    void returningABookEndsTheLoanAndNotifiesTheMember(Expect expect) {
        NoticeLog notices = NoticeLog.createNull();
        LoanDesk desk = LoanDesk.createNull(notices);

        Result<Loan.State, LoanDesk.Failure> outcome = desk.handle(DAY, active(), new Loan.Return());

        expect.equal(Result.ok(new Loan.Ended(new LoanId(1), new MemberId(42), DAY)), outcome);
        expect.equal(List.of(new NoticeLog.NoticeSent(42, LoanDesk.RETURN_NOTICE)), notices.outputs());
    }

    @Test
    void aRefusedCommandSendsNothing(Expect expect) {
        NoticeLog notices = NoticeLog.createNull();
        LoanDesk desk = LoanDesk.createNull(notices);
        Loan.State ended = new Loan.Ended(new LoanId(1), new MemberId(42), DAY);

        expect.equal(Result.err(new LoanDesk.Refused(Loan.Refusal.LOAN_ENDED)),
                desk.handle(DAY, ended, new Loan.Return()));
        expect.equal(List.of(), notices.outputs());
    }

    @Test
    void aFailedNoticeIsReportedAsAValue(Expect expect) {
        NoticeLog notices = NoticeLog.createNull(List.of(NoticeLog.Response.WRITE_FAILED));
        LoanDesk desk = LoanDesk.createNull(notices);

        expect.equal(Result.err(new LoanDesk.NoticeNotSent(NoticeLog.Failure.WRITE_FAILED)),
                desk.handle(DAY, active(), new Loan.Return()));
        expect.equal(List.of(), notices.outputs());
    }

    private static Loan.State active() {
        return switch (LoanDays.create(14)) {
            case Result.Ok<LoanDays, LoanDays.Invalid> ok ->
                    new Loan.Active(new LoanId(1), new BookId(7), new MemberId(42), ok.value(), DAY.minusDays(3));
            case Result.Err<LoanDays, LoanDays.Invalid> err -> throw new IllegalStateException(err.error().name());
        };
    }
}

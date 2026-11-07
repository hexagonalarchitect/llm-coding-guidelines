package starter.loans.domain;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import starter.kernel.Result;
import starter.testing.Expect;

/** Examples of the loan decider, read as its specification (B3). Logic tier: 20 ms each (E8). */
class LoanTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);
    private static final Loan.Active ACTIVE =
            new Loan.Active(new LoanId(1), new BookId(7), new MemberId(42), days(14), DAY);

    @Test
    void returningAnActiveLoanEndsIt(Expect expect) {
        Result<List<Loan.Event>, Loan.Refusal> decided = Loan.decide(DAY.plusDays(3), new Loan.Return(), ACTIVE);

        expect.equal(Result.ok(List.of(new Loan.Returned(new LoanId(1), new MemberId(42), DAY.plusDays(3)))), decided);
    }

    @Test
    void renewingToTheSameLengthIsRefused(Expect expect) {
        expect.equal(Result.err(Loan.Refusal.NOT_AN_EXTENSION), Loan.decide(DAY, new Loan.Renew(days(14)), ACTIVE));
    }

    @Test
    void anEndedLoanRefusesARenewal(Expect expect) {
        Loan.State ended = Loan.evolve(ACTIVE, new Loan.Returned(new LoanId(1), new MemberId(42), DAY));

        expect.that(Loan.isTerminal(ended), "a returned loan is terminal");
        expect.equal(Result.err(Loan.Refusal.LOAN_ENDED), Loan.decide(DAY, new Loan.Renew(days(20)), ended));
    }

    @Test
    void loanDaysAreValues(Expect expect) {
        expect.equal(days(5), days(5));
        expect.that(!days(5).equals(days(6)), "5 days differ from 6 days");
    }

    static LoanDays days(int raw) {
        return switch (LoanDays.create(raw)) {
            case Result.Ok<LoanDays, LoanDays.Invalid> ok -> ok.value();
            case Result.Err<LoanDays, LoanDays.Invalid> err -> throw new IllegalArgumentException(err.error().name());
        };
    }
}

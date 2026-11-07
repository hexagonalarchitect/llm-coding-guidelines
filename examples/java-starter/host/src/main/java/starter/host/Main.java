package starter.host;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import starter.kernel.Result;
import starter.loans.application.LoanDesk;
import starter.loans.domain.BookId;
import starter.loans.domain.Loan;
import starter.loans.domain.LoanDays;
import starter.loans.domain.LoanId;
import starter.loans.domain.MemberId;

/** Runs one loan through renew, return and a refused second return, writing real notices. */
public final class Main {

    private Main() {}

    /**
     * Entry point.
     *
     * @param args optional path of the notice file
     */
    public static void main(String[] args) {
        Path noticeFile = Path.of(args.length > 0 ? args[0] : "build/notices.jsonl");
        // The host owns the clock and the time zone; the core never reads either (D1).
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LoanDesk desk = LoanDesk.create(noticeFile);

        Loan.State loan = Loan.start(new Loan.Opened(new LoanId(1), new BookId(7), new MemberId(42), days(14), today));
        System.out.println("opened:   " + loan);

        loan = step(desk.handle(today, loan, new Loan.Renew(days(21))), loan);
        loan = step(desk.handle(today, loan, new Loan.Return()), loan);
        step(desk.handle(today, loan, new Loan.Return()), loan);
        System.out.println("notices written to " + noticeFile.toAbsolutePath());
    }

    private static Loan.State step(Result<Loan.State, LoanDesk.Failure> outcome, Loan.State before) {
        return switch (outcome) {
            case Result.Ok<Loan.State, LoanDesk.Failure> ok -> {
                System.out.println("now:      " + ok.value());
                yield ok.value();
            }
            case Result.Err<Loan.State, LoanDesk.Failure> err -> {
                System.out.println("refused:  " + err.error());
                yield before;
            }
        };
    }

    private static LoanDays days(int raw) {
        return switch (LoanDays.create(raw)) {
            case Result.Ok<LoanDays, LoanDays.Invalid> ok -> ok.value();
            case Result.Err<LoanDays, LoanDays.Invalid> err ->
                    throw new IllegalArgumentException("bad demo input " + raw + ": " + err.error());
        };
    }
}

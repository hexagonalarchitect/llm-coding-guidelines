package starter.loans.domain;

import java.time.LocalDate;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import starter.kernel.Result;

/**
 * Properties of the loan decider and of {@link LoanDays} (B3, C3). Each property has a fixed seed,
 * so a run is the same on every machine (D-group); a property returns its verdict as a boolean.
 */
class LoanProperties {

    private static final String SEED = "20261107";

    @Property(seed = SEED, tries = 100)
    boolean loanDaysAcceptsExactlyTheLegalRange(@ForAll @IntRange(min = -5, max = 40) int raw) {
        boolean legal = raw >= LoanRules.MIN_LOAN_DAYS && raw <= LoanRules.MAX_LOAN_DAYS;
        return switch (LoanDays.create(raw)) {
            case Result.Ok<LoanDays, LoanDays.Invalid> ok -> legal && ok.value().value() == raw;
            case Result.Err<LoanDays, LoanDays.Invalid> err -> !legal
                    && err.error() == (raw < LoanRules.MIN_LOAN_DAYS
                            ? LoanDays.Invalid.BELOW_MINIMUM
                            : LoanDays.Invalid.ABOVE_MAXIMUM);
        };
    }

    @Property(seed = SEED, tries = 200)
    boolean decideIsDeterministic(
            @ForAll("dates") LocalDate today, @ForAll("commands") Loan.Command command, @ForAll("states") Loan.State state) {
        return Loan.decide(today, command, state).equals(Loan.decide(today, command, state));
    }

    @Property(seed = SEED, tries = 200)
    boolean evolveIsTotalAndEndsOnlyOnReturn(@ForAll("states") Loan.State state, @ForAll("events") Loan.Event event) {
        boolean ends = switch (event) {
            case Loan.Opened opened -> false;
            case Loan.Renewed renewed -> Loan.isTerminal(state);
            case Loan.Returned returned -> true;
        };
        return Loan.isTerminal(Loan.evolve(state, event)) == ends;
    }

    @Property(seed = SEED, tries = 200)
    boolean aTerminalLoanRefusesEveryCommand(
            @ForAll("dates") LocalDate today, @ForAll("commands") Loan.Command command, @ForAll("ended") Loan.Ended state) {
        return Loan.decide(today, command, state).equals(Result.err(Loan.Refusal.LOAN_ENDED));
    }

    @Property(seed = SEED, tries = 200)
    boolean aRenewalMustExtend(@ForAll("dates") LocalDate today, @ForAll("active") Loan.Active state,
            @ForAll("days") LoanDays duration) {
        boolean longer = duration.value() > state.duration().value();
        return Loan.decide(today, new Loan.Renew(duration), state) instanceof Result.Ok<?, ?> == longer;
    }

    @Provide
    Arbitrary<LocalDate> dates() {
        return Arbitraries.longs().between(0, 40_000).map(LocalDate::ofEpochDay);
    }

    @Provide
    Arbitrary<LoanDays> days() {
        return Arbitraries.integers().between(LoanRules.MIN_LOAN_DAYS, LoanRules.MAX_LOAN_DAYS).map(LoanTest::days);
    }

    @Provide
    Arbitrary<Loan.Active> active() {
        return Combinators.combine(ids(), ids(), ids(), days(), dates()).as((loan, book, member, days, since) ->
                new Loan.Active(new LoanId(loan), new BookId(book), new MemberId(member), days, since));
    }

    @Provide
    Arbitrary<Loan.Ended> ended() {
        return Combinators.combine(ids(), ids(), dates())
                .as((loan, member, on) -> new Loan.Ended(new LoanId(loan), new MemberId(member), on));
    }

    @Provide
    Arbitrary<Loan.State> states() {
        return Arbitraries.oneOf(active(), ended());
    }

    @Provide
    Arbitrary<Loan.Command> commands() {
        return Arbitraries.oneOf(days().map(Loan.Renew::new), Arbitraries.just(new Loan.Return()));
    }

    @Provide
    Arbitrary<Loan.Event> events() {
        Arbitrary<Loan.Event> opened = Combinators.combine(ids(), ids(), ids(), days(), dates()).as((loan, book,
                member, days, on) -> new Loan.Opened(new LoanId(loan), new BookId(book), new MemberId(member), days, on));
        Arbitrary<Loan.Event> renewed = Combinators.combine(ids(), days(), dates())
                .as((loan, days, on) -> new Loan.Renewed(new LoanId(loan), days, on));
        Arbitrary<Loan.Event> returned = Combinators.combine(ids(), ids(), dates())
                .as((loan, member, on) -> new Loan.Returned(new LoanId(loan), new MemberId(member), on));
        return Arbitraries.oneOf(opened, renewed, returned);
    }

    private static Arbitrary<Long> ids() {
        return Arbitraries.longs().between(1, 1_000);
    }
}

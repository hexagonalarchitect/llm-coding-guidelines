package starter.loans.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** BREACH of D1-pure-core: the domain reads the clock instead of taking the date as a parameter. */
public final class DueDateFromTheClock {

    private DueDateFromTheClock() {}

    /** Computes a due date from today's date as read from the system clock. */
    public static LocalDate dueDate(LoanDays days) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(days.value());
    }
}

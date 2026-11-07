package starter.breaches;

import org.junit.jupiter.api.Test;
import starter.loans.domain.LoanDays;

/** BREACH of E22-asserts-something: a test that runs code and asserts nothing. */
class AssertsNothingTest {

    @Test
    void runsButChecksNothing() {
        LoanDays.create(3).map(LoanDays::value);
    }
}

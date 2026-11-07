package starter.loans.domain;

import starter.loans.application.LoanDesk;

/** BREACH of A1-layers: the domain names an application type, a layer above it. */
public final class DomainReachesUp {

    private DomainReachesUp() {}

    /** Returns the refusal case of the workflow's error union. */
    public static Class<?> leak() {
        return LoanDesk.Refused.class;
    }
}

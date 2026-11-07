package starter.loans.codec;

import starter.loans.application.LoanDesk;

/** BREACH of A1-codec-layer (dormant): a codec names an application type. */
public final class CodecReachesUp {

    private CodecReachesUp() {}

    /** Returns the refusal case of the workflow's error union. */
    public static Class<?> leak() {
        return LoanDesk.Refused.class;
    }
}

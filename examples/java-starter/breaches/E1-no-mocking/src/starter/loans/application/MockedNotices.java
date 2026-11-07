package starter.loans.application;

import org.mockito.Mockito;
import starter.infrastructure.notices.NoticeLog;

/** BREACH of E1-no-mocking: a test fixture built with a mocking library instead of createNull. */
public final class MockedNotices {

    private MockedNotices() {}

    /** Returns a mock in place of the Nullable wrapper. */
    public static NoticeLog notices() {
        return Mockito.mock(NoticeLog.class);
    }
}

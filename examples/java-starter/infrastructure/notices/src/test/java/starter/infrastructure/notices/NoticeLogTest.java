package starter.infrastructure.notices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import starter.kernel.Result;
import starter.testing.Expect;

/** Narrow tests of the wrapper: the real backend against a temporary file, and the Nullable one. */
@Tag("scenario")
class NoticeLogTest {

    @Test
    void theRealWrapperAppendsOneJsonLinePerNotice(@TempDir Path dir, Expect expect) throws IOException {
        Path file = dir.resolve("notices.jsonl");
        NoticeLog log = NoticeLog.create(file);

        log.send(42, "loan.returned");
        log.send(43, "loan.returned");

        expect.equal(List.of("{\"recipient\":42,\"key\":\"loan.returned\"}", "{\"recipient\":43,\"key\":\"loan.returned\"}"),
                Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    void theRealWrapperReportsAnUnwritableFileAsAValue(@TempDir Path dir, Expect expect) {
        NoticeLog log = NoticeLog.create(dir); // a directory cannot be appended to

        expect.equal(Result.err(NoticeLog.Failure.WRITE_FAILED), log.send(42, "loan.returned"));
        expect.equal(List.of(), log.outputs());
    }

    @Test
    void theNullableWrapperFailsLoudlyWhenItsResponsesRunOut(Expect expect) {
        NoticeLog log = NoticeLog.createNull(List.of(NoticeLog.Response.DELIVERED));
        log.send(1, "a");

        boolean loud;
        try {
            log.send(2, "b");
            loud = false;
        } catch (IllegalStateException e) {
            loud = true;
        }
        expect.that(loud, "a second send past the configured responses throws");
    }
}

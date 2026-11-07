package starter.infrastructure.notices;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import starter.kernel.Result;

/**
 * Writes member notices as JSON lines: a Nullable infrastructure wrapper (E1, E5).
 *
 * <p>{@link #create} gives the real wrapper and {@link #createNull} an embedded stub; neither does
 * I/O. Every successful send is recorded as a {@link NoticeSent} behaviour value that tests read
 * through {@link #outputs}. Gson is used here and nowhere else (A10).
 */
public final class NoticeLog {

    /**
     * A recorded output: a notice went to a member (E5).
     *
     * @param recipient the member's raw id
     * @param key the notice text key
     */
    public record NoticeSent(long recipient, String key) {}

    /** Why a notice could not be sent: an infrastructure failure, as a value (C11). */
    public enum Failure {
        /** The notice file could not be written. */
        WRITE_FAILED
    }

    /** A configured response for the Nullable wrapper. */
    public enum Response {
        /** The next send succeeds. */
        DELIVERED,
        /** The next send fails with {@link Failure#WRITE_FAILED}. */
        WRITE_FAILED
    }

    private sealed interface Backend permits Real, Null {}

    private record Real(Path file, Gson gson) implements Backend {}

    private record Null(Deque<Response> canned, boolean always) implements Backend {}

    private final Backend backend;
    private final List<NoticeSent> recorded = new ArrayList<>();

    private NoticeLog(Backend backend) {
        this.backend = backend;
    }

    /**
     * The real wrapper. Stores configuration only; the file is first touched on {@link #send}.
     *
     * @param file the JSON-lines file to append to
     * @return the wrapper
     */
    public static NoticeLog create(Path file) {
        return new NoticeLog(new Real(file, new Gson()));
    }

    /**
     * A Nullable wrapper whose every send succeeds without I/O.
     *
     * @return the wrapper
     */
    public static NoticeLog createNull() {
        return new NoticeLog(new Null(new ArrayDeque<>(), true));
    }

    /**
     * A Nullable wrapper that plays the given responses in order and fails loudly when they run out.
     *
     * @param responses the responses, one per send
     * @return the wrapper
     */
    public static NoticeLog createNull(List<Response> responses) {
        return new NoticeLog(new Null(new ArrayDeque<>(responses), false));
    }

    /**
     * Sends one notice, or replays the next configured response.
     *
     * @param recipient the member's raw id
     * @param key the notice text key
     * @return the recorded behaviour, or why it failed
     */
    public Result<NoticeSent, Failure> send(long recipient, String key) {
        NoticeSent notice = new NoticeSent(recipient, key);
        Result<NoticeSent, Failure> outcome = switch (backend) {
            case Real real -> write(real, notice);
            case Null nul -> replay(nul, notice);
        };
        if (outcome instanceof Result.Ok<NoticeSent, Failure>) {
            recorded.add(notice);
        }
        return outcome;
    }

    /**
     * The notices sent so far, in order (E5, E9).
     *
     * @return an immutable copy
     */
    public List<NoticeSent> outputs() {
        return List.copyOf(recorded);
    }

    private static Result<NoticeSent, Failure> write(Real real, NoticeSent notice) {
        String line = real.gson().toJson(notice) + "\n";
        try {
            Files.writeString(real.file(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return Result.ok(notice);
        } catch (IOException e) {
            return Result.err(Failure.WRITE_FAILED);
        }
    }

    private static Result<NoticeSent, Failure> replay(Null nul, NoticeSent notice) {
        if (nul.always()) {
            return Result.ok(notice);
        }
        Response next = nul.canned().pollFirst();
        if (next == null) {
            // E5: a configured response list that runs out fails loudly; this is a test defect.
            throw new IllegalStateException("NoticeLog.createNull responses exhausted at " + notice);
        }
        return switch (next) {
            case DELIVERED -> Result.ok(notice);
            case WRITE_FAILED -> Result.err(Failure.WRITE_FAILED);
        };
    }
}

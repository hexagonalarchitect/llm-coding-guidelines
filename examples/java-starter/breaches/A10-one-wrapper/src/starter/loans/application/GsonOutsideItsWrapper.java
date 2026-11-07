package starter.loans.application;

import com.google.gson.Gson;

/** BREACH of A10-one-wrapper: Gson used outside its one wrapper, starter.infrastructure.notices. */
public final class GsonOutsideItsWrapper {

    private GsonOutsideItsWrapper() {}

    /** Encodes a value with Gson directly. */
    public static String encode(Object value) {
        return new Gson().toJson(value);
    }
}

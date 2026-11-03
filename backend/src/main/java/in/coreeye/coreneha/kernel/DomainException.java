package in.coreeye.coreneha.kernel;

/** Base for business errors that map to a problem response. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable machine-readable error code, for example {@code assignment.self-approval}. */
    public String code() {
        return code;
    }
}

package in.coreeye.coreneha.kernel;

/** Concurrent change or uniqueness conflict. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super("conflict", message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}

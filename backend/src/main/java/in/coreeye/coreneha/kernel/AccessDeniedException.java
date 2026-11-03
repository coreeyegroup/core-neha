package in.coreeye.coreneha.kernel;

/** The actor lacks the permission at the scope. Already audited when raised. */
public class AccessDeniedException extends DomainException {

    public AccessDeniedException(String message) {
        super("access-denied", message);
    }

    public AccessDeniedException(String code, String message) {
        super(code, message);
    }
}

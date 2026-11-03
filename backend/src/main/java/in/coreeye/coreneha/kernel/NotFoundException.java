package in.coreeye.coreneha.kernel;

/** The object does not exist in this tenant (or is not visible). */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super("not-found", message);
    }

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}

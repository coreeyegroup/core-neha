package in.coreeye.coreneha.kernel;

/** No valid login for the request. */
public class UnauthenticatedException extends DomainException {

    public UnauthenticatedException(String message) {
        super("unauthenticated", message);
    }

    public UnauthenticatedException(String code, String message) {
        super(code, message);
    }
}

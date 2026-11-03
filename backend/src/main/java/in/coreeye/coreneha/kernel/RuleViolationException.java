package in.coreeye.coreneha.kernel;

/** A business rule refused the change (for example self-approval). */
public class RuleViolationException extends DomainException {

    public RuleViolationException(String message) {
        super("rule-violation", message);
    }

    public RuleViolationException(String code, String message) {
        super(code, message);
    }
}

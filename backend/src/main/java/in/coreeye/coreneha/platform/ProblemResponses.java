package in.coreeye.coreneha.platform;

import java.net.URI;

import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.DomainException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.UnauthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps business errors to RFC 9457 problem responses with a stable code and
 * the request id. Messages never include data from other tenants or stack traces.
 */
@RestControllerAdvice
class ProblemResponses extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemResponses.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        HttpStatus status = switch (e) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case RuleViolationException ignored -> HttpStatus.valueOf(422);
            case AccessDeniedException ignored -> HttpStatus.FORBIDDEN;
            case UnauthenticatedException ignored -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, e.code(), e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "data.integrity", "The change conflicts with existing data or a rule");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:core-neha:problem:" + code));
        pd.setProperty("code", code);
        String requestId = MDC.get("request_id");
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}

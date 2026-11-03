package in.coreeye.coreneha.kernel;

import java.util.Arrays;
import java.util.Optional;

/**
 * Permission catalogue. Roles grant these; checks always name a permission,
 * never a role (ADR-0006). Must match iam.permission rows (verified by test).
 * {@code tenantOnly} permissions are effective only from a tenant-wide assignment.
 */
public enum Permission {
    ORG_STRUCTURE_READ("org.structure.read", false),
    ORG_STRUCTURE_MANAGE("org.structure.manage", true),
    ORG_CENTRE_CREATE("org.centre.create", false),
    ORG_CENTRE_READ("org.centre.read", false),
    ORG_CENTRE_CONFIGURE("org.centre.configure", false),
    WORKFORCE_POSITION_DEFINE("workforce.position.define", true),
    WORKFORCE_READ("workforce.read", false),
    WORKFORCE_ESTABLISH("workforce.establish", false),
    WORKFORCE_PERSON_MANAGE("workforce.person.manage", false),
    IAM_ROLE_READ("iam.role.read", true),
    IAM_ROLE_DRAFT("iam.role.draft", true),
    IAM_ROLE_APPROVE("iam.role.approve", true),
    IAM_USER_MANAGE("iam.user.manage", false),
    IAM_ASSIGNMENT_READ("iam.assignment.read", false),
    IAM_ASSIGNMENT_REQUEST("iam.assignment.request", false),
    IAM_ASSIGNMENT_APPROVE("iam.assignment.approve", false),
    IAM_ASSIGNMENT_REVOKE("iam.assignment.revoke", false),
    IAM_RESPONSIBILITY_MANAGE("iam.responsibility.manage", false),
    AUDIT_READ("audit.read", false);

    private final String code;
    private final boolean tenantOnly;

    Permission(String code, boolean tenantOnly) {
        this.code = code;
        this.tenantOnly = tenantOnly;
    }

    public String code() {
        return code;
    }

    public boolean tenantOnly() {
        return tenantOnly;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}

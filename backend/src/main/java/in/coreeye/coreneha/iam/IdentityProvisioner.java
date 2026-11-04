package in.coreeye.coreneha.iam;

/**
 * Creates and controls staff login accounts in the identity provider.
 * Implemented for Keycloak; replaceable without touching access rules.
 */
public interface IdentityProvisioner {

    /** A new login account. The temporary password is shown once and must be changed at first login (with MFA set-up). */
    record ProvisionedIdentity(String subject, String temporaryPassword) {
    }

    ProvisionedIdentity create(String username, String email, String givenName, String familyName, boolean demo);

    void disable(String subject);

    void enable(String subject);

    void delete(String subject);

    /**
     * Required: the authenticator is asked for at every sign-in (set up at the next sign-in if missing).
     * Not required: existing authenticators are removed and sign-in needs the password only.
     */
    void setOneTimeCodeRequired(String subject, boolean required);
}

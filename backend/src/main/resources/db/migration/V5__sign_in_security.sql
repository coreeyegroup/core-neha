-- Per-account sign-in security: whether a one-time code (authenticator app) is required.
-- Default on. Changed only by a group-wide user administrator, with a reason, audited
-- (UserService.changeMfa); the identity provider is updated in the same action.
ALTER TABLE iam.user_account ADD COLUMN mfa_required BOOLEAN NOT NULL DEFAULT true;

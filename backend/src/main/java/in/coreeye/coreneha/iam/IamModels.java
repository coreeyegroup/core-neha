package in.coreeye.coreneha.iam;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class IamModels {

    private IamModels() {
    }

    // ------------------------------------------------------------------ users
    public record UserView(UUID id, UUID personId, String personName, String username, String status,
                           int accessVersion, boolean loginLinked, int version) {
    }

    /** The temporary password is returned once only; it is never stored or logged by core-neha. */
    public record UserCreated(UserView user, String temporaryPassword, String instructions) {
    }

    public record CreateUser(@NotNull UUID personId,
                             @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{2,59}$") String username) {
    }

    public record ChangeUserStatus(@NotBlank @Size(max = 500) String reason, @NotNull Integer expectedVersion) {
    }

    // ------------------------------------------------------------------ roles
    public record PermissionView(String code, String module, String description, boolean tenantOnly) {
    }

    public record RoleSummary(UUID id, String code, String name, String description, boolean system, String status,
                              Integer publishedVersion, Integer openVersion, String openVersionStatus, int version) {
    }

    public record RoleVersionView(UUID id, UUID roleId, int versionNo, String status, String changeNote,
                                  UUID createdBy, UUID submittedBy, OffsetDateTime submittedAt, UUID decidedBy,
                                  OffsetDateTime decidedAt, String decisionNote, List<String> permissions, int version) {
    }

    public record RoleDetail(RoleSummary role, List<RoleVersionView> versions) {
    }

    public record CreateRole(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String code,
                             @NotBlank @Size(max = 200) String name, @Size(max = 1000) String description,
                             @NotEmpty Set<String> permissions, @Size(max = 1000) String changeNote) {
    }

    public record CloneRole(@NotNull UUID sourceRoleId,
                            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String code,
                            @NotBlank @Size(max = 200) String name, @Size(max = 1000) String description) {
    }

    public record DraftVersion(@NotEmpty Set<String> permissions, @Size(max = 1000) String changeNote) {
    }

    public record UpdateDraft(@NotEmpty Set<String> permissions, @Size(max = 1000) String changeNote,
                              @NotNull Integer expectedVersion) {
    }

    public record Decision(@Size(max = 1000) String note, @NotNull Integer expectedVersion) {
    }

    public record VersionImpact(UUID roleVersionId, List<String> added, List<String> removed, int affectedUsers,
                                List<UserView> affected) {
    }

    // ------------------------------------------------------------------ assignments
    public record RequestAssignment(@NotNull @Pattern(regexp = "ACCESS|AUTHORITY") String kind,
                                    @NotNull UUID userId, UUID roleId,
                                    @NotNull @Pattern(regexp = "TENANT|ORG_NODE|CENTRE") String scopeType,
                                    @NotNull UUID scopeId, UUID postingId, LocalDate validFrom, LocalDate validTo,
                                    @NotBlank @Size(max = 500) String reason) {
    }

    public record AssignmentView(UUID id, String kind, UUID userId, String username, UUID roleId, String roleCode,
                                 String roleName, String scopeType, UUID scopeId, String scopeName, UUID postingId,
                                 LocalDate validFrom, LocalDate validTo, String status, String reason,
                                 UUID requestedBy, String requestedByName, OffsetDateTime requestedAt, UUID decidedBy,
                                 OffsetDateTime decidedAt, String decisionNote, boolean viaBootstrap, int version) {
    }

    public record Revoke(@NotBlank @Size(max = 500) String reason, @NotNull Integer expectedVersion) {
    }

    // ------------------------------------------------------------------ responsibilities
    public record ResponsibilityTypeView(UUID id, String code, String name, String status) {
    }

    public record DefineResponsibility(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String code,
                                       @NotBlank @Size(max = 200) String name) {
    }

    public record AssignResponsibility(@NotNull UUID personId, @NotNull UUID responsibilityId,
                                       @NotNull @Pattern(regexp = "TENANT|ORG_NODE|CENTRE") String scopeType,
                                       @NotNull UUID scopeId, LocalDate validFrom, LocalDate validTo) {
    }

    public record ResponsibilityView(UUID id, UUID personId, UUID responsibilityId, String code, String name,
                                     String scopeType, UUID scopeId, String scopeName, LocalDate validFrom,
                                     LocalDate validTo) {
    }

    // ------------------------------------------------------------------ effective access
    public record ScopeGrant(String scopeType, UUID scopeId, String scopeName, String viaRole, UUID viaAssignment) {
    }

    public record PermissionGrant(String permission, String description, boolean tenantOnly, List<ScopeGrant> scopes) {
    }

    public record EffectiveAccess(UserView user, List<AssignmentView> assignments, List<PermissionGrant> permissions,
                                  List<ResponsibilityView> responsibilities, List<String> notes) {
    }

    public record Explanation(boolean allowed, String permission, String scopeType, UUID scopeId, List<String> reasons) {
    }

    public record CentreAccess(UUID centreId, String code, String name, List<String> permissions) {
    }

    /** One of the signed-in person's current postings: where they work and in which position. */
    public record MyPosting(UUID centreId, String centreName, String positionTitle, String departmentName) {
    }

    public record Me(UUID userId, String username, String displayName, UUID tenantId, String tenantName,
                     boolean demoTenant, List<String> tenantPermissions, List<CentreAccess> centres,
                     int pendingApprovals, List<ResponsibilityView> responsibilities, List<MyPosting> postings) {
    }
}

package in.coreeye.coreneha.iam;

import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.iam.IamModels.AssignmentView;
import in.coreeye.coreneha.iam.IamModels.EffectiveAccess;
import in.coreeye.coreneha.iam.IamModels.Explanation;
import in.coreeye.coreneha.iam.IamModels.Me;
import in.coreeye.coreneha.iam.IamModels.PermissionView;
import in.coreeye.coreneha.iam.IamModels.ResponsibilityTypeView;
import in.coreeye.coreneha.iam.IamModels.ResponsibilityView;
import in.coreeye.coreneha.iam.IamModels.RoleDetail;
import in.coreeye.coreneha.iam.IamModels.RoleSummary;
import in.coreeye.coreneha.iam.IamModels.UserView;
import in.coreeye.coreneha.iam.IamModels.VersionImpact;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class IamController {

    private final CurrentActor actor;
    private final UserService users;
    private final RoleService roles;
    private final AssignmentService assignments;
    private final ResponsibilityService responsibilities;
    private final EffectiveAccessService effective;

    IamController(CurrentActor actor, UserService users, RoleService roles, AssignmentService assignments,
                  ResponsibilityService responsibilities, EffectiveAccessService effective) {
        this.actor = actor;
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.responsibilities = responsibilities;
        this.effective = effective;
    }

    @GetMapping("/me")
    Me me() {
        return effective.me(actor.require());
    }

    // users
    @GetMapping("/users")
    List<UserView> users() {
        return users.list(actor.require());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserView createUser(@Valid @RequestBody IamModels.CreateUser body) {
        return users.create(actor.require(), body);
    }

    @PostMapping("/users/{userId}/disable")
    UserView disable(@PathVariable UUID userId, @Valid @RequestBody IamModels.ChangeUserStatus body) {
        return users.disable(actor.require(), userId, body);
    }

    @PostMapping("/users/{userId}/enable")
    UserView enable(@PathVariable UUID userId, @Valid @RequestBody IamModels.ChangeUserStatus body) {
        return users.enable(actor.require(), userId, body);
    }

    @GetMapping("/users/{userId}/assignments")
    List<AssignmentView> userAssignments(@PathVariable UUID userId) {
        return assignments.forUser(actor.require(), userId);
    }

    @GetMapping("/users/{userId}/effective-access")
    EffectiveAccess effectiveAccess(@PathVariable UUID userId) {
        return effective.forUser(actor.require(), userId);
    }

    @GetMapping("/users/{userId}/effective-access/explain")
    Explanation explain(@PathVariable UUID userId, @RequestParam String permission, @RequestParam String scopeType,
                        @RequestParam UUID scopeId) {
        return effective.explain(actor.require(), userId, permission, scopeType, scopeId);
    }

    // roles
    @GetMapping("/permissions")
    List<PermissionView> permissions() {
        return roles.permissions(actor.require());
    }

    @GetMapping("/roles")
    List<RoleSummary> roles() {
        return roles.roles(actor.require());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleDetail createRole(@Valid @RequestBody IamModels.CreateRole body) {
        return roles.create(actor.require(), body);
    }

    @PostMapping("/roles/clone")
    @ResponseStatus(HttpStatus.CREATED)
    RoleDetail cloneRole(@Valid @RequestBody IamModels.CloneRole body) {
        return roles.cloneRole(actor.require(), body);
    }

    @GetMapping("/roles/{roleId}")
    RoleDetail role(@PathVariable UUID roleId) {
        return roles.role(actor.require(), roleId);
    }

    @PostMapping("/roles/{roleId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    RoleDetail draftVersion(@PathVariable UUID roleId, @Valid @RequestBody IamModels.DraftVersion body) {
        return roles.draftVersion(actor.require(), roleId, body);
    }

    @PutMapping("/role-versions/{versionId}")
    RoleDetail updateDraft(@PathVariable UUID versionId, @Valid @RequestBody IamModels.UpdateDraft body) {
        return roles.updateDraft(actor.require(), versionId, body);
    }

    @GetMapping("/role-versions/{versionId}/impact")
    VersionImpact impact(@PathVariable UUID versionId) {
        return roles.impact(actor.require(), versionId);
    }

    @PostMapping("/role-versions/{versionId}/submit")
    RoleDetail submit(@PathVariable UUID versionId, @Valid @RequestBody IamModels.Decision body) {
        return roles.submit(actor.require(), versionId, body);
    }

    @PostMapping("/role-versions/{versionId}/approve")
    RoleDetail approveVersion(@PathVariable UUID versionId, @Valid @RequestBody IamModels.Decision body) {
        return roles.approve(actor.require(), versionId, body);
    }

    @PostMapping("/role-versions/{versionId}/reject")
    RoleDetail rejectVersion(@PathVariable UUID versionId, @Valid @RequestBody IamModels.Decision body) {
        return roles.reject(actor.require(), versionId, body);
    }

    // assignments
    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    AssignmentView request(@Valid @RequestBody IamModels.RequestAssignment body) {
        return assignments.request(actor.require(), body);
    }

    @GetMapping("/approvals")
    List<AssignmentView> pending() {
        return assignments.pendingForMe(actor.require());
    }

    @PostMapping("/assignments/{assignmentId}/approve")
    AssignmentView approve(@PathVariable UUID assignmentId, @Valid @RequestBody IamModels.Decision body) {
        return assignments.approve(actor.require(), assignmentId, body);
    }

    @PostMapping("/assignments/{assignmentId}/reject")
    AssignmentView reject(@PathVariable UUID assignmentId, @Valid @RequestBody IamModels.Decision body) {
        return assignments.reject(actor.require(), assignmentId, body);
    }

    @PostMapping("/assignments/{assignmentId}/revoke")
    AssignmentView revoke(@PathVariable UUID assignmentId, @Valid @RequestBody IamModels.Revoke body) {
        return assignments.revoke(actor.require(), assignmentId, body);
    }

    // responsibilities
    @GetMapping("/responsibility-types")
    List<ResponsibilityTypeView> responsibilityTypes() {
        return responsibilities.types(actor.require());
    }

    @PostMapping("/responsibility-types")
    @ResponseStatus(HttpStatus.CREATED)
    ResponsibilityTypeView defineResponsibility(@Valid @RequestBody IamModels.DefineResponsibility body) {
        return responsibilities.define(actor.require(), body);
    }

    @PostMapping("/responsibilities")
    @ResponseStatus(HttpStatus.CREATED)
    ResponsibilityView assignResponsibility(@Valid @RequestBody IamModels.AssignResponsibility body) {
        return responsibilities.assign(actor.require(), body);
    }
}

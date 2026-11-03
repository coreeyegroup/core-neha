package in.coreeye.coreneha.iam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.iam.IamModels.ResponsibilityTypeView;
import in.coreeye.coreneha.iam.IamModels.ResponsibilityView;
import in.coreeye.coreneha.workforce.WorkforceDirectory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responsibilities record accountability (for example "stock controller,
 * optical store, Centre A"). They never grant access (IAM-15): the access
 * evaluator does not read these tables.
 */
@Service
public class ResponsibilityService {

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final IamSupport support;
    private final WorkforceDirectory workforce;

    ResponsibilityService(JdbcClient jdbc, AccessControl access, AuditLog audit, IamSupport support, WorkforceDirectory workforce) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.support = support;
        this.workforce = workforce;
    }

    @Transactional
    public ResponsibilityTypeView define(Actor actor, IamModels.DefineResponsibility cmd) {
        Scope scope = Scope.tenant(actor.tenantId());
        access.require(actor, Permission.IAM_RESPONSIBILITY_MANAGE, scope, "iam.responsibility.define", "responsibility_type", null);
        UUID id;
        try {
            id = jdbc.sql("insert into iam.responsibility_type (tenant_id, code, name) values (:t, :c, :n) returning id")
                    .param("t", actor.tenantId()).param("c", cmd.code()).param("n", cmd.name().trim()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", "A responsibility with this code already exists");
        }
        audit.record(AuditEntry.success(actor, "iam.responsibility.define", "responsibility_type", id, scope,
                IamSupport.details("code", cmd.code(), "name", cmd.name())));
        return types().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ResponsibilityTypeView> types(Actor actor) {
        access.requireAnywhere(actor, Permission.IAM_ASSIGNMENT_READ, "iam.responsibility.types", "responsibility_type");
        return types();
    }

    private List<ResponsibilityTypeView> types() {
        return jdbc.sql("select id, code, name, status from iam.responsibility_type order by name")
                .query(ResponsibilityTypeView.class).list();
    }

    @Transactional
    public ResponsibilityView assign(Actor actor, IamModels.AssignResponsibility cmd) {
        Scope scope = support.validScope(actor.tenantId(), cmd.scopeType(), cmd.scopeId());
        access.require(actor, Permission.IAM_RESPONSIBILITY_MANAGE, scope, "iam.responsibility.assign", "person",
                cmd.personId().toString());
        workforce.person(cmd.personId()).orElseThrow(() -> new NotFoundException("Person not found"));
        LocalDate from = cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom();
        if (cmd.validTo() != null && cmd.validTo().isBefore(from)) {
            throw new RuleViolationException("iam.responsibility.dates", "The responsibility ends before it starts");
        }
        UUID id = jdbc.sql("""
                insert into iam.responsibility_assignment (tenant_id, person_id, responsibility_id, scope_type, scope_id,
                    valid_from, valid_to, assigned_by)
                values (:t, :p, :r, :st, :sid, :from, :to, :by) returning id
                """).param("t", actor.tenantId()).param("p", cmd.personId()).param("r", cmd.responsibilityId())
                .param("st", scope.type().name()).param("sid", scope.id()).param("from", from).param("to", cmd.validTo())
                .param("by", actor.userId()).query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "iam.responsibility.assign", "responsibility_assignment", id, scope,
                IamSupport.details("personId", cmd.personId().toString(), "responsibilityId", cmd.responsibilityId().toString(),
                        "grantsAccess", false)));
        return forPersonRows(cmd.personId()).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    List<ResponsibilityView> forPersonRows(UUID personId) {
        return jdbc.sql("""
                select ra.id, ra.person_id, ra.responsibility_id, rt.code, rt.name, ra.scope_type, ra.scope_id,
                       null as scope_name, ra.valid_from, ra.valid_to
                from iam.responsibility_assignment ra join iam.responsibility_type rt on rt.id = ra.responsibility_id
                where ra.person_id = :p and (ra.valid_to is null or ra.valid_to >= current_date)
                order by rt.name
                """).param("p", personId).query(ResponsibilityView.class).list().stream()
                .map(r -> new ResponsibilityView(r.id(), r.personId(), r.responsibilityId(), r.code(), r.name(),
                        r.scopeType(), r.scopeId(), support.scopeName(r.scopeType(), r.scopeId()), r.validFrom(), r.validTo()))
                .toList();
    }
}

package in.coreeye.coreneha.workforce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import in.coreeye.coreneha.org.DepartmentRetirementCheck;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.workforce.WorkforceModels.EstablishmentView;
import in.coreeye.coreneha.workforce.WorkforceModels.PersonDetail;
import in.coreeye.coreneha.workforce.WorkforceModels.PersonView;
import in.coreeye.coreneha.workforce.WorkforceModels.PositionView;
import in.coreeye.coreneha.workforce.WorkforceModels.PostingView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkforceService implements WorkforceDirectory, DepartmentRetirementCheck {

    private static final String POSTING_SELECT = """
            select p.id, p.person_id, p.establishment_id, e.centre_id, c.name as centre_name, pd.title as position_title,
                   coalesce(cd.local_name, dd.name) as department_name, p.fte, p.valid_from, p.valid_to, p.version
            from workforce.posting p
            join workforce.establishment e on e.id = p.establishment_id
            join workforce.position_definition pd on pd.id = e.position_id
            join org.centre c on c.id = e.centre_id
            left join org.centre_department cd on cd.id = e.centre_department_id
            left join org.department_definition dd on dd.id = cd.department_id
            """;

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final OrgDirectory org;

    WorkforceService(JdbcClient jdbc, AccessControl access, AuditLog audit, OrgDirectory org) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.org = org;
    }

    // ---------------------------------------------------------------- directory

    @Override
    public Optional<PersonRef> person(UUID personId) {
        return jdbc.sql("select id, tenant_id, employee_no, given_name, family_name, email, status from workforce.person where id = :id")
                .param("id", personId).query(PersonRef.class).optional();
    }

    @Override
    public Optional<PostingRef> posting(UUID postingId) {
        return jdbc.sql("""
                select p.id, p.person_id, e.centre_id, e.position_id, pd.title as position_title,
                       coalesce(cd.local_name, d.name) as department_name,
                       (p.valid_from <= current_date and (p.valid_to is null or p.valid_to >= current_date)) as active
                from workforce.posting p join workforce.establishment e on e.id = p.establishment_id
                join workforce.position_definition pd on pd.id = e.position_id
                left join org.centre_department cd on cd.id = e.centre_department_id
                left join org.department_definition d on d.id = cd.department_id where p.id = :id
                """).param("id", postingId).query(PostingRef.class).optional();
    }

    @Override
    public List<PostingRef> activePostings(UUID personId) {
        return jdbc.sql("""
                select p.id, p.person_id, e.centre_id, e.position_id, pd.title as position_title,
                       coalesce(cd.local_name, d.name) as department_name, true as active
                from workforce.posting p join workforce.establishment e on e.id = p.establishment_id
                join workforce.position_definition pd on pd.id = e.position_id
                left join org.centre_department cd on cd.id = e.centre_department_id
                left join org.department_definition d on d.id = cd.department_id
                where p.person_id = :id and p.valid_from <= current_date and (p.valid_to is null or p.valid_to >= current_date)
                """).param("id", personId).query(PostingRef.class).list();
    }

    @Override
    public List<StaffRef> staffAt(UUID centreId, String departmentCode) {
        return jdbc.sql("""
                select distinct pe.id as person_id, trim(pe.given_name || ' ' || pe.family_name) as name, pd.title as position_title,
                       d.code as department_code, coalesce(cd.local_name, d.name) as department_name
                from workforce.posting p join workforce.establishment e on e.id = p.establishment_id
                join workforce.position_definition pd on pd.id = e.position_id
                join workforce.person pe on pe.id = p.person_id and pe.status = 'ACTIVE'
                left join org.centre_department cd on cd.id = e.centre_department_id
                left join org.department_definition d on d.id = cd.department_id
                where e.centre_id = :c and p.valid_from <= current_date and (p.valid_to is null or p.valid_to >= current_date)
                  and (cast(:dept as text) is null or d.code = :dept)
                order by name
                """).param("c", centreId).param("dept", departmentCode).query(StaffRef.class).list();
    }

    @Override
    public List<Blocker> blockers(UUID centreDepartmentId) {
        int establishments = jdbc.sql("""
                select count(*) from workforce.establishment
                where centre_department_id = :cd and (valid_to is null or valid_to >= current_date)
                """).param("cd", centreDepartmentId).query(Integer.class).single();
        int postings = jdbc.sql("""
                select count(*) from workforce.posting p join workforce.establishment e on e.id = p.establishment_id
                where e.centre_department_id = :cd and (p.valid_to is null or p.valid_to >= current_date)
                """).param("cd", centreDepartmentId).query(Integer.class).single();
        List<Blocker> list = new ArrayList<>();
        if (establishments > 0) {
            list.add(new Blocker("workforce", "open position establishment(s)", establishments));
        }
        if (postings > 0) {
            list.add(new Blocker("workforce", "current or future posting(s)", postings));
        }
        return list;
    }

    // ---------------------------------------------------------------- positions

    @Transactional
    public PositionView definePosition(Actor actor, WorkforceModels.DefinePosition cmd) {
        Scope scope = Scope.tenant(actor.tenantId());
        access.require(actor, Permission.WORKFORCE_POSITION_DEFINE, scope, "workforce.position.define", "position_definition", null);
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into workforce.position_definition (tenant_id, code, title, category, grade)
                    values (:t, :code, :title, :category, :grade) returning id
                    """).param("t", actor.tenantId()).param("code", cmd.code()).param("title", cmd.title().trim())
                    .param("category", cmd.category()).param("grade", cmd.grade()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", "A position with this code already exists");
        }
        audit.record(AuditEntry.success(actor, "workforce.position.define", "position_definition", id, scope,
                Map.of("code", cmd.code(), "title", cmd.title(), "category", cmd.category())));
        return positionRows().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PositionView> positions(Actor actor) {
        access.requireAnywhere(actor, Permission.WORKFORCE_READ, "workforce.position.list", "position_definition");
        return positionRows();
    }

    private List<PositionView> positionRows() {
        return jdbc.sql("select id, code, title, category, grade, status, version from workforce.position_definition order by title")
                .query(PositionView.class).list();
    }

    // ---------------------------------------------------------------- establishments

    @Transactional
    public EstablishmentView establish(Actor actor, UUID centreId, WorkforceModels.Establish cmd) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.WORKFORCE_ESTABLISH, scope, "workforce.establish", "centre", centreId.toString());
        var centre = org.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found"));
        if (!"ACTIVE".equals(centre.status())) {
            throw new RuleViolationException("org.centre.retired", "The centre is retired");
        }
        boolean positionActive = jdbc.sql("select count(*) from workforce.position_definition where id = :id and status = 'ACTIVE'")
                .param("id", cmd.positionId()).query(Integer.class).single() == 1;
        if (!positionActive) {
            throw new NotFoundException("Position not found");
        }
        if (cmd.centreDepartmentId() != null) {
            // A position can only be established in a department that this centre has enabled.
            var cd = org.centreDepartment(cmd.centreDepartmentId())
                    .filter(d -> d.centreId().equals(centreId))
                    .orElseThrow(() -> new RuleViolationException("workforce.department-not-enabled",
                            "The department is not enabled at this centre"));
            if (!cd.open()) {
                throw new RuleViolationException("workforce.department-retired", "The department is retired at this centre");
            }
        }
        LocalDate from = cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom();
        UUID id = jdbc.sql("""
                insert into workforce.establishment (tenant_id, position_id, centre_id, centre_department_id, approved_headcount, valid_from)
                values (:t, :p, :c, :cd, :n, :from) returning id
                """).param("t", actor.tenantId()).param("p", cmd.positionId()).param("c", centreId)
                .param("cd", cmd.centreDepartmentId()).param("n", cmd.approvedHeadcount()).param("from", from)
                .query(UUID.class).single();
        Map<String, Object> details = new HashMap<>();
        details.put("positionId", cmd.positionId().toString());
        details.put("centreDepartmentId", String.valueOf(cmd.centreDepartmentId()));
        details.put("approvedHeadcount", cmd.approvedHeadcount());
        audit.record(AuditEntry.success(actor, "workforce.establish", "establishment", id, scope, details));
        return establishmentRows(centreId).stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<EstablishmentView> establishments(Actor actor, UUID centreId) {
        access.require(actor, Permission.WORKFORCE_READ, Scope.centre(centreId), "workforce.establishment.list", "centre", centreId.toString());
        return establishmentRows(centreId);
    }

    private List<EstablishmentView> establishmentRows(UUID centreId) {
        return jdbc.sql("""
                select e.id, e.position_id, pd.code as position_code, pd.title as position_title, e.centre_id,
                       e.centre_department_id, coalesce(cd.local_name, dd.name) as department_name, e.approved_headcount,
                       (select count(*) from workforce.posting p where p.establishment_id = e.id
                          and (p.valid_to is null or p.valid_to >= current_date))::int as active_postings,
                       e.valid_from, e.valid_to, e.version
                from workforce.establishment e
                join workforce.position_definition pd on pd.id = e.position_id
                left join org.centre_department cd on cd.id = e.centre_department_id
                left join org.department_definition dd on dd.id = cd.department_id
                where e.centre_id = :c order by pd.title
                """).param("c", centreId).query(EstablishmentView.class).list();
    }

    // ---------------------------------------------------------------- people and postings

    @Transactional
    public PersonView createPerson(Actor actor, WorkforceModels.CreatePerson cmd, boolean demo) {
        access.requireAnywhere(actor, Permission.WORKFORCE_PERSON_MANAGE, "workforce.person.create", "person");
        return insertPerson(actor.tenantId(), cmd, demo, actor);
    }

    /** Used by the bootstrap and demonstration loaders (they audit as the system). */
    public PersonView insertPerson(UUID tenantId, WorkforceModels.CreatePerson cmd, boolean demo, Actor actorOrNull) {
        UUID id;
        try {
            // A permanent employee number: the person's login id, unchanged when positions change.
            String employeeNo = jdbc.sql("""
                    insert into workforce.employee_counter (tenant_id, last_value) values (:t, 10001)
                    on conflict (tenant_id) do update set last_value = workforce.employee_counter.last_value + 1
                    returning prefix || last_value
                    """).param("t", tenantId).query(String.class).single();
            id = jdbc.sql("""
                    insert into workforce.person (tenant_id, employee_no, given_name, family_name, email, is_demo)
                    values (:t, :no, :g, :f, :e, :demo) returning id
                    """).param("t", tenantId).param("no", employeeNo).param("g", cmd.givenName().trim()).param("f", cmd.familyName().trim())
                    .param("e", cmd.email().trim()).param("demo", demo).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", "A person with this email already exists");
        }
        Map<String, Object> details = Map.of("givenName", cmd.givenName(), "familyName", cmd.familyName(), "demo", demo);
        audit.record(actorOrNull == null
                ? AuditEntry.system(tenantId, "system", "workforce.person.create", "person", id, Scope.tenant(tenantId), details)
                : AuditEntry.success(actorOrNull, "workforce.person.create", "person", id, Scope.tenant(tenantId), details));
        return personRow(id);
    }

    @Transactional(readOnly = true)
    public List<PersonView> people(Actor actor) {
        if (access.holds(actor, Permission.WORKFORCE_READ, Scope.tenant(actor.tenantId()))) {
            return jdbc.sql("select id, employee_no, given_name, family_name, email, is_demo as demo, status, version from workforce.person order by family_name, given_name")
                    .query(PersonView.class).list();
        }
        Set<UUID> centres = access.centresWhere(actor, Permission.WORKFORCE_READ);
        if (centres.isEmpty()) {
            access.requireAnywhere(actor, Permission.WORKFORCE_READ, "workforce.person.list", "person");
        }
        return jdbc.sql("""
                select distinct pe.id, pe.employee_no, pe.given_name, pe.family_name, pe.email, pe.is_demo as demo, pe.status, pe.version
                from workforce.person pe join workforce.posting p on p.person_id = pe.id
                join workforce.establishment e on e.id = p.establishment_id
                where e.centre_id in (:centres) and (p.valid_to is null or p.valid_to >= current_date)
                order by pe.family_name, pe.given_name
                """).param("centres", new ArrayList<>(centres)).query(PersonView.class).list();
    }

    @Transactional(readOnly = true)
    public PersonDetail personDetail(Actor actor, UUID personId) {
        PersonView person = personRow(personId);
        List<PostingView> postings = postingRows(personId);
        boolean allowed = access.holds(actor, Permission.WORKFORCE_READ, Scope.tenant(actor.tenantId()))
                || postings.stream().anyMatch(p -> access.holds(actor, Permission.WORKFORCE_READ, Scope.centre(p.centreId())));
        if (!allowed) {
            access.require(actor, Permission.WORKFORCE_READ, Scope.tenant(actor.tenantId()), "workforce.person.read",
                    "person", personId.toString());
        }
        audit.recordIndependently(AuditEntry.success(actor, "workforce.person.read", "person", personId, null, Map.of()));
        return new PersonDetail(person, postings);
    }

    @Transactional
    public PostingView addPosting(Actor actor, UUID personId, WorkforceModels.AddPosting cmd) {
        var est = jdbc.sql("""
                select id, centre_id, approved_headcount, valid_to from workforce.establishment where id = :id for update
                """).param("id", cmd.establishmentId())
                .query((rs, i) -> new Object[] {rs.getObject("centre_id", UUID.class), rs.getInt("approved_headcount"),
                        rs.getObject("valid_to", LocalDate.class)})
                .optional().orElseThrow(() -> new NotFoundException("Establishment not found"));
        UUID centreId = (UUID) est[0];
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.WORKFORCE_PERSON_MANAGE, scope, "workforce.posting.add", "person", personId.toString());
        personRow(personId);
        if (est[2] != null && ((LocalDate) est[2]).isBefore(LocalDate.now())) {
            throw new RuleViolationException("workforce.establishment-closed", "The establishment is closed");
        }
        int filled = jdbc.sql("""
                select count(*) from workforce.posting where establishment_id = :e and (valid_to is null or valid_to >= current_date)
                """).param("e", cmd.establishmentId()).query(Integer.class).single();
        if (filled >= (int) est[1]) {
            throw new RuleViolationException("workforce.headcount-full", "The approved headcount for this establishment is filled");
        }
        LocalDate from = cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom();
        if (cmd.validTo() != null && cmd.validTo().isBefore(from)) {
            throw new RuleViolationException("workforce.dates", "The posting ends before it starts");
        }
        BigDecimal fte = cmd.fte() == null ? BigDecimal.ONE : cmd.fte();
        UUID id = jdbc.sql("""
                insert into workforce.posting (tenant_id, person_id, establishment_id, fte, valid_from, valid_to)
                values (:t, :p, :e, :fte, :from, :to) returning id
                """).param("t", actor.tenantId()).param("p", personId).param("e", cmd.establishmentId())
                .param("fte", fte).param("from", from).param("to", cmd.validTo()).query(UUID.class).single();
        Map<String, Object> details = new HashMap<>();
        details.put("personId", personId.toString());
        details.put("establishmentId", cmd.establishmentId().toString());
        details.put("validFrom", from.toString());
        details.put("validTo", String.valueOf(cmd.validTo()));
        audit.record(AuditEntry.success(actor, "workforce.posting.add", "posting", id, scope, details));
        return postingRows(personId).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public PostingView endPosting(Actor actor, UUID postingId, WorkforceModels.EndPosting cmd) {
        PostingRef ref = posting(postingId).orElseThrow(() -> new NotFoundException("Posting not found"));
        Scope scope = Scope.centre(ref.centreId());
        access.require(actor, Permission.WORKFORCE_PERSON_MANAGE, scope, "workforce.posting.end", "posting", postingId.toString());
        int updated = jdbc.sql("""
                update workforce.posting set valid_to = :to, version = version + 1
                where id = :id and version = :v and valid_from <= :to
                """).param("to", cmd.validTo()).param("id", postingId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("workforce.posting.stale", "The posting was changed by someone else, or the end date is before its start");
        }
        audit.record(AuditEntry.success(actor, "workforce.posting.end", "posting", postingId, scope,
                Map.of("validTo", cmd.validTo().toString())));
        return postingRows(ref.personId()).stream().filter(p -> p.id().equals(postingId)).findFirst().orElseThrow();
    }

    private PersonView personRow(UUID id) {
        return jdbc.sql("select id, employee_no, given_name, family_name, email, is_demo as demo, status, version from workforce.person where id = :id")
                .param("id", id).query(PersonView.class).optional()
                .orElseThrow(() -> new NotFoundException("Person not found"));
    }

    private List<PostingView> postingRows(UUID personId) {
        return jdbc.sql(POSTING_SELECT + " where p.person_id = :id order by p.valid_from desc")
                .param("id", personId).query(PostingView.class).list();
    }
}

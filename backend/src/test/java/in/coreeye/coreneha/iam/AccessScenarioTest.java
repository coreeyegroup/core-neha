package in.coreeye.coreneha.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end access scenario for slice 1.1 through the HTTP API, against the
 * real database with row-level security. Each test maps to an acceptance item.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccessScenarioTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired ActorContext actorContext;
    @Autowired BootstrapService bootstrap;
    @Autowired UserService users;

    MockMvc mvc;
    final String run = UUID.randomUUID().toString().substring(0, 8);
    final String adminA = "sub-a-" + run;
    final String adminB = "sub-b-" + run;
    final String staff = "sub-u-" + run;
    final String otherAdmin = "sub-x-" + run;

    UUID tenantId;
    String centre1;
    String centre2;
    String c1Lab;
    String userU;
    String personP;
    String viewerRole;

    @BeforeAll
    void world() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(actorContext).build();
        tenantId = bootstrap.bootstrap("t1-" + run, "Demo Group " + run, true, List.of(
                new BootstrapService.Administrator("Asha", "Admin", "asha-" + run + "@demo.test", "asha-" + run, adminA),
                new BootstrapService.Administrator("Bala", "Approver", "bala-" + run + "@demo.test", "bala-" + run, adminB))).tenantId();

        post(adminA, "/v1/hierarchy/levels", "{\"code\":\"STATE\",\"name\":\"State\",\"rank\":1}", 201);
        String levels = get(adminA, "/v1/hierarchy/levels", 200);
        String state = JsonPath.read(levels, "$[0].id");
        String city = id(post(adminA, "/v1/hierarchy/levels", "{\"code\":\"CITY\",\"name\":\"City\",\"rank\":2}", 201));
        String ka = id(post(adminA, "/v1/hierarchy/nodes", "{\"levelId\":\"" + state + "\",\"code\":\"KA\",\"name\":\"Karnataka\"}", 201));
        String blr = id(post(adminA, "/v1/hierarchy/nodes",
                "{\"levelId\":\"" + city + "\",\"parentId\":\"" + ka + "\",\"code\":\"BLR\",\"name\":\"Bengaluru\"}", 201));
        String le = id(post(adminA, "/v1/legal-entities", "{\"code\":\"GEC\",\"name\":\"Group Eye Care Pvt Ltd\"}", 201));
        String consult = dept("CONSULTATION", "Consultation", "CLINICAL");
        String lab = dept("LABORATORY", "Laboratory", "CLINICAL_SUPPORT");
        String pharmacy = dept("PHARMACY", "Pharmacy", "CLINICAL_SUPPORT");
        String optical = dept("OPTICAL_RETAIL", "Optical retail", "OPERATIONS");
        String surgery = dept("SURGERY_DAYCARE", "Surgery and day-care", "CLINICAL");
        centre1 = id(post(adminA, "/v1/centres", "{\"nodeId\":\"" + blr + "\",\"legalEntityId\":\"" + le
                + "\",\"code\":\"BLR-A\",\"name\":\"Centre A\",\"centreType\":\"CLINIC\",\"departmentIds\":[\""
                + consult + "\",\"" + lab + "\",\"" + pharmacy + "\",\"" + optical + "\"]}", 201));
        centre2 = id(post(adminA, "/v1/centres", "{\"nodeId\":\"" + blr + "\",\"legalEntityId\":\"" + le
                + "\",\"code\":\"BLR-B\",\"name\":\"Centre B\",\"centreType\":\"CLINIC_DAYCARE\",\"departmentIds\":[\""
                + consult + "\",\"" + lab + "\",\"" + surgery + "\"]}", 201));
        c1Lab = JsonPath.<List<String>>read(get(adminA, "/v1/centres/" + centre1 + "/departments", 200),
                "$[?(@.departmentCode == 'LABORATORY')].id").get(0);
    }

    private String dept(String code, String name, String category) throws Exception {
        return id(post(adminA, "/v1/departments", "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"category\":\"" + category + "\"}", 201));
    }

    // ------------------------------------------------------------------ acceptance

    @Test
    @Order(1)
    void centresHaveDifferentDepartmentsAndPositionsFollowEnablement() throws Exception {
        String c1 = get(adminA, "/v1/centres/" + centre1 + "/departments", 200);
        String c2 = get(adminA, "/v1/centres/" + centre2 + "/departments", 200);
        assertThat(JsonPath.<List<String>>read(c1, "$[*].departmentCode")).containsExactlyInAnyOrder("CONSULTATION", "LABORATORY", "PHARMACY", "OPTICAL_RETAIL");
        assertThat(JsonPath.<List<String>>read(c2, "$[*].departmentCode")).containsExactlyInAnyOrder("CONSULTATION", "LABORATORY", "SURGERY_DAYCARE");

        String labTech = id(post(adminA, "/v1/positions", "{\"code\":\"LAB_TECHNICIAN\",\"title\":\"Lab technician\",\"category\":\"TECHNICAL\"}", 201));
        post(adminA, "/v1/centres/" + centre1 + "/establishments",
                "{\"positionId\":\"" + labTech + "\",\"centreDepartmentId\":\"" + c1Lab + "\",\"approvedHeadcount\":2}", 201);
        // Centre B cannot use Centre A's department enablement.
        String refused = post(adminA, "/v1/centres/" + centre2 + "/establishments",
                "{\"positionId\":\"" + labTech + "\",\"centreDepartmentId\":\"" + c1Lab + "\",\"approvedHeadcount\":1}", 422);
        assertThat(refused).contains("workforce.department-not-enabled");
        assertThat(get(adminA, "/v1/centres/" + centre2 + "/establishments", 200)).isEqualTo("[]");
    }

    @Test
    @Order(2)
    void personHoldsPostingsAtTwoCentres() throws Exception {
        String labTech = JsonPath.<List<String>>read(get(adminA, "/v1/positions", 200), "$[?(@.code == 'LAB_TECHNICIAN')].id").get(0);
        String c2Lab = JsonPath.<List<String>>read(get(adminA, "/v1/centres/" + centre2 + "/departments", 200),
                "$[?(@.departmentCode == 'LABORATORY')].id").get(0);
        String est2 = id(post(adminA, "/v1/centres/" + centre2 + "/establishments",
                "{\"positionId\":\"" + labTech + "\",\"centreDepartmentId\":\"" + c2Lab + "\",\"approvedHeadcount\":1}", 201));
        String est1 = JsonPath.read(get(adminA, "/v1/centres/" + centre1 + "/establishments", 200), "$[0].id");
        personP = id(post(adminA, "/v1/people", "{\"givenName\":\"Priya\",\"familyName\":\"Rao\",\"email\":\"priya-" + run + "@demo.test\"}", 201));
        post(adminA, "/v1/people/" + personP + "/postings", "{\"establishmentId\":\"" + est1 + "\",\"fte\":0.6}", 201);
        post(adminA, "/v1/people/" + personP + "/postings", "{\"establishmentId\":\"" + est2 + "\",\"fte\":0.4}", 201);
        String detail = get(adminA, "/v1/people/" + personP, 200);
        assertThat(JsonPath.<List<String>>read(detail, "$.postings[*].centreName")).containsExactlyInAnyOrder("Centre A", "Centre B");

        userU = id(post(adminA, "/v1/users", "{\"personId\":\"" + personP + "\",\"username\":\"priya-" + run + "\"}", 201));
        TenantContext.callAs(tenantId, () -> {
            users.linkIdentity(tenantId, UUID.fromString(userU), staff);
            return null;
        });
    }

    @Test
    @Order(3)
    void customRoleNeedsIndependentApproval() throws Exception {
        String role = post(adminA, "/v1/roles", "{\"code\":\"CENTRE_VIEWER\",\"name\":\"Centre viewer\","
                + "\"permissions\":[\"org.centre.read\",\"workforce.read\"],\"changeNote\":\"Read-only centre access\"}", 201);
        viewerRole = JsonPath.read(role, "$.role.id");
        String versionId = JsonPath.read(role, "$.versions[0].id");
        int v = JsonPath.read(role, "$.versions[0].version");
        String submitted = post(adminA, "/v1/role-versions/" + versionId + "/submit", "{\"expectedVersion\":" + v + "}", 200);
        int v2 = JsonPath.read(submitted, "$.versions[0].version");
        String self = post(adminA, "/v1/role-versions/" + versionId + "/approve", "{\"expectedVersion\":" + v2 + "}", 422);
        assertThat(self).contains("iam.self-approval");
        String approved = post(adminB, "/v1/role-versions/" + versionId + "/approve", "{\"expectedVersion\":" + v2 + ",\"note\":\"ok\"}", 200);
        assertThat((String) JsonPath.read(approved, "$.versions[0].status")).isEqualTo("PUBLISHED");
    }

    @Test
    @Order(4)
    void assignmentIsEffectiveOnlyAfterSomeoneElseApproves() throws Exception {
        String req = post(adminA, "/v1/assignments", "{\"kind\":\"ACCESS\",\"userId\":\"" + userU + "\",\"roleId\":\"" + viewerRole
                + "\",\"scopeType\":\"CENTRE\",\"scopeId\":\"" + centre1 + "\",\"reason\":\"Works in Centre A laboratory\"}", 201);
        String assignment = id(req);
        int v = JsonPath.read(req, "$.version");
        get(staff, "/v1/centres/" + centre1, 403);                      // pending: not effective
        assertThat(post(adminA, "/v1/assignments/" + assignment + "/approve", "{\"expectedVersion\":" + v + "}", 422))
                .contains("iam.self-approval");                          // requester cannot approve
        assertThat(get(adminB, "/v1/approvals", 200)).contains(assignment);
        post(adminB, "/v1/assignments/" + assignment + "/approve", "{\"expectedVersion\":" + v + "}", 200);
        get(staff, "/v1/centres/" + centre1, 200);
        assertThat(JsonPath.<List<String>>read(get(staff, "/v1/centres", 200), "$[*].id")).containsExactly(centre1);
    }

    @Test
    @Order(5)
    void crossCentreAccessIsRefused() throws Exception {
        get(staff, "/v1/centres/" + centre2, 403);
        get(staff, "/v1/centres/" + centre2 + "/establishments", 403);
        post(staff, "/v1/centres/" + centre1 + "/departments", "{\"departmentId\":\"" + UUID.randomUUID() + "\"}", 403);
    }

    @Test
    @Order(6)
    void responsibilityGrantsNoPermission() throws Exception {
        String type = id(post(adminA, "/v1/responsibility-types", "{\"code\":\"STOCK_CONTROLLER\",\"name\":\"Stock controller\"}", 201));
        post(adminA, "/v1/responsibilities", "{\"personId\":\"" + personP + "\",\"responsibilityId\":\"" + type
                + "\",\"scopeType\":\"CENTRE\",\"scopeId\":\"" + centre2 + "\"}", 201);
        get(staff, "/v1/centres/" + centre2, 403);
        String explain = get(adminA, "/v1/users/" + userU + "/effective-access/explain?permission=org.centre.read&scopeType=CENTRE&scopeId=" + centre2, 200);
        assertThat((Boolean) JsonPath.read(explain, "$.allowed")).isFalse();
        String preview = get(adminA, "/v1/users/" + userU + "/effective-access", 200);
        assertThat(JsonPath.<List<String>>read(preview, "$.permissions[*].permission")).containsExactlyInAnyOrder("org.centre.read", "workforce.read");
        assertThat(JsonPath.<List<String>>read(preview, "$.responsibilities[*].code")).containsExactly("STOCK_CONTROLLER");
    }

    @Test
    @Order(7)
    void selfExpansionIsRefused() throws Exception {
        String asha = JsonPath.<List<String>>read(get(adminA, "/v1/users", 200), "$[?(@.username == 'asha-" + run + "')].id").get(0);
        String refused = post(adminA, "/v1/assignments", "{\"kind\":\"ACCESS\",\"userId\":\"" + asha + "\",\"roleId\":\"" + viewerRole
                + "\",\"scopeType\":\"TENANT\",\"scopeId\":\"" + tenantId + "\",\"reason\":\"self\"}", 422);
        assertThat(refused).contains("iam.assignment.self");
        // Staff without assignment authority cannot request access for anyone.
        post(staff, "/v1/assignments", "{\"kind\":\"ACCESS\",\"userId\":\"" + asha + "\",\"roleId\":\"" + viewerRole
                + "\",\"scopeType\":\"CENTRE\",\"scopeId\":\"" + centre1 + "\",\"reason\":\"try\"}", 403);
    }

    @Test
    @Order(8)
    void crossTenantAccessIsRefused() throws Exception {
        bootstrap.bootstrap("t2-" + run, "Other Group " + run, true, List.of(
                new BootstrapService.Administrator("Xavier", "Admin", "x-" + run + "@other.test", "xavier-" + run, otherAdmin),
                new BootstrapService.Administrator("Yamini", "Approver", "y-" + run + "@other.test", "yamini-" + run, "sub-y-" + run)));
        get(otherAdmin, "/v1/centres/" + centre1, 404);
        assertThat(get(otherAdmin, "/v1/centres", 200)).isEqualTo("[]");
        get(otherAdmin, "/v1/people/" + personP, 404);
        post(otherAdmin, "/v1/centres/" + centre1 + "/departments", "{\"departmentId\":\"" + UUID.randomUUID() + "\"}", 404);
    }

    @Test
    @Order(9)
    void auditIdentifiesActorActionObjectScopeAndOutcome() throws Exception {
        String denied = get(adminA, "/v1/audit-events?outcome=DENIED&limit=200", 200);
        List<String> actions = JsonPath.read(denied, "$[?(@.actorLabel == 'priya-" + run + "')].action");
        assertThat(actions).contains("org.centre.read");
        List<String> scopes = JsonPath.read(denied, "$[?(@.actorLabel == 'priya-" + run + "' && @.action == 'org.centre.read')].scopeId");
        assertThat(scopes).contains(centre2);
        String approvals = get(adminA, "/v1/audit-events?action=iam.assignment.approve&outcome=SUCCESS", 200);
        assertThat(JsonPath.<List<String>>read(approvals, "$[*].actorLabel")).contains("bala-" + run);
        assertThat(JsonPath.<List<String>>read(approvals, "$[*].outcome")).containsOnly("SUCCESS");
        String self = get(adminA, "/v1/audit-events?outcome=DENIED&action=iam.role.version.approve", 200);
        assertThat(JsonPath.<List<String>>read(self, "$[*].reason").get(0)).contains("self-approval");
    }

    @Test
    @Order(10)
    void disablingAnAccountEndsAccessImmediately() throws Exception {
        get(staff, "/v1/me", 200);
        String user = JsonPath.<List<String>>read(get(adminB, "/v1/users", 200), "$[?(@.id == '" + userU + "')].version").toString();
        int v = Integer.parseInt(user.replaceAll("[^0-9]", ""));
        post(adminB, "/v1/users/" + userU + "/disable", "{\"reason\":\"Left the organisation\",\"expectedVersion\":" + v + "}", 200);
        get(staff, "/v1/me", 401);
        get(staff, "/v1/centres/" + centre1, 401);
    }

    // ------------------------------------------------------------------ helpers

    private String post(String subject, String path, String body, int expected) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path).with(user(subject)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String get(String subject, String path, int expected) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).with(user(subject)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }
}

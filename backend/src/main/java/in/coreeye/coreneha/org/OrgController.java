package in.coreeye.coreneha.org;

import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.org.OrgModels.CentreDepartmentView;
import in.coreeye.coreneha.org.OrgModels.CentreView;
import in.coreeye.coreneha.org.OrgModels.DepartmentView;
import in.coreeye.coreneha.org.OrgModels.LegalEntityView;
import in.coreeye.coreneha.org.OrgModels.LevelView;
import in.coreeye.coreneha.org.OrgModels.NodeView;
import in.coreeye.coreneha.org.OrgModels.RetireImpact;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class OrgController {

    private final OrgService org;
    private final CurrentActor actor;

    OrgController(OrgService org, CurrentActor actor) {
        this.org = org;
        this.actor = actor;
    }

    @GetMapping("/legal-entities")
    List<LegalEntityView> legalEntities() {
        return org.legalEntities(actor.require());
    }

    @PostMapping("/legal-entities")
    @ResponseStatus(HttpStatus.CREATED)
    LegalEntityView createLegalEntity(@Valid @RequestBody OrgModels.CreateLegalEntity body) {
        return org.createLegalEntity(actor.require(), body);
    }

    @GetMapping("/hierarchy/levels")
    List<LevelView> levels() {
        return org.levels(actor.require());
    }

    @PostMapping("/hierarchy/levels")
    @ResponseStatus(HttpStatus.CREATED)
    LevelView createLevel(@Valid @RequestBody OrgModels.CreateLevel body) {
        return org.createLevel(actor.require(), body);
    }

    @GetMapping("/hierarchy/nodes")
    List<NodeView> nodes() {
        return org.nodes(actor.require());
    }

    @PostMapping("/hierarchy/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    NodeView createNode(@Valid @RequestBody OrgModels.CreateNode body) {
        return org.createNode(actor.require(), body);
    }

    @GetMapping("/departments")
    List<DepartmentView> departments() {
        return org.departments(actor.require());
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentView createDepartment(@Valid @RequestBody OrgModels.CreateDepartment body) {
        return org.createDepartment(actor.require(), body);
    }

    @GetMapping("/centres")
    List<CentreView> centres() {
        return org.centres(actor.require());
    }

    @PostMapping("/centres")
    @ResponseStatus(HttpStatus.CREATED)
    CentreView createCentre(@Valid @RequestBody OrgModels.CreateCentre body) {
        return org.createCentre(actor.require(), body);
    }

    @GetMapping("/centres/{centreId}")
    CentreView centre(@PathVariable UUID centreId) {
        return org.centre(actor.require(), centreId);
    }

    @GetMapping("/centres/{centreId}/departments")
    List<CentreDepartmentView> centreDepartments(@PathVariable UUID centreId,
                                                 @RequestParam(defaultValue = "false") boolean includeRetired) {
        return org.centreDepartments(actor.require(), centreId, includeRetired);
    }

    @PostMapping("/centres/{centreId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    CentreDepartmentView enableDepartment(@PathVariable UUID centreId, @Valid @RequestBody OrgModels.EnableDepartment body) {
        return org.enableDepartment(actor.require(), centreId, body);
    }

    @GetMapping("/centres/{centreId}/departments/{centreDepartmentId}/retire-impact")
    RetireImpact retireImpact(@PathVariable UUID centreId, @PathVariable UUID centreDepartmentId) {
        return org.retireImpact(actor.require(), centreId, centreDepartmentId);
    }

    @PostMapping("/centres/{centreId}/departments/{centreDepartmentId}/retire")
    CentreDepartmentView retire(@PathVariable UUID centreId, @PathVariable UUID centreDepartmentId,
                                @Valid @RequestBody OrgModels.RetireDepartment body) {
        return org.retireDepartment(actor.require(), centreId, centreDepartmentId, body);
    }
}

package in.coreeye.coreneha.workforce;

import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.workforce.WorkforceModels.EstablishmentView;
import in.coreeye.coreneha.workforce.WorkforceModels.PersonDetail;
import in.coreeye.coreneha.workforce.WorkforceModels.PersonView;
import in.coreeye.coreneha.workforce.WorkforceModels.PositionView;
import in.coreeye.coreneha.workforce.WorkforceModels.PostingView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class WorkforceController {

    private final WorkforceService workforce;
    private final CurrentActor actor;

    WorkforceController(WorkforceService workforce, CurrentActor actor) {
        this.workforce = workforce;
        this.actor = actor;
    }

    @GetMapping("/positions")
    List<PositionView> positions() {
        return workforce.positions(actor.require());
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    PositionView definePosition(@Valid @RequestBody WorkforceModels.DefinePosition body) {
        return workforce.definePosition(actor.require(), body);
    }

    @GetMapping("/centres/{centreId}/establishments")
    List<EstablishmentView> establishments(@PathVariable UUID centreId) {
        return workforce.establishments(actor.require(), centreId);
    }

    @PostMapping("/centres/{centreId}/establishments")
    @ResponseStatus(HttpStatus.CREATED)
    EstablishmentView establish(@PathVariable UUID centreId, @Valid @RequestBody WorkforceModels.Establish body) {
        return workforce.establish(actor.require(), centreId, body);
    }

    @GetMapping("/people")
    List<PersonView> people() {
        return workforce.people(actor.require());
    }

    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    PersonView createPerson(@Valid @RequestBody WorkforceModels.CreatePerson body) {
        return workforce.createPerson(actor.require(), body, false);
    }

    @GetMapping("/people/{personId}")
    PersonDetail person(@PathVariable UUID personId) {
        return workforce.personDetail(actor.require(), personId);
    }

    @PostMapping("/people/{personId}/postings")
    @ResponseStatus(HttpStatus.CREATED)
    PostingView addPosting(@PathVariable UUID personId, @Valid @RequestBody WorkforceModels.AddPosting body) {
        return workforce.addPosting(actor.require(), personId, body);
    }

    @PostMapping("/postings/{postingId}/end")
    PostingView endPosting(@PathVariable UUID postingId, @Valid @RequestBody WorkforceModels.EndPosting body) {
        return workforce.endPosting(actor.require(), postingId, body);
    }
}

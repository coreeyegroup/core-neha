package in.coreeye.coreneha.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/audit-events")
class AuditController {

    private final AuditQuery query;
    private final CurrentActor currentActor;

    AuditController(AuditQuery query, CurrentActor currentActor) {
        this.query = query;
        this.currentActor = currentActor;
    }

    @GetMapping
    List<AuditQuery.EventView> search(@RequestParam(required = false) UUID actorUserId,
                                      @RequestParam(required = false) String objectType,
                                      @RequestParam(required = false) String objectId,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(required = false) String outcome,
                                      @RequestParam(required = false) Instant from,
                                      @RequestParam(required = false) Instant to,
                                      @RequestParam(required = false) Long beforeId,
                                      @RequestParam(required = false) Integer limit) {
        return query.search(currentActor.require(),
                new AuditQuery.Filter(actorUserId, objectType, objectId, action, outcome, from, to, beforeId, limit));
    }
}

package in.coreeye.coreneha.iam;

import in.coreeye.coreneha.kernel.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class AccessGuideController {

    private final CurrentActor actor;
    private final AccessGuideService guide;

    AccessGuideController(CurrentActor actor, AccessGuideService guide) {
        this.actor = actor;
        this.guide = guide;
    }

    /**
     * The access guide for the help centre: licence modules, every permission and what the signed-in
     * person holds; for group-wide administrators also every role and position with who holds what.
     */
    @GetMapping("/access-guide")
    AccessGuideService.AccessGuide accessGuide() {
        return guide.guide(actor.require());
    }
}

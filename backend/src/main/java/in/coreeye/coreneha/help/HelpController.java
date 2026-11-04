package in.coreeye.coreneha.help;

import in.coreeye.coreneha.kernel.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class HelpController {

    private final HelpService help;
    private final CurrentActor actor;

    HelpController(HelpService help, CurrentActor actor) {
        this.help = help;
        this.actor = actor;
    }

    /** The help chapters for the signed-in user's work only. */
    @GetMapping("/help")
    HelpService.HelpLibrary helpLibrary() {
        return help.library(actor.require());
    }
}

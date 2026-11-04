package in.coreeye.coreneha.patient;

import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.patient.PatientModels.PatientView;
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
@RequestMapping("/v1/patients")
class PatientController {

    private final PatientService patients;
    private final CurrentActor actor;

    PatientController(PatientService patients, CurrentActor actor) {
        this.patients = patients;
        this.actor = actor;
    }

    @GetMapping
    List<PatientView> searchPatients(@RequestParam(required = false) String q, @RequestParam(required = false) Integer limit) {
        return patients.search(actor.require(), q, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PatientView registerPatient(@Valid @RequestBody PatientModels.RegisterPatient body) {
        return patients.register(actor.require(), body);
    }

    @GetMapping("/{patientId}")
    PatientView patient(@PathVariable UUID patientId) {
        return patients.view(actor.require(), patientId);
    }

    @PutMapping("/{patientId}")
    PatientView updatePatient(@PathVariable UUID patientId, @Valid @RequestBody PatientModels.UpdatePatient body) {
        return patients.update(actor.require(), patientId, body);
    }
}

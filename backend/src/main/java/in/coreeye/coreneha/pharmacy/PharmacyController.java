package in.coreeye.coreneha.pharmacy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.pharmacy.PharmacyModels.PrescriptionView;
import in.coreeye.coreneha.pharmacy.PharmacyModels.ReturnLine;
import in.coreeye.coreneha.pharmacy.PharmacyModels.SaleView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
class PharmacyController {

    record CancelPrescription(@NotBlank @Size(max = 300) String reason) {
    }

    record ReturnSale(@NotEmpty @Size(max = 40) List<@Valid ReturnLine> lines) {
    }

    private final PharmacyService pharmacy;
    private final CurrentActor actor;

    PharmacyController(PharmacyService pharmacy, CurrentActor actor) {
        this.pharmacy = pharmacy;
        this.actor = actor;
    }

    @PostMapping("/centres/{centreId}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    PrescriptionView prescribe(@PathVariable UUID centreId, @Valid @RequestBody PharmacyModels.CreatePrescription cmd) {
        return pharmacy.prescribe(actor.require(), centreId, cmd);
    }

    @GetMapping("/centres/{centreId}/prescriptions")
    List<PrescriptionView> prescriptions(@PathVariable UUID centreId, @RequestParam(required = false) String status) {
        return pharmacy.prescriptions(actor.require(), centreId, status);
    }

    @GetMapping("/patients/{patientId}/prescriptions")
    List<PrescriptionView> patientPrescriptions(@PathVariable UUID patientId) {
        return pharmacy.prescriptionsForPatient(actor.require(), patientId);
    }

    @GetMapping("/prescriptions/{prescriptionId}")
    PrescriptionView prescription(@PathVariable UUID prescriptionId) {
        return pharmacy.getPrescription(actor.require(), prescriptionId);
    }

    @PostMapping("/prescriptions/{prescriptionId}/cancel")
    PrescriptionView cancelPrescription(@PathVariable UUID prescriptionId, @Valid @RequestBody CancelPrescription cmd) {
        return pharmacy.cancelPrescription(actor.require(), prescriptionId, cmd.reason());
    }

    @PostMapping("/pharmacy/sales")
    @ResponseStatus(HttpStatus.CREATED)
    SaleView dispense(@Valid @RequestBody PharmacyModels.Dispense cmd) {
        return pharmacy.dispense(actor.require(), cmd);
    }

    @GetMapping("/centres/{centreId}/pharmacy-sales")
    List<SaleView> sales(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate date) {
        return pharmacy.sales(actor.require(), centreId, date);
    }

    @GetMapping("/pharmacy/sales/{saleId}")
    SaleView sale(@PathVariable UUID saleId) {
        return pharmacy.getSale(actor.require(), saleId);
    }

    @PostMapping("/pharmacy/sales/{saleId}/return")
    SaleView returnSale(@PathVariable UUID saleId, @Valid @RequestBody ReturnSale cmd) {
        return pharmacy.returnToStock(actor.require(), saleId, cmd.lines());
    }
}

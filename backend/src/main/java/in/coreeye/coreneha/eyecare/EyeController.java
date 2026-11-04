package in.coreeye.coreneha.eyecare;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.billing.BillingDirectory.BillSummary;
import in.coreeye.coreneha.eyecare.EyeModels.AppointmentView;
import in.coreeye.coreneha.eyecare.EyeModels.ClinicBoard;
import in.coreeye.coreneha.eyecare.EyeModels.ClinicianView;
import in.coreeye.coreneha.eyecare.EyeModels.PatientEyeHistory;
import in.coreeye.coreneha.eyecare.EyeModels.SurgeryView;
import in.coreeye.coreneha.eyecare.EyeModels.VisitChart;
import in.coreeye.coreneha.eyecare.EyeModels.VisitView;
import in.coreeye.coreneha.kernel.CurrentActor;
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
@RequestMapping("/v1")
class EyeController {

    private final VisitService visits;
    private final SurgeryService surgeries;
    private final CurrentActor actor;

    EyeController(VisitService visits, SurgeryService surgeries, CurrentActor actor) {
        this.visits = visits;
        this.surgeries = surgeries;
        this.actor = actor;
    }

    @GetMapping("/centres/{centreId}/clinicians")
    List<ClinicianView> clinicians(@PathVariable UUID centreId, @RequestParam(required = false) String department) {
        return visits.clinicians(actor.require(), centreId, department);
    }

    @GetMapping("/centres/{centreId}/clinic-board")
    ClinicBoard clinicBoard(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate date) {
        return visits.board(actor.require(), centreId, date);
    }

    // appointments
    @GetMapping("/centres/{centreId}/appointments")
    List<AppointmentView> appointments(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate date) {
        return visits.appointments(actor.require(), centreId, date);
    }

    @PostMapping("/centres/{centreId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentView bookAppointment(@PathVariable UUID centreId, @Valid @RequestBody EyeModels.BookAppointment body) {
        return visits.book(actor.require(), centreId, body);
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    AppointmentView cancelAppointment(@PathVariable UUID appointmentId, @Valid @RequestBody EyeModels.CancelAppointment body) {
        return visits.cancelAppointment(actor.require(), appointmentId, body);
    }

    @PostMapping("/appointments/{appointmentId}/check-in")
    VisitView checkInAppointment(@PathVariable UUID appointmentId, @Valid @RequestBody EyeModels.CheckInAppointment body) {
        return visits.checkIn(actor.require(), appointmentId, body);
    }

    // visits
    @GetMapping("/centres/{centreId}/visits")
    List<VisitView> visits(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate date,
                           @RequestParam(required = false) String stage) {
        return visits.visits(actor.require(), centreId, date, stage);
    }

    @PostMapping("/centres/{centreId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    VisitView startWalkInVisit(@PathVariable UUID centreId, @Valid @RequestBody EyeModels.StartVisit body) {
        return visits.startWalkIn(actor.require(), centreId, body);
    }

    @GetMapping("/visits/{visitId}")
    VisitChart visitChart(@PathVariable UUID visitId) {
        return visits.chart(actor.require(), visitId);
    }

    @PutMapping("/visits/{visitId}/optometry")
    VisitChart saveOptometry(@PathVariable UUID visitId, @Valid @RequestBody EyeModels.SaveOptometry body) {
        return visits.saveOptometry(actor.require(), visitId, body);
    }

    @PutMapping("/visits/{visitId}/consultation")
    VisitChart saveConsultation(@PathVariable UUID visitId, @Valid @RequestBody EyeModels.SaveConsultation body) {
        return visits.saveConsultation(actor.require(), visitId, body);
    }

    @PostMapping("/visits/{visitId}/checkout")
    VisitView checkoutVisit(@PathVariable UUID visitId, @Valid @RequestBody EyeModels.Checkout body) {
        return visits.checkout(actor.require(), visitId, body);
    }

    @PostMapping("/visits/{visitId}/cancel")
    VisitView cancelVisit(@PathVariable UUID visitId, @Valid @RequestBody EyeModels.CancelVisit body) {
        return visits.cancelVisit(actor.require(), visitId, body);
    }

    @GetMapping("/patients/{patientId}/eye-history")
    PatientEyeHistory patientEyeHistory(@PathVariable UUID patientId) {
        return visits.history(actor.require(), patientId);
    }

    // surgeries
    @GetMapping("/centres/{centreId}/surgeries")
    List<SurgeryView> surgeries(@PathVariable UUID centreId, @RequestParam(required = false) String view,
                                @RequestParam(required = false) LocalDate date) {
        return surgeries.list(actor.require(), centreId, view, date);
    }

    @GetMapping("/surgeries/{surgeryId}")
    SurgeryView surgery(@PathVariable UUID surgeryId) {
        return surgeries.surgery(actor.require(), surgeryId);
    }

    @GetMapping("/surgeries/{surgeryId}/bills")
    List<BillSummary> surgeryBills(@PathVariable UUID surgeryId) {
        return surgeries.bills(actor.require(), surgeryId);
    }

    @PostMapping("/surgeries/{surgeryId}/counselling")
    SurgeryView counselSurgery(@PathVariable UUID surgeryId, @Valid @RequestBody EyeModels.Counsel body) {
        return surgeries.counsel(actor.require(), surgeryId, body);
    }

    @PostMapping("/surgeries/{surgeryId}/schedule")
    SurgeryView scheduleSurgery(@PathVariable UUID surgeryId, @Valid @RequestBody EyeModels.ScheduleSurgery body) {
        return surgeries.schedule(actor.require(), surgeryId, body);
    }

    @PutMapping("/surgeries/{surgeryId}/pre-op")
    SurgeryView saveSurgeryPreOp(@PathVariable UUID surgeryId, @Valid @RequestBody EyeModels.PreOp body) {
        return surgeries.preOp(actor.require(), surgeryId, body);
    }

    @PutMapping("/surgeries/{surgeryId}/operation")
    SurgeryView saveSurgeryOperation(@PathVariable UUID surgeryId, @Valid @RequestBody EyeModels.Operation body) {
        return surgeries.operate(actor.require(), surgeryId, body);
    }

    @PostMapping("/surgeries/{surgeryId}/cancel")
    SurgeryView cancelSurgery(@PathVariable UUID surgeryId, @Valid @RequestBody EyeModels.CancelSurgery body) {
        return surgeries.cancel(actor.require(), surgeryId, body);
    }
}

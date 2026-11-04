package in.coreeye.coreneha.eyecare;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.billing.BillingDirectory.BillSummary;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the eye-care API. RE = right eye, LE = left eye, BE = both eyes. */
public final class EyeModels {

    private EyeModels() {
    }

    static final String VISUAL_ACUITY = "^$|^(6/(5|6|7\\.5|9|12|18|24|36|60)P?|[1-5]/60|CF|HM|PL|PR|NPL|N(5|6|8|10|12|18|24|36))$";

    // ---------------------------------------------------------------- shared

    public record ClinicianView(UUID personId, String name, String positionTitle, String departmentCode, String departmentName) {
    }

    // ---------------------------------------------------------------- appointments

    public record AppointmentView(UUID id, UUID centreId, UUID patientId, String patientUhid, String patientName, String patientSex,
                                  Integer patientAge, String patientMobile, LocalDate appointmentDate, LocalTime slotTime,
                                  String visitType, String source, UUID consultantPersonId, String consultantName, String reason,
                                  String status, String cancelReason, UUID visitId, String createdByName,
                                  OffsetDateTime createdAt, int version) {
    }

    public record BookAppointment(@NotNull UUID patientId, @NotNull LocalDate appointmentDate, @NotNull LocalTime slotTime,
                                  @NotBlank @Pattern(regexp = "NEW|REVIEW|POST_OP") String visitType,
                                  @NotBlank @Pattern(regexp = "WALK_IN|PHONE|ONLINE|REFERRAL") String source,
                                  UUID consultantPersonId, @Size(max = 500) String reason) {
    }

    public record CancelAppointment(@NotBlank @Size(max = 500) String reason, boolean noShow, @NotNull Integer expectedVersion) {
    }

    public record CheckInAppointment(@Size(max = 500) String chiefComplaint, UUID consultantPersonId,
                                     @NotNull Integer expectedVersion) {
    }

    // ---------------------------------------------------------------- visits

    public record VisitView(UUID id, UUID centreId, UUID patientId, String patientUhid, String patientName, String patientSex,
                            Integer patientAge, String patientMobile, LocalDate visitDate, int tokenNo, String visitType,
                            String chiefComplaint, UUID consultantPersonId, String consultantName, String stage,
                            OffsetDateTime checkedInAt, OffsetDateTime optometryDoneAt, OffsetDateTime consultationDoneAt,
                            OffsetDateTime counsellingDoneAt, OffsetDateTime completedAt, Integer waitingMinutes,
                            boolean surgeryAdvised, String noChargeReason, String cancelReason, String centreName, int version) {
    }

    public record StartVisit(@NotNull UUID patientId, @NotBlank @Pattern(regexp = "NEW|REVIEW|POST_OP") String visitType,
                             @Size(max = 500) String chiefComplaint, UUID consultantPersonId) {
    }

    public record Checkout(@Size(max = 500) String noChargeReason, @NotNull Integer expectedVersion) {
    }

    public record CancelVisit(@NotBlank @Size(max = 500) String reason, @NotNull Integer expectedVersion) {
    }

    public record OptometryView(UUID visitId, String ucvaRe, String ucvaLe, String pgvaRe, String pgvaLe, String nearRe,
                                String nearLe, BigDecimal sphRe, BigDecimal cylRe, Integer axisRe, BigDecimal addRe, String bcvaRe,
                                BigDecimal sphLe, BigDecimal cylLe, Integer axisLe, BigDecimal addLe, String bcvaLe, Integer iopRe,
                                Integer iopLe, String iopMethod, String colourVision, String notes, String recordedByName,
                                OffsetDateTime recordedAt, int version) {
    }

    public record SaveOptometry(
            @Pattern(regexp = VISUAL_ACUITY) String ucvaRe, @Pattern(regexp = VISUAL_ACUITY) String ucvaLe,
            @Pattern(regexp = VISUAL_ACUITY) String pgvaRe, @Pattern(regexp = VISUAL_ACUITY) String pgvaLe,
            @Pattern(regexp = VISUAL_ACUITY) String nearRe, @Pattern(regexp = VISUAL_ACUITY) String nearLe,
            @DecimalMin("-30") @DecimalMax("30") BigDecimal sphRe, @DecimalMin("-10") @DecimalMax("10") BigDecimal cylRe,
            @Min(0) @Max(180) Integer axisRe, @DecimalMin("0") @DecimalMax("4") BigDecimal addRe,
            @Pattern(regexp = VISUAL_ACUITY) String bcvaRe,
            @DecimalMin("-30") @DecimalMax("30") BigDecimal sphLe, @DecimalMin("-10") @DecimalMax("10") BigDecimal cylLe,
            @Min(0) @Max(180) Integer axisLe, @DecimalMin("0") @DecimalMax("4") BigDecimal addLe,
            @Pattern(regexp = VISUAL_ACUITY) String bcvaLe,
            @Min(0) @Max(80) Integer iopRe, @Min(0) @Max(80) Integer iopLe,
            @Pattern(regexp = "^$|NCT|APPLANATION|TONOPEN|ICARE") String iopMethod,
            @Size(max = 200) String colourVision, @Size(max = 2000) String notes,
            boolean complete, Integer expectedVersion) {
    }

    public record ConsultationView(UUID visitId, String history, String anteriorSegmentRe, String anteriorSegmentLe,
                                   String posteriorSegmentRe, String posteriorSegmentLe, String diagnosis, String diagnosisCode,
                                   String diagnosisEye, String medications, boolean glassesPrescribed, String advice,
                                   Integer followUpDays, boolean surgeryAdvised, String surgeryProcedure, String surgeryEye,
                                   String surgeryUrgency, UUID consultantPersonId, String consultantName, String recordedByName,
                                   OffsetDateTime recordedAt, int version) {
    }

    public record SaveConsultation(
            @Size(max = 4000) String history,
            @Size(max = 2000) String anteriorSegmentRe, @Size(max = 2000) String anteriorSegmentLe,
            @Size(max = 2000) String posteriorSegmentRe, @Size(max = 2000) String posteriorSegmentLe,
            @Size(max = 500) String diagnosis,
            @Pattern(regexp = "^$|^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$", message = "ICD-10 code looks like H25.1") String diagnosisCode,
            @Pattern(regexp = "^$|RE|LE|BE") String diagnosisEye,
            @Size(max = 4000) String medications, boolean glassesPrescribed, @Size(max = 2000) String advice,
            @Min(0) @Max(730) Integer followUpDays,
            boolean surgeryAdvised, @Size(max = 200) String surgeryProcedure, @Pattern(regexp = "^$|RE|LE|BE") String surgeryEye,
            @Pattern(regexp = "^$|ROUTINE|SOON|URGENT") String surgeryUrgency,
            boolean complete, Integer expectedVersion) {
    }

    // ---------------------------------------------------------------- surgery

    public record SurgeryView(UUID id, UUID centreId, UUID patientId, String patientUhid, String patientName, String patientSex,
                              Integer patientAge, String patientMobile, UUID visitId, String procedureName, String eye,
                              String urgency, String status, String advisedByName, String diagnosis,
                              UUID packageItemId, String packageName, BigDecimal estimatedCost, String paymentPlan,
                              String insurerName, String counsellingNotes, LocalDate preferredDate, String counselledByName,
                              OffsetDateTime counselledAt, LocalDate surgeryDate, LocalTime slotTime, String otRoom,
                              UUID surgeonPersonId, String surgeonName, boolean consentSigned, boolean siteMarked,
                              boolean fitnessCleared, boolean biometryDone, String iolPower, boolean pupilDilated, String preOpBp,
                              String preOpBloodSugar, String preOpNotes, String preparedByName, OffsetDateTime preparedAt,
                              String anaesthesia, String iolImplanted, String operativeNotes, String complications,
                              String postOpInstructions, String operatedByName, OffsetDateTime startedAt,
                              OffsetDateTime completedAt, String cancelReason, OffsetDateTime createdAt, int version) {
    }

    public record Counsel(UUID packageItemId, @DecimalMin("0") BigDecimal estimatedCost,
                          @Pattern(regexp = "^$|SELF_PAY|INSURANCE|CORPORATE|GOVERNMENT_SCHEME") String paymentPlan,
                          @Size(max = 200) String insurerName, @Size(max = 4000) String notes, LocalDate preferredDate,
                          @NotBlank @Pattern(regexp = "ACCEPTED|DECLINED|UNDECIDED") String decision,
                          @NotNull Integer expectedVersion) {
    }

    public record ScheduleSurgery(@NotNull LocalDate surgeryDate, @NotNull LocalTime slotTime, @Size(max = 50) String otRoom,
                                  @NotNull UUID surgeonPersonId, @NotNull Integer expectedVersion) {
    }

    public record PreOp(boolean consentSigned, boolean siteMarked, boolean fitnessCleared, boolean biometryDone,
                        @Size(max = 50) String iolPower, boolean pupilDilated, @Size(max = 20) String preOpBp,
                        @Size(max = 20) String preOpBloodSugar, @Size(max = 2000) String preOpNotes, boolean ready,
                        @NotNull Integer expectedVersion) {
    }

    public record Operation(@Pattern(regexp = "^$|TOPICAL|PERIBULBAR|RETROBULBAR|SUBTENON|GENERAL") String anaesthesia,
                            @Size(max = 200) String iolImplanted, @Size(max = 8000) String operativeNotes,
                            @Size(max = 2000) String complications, @Size(max = 4000) String postOpInstructions,
                            boolean complete, @NotNull Integer expectedVersion) {
    }

    public record CancelSurgery(@NotBlank @Size(max = 500) String reason, @NotNull Integer expectedVersion) {
    }

    // ---------------------------------------------------------------- composite views

    /** The visit as every department sees it. Clinical sections are null unless the viewer may read them. */
    public record VisitChart(VisitView visit, boolean clinicalVisible, OptometryView optometry, ConsultationView consultation,
                             SurgeryView surgery, boolean billingVisible, List<BillSummary> bills, List<VisitView> previousVisits) {
    }

    public record HistoryVisit(VisitView visit, String diagnosis, String diagnosisCode, String bcvaRe, String bcvaLe,
                               Integer iopRe, Integer iopLe) {
    }

    public record PatientEyeHistory(UUID patientId, boolean clinicalVisible, List<HistoryVisit> visits, List<SurgeryView> surgeries,
                                    List<AppointmentView> upcomingAppointments) {
    }

    public record ClinicBoard(UUID centreId, LocalDate date, int appointmentsBooked, int appointmentsArrived,
                              int appointmentsCancelled, int visits, int waitingOptometry, int waitingConsultation,
                              int waitingCounselling, int waitingCheckout, int completed, int cancelled,
                              Integer averageWaitMinutes, Integer longestWaitMinutes, int newPatients, int surgeriesAdvised,
                              int surgeriesToSchedule, int surgeriesToday, int surgeriesReadyToday, int surgeriesCompletedToday,
                              int surgeriesUpcoming) {
    }
}

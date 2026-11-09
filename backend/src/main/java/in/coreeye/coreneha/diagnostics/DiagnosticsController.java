package in.coreeye.coreneha.diagnostics;

import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.diagnostics.DiagnosticsModels.Catalogue;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.DiagnosticOrderView;
import in.coreeye.coreneha.kernel.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class DiagnosticsController {

    private final DiagnosticsService diagnostics;
    private final ImagingAccess imaging;
    private final CurrentActor actor;

    DiagnosticsController(DiagnosticsService diagnostics, ImagingAccess imaging, CurrentActor actor) {
        this.diagnostics = diagnostics;
        this.imaging = imaging;
        this.actor = actor;
    }

    @GetMapping("/diagnostics/catalogue")
    Catalogue diagnosticCatalogue() {
        return diagnostics.catalogue(actor.require());
    }

    @PostMapping("/centres/{centreId}/diagnostic-orders")
    @ResponseStatus(HttpStatus.CREATED)
    List<DiagnosticOrderView> orderDiagnostics(@PathVariable UUID centreId, @Valid @RequestBody DiagnosticsModels.CreateOrders cmd) {
        return diagnostics.order(actor.require(), centreId, cmd);
    }

    @GetMapping("/centres/{centreId}/diagnostic-orders")
    List<DiagnosticOrderView> diagnosticWorklist(@PathVariable UUID centreId, @RequestParam String kind, @RequestParam(required = false) String status) {
        return diagnostics.worklist(actor.require(), centreId, kind, status);
    }

    @GetMapping("/patients/{patientId}/diagnostic-orders")
    List<DiagnosticOrderView> patientDiagnostics(@PathVariable UUID patientId) {
        return diagnostics.forPatient(actor.require(), patientId);
    }

    @GetMapping("/diagnostic-orders/{orderId}")
    DiagnosticOrderView diagnosticOrder(@PathVariable UUID orderId) {
        return diagnostics.get(actor.require(), orderId);
    }

    @PostMapping("/diagnostic-orders/{orderId}/cancel")
    DiagnosticOrderView cancelDiagnosticOrder(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Step cmd) {
        return diagnostics.cancel(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/collect")
    DiagnosticOrderView collectSample(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Step cmd) {
        return diagnostics.collect(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/receive")
    DiagnosticOrderView receiveSample(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Step cmd) {
        return diagnostics.receive(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/reject")
    DiagnosticOrderView rejectSample(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Step cmd) {
        return diagnostics.reject(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/results")
    DiagnosticOrderView enterLabResults(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.EnterResults cmd) {
        return diagnostics.enterResults(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/validate")
    DiagnosticOrderView validateLabResults(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Step cmd) {
        return diagnostics.validate(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/schedule")
    DiagnosticOrderView scheduleImaging(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Schedule cmd) {
        return diagnostics.schedule(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/acquire")
    DiagnosticOrderView acquireImaging(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Acquire cmd) {
        return diagnostics.acquire(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/report")
    DiagnosticOrderView reportImaging(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Report cmd) {
        return diagnostics.report(actor.require(), orderId, cmd);
    }

    @PostMapping("/diagnostic-orders/{orderId}/verify")
    DiagnosticOrderView verifyImaging(@PathVariable UUID orderId, @Valid @RequestBody DiagnosticsModels.Step cmd) {
        return diagnostics.verify(actor.require(), orderId, cmd);
    }

    /** For the gateway only (auth_request): may the signed-in user load this image-archive address? */
    @GetMapping("/imaging/authorize")
    ResponseEntity<Void> authorizeImaging(@RequestHeader(name = "X-Original-URI", required = false) String uri) {
        return imaging.allows(actor.require(), uri) ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}

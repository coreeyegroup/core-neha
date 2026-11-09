package in.coreeye.coreneha.optical;

import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.optical.OpticalModels.JobView;
import in.coreeye.coreneha.optical.OpticalModels.OrderView;
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
class OpticalController {

    private final OpticalService optical;
    private final CurrentActor actor;

    OpticalController(OpticalService optical, CurrentActor actor) {
        this.optical = optical;
        this.actor = actor;
    }

    @PostMapping("/optical/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView createSpectacleOrder(@Valid @RequestBody OpticalModels.CreateOrder cmd) {
        return optical.createOrder(actor.require(), cmd);
    }

    @GetMapping("/centres/{centreId}/spectacle-orders")
    List<OrderView> spectacleOrders(@PathVariable UUID centreId, @RequestParam(required = false) String status) {
        return optical.orders(actor.require(), centreId, status);
    }

    @GetMapping("/patients/{patientId}/spectacle-orders")
    List<OrderView> patientSpectacleOrders(@PathVariable UUID patientId) {
        return optical.ordersForPatient(actor.require(), patientId);
    }

    @GetMapping("/optical/orders/{orderId}")
    OrderView spectacleOrder(@PathVariable UUID orderId) {
        return optical.getOrder(actor.require(), orderId);
    }

    @PostMapping("/optical/orders/{orderId}/ready")
    OrderView markSpectaclesReady(@PathVariable UUID orderId, @Valid @RequestBody OpticalModels.OrderAction cmd) {
        return optical.markReady(actor.require(), orderId, cmd);
    }

    @PostMapping("/optical/orders/{orderId}/deliver")
    OrderView deliverSpectacles(@PathVariable UUID orderId, @Valid @RequestBody OpticalModels.OrderAction cmd) {
        return optical.deliver(actor.require(), orderId, cmd);
    }

    @PostMapping("/optical/orders/{orderId}/cancel")
    OrderView cancelSpectacleOrder(@PathVariable UUID orderId, @Valid @RequestBody OpticalModels.OrderAction cmd) {
        return optical.cancel(actor.require(), orderId, cmd);
    }

    @GetMapping("/centres/{centreId}/lab-jobs")
    List<JobView> labJobs(@PathVariable UUID centreId, @RequestParam(required = false) String status) {
        return optical.jobs(actor.require(), centreId, status);
    }

    @GetMapping("/lab-jobs/{jobId}")
    JobView labJob(@PathVariable UUID jobId) {
        return optical.getJob(actor.require(), jobId);
    }

    @PostMapping("/lab-jobs/{jobId}/advance")
    JobView advanceLabJob(@PathVariable UUID jobId, @Valid @RequestBody OpticalModels.AdvanceJob cmd) {
        return optical.advance(actor.require(), jobId, cmd);
    }

    @PostMapping("/lab-jobs/{jobId}/quality-check")
    JobView qualityCheckLabJob(@PathVariable UUID jobId, @Valid @RequestBody OpticalModels.QualityCheck cmd) {
        return optical.qualityCheck(actor.require(), jobId, cmd);
    }
}

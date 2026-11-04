package in.coreeye.coreneha.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.billing.BillingModels.BillDetail;
import in.coreeye.coreneha.billing.BillingModels.BillView;
import in.coreeye.coreneha.billing.BillingModels.CollectionReport;
import in.coreeye.coreneha.billing.BillingModels.DayClosePreview;
import in.coreeye.coreneha.billing.BillingModels.DayCloseView;
import in.coreeye.coreneha.billing.BillingModels.TariffItemView;
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
class BillingController {

    private final BillingService billing;
    private final AccountsService accounts;
    private final CurrentActor actor;

    BillingController(BillingService billing, AccountsService accounts, CurrentActor actor) {
        this.billing = billing;
        this.accounts = accounts;
        this.actor = actor;
    }

    // tariff
    @GetMapping("/centres/{centreId}/tariff")
    List<TariffItemView> tariff(@PathVariable UUID centreId, @RequestParam(defaultValue = "false") boolean includeRetired) {
        return billing.tariff(actor.require(), centreId, includeRetired);
    }

    @PostMapping("/centres/{centreId}/tariff")
    @ResponseStatus(HttpStatus.CREATED)
    TariffItemView createTariffItem(@PathVariable UUID centreId, @Valid @RequestBody BillingModels.CreateTariffItem body) {
        return billing.createTariffItem(actor.require(), centreId, body);
    }

    @PutMapping("/centres/{centreId}/tariff/{itemId}")
    TariffItemView updateTariffItem(@PathVariable UUID centreId, @PathVariable UUID itemId,
                                    @Valid @RequestBody BillingModels.UpdateTariffItem body) {
        return billing.updateTariffItem(actor.require(), centreId, itemId, body);
    }

    // bills
    @GetMapping("/centres/{centreId}/bills")
    List<BillView> bills(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String status) {
        return billing.bills(actor.require(), centreId, from, to, status);
    }

    @PostMapping("/centres/{centreId}/bills")
    @ResponseStatus(HttpStatus.CREATED)
    BillDetail createBill(@PathVariable UUID centreId, @Valid @RequestBody BillingModels.CreateBill body) {
        return billing.createBill(actor.require(), centreId, body);
    }

    @GetMapping("/centres/{centreId}/discounts/pending")
    List<BillView> pendingDiscounts(@PathVariable UUID centreId) {
        return billing.pendingDiscounts(actor.require(), centreId);
    }

    @GetMapping("/bills/{billId}")
    BillDetail bill(@PathVariable UUID billId) {
        return billing.bill(actor.require(), billId);
    }

    @PostMapping("/bills/{billId}/lines")
    BillDetail addBillLines(@PathVariable UUID billId, @Valid @RequestBody BillingModels.AddLines body) {
        return billing.addLines(actor.require(), billId, body);
    }

    @PostMapping("/bills/{billId}/remove-line")
    BillDetail removeBillLine(@PathVariable UUID billId, @Valid @RequestBody BillingModels.RemoveLine body) {
        return billing.removeLine(actor.require(), billId, body);
    }

    @PostMapping("/bills/{billId}/discount")
    BillDetail requestDiscount(@PathVariable UUID billId, @Valid @RequestBody BillingModels.RequestDiscount body) {
        return billing.requestDiscount(actor.require(), billId, body);
    }

    @PostMapping("/bills/{billId}/discount/decision")
    BillDetail decideDiscount(@PathVariable UUID billId, @Valid @RequestBody BillingModels.Decision body) {
        return billing.decideDiscount(actor.require(), billId, body);
    }

    @PostMapping("/bills/{billId}/payments")
    BillDetail collectPayment(@PathVariable UUID billId, @Valid @RequestBody BillingModels.CollectPayment body) {
        return billing.collect(actor.require(), billId, body);
    }

    @PostMapping("/bills/{billId}/cancel")
    BillDetail cancelBill(@PathVariable UUID billId, @Valid @RequestBody BillingModels.CancelBill body) {
        return billing.cancel(actor.require(), billId, body);
    }

    @GetMapping("/patients/{patientId}/bills")
    List<BillView> patientBills(@PathVariable UUID patientId) {
        return billing.billsForPatient(actor.require(), patientId);
    }

    // accounts
    @GetMapping("/centres/{centreId}/collections")
    CollectionReport collections(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate from,
                                 @RequestParam(required = false) LocalDate to) {
        return accounts.collections(actor.require(), centreId, from, to);
    }

    @GetMapping("/centres/{centreId}/day-closes")
    List<DayCloseView> dayCloses(@PathVariable UUID centreId) {
        return accounts.dayCloses(actor.require(), centreId);
    }

    @GetMapping("/centres/{centreId}/day-closes/preview")
    DayClosePreview dayClosePreview(@PathVariable UUID centreId, @RequestParam(required = false) LocalDate date) {
        return accounts.preview(actor.require(), centreId, date);
    }

    @PostMapping("/centres/{centreId}/day-closes")
    @ResponseStatus(HttpStatus.CREATED)
    DayCloseView submitDayClose(@PathVariable UUID centreId, @Valid @RequestBody BillingModels.SubmitDayClose body) {
        return accounts.submit(actor.require(), centreId, body);
    }

    @PostMapping("/day-closes/{dayCloseId}/decision")
    DayCloseView decideDayClose(@PathVariable UUID dayCloseId, @Valid @RequestBody BillingModels.Decision body) {
        return accounts.decide(actor.require(), dayCloseId, body);
    }
}

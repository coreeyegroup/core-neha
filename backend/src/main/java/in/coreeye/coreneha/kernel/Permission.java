package in.coreeye.coreneha.kernel;

import java.util.Arrays;
import java.util.Optional;

/**
 * Permission catalogue. Roles grant these; checks always name a permission,
 * never a role (ADR-0006). Must match iam.permission rows (verified by test).
 * {@code tenantOnly} permissions are effective only from a tenant-wide assignment.
 */
public enum Permission {
    ORG_STRUCTURE_READ("org.structure.read", false),
    ORG_STRUCTURE_MANAGE("org.structure.manage", true),
    ORG_CENTRE_CREATE("org.centre.create", false),
    ORG_CENTRE_READ("org.centre.read", false),
    ORG_CENTRE_CONFIGURE("org.centre.configure", false),
    WORKFORCE_POSITION_DEFINE("workforce.position.define", true),
    WORKFORCE_READ("workforce.read", false),
    WORKFORCE_ESTABLISH("workforce.establish", false),
    WORKFORCE_PERSON_MANAGE("workforce.person.manage", false),
    IAM_ROLE_READ("iam.role.read", true),
    IAM_ROLE_DRAFT("iam.role.draft", true),
    IAM_ROLE_APPROVE("iam.role.approve", true),
    IAM_USER_MANAGE("iam.user.manage", false),
    IAM_ASSIGNMENT_READ("iam.assignment.read", false),
    IAM_ASSIGNMENT_REQUEST("iam.assignment.request", false),
    IAM_ASSIGNMENT_APPROVE("iam.assignment.approve", false),
    IAM_ASSIGNMENT_REVOKE("iam.assignment.revoke", false),
    IAM_RESPONSIBILITY_MANAGE("iam.responsibility.manage", false),
    AUDIT_READ("audit.read", false),

    // Patients (one identity across the group)
    PATIENT_READ("patient.read", false),
    PATIENT_REGISTER("patient.register", false),
    // Outpatient flow: appointments, check-in, queue, checkout
    OPD_QUEUE_READ("opd.queue.read", false),
    OPD_APPOINTMENT_MANAGE("opd.appointment.manage", false),
    OPD_CHECKIN("opd.checkin", false),
    // Eye care clinical records
    EYE_CLINICAL_READ("eye.clinical.read", false),
    EYE_OPTOMETRY_RECORD("eye.optometry.record", false),
    EYE_CONSULTATION_RECORD("eye.consultation.record", false),
    EYE_COUNSELLING_RECORD("eye.counselling.record", false),
    EYE_SURGERY_SCHEDULE("eye.surgery.schedule", false),
    EYE_SURGERY_PREPARE("eye.surgery.prepare", false),
    EYE_SURGERY_OPERATE("eye.surgery.operate", false),
    // Billing and accounts
    BILLING_READ("billing.read", false),
    BILLING_CHARGE("billing.charge", false),
    BILLING_COLLECT("billing.collect", false),
    BILLING_DISCOUNT_APPROVE("billing.discount.approve", false),
    BILLING_TARIFF_MANAGE("billing.tariff.manage", false),
    BILLING_REPORT_READ("billing.report.read", false),
    BILLING_DAYCLOSE_SUBMIT("billing.dayclose.submit", false),
    BILLING_DAYCLOSE_APPROVE("billing.dayclose.approve", false),
    // Inventory: item master, stores, stock by batch, receipts, transfers, adjustments
    INVENTORY_ITEM_MANAGE("inventory.item.manage", true),
    INVENTORY_READ("inventory.read", false),
    INVENTORY_STORE_MANAGE("inventory.store.manage", false),
    INVENTORY_RECEIVE("inventory.receive", false),
    INVENTORY_TRANSFER("inventory.transfer", false),
    INVENTORY_ADJUST_REQUEST("inventory.adjust.request", false),
    INVENTORY_ADJUST_APPROVE("inventory.adjust.approve", false),
    // Pharmacy
    PHARMACY_PRESCRIBE("pharmacy.prescribe", false),
    PHARMACY_READ("pharmacy.read", false),
    PHARMACY_DISPENSE("pharmacy.dispense", false),
    PHARMACY_RETURN("pharmacy.return", false),
    // Optical shop
    OPTICAL_READ("optical.read", false),
    OPTICAL_ORDER("optical.order", false),
    OPTICAL_DELIVER("optical.deliver", false),
    // Optical manufacturing (lab)
    OPTICALLAB_READ("opticallab.read", false),
    OPTICALLAB_PROCESS("opticallab.process", false),
    OPTICALLAB_QC("opticallab.qc", false),
    // Laboratory
    LAB_CATALOGUE_MANAGE("lab.catalogue.manage", true),
    LAB_ORDER("lab.order", false),
    LAB_READ("lab.read", false),
    LAB_COLLECT("lab.collect", false),
    LAB_PROCESS("lab.process", false),
    LAB_VALIDATE("lab.validate", false),
    // Imaging (radiology)
    RADIOLOGY_CATALOGUE_MANAGE("radiology.catalogue.manage", true),
    RADIOLOGY_ORDER("radiology.order", false),
    RADIOLOGY_READ("radiology.read", false),
    RADIOLOGY_PERFORM("radiology.perform", false),
    RADIOLOGY_REPORT("radiology.report", false),
    RADIOLOGY_VERIFY("radiology.verify", false);

    private final String code;
    private final boolean tenantOnly;

    Permission(String code, boolean tenantOnly) {
        this.code = code;
        this.tenantOnly = tenantOnly;
    }

    public String code() {
        return code;
    }

    public boolean tenantOnly() {
        return tenantOnly;
    }

    /**
     * Administration permissions (organisation, workforce, access, audit). Only these
     * go to the bootstrap system administrator role: administrators configure the
     * group but do not see patients, clinical records or money unless given a role for it.
     */
    public boolean administrative() {
        return code.startsWith("org.") || code.startsWith("workforce.") || code.startsWith("iam.") || code.startsWith("audit.");
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}

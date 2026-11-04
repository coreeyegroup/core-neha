package in.coreeye.coreneha.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only organisation lookups for other modules (no access checks; callers check). */
public interface OrgDirectory {

    record CentreRef(UUID id, UUID tenantId, UUID nodeId, String nodePath, String code, String name, String status) {
    }

    record NodeRef(UUID id, UUID tenantId, String path, String name) {
    }

    record CentreDepartmentRef(UUID id, UUID centreId, UUID departmentId, String departmentCode, boolean open) {
    }

    Optional<CentreRef> centre(UUID centreId);

    Optional<NodeRef> node(UUID nodeId);

    List<CentreRef> allCentres();

    Optional<CentreDepartmentRef> centreDepartment(UUID centreDepartmentId);

    /** Operator commands only: the id of a tenant by its code (works without a tenant context). */
    Optional<UUID> tenantIdByCode(String code);
}

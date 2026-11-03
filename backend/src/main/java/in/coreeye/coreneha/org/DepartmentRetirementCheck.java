package in.coreeye.coreneha.org;

import java.util.List;
import java.util.UUID;

/**
 * Extension contract: modules that hang work on a centre department (positions
 * now; orders, rosters and stock later) report what would block its retirement.
 */
public interface DepartmentRetirementCheck {

    record Blocker(String module, String description, int count) {
    }

    List<Blocker> blockers(UUID centreDepartmentId);
}

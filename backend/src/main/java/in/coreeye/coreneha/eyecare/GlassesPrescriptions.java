package in.coreeye.coreneha.eyecare;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** The refraction recorded at a visit, for the optical shop (no access checks; callers check their own). */
public interface GlassesPrescriptions {

    record GlassesRx(UUID visitId, UUID patientId, BigDecimal sphRe, BigDecimal cylRe, Integer axisRe, BigDecimal addRe,
                     BigDecimal sphLe, BigDecimal cylLe, Integer axisLe, BigDecimal addLe) {
    }

    Optional<GlassesRx> forVisit(UUID visitId);
}

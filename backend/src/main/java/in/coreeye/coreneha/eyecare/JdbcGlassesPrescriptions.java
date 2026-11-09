package in.coreeye.coreneha.eyecare;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcGlassesPrescriptions implements GlassesPrescriptions {

    private final JdbcClient jdbc;

    JdbcGlassesPrescriptions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GlassesRx> forVisit(UUID visitId) {
        return jdbc.sql("""
                select o.visit_id, v.patient_id, o.sph_re, o.cyl_re, o.axis_re, o.add_re, o.sph_le, o.cyl_le, o.axis_le, o.add_le
                from eyecare.optometry_record o join eyecare.visit v on v.id = o.visit_id
                where o.visit_id = :v and (o.sph_re is not null or o.sph_le is not null or o.cyl_re is not null or o.cyl_le is not null)""")
                .param("v", visitId).query(GlassesRx.class).optional();
    }
}

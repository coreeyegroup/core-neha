package in.coreeye.coreneha.diagnostics;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.CoreNehaProperties;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Decides, for the gateway, whether the signed-in user may load an address of the image archive.
 * Allowed: the viewer's own files (for anyone who works with imaging), and DICOMweb requests for
 * one named study that belongs to an imaging order at a centre where the user can read imaging.
 * Everything else is refused: searching the whole archive, the archive's own pages and its REST API.
 * The gateway also refuses every method except GET and HEAD. Answers are remembered for a minute.
 */
@Component
public class ImagingAccess {

    private static final List<Permission> IMAGING = List.of(Permission.RADIOLOGY_READ, Permission.RADIOLOGY_PERFORM,
            Permission.RADIOLOGY_REPORT, Permission.RADIOLOGY_VERIFY);

    private record Key(UUID user, String what) {
    }

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final String prefix;
    private final Map<Key, Long> allowed = new ConcurrentHashMap<>();

    ImagingAccess(JdbcClient jdbc, AccessControl access, CoreNehaProperties props) {
        this.jdbc = jdbc;
        this.access = access;
        this.prefix = props.basePath() + "/api/pacs/";
    }

    public boolean allows(Actor actor, String originalUri) {
        if (originalUri == null || originalUri.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(originalUri);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(prefix) || path.contains("..")) {
            return false;
        }
        String rest = path.substring(prefix.length());
        if (rest.startsWith("ohif/") || rest.startsWith("stone-webviewer/")) {
            return remembered(actor, "viewer", () -> IMAGING.stream().anyMatch(p -> !access.centresWhere(actor, p).isEmpty()));
        }
        Optional<String> uid = Optional.empty();
        if (rest.startsWith("dicom-web/studies/")) {
            String after = rest.substring("dicom-web/studies/".length());
            int slash = after.indexOf('/');
            uid = Optional.of(slash < 0 ? after : after.substring(0, slash));
        } else if (rest.equals("dicom-web/studies") || rest.equals("dicom-web/series") || rest.equals("dicom-web/instances")) {
            uid = queryValue(uri.getRawQuery(), "StudyInstanceUID");
        }
        if (uid.isEmpty() || !uid.get().matches("^[0-9]+(\\.[0-9]+)*$")) {
            return false;
        }
        String study = uid.get();
        return remembered(actor, study, () -> {
            List<UUID> centres = jdbc.sql("select centre_id from diagnostics.diagnostic_order where study_instance_uid = :u")
                    .param("u", study).query(UUID.class).list();
            return !centres.isEmpty() && centres.stream().allMatch(c -> IMAGING.stream().anyMatch(p -> access.holds(actor, p, Scope.centre(c))));
        });
    }

    private boolean remembered(Actor actor, String what, java.util.function.BooleanSupplier check) {
        Key key = new Key(actor.userId(), what + "@" + actor.accessVersion());
        Long until = allowed.get(key);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            return true;
        }
        boolean ok = check.getAsBoolean();
        if (ok) {
            if (allowed.size() > 20_000) {
                allowed.clear();
            }
            allowed.put(key, now + 60_000);
        }
        return ok;
    }

    private static Optional<String> queryValue(String rawQuery, String name) {
        if (rawQuery == null) {
            return Optional.empty();
        }
        for (String part : rawQuery.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8).equalsIgnoreCase(name)) {
                String v = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                return v.contains(",") || v.contains("\\") ? Optional.empty() : Optional.of(v);
            }
        }
        return Optional.empty();
    }
}

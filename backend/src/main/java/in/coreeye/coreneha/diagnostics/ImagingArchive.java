package in.coreeye.coreneha.diagnostics;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The image archive (Orthanc, reached only on the internal network). Finds the study that a modality
 * stored under an order's accession number, and stores instances (used by the demonstration). Off when
 * CORE_NEHA_PACS_URL is empty; the technician then enters the Study Instance UID by hand.
 */
@Component
public class ImagingArchive {

    public record Study(String studyInstanceUid, Integer instances) {
    }

    private final String baseUrl;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    ImagingArchive(@Value("${CORE_NEHA_PACS_URL:}") String baseUrl, JsonMapper json) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.json = json;
    }

    public boolean enabled() {
        return !baseUrl.isBlank();
    }

    /** The study stored with this accession number, if the archive has one. */
    public Optional<Study> findByAccession(String accessionNo) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/dicom-web/studies?AccessionNumber="
                            + URLEncoder.encode(accessionNo, StandardCharsets.UTF_8) + "&includefield=00201208"))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/dicom+json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204 || res.body() == null || res.body().isBlank()) {
                return Optional.empty();
            }
            if (res.statusCode() != 200) {
                throw new IllegalStateException("Image archive answered " + res.statusCode());
            }
            List<?> arr = json.readValue(res.body(), List.class);
            if (arr.isEmpty() || !(arr.getFirst() instanceof Map<?, ?> study)) {
                return Optional.empty();
            }
            Object uid = first(study.get("0020000D"));
            Object n = first(study.get("00201208"));
            return uid == null ? Optional.empty()
                    : Optional.of(new Study(uid.toString(), n instanceof Number num ? num.intValue() : null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while asking the image archive", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The image archive could not be reached: " + e.getMessage(), e);
        }
    }

    private static Object first(Object element) {
        if (element instanceof Map<?, ?> m && m.get("Value") instanceof List<?> values && !values.isEmpty()) {
            return values.getFirst();
        }
        return null;
    }

    /** Stores one DICOM instance (Orthanc REST). */
    public void store(byte[] dicom) {
        if (!enabled()) {
            throw new IllegalStateException("No image archive is configured (CORE_NEHA_PACS_URL)");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/instances")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/dicom").POST(HttpRequest.BodyPublishers.ofByteArray(dicom)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Image archive refused the instance: " + res.statusCode() + " " + res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while storing in the image archive", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The image archive could not be reached: " + e.getMessage(), e);
        }
    }
}

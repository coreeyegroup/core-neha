package in.coreeye.coreneha.kernel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Business dates (appointment day, collection day, day close) are calendar days
 * in India Standard Time, not UTC: a patient seen at 01:00 IST belongs to that IST day.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private BusinessTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalTime now() {
        return LocalTime.now(ZONE).withSecond(0).withNano(0);
    }
}

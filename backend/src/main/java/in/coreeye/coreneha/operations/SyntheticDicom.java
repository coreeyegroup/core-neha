package in.coreeye.coreneha.operations;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * Writes small synthetic DICOM images (Secondary Capture, explicit VR little endian, 8-bit grey) for
 * the demonstration: drawn shapes, no real patient images. Enough for the archive to index them by
 * accession number and for the viewer to show them.
 */
final class SyntheticDicom {

    private static final String SECONDARY_CAPTURE = "1.2.840.10008.5.1.4.1.1.7";
    private static final String EXPLICIT_LE = "1.2.840.10008.1.2.1";

    record Study(String studyUid, String seriesUid) {
    }

    private SyntheticDicom() {
    }

    static String uid() {
        UUID u = UUID.randomUUID();
        BigInteger n = new BigInteger(1, ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array());
        return "2.25." + n;
    }

    static Study newStudy() {
        return new Study(uid(), uid());
    }

    /** One image of a series; {@code kind} picks what is drawn (FUNDUS, OCT, ORBIT, CHEST, BSCAN). */
    static byte[] image(Study study, int instance, int slices, String modality, String description, String accession, String patientName,
                        String patientId, String sex, String kind, LocalDateTime when) {
        int size = "OCT".equals(kind) ? 384 : 256;
        byte[] pixels = draw(kind, size, instance, slices);
        String sop = uid();
        ByteArrayOutputStream meta = new ByteArrayOutputStream();
        el(meta, 0x0002, 0x0001, "OB", new byte[] {0, 1});
        el(meta, 0x0002, 0x0002, "UI", ui(SECONDARY_CAPTURE));
        el(meta, 0x0002, 0x0003, "UI", ui(sop));
        el(meta, 0x0002, 0x0010, "UI", ui(EXPLICIT_LE));
        el(meta, 0x0002, 0x0012, "UI", ui("2.25.271828182845904523536"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[128]);
        out.writeBytes("DICM".getBytes(StandardCharsets.US_ASCII));
        el(out, 0x0002, 0x0000, "UL", ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(meta.size()).array());
        out.writeBytes(meta.toByteArray());
        String date = when.format(DateTimeFormatter.BASIC_ISO_DATE);
        String time = when.format(DateTimeFormatter.ofPattern("HHmmss"));
        el(out, 0x0008, 0x0016, "UI", ui(SECONDARY_CAPTURE));
        el(out, 0x0008, 0x0018, "UI", ui(sop));
        el(out, 0x0008, 0x0020, "DA", text(date));
        el(out, 0x0008, 0x0030, "TM", text(time));
        el(out, 0x0008, 0x0050, "SH", text(accession));
        el(out, 0x0008, 0x0060, "CS", text(modality));
        el(out, 0x0008, 0x0064, "CS", text("SYN"));
        el(out, 0x0008, 0x1030, "LO", text(description + " (synthetic demonstration image)"));
        el(out, 0x0010, 0x0010, "PN", text(patientName.replace(' ', '^')));
        el(out, 0x0010, 0x0020, "LO", text(patientId));
        el(out, 0x0010, 0x0040, "CS", text(sex.startsWith("F") ? "F" : sex.startsWith("M") ? "M" : "O"));
        el(out, 0x0020, 0x000D, "UI", ui(study.studyUid()));
        el(out, 0x0020, 0x000E, "UI", ui(study.seriesUid()));
        el(out, 0x0020, 0x0011, "IS", text("1"));
        el(out, 0x0020, 0x0013, "IS", text(String.valueOf(instance)));
        el(out, 0x0028, 0x0002, "US", us(1));
        el(out, 0x0028, 0x0004, "CS", text("MONOCHROME2"));
        el(out, 0x0028, 0x0010, "US", us(size));
        el(out, 0x0028, 0x0011, "US", us(size));
        el(out, 0x0028, 0x0100, "US", us(8));
        el(out, 0x0028, 0x0101, "US", us(8));
        el(out, 0x0028, 0x0102, "US", us(7));
        el(out, 0x0028, 0x0103, "US", us(0));
        el(out, 0x7FE0, 0x0010, "OB", pixels);
        return out.toByteArray();
    }

    private static byte[] draw(String kind, int n, int instance, int slices) {
        byte[] p = new byte[n * n];
        Random noise = new Random(instance * 7919L + kind.hashCode());
        double c = n / 2.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = x - c;
                double dy = y - c;
                double r = Math.sqrt(dx * dx + dy * dy);
                double v;
                switch (kind) {
                    case "FUNDUS" -> {
                        // Retina disc with an optic disc and branching vessels.
                        v = r < n * 0.46 ? 95 + 40 * (1 - r / (n * 0.46)) : 5;
                        double od = Math.hypot(x - n * 0.68, y - c);
                        if (od < n * 0.07) {
                            v = 225;
                        }
                        double vessel = Math.abs(Math.sin((Math.atan2(y - c, x - n * 0.68)) * 4)) * od;
                        if (r < n * 0.44 && od > n * 0.07 && vessel < 3.2) {
                            v -= 55;
                        }
                        if (Math.hypot(x - n * 0.38, y - c) < n * 0.05) {
                            v -= 30;
                        }
                    }
                    case "OCT" -> {
                        // Retinal layers with a foveal dip.
                        double fovea = 22 * Math.exp(-Math.pow((x - c) / (n * 0.08), 2));
                        double top = n * 0.38 + fovea;
                        double d = y - top;
                        v = d < 0 ? 8 : d < 6 ? 200 : d < 30 ? 70 + 40 * Math.sin(d / 3.0) : d < 40 ? 150 : d < 52 ? 230 : d < 90 ? 110 - d : 10;
                    }
                    case "ORBIT" -> {
                        // Axial head slice with both orbits and globes; changes slightly by slice.
                        double t = (instance - 1.0) / Math.max(1, slices - 1) - 0.5;
                        double head = Math.pow(dx / (n * 0.42), 2) + Math.pow(dy / (n * 0.47), 2);
                        v = head < 1 ? (head > 0.86 ? 235 : 95) : 0;
                        for (int side : new int[] {-1, 1}) {
                            double g = Math.hypot(x - (c + side * n * 0.17), y - (c - n * 0.24));
                            double globe = n * (0.085 - 0.03 * Math.abs(t));
                            if (g < globe) {
                                v = g > globe - 2 ? 170 : 45;
                            }
                            if (Math.abs(x - (c + side * n * 0.12)) < 3 && y > c - n * 0.17 && y < c - n * 0.02) {
                                v = 120;
                            }
                        }
                    }
                    case "CHEST" -> {
                        // Frontal chest radiograph: lungs dark, ribs and spine bright.
                        double lungs = Math.min(Math.hypot((x - c * 0.62) / 0.7, y - c * 0.95), Math.hypot((x - c * 1.38) / 0.7, y - c * 0.95));
                        v = lungs < n * 0.3 ? 45 : 170;
                        if (Math.abs(x - c) < n * 0.035) {
                            v = 215;
                        }
                        if (lungs < n * 0.32 && Math.abs(Math.sin(y / 9.0 + Math.abs(dx) / 40.0)) < 0.12) {
                            v += 70;
                        }
                    }
                    default -> {
                        // B-scan ultrasound: fan with a dark vitreous and bright retina.
                        double ang = Math.atan2(dx, y);
                        v = y > 0 && Math.abs(ang) < 0.75 ? (r < n * 0.25 ? 20 : r < n * 0.27 ? 210 : r < n * 0.6 ? 90 : 0) : 0;
                    }
                }
                v += noise.nextGaussian() * 6;
                p[y * n + x] = (byte) Math.max(0, Math.min(255, (int) v));
            }
        }
        return p;
    }

    private static void el(ByteArrayOutputStream out, int group, int element, String vr, byte[] value) {
        ByteBuffer h = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        h.putShort((short) group).putShort((short) element).put(vr.getBytes(StandardCharsets.US_ASCII));
        if (vr.equals("OB") || vr.equals("OW") || vr.equals("UN") || vr.equals("SQ") || vr.equals("UT")) {
            h.putShort((short) 0).putInt(value.length);
            out.write(h.array(), 0, 12);
        } else {
            h.putShort((short) value.length);
            out.write(h.array(), 0, 8);
        }
        out.writeBytes(value);
    }

    private static byte[] ui(String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        return b.length % 2 == 0 ? b : java.util.Arrays.copyOf(b, b.length + 1);
    }

    private static byte[] text(String s) {
        String v = s.length() % 2 == 0 ? s : s + " ";
        return v.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] us(int v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v).array();
    }
}

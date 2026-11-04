package in.coreeye.coreneha.help;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Serves help chapters filtered by audience. A chapter file starts with
 * {@code # Title}, {@code > summary} and {@code <!-- audience: ... -->}, where the audience is
 * {@code everyone}, {@code administrator} (holds iam.role.approve for the whole group), or
 * permission codes of which the user must hold at least one somewhere.
 */
@Service
public class HelpService {

    public record HelpChapter(String book, String slug, int order, String title, String summary, String body) {
    }

    public record HelpLibrary(boolean administrator, List<HelpChapter> chapters) {
    }

    record Entry(HelpChapter chapter, Set<String> audience) {
    }

    private static final Pattern FILE = Pattern.compile("^(\\d+)-(.+)\\.md$");
    private static final Pattern AUDIENCE = Pattern.compile("^<!--\\s*audience:\\s*(.*?)\\s*-->$");

    private final AccessControl access;
    private final List<Entry> entries;

    HelpService(AccessControl access) throws IOException {
        this.access = access;
        this.entries = load();
    }

    public HelpLibrary library(Actor actor) {
        boolean admin = access.holds(actor, Permission.IAM_ROLE_APPROVE, Scope.tenant(actor.tenantId()));
        Set<String> held = new HashSet<>();
        for (Permission p : Permission.values()) {
            if (access.holds(actor, p, Scope.tenant(actor.tenantId())) || (!p.tenantOnly() && !access.centresWhere(actor, p).isEmpty())) {
                held.add(p.code());
            }
        }
        List<HelpChapter> visible = entries.stream().filter(e -> visible(e.audience(), admin, held)).map(Entry::chapter).toList();
        return new HelpLibrary(admin, visible);
    }

    static boolean visible(Set<String> audience, boolean admin, Set<String> held) {
        if (audience.contains("everyone")) {
            return true;
        }
        if (audience.contains("administrator")) {
            return admin;
        }
        return audience.stream().anyMatch(held::contains);
    }

    private static List<Entry> load() throws IOException {
        List<Entry> list = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String book : List.of("manual", "developer")) {
            for (Resource r : resolver.getResources("classpath*:help/" + book + "/*.md")) {
                Matcher m = FILE.matcher(String.valueOf(r.getFilename()));
                if (!m.matches()) {
                    continue;
                }
                String raw;
                try (InputStream in = r.getInputStream()) {
                    raw = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                }
                List<String> lines = new ArrayList<>(Arrays.asList(raw.split("\n", -1)));
                String title = lines.isEmpty() ? "" : lines.removeFirst().replaceFirst("^#\\s+", "").trim();
                String summary = !lines.isEmpty() && lines.getFirst().startsWith(">") ? lines.removeFirst().replaceFirst("^>\\s*", "").trim() : "";
                Set<String> audience = Set.of("administrator");
                if (!lines.isEmpty()) {
                    Matcher a = AUDIENCE.matcher(lines.getFirst().trim());
                    if (a.matches()) {
                        audience = Set.of(a.group(1).trim().split("\\s+"));
                        lines.removeFirst();
                    }
                }
                list.add(new Entry(new HelpChapter(book, m.group(2), Integer.parseInt(m.group(1)), title, summary,
                        String.join("\n", lines).trim()), audience));
            }
        }
        list.sort(Comparator.comparing((Entry e) -> e.chapter().book().equals("manual") ? 0 : 1).thenComparingInt(e -> e.chapter().order()));
        return List.copyOf(list);
    }
}

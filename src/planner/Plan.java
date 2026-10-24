package planner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A student's plan: completed courses plus an ordered list of semesters. */
final class Plan {
    enum Status { OK, WARN, ERROR }

    record Check(Status status, List<String> messages) {}

    static final class Semester {
        String name;
        final List<String> courses = new ArrayList<>();

        Semester(String name) {
            this.name = name;
        }
    }

    private static final List<String> TERMS = List.of("Fall", "Winter", "Spring", "Summer");

    final Set<String> completed = new LinkedHashSet<>();
    final List<Semester> semesters = new ArrayList<>();

    /** Checks one course in a semester against everything taken before it. */
    Check check(Catalog catalog, int semIndex, String courseId) {
        Course c = catalog.get(courseId);
        if (c == null) return new Check(Status.WARN, List.of("Not found in catalog"));
        Semester sem = semesters.get(semIndex);
        List<String> msgs = new ArrayList<>();
        Status status = Status.OK;

        String earlier = whereTaken(courseId, semIndex);
        if (earlier != null) {
            msgs.add("Already in plan: " + earlier);
            status = Status.WARN;
        }

        Set<String> available = new HashSet<>(completed);
        for (int k = 0; k < semIndex; k++) available.addAll(semesters.get(k).courses);
        if (c.prereq.concurrentOk()) available.addAll(sem.courses);

        for (Prereq.Group g : c.prereq.groups()) {
            if (g.anyOf().stream().anyMatch(available::contains)) continue;
            if (g.soft()) {
                msgs.add("Can't verify: " + g.text());
                status = worst(status, Status.WARN);
            } else {
                msgs.add("Missing prerequisite: " + String.join(" or ", g.anyOf()));
                status = Status.ERROR;
            }
        }
        for (String note : c.prereq.notes()) {
            msgs.add("Check manually: " + note);
            status = worst(status, Status.WARN);
        }

        String term = termOf(sem.name);
        if (term != null && !c.terms.isEmpty() && !c.terms.contains(term)) {
            msgs.add("Not usually offered in " + term + " (offered: " + c.typ + ")");
            status = worst(status, Status.WARN);
        }

        if (msgs.isEmpty()) msgs.add(c.prereq.groups().isEmpty() ? "No prerequisites" : "Prerequisites met");
        return new Check(status, msgs);
    }

    /** How a catalog course relates to the plan, for the search-result badges. */
    enum Availability { IN_PLAN, READY, CHECK, LOCKED }

    /** Whether a course could be taken after everything currently in the plan. */
    Availability availability(Catalog catalog, String courseId) {
        Set<String> have = allPlanned();
        if (have.contains(courseId)) return Availability.IN_PLAN;
        Course c = catalog.get(courseId);
        if (c == null) return Availability.CHECK;
        boolean unsure = !c.prereq.notes().isEmpty();
        for (Prereq.Group g : c.prereq.groups()) {
            if (g.anyOf().stream().anyMatch(have::contains)) continue;
            if (!g.soft()) return Availability.LOCKED;
            unsure = true;
        }
        return unsure ? Availability.CHECK : Availability.READY;
    }

    /** Required prerequisites not covered anywhere in the plan, e.g. "MATH 241 or MATH 242". */
    List<String> missing(Catalog catalog, String courseId) {
        Course c = catalog.get(courseId);
        if (c == null) return List.of();
        Set<String> have = allPlanned();
        List<String> out = new ArrayList<>();
        for (Prereq.Group g : c.prereq.groups()) {
            if (!g.soft() && g.anyOf().stream().noneMatch(have::contains)) out.add(String.join(" or ", g.anyOf()));
        }
        return out;
    }

    private Set<String> allPlanned() {
        Set<String> all = new HashSet<>(completed);
        for (Semester s : semesters) all.addAll(s.courses);
        return all;
    }

    private String whereTaken(String courseId, int beforeIndex) {
        if (completed.contains(courseId)) return "Completed";
        for (int k = 0; k < beforeIndex; k++) {
            if (semesters.get(k).courses.contains(courseId)) return semesters.get(k).name;
        }
        return null;
    }

    private static Status worst(Status a, Status b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static String termOf(String semesterName) {
        String first = semesterName.trim().split("\\s+")[0];
        for (String t : TERMS) {
            if (t.equalsIgnoreCase(first)) return t;
        }
        return null;
    }

    /** Credit total for a list of courses as "12" or "12–15" for variable-credit courses. */
    static String creditTotal(Catalog catalog, Iterable<String> courseIds) {
        double min = 0, max = 0;
        for (String id : courseIds) {
            Course c = catalog.get(id);
            if (c == null) continue;
            min += c.minCredits;
            max += c.maxCredits;
        }
        return min == max ? fmt(min) : fmt(min) + "–" + fmt(max);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Suggests the semester after the given one, e.g. "Fall 2026" -> "Spring 2027". */
    static String nextTermName(String previous) {
        if (previous == null) return "Fall 2026";
        String[] parts = previous.trim().split("\\s+");
        if (parts.length == 2 && parts[1].matches("\\d{4}")) {
            int year = Integer.parseInt(parts[1]);
            switch (parts[0].toLowerCase()) {
                case "fall": return "Spring " + (year + 1);
                case "winter": return "Spring " + year;
                case "spring", "summer": return "Fall " + year;
                default: break;
            }
        }
        return "";
    }

    // File format, one line each:
    //   COMPLETED|ACCT 207|ACCT 208
    //   SEMESTER|Fall 2026|MATH 241|CHEM 103

    void save(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Course plan");
        lines.add(join("COMPLETED", completed));
        for (Semester s : semesters) lines.add(join("SEMESTER|" + s.name, s.courses));
        Files.write(path, lines);
    }

    private static String join(String head, Iterable<String> ids) {
        StringBuilder sb = new StringBuilder(head);
        for (String id : ids) sb.append('|').append(id);
        return sb.toString();
    }

    static Plan load(Path path) throws IOException {
        Plan plan = new Plan();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\\|", -1);
            switch (parts[0]) {
                case "COMPLETED" -> {
                    for (int k = 1; k < parts.length; k++) {
                        if (!parts[k].isBlank()) plan.completed.add(parts[k].trim());
                    }
                }
                case "SEMESTER" -> {
                    if (parts.length < 2) throw new IOException("Semester line has no name: " + line);
                    Semester s = new Semester(parts[1].trim());
                    for (int k = 2; k < parts.length; k++) {
                        if (!parts[k].isBlank()) s.courses.add(parts[k].trim());
                    }
                    plan.semesters.add(s);
                }
                default -> throw new IOException("Unrecognized line: " + line);
            }
        }
        return plan;
    }
}

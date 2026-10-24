package planner.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free-text prerequisites like "CHEM103/104 or equivalent, MSEG302 or
 * permission of instructor." into structured {@link Prereq} groups.
 *
 * Rules:
 *  - ";" and sentence breaks separate requirements (AND).
 *  - "," and "and" separate requirements (AND), unless the list ends in
 *    ", or X", in which case the whole list is alternatives (OR).
 *  - "or" and "/" separate alternatives (OR).
 *  - A bare number like "104" in "CHEM 103/104" reuses the last department.
 *  - Only course codes that exist in the catalog are kept; anything else
 *    becomes a note the user has to check themselves.
 */
final class PrereqParser {
    private static final Pattern CODE = Pattern.compile("\\b([A-Z]{2,5})\\s?(\\d{3}[A-Z]?)\\b");
    private static final Pattern BARE = Pattern.compile("^(\\d{3}[A-Z]?)\\b(?!-)");
    private static final Pattern GRADE = Pattern.compile(
            "(?i)\\b(?:a\\s+)?(?:minimum\\s+)?(?:grade\\s+of\\s+)?[A-D][+-]?\\s+or\\s+(?:better|higher)(?:\\s+in)?(?=\\s|$)");
    private static final Pattern CONCURRENT = Pattern.compile("(?i)concurrent|co-?requisite");

    private PrereqParser() {}

    static Prereq parse(String text, Catalog catalog, String selfId) {
        if (text == null || text.isBlank()) return Prereq.NONE;
        List<Prereq.Group> groups = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String[] lastDept = {null};

        for (String sentence : normalize(text).split("\\s*(?:;|\\.(?=\\s|$))\\s*")) {
            if (sentence.isBlank()) continue;
            for (String clause : splitRequirements(sentence.trim())) {
                parseClause(clause, catalog, selfId, lastDept, groups, notes);
            }
        }
        return new Prereq(List.copyOf(groups), List.copyOf(notes), CONCURRENT.matcher(text).find());
    }

    /**
     * Removes grade wording ("C- or better in") so its "or" isn't read as an
     * alternative, and treats comma lists inside parentheses as alternatives,
     * e.g. "approved substitutes (STAT200, MATH202)".
     */
    private static String normalize(String text) {
        String t = GRADE.matcher(text).replaceAll("");
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (char ch : t.toCharArray()) {
            if (ch == '(') depth++;
            else if (ch == ')') depth = Math.max(0, depth - 1);
            if (ch == '(' || ch == ')') sb.append(' ');
            else if (ch == ',' && depth > 0) sb.append(" or ");
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static List<String> splitRequirements(String sentence) {
        String[] pieces = sentence.split("\\s*,\\s*");
        boolean orList = false;
        for (int k = 1; k < pieces.length; k++) {
            if (pieces[k].toLowerCase().startsWith("or ")) orList = true;
        }
        List<String> out = new ArrayList<>();
        if (orList) {
            out.add(String.join(" or ", pieces));
            return out;
        }
        for (String piece : pieces) {
            for (String part : piece.split("(?i)\\s+and\\s+")) {
                if (!part.isBlank()) out.add(part.trim());
            }
        }
        return out;
    }

    private static void parseClause(String clause, Catalog catalog, String selfId, String[] lastDept,
                                    List<Prereq.Group> groups, List<String> notes) {
        Set<String> codes = new LinkedHashSet<>();
        boolean hasNonCourseAlternative = false;

        for (String alt : clause.split("(?i)\\s*(?:\\bor\\b|/)\\s*")) {
            alt = alt.trim();
            if (alt.isEmpty()) continue;
            List<String> found = new ArrayList<>();
            boolean sawCode = false;
            Matcher m = CODE.matcher(alt);
            while (m.find()) {
                sawCode = true;
                lastDept[0] = m.group(1);
                addIfKnown(catalog, selfId, m.group(1) + " " + m.group(2), found);
            }
            if (!sawCode && lastDept[0] != null) {
                Matcher bare = BARE.matcher(alt);
                if (bare.find()) addIfKnown(catalog, selfId, lastDept[0] + " " + bare.group(1), found);
            }
            if (found.isEmpty()) hasNonCourseAlternative = true;
            else codes.addAll(found);
        }

        if (codes.isEmpty()) notes.add(clause);
        else groups.add(new Prereq.Group(List.copyOf(codes), hasNonCourseAlternative, clause));
    }

    private static void addIfKnown(Catalog catalog, String selfId, String code, List<String> out) {
        Course c = catalog.get(code);
        if (c != null && !c.id.equals(selfId)) out.add(c.id);
    }
}

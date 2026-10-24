package planner;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prerequisites for a course: every group must be satisfied (AND), and a group
 * is satisfied by any one of its courses (OR).
 *
 * @param groups       course requirements the planner can check
 * @param notes        prerequisite text that could not be turned into courses
 * @param concurrentOk true if the text allows taking prerequisites in the same semester
 */
record Prereq(List<Group> groups, List<String> notes, boolean concurrentOk) {
    static final Prereq NONE = new Prereq(List.of(), List.of(), false);

    /**
     * @param anyOf course ids, any one of which satisfies the group
     * @param soft  true if the group also had a non-course alternative
     *              (e.g. "or permission of instructor"), so a miss is a warning
     * @param text  the original text of this part of the prerequisite
     */
    record Group(List<String> anyOf, boolean soft, String text) {}

    String describe() {
        return groups.stream().map(g -> {
            String s = g.anyOf().size() == 1 ? g.anyOf().get(0) : "(" + String.join(" or ", g.anyOf()) + ")";
            return g.soft() ? s + " [or other option]" : s;
        }).collect(Collectors.joining(" AND "));
    }
}

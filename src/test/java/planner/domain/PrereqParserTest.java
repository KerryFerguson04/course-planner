package planner.domain;

import org.junit.jupiter.api.Test;
import planner.TestCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One test per prerequisite pattern listed in the README. */
class PrereqParserTest {
    private final Catalog catalog = TestCatalog.get();

    private Prereq parse(String text) {
        return PrereqParser.parse(text, catalog, "TEST 000");
    }

    private static List<List<String>> groups(Prereq p) {
        return p.groups().stream().map(Prereq.Group::anyOf).toList();
    }

    @Test
    void blankTextHasNoPrerequisites() {
        assertThat(parse("")).isSameAs(Prereq.NONE);
        assertThat(parse(null)).isSameAs(Prereq.NONE);
    }

    @Test
    void singleCourse() {
        assertThat(groups(parse("MATH 241."))).containsExactly(List.of("MATH 241"));
    }

    @Test
    void andMeansAllRequired() {
        assertThat(groups(parse("ECON 101 and ECON 103"))).containsExactly(List.of("ECON 101"), List.of("ECON 103"));
    }

    @Test
    void semicolonsAndSentencesMeanAllRequired() {
        assertThat(groups(parse("ECON 101; ECON 103"))).hasSize(2);
        assertThat(groups(parse("ECON 101. ECON 103."))).hasSize(2);
    }

    @Test
    void orMeansAnyOne() {
        assertThat(groups(parse("CHEM 103 or CHEM 104"))).containsExactly(List.of("CHEM 103", "CHEM 104"));
    }

    @Test
    void slashShorthandReusesDepartment() {
        assertThat(groups(parse("CHEM103/104"))).containsExactly(List.of("CHEM 103", "CHEM 104"));
    }

    @Test
    void commaListEndingInOrIsAnyOne() {
        assertThat(groups(parse("MATH 241, 242, or 243"))).containsExactly(List.of("MATH 241", "MATH 242", "MATH 243"));
    }

    @Test
    void commaListWithoutOrIsAllRequired() {
        assertThat(groups(parse("MATH 241, ECON 101"))).containsExactly(List.of("MATH 241"), List.of("ECON 101"));
    }

    @Test
    void parenthesesListAlternatives() {
        Prereq p = parse("PSYC209 or approved substitutes (STAT200, MATH241).");
        assertThat(groups(p)).containsExactly(List.of("PSYC 209", "STAT 200", "MATH 241"));
    }

    @Test
    void gradeWordingIsIgnored() {
        Prereq p = parse("C- or better in PSYC100.");
        assertThat(groups(p)).containsExactly(List.of("PSYC 100"));
        assertThat(p.groups().get(0).soft()).isFalse();
    }

    @Test
    void nonCourseAlternativeMakesGroupSoft() {
        Prereq p = parse("CHEM 321 or permission of instructor.");
        assertThat(groups(p)).containsExactly(List.of("CHEM 321"));
        assertThat(p.groups().get(0).soft()).isTrue();
        assertThat(p.describe()).isEqualTo("CHEM 321 [or other option]");
    }

    @Test
    void unreadableTextBecomesANote() {
        Prereq p = parse("Any two 200-level German courses.");
        assertThat(p.groups()).isEmpty();
        assertThat(p.notes()).containsExactly("Any two 200-level German courses");
    }

    @Test
    void codesNotInCatalogAreDropped() {
        Prereq p = parse("MSEG301.");
        assertThat(p.groups()).isEmpty();
        assertThat(p.notes()).containsExactly("MSEG301");
    }

    @Test
    void courseCannotRequireItself() {
        assertThat(PrereqParser.parse("MATH 241", catalog, "MATH 241").groups()).isEmpty();
    }

    @Test
    void detectsConcurrentEnrollment() {
        assertThat(parse("MATH 241 may be taken concurrently").concurrentOk()).isTrue();
        assertThat(parse("Corequisite: MATH 241").concurrentOk()).isTrue();
        assertThat(parse("MATH 241").concurrentOk()).isFalse();
    }

    @Test
    void describeJoinsGroups() {
        assertThat(parse("MATH 241 or MATH 242; ECON 101").describe()).isEqualTo("(MATH 241 or MATH 242) AND ECON 101");
    }
}

package planner.domain;

import org.junit.jupiter.api.Test;
import planner.TestCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {
    private final Catalog catalog = TestCatalog.get();

    /** Builds a plan from semesters given as "Name: COURSE, COURSE". */
    private static Plan plan(List<String> completed, String... semesters) {
        Plan plan = new Plan();
        plan.completed.addAll(completed);
        for (String spec : semesters) {
            String[] parts = spec.split(":", 2);
            Plan.Semester s = new Plan.Semester(parts[0].trim());
            if (parts.length > 1 && !parts[1].isBlank()) {
                for (String id : parts[1].split(",")) s.courses.add(id.trim());
            }
            plan.semesters.add(s);
        }
        return plan;
    }

    @Test
    void prerequisiteInEarlierSemesterIsMet() {
        Plan p = plan(List.of(), "Fall 2026: MATH 241", "Spring 2027: MATH 242");
        Plan.Check check = p.check(catalog, 1, "MATH 242");
        assertThat(check.status()).isEqualTo(Plan.Status.OK);
        assertThat(check.messages()).containsExactly("Prerequisites met");
    }

    @Test
    void completedCoursesCount() {
        Plan p = plan(List.of("MATH 241"), "Fall 2026: MATH 242");
        assertThat(p.check(catalog, 0, "MATH 242").status()).isEqualTo(Plan.Status.OK);
    }

    @Test
    void missingPrerequisiteIsAnError() {
        Plan p = plan(List.of(), "Fall 2026: MATH 242");
        Plan.Check check = p.check(catalog, 0, "MATH 242");
        assertThat(check.status()).isEqualTo(Plan.Status.ERROR);
        assertThat(check.messages()).containsExactly("Missing prerequisite: MATH 241");
    }

    @Test
    void sameSemesterDoesNotCountUnlessConcurrentAllowed() {
        Plan same = plan(List.of(), "Fall 2026: MATH 241, MATH 242");
        assertThat(same.check(catalog, 0, "MATH 242").status()).isEqualTo(Plan.Status.ERROR);

        // PHYS 208 needs PHYS 207 and MATH 242, and allows taking them concurrently
        Plan concurrent = plan(List.of("MATH 241"), "Fall 2026: MATH 242, PHYS 207, PHYS 208");
        assertThat(concurrent.check(catalog, 0, "PHYS 208").status()).isEqualTo(Plan.Status.OK);
    }

    @Test
    void anyOneAlternativeSatisfiesGroup() {
        Plan p = plan(List.of("MATH 243"), "Fall 2026: PHYS 207");
        assertThat(p.check(catalog, 0, "PHYS 207").status()).isEqualTo(Plan.Status.OK);
    }

    @Test
    void softGroupMissIsAWarning() {
        Plan p = plan(List.of(), "Fall 2026: CHEM 444");
        Plan.Check check = p.check(catalog, 0, "CHEM 444");
        assertThat(check.status()).isEqualTo(Plan.Status.WARN);
        assertThat(check.messages()).containsExactly("Can't verify: CHEM 321 or permission of instructor");
    }

    @Test
    void notesAreWarnings() {
        Plan p = plan(List.of(), "Fall 2026: GRMN 355");
        Plan.Check check = p.check(catalog, 0, "GRMN 355");
        assertThat(check.status()).isEqualTo(Plan.Status.WARN);
        assertThat(check.messages()).containsExactly("Check manually: Any two 200-level German courses");
    }

    @Test
    void wrongTermIsAWarning() {
        Plan p = plan(List.of(), "Spring 2027: CHEM 103");
        Plan.Check check = p.check(catalog, 0, "CHEM 103");
        assertThat(check.status()).isEqualTo(Plan.Status.WARN);
        assertThat(check.messages()).containsExactly("Not usually offered in Spring (offered: Fall)");
    }

    @Test
    void unrecognizedSemesterNameSkipsTermCheck() {
        Plan p = plan(List.of(), "Year 1: CHEM 103");
        assertThat(p.check(catalog, 0, "CHEM 103").status()).isEqualTo(Plan.Status.OK);
    }

    @Test
    void duplicateIsAWarning() {
        Plan p = plan(List.of("ECON 101"), "Fall 2026: ECON 101");
        assertThat(p.check(catalog, 0, "ECON 101").messages()).contains("Already in plan: Completed");

        Plan twice = plan(List.of(), "Fall 2026: ECON 101", "Spring 2027: ECON 101");
        assertThat(twice.check(catalog, 1, "ECON 101").messages()).contains("Already in plan: Fall 2026");
    }

    @Test
    void errorOutranksWarning() {
        // CHEM 104 is Spring-only (warning) and needs CHEM 103 (error)
        Plan p = plan(List.of(), "Fall 2026: CHEM 104");
        Plan.Check check = p.check(catalog, 0, "CHEM 104");
        assertThat(check.status()).isEqualTo(Plan.Status.ERROR);
        assertThat(check.messages()).hasSize(2);
    }

    @Test
    void unknownCourseIsAWarning() {
        Plan p = plan(List.of(), "Fall 2026: FAKE 101");
        assertThat(p.check(catalog, 0, "FAKE 101").status()).isEqualTo(Plan.Status.WARN);
    }

    @Test
    void noPrerequisitesMessage() {
        Plan p = plan(List.of(), "Fall 2026: ECON 101");
        assertThat(p.check(catalog, 0, "ECON 101").messages()).containsExactly("No prerequisites");
    }

    @Test
    void availabilityBadges() {
        Plan p = plan(List.of("MATH 241"), "Fall 2026: ECON 101");
        assertThat(p.availability(catalog, "ECON 101")).isEqualTo(Plan.Availability.IN_PLAN);
        assertThat(p.availability(catalog, "MATH 242")).isEqualTo(Plan.Availability.READY);
        assertThat(p.availability(catalog, "MATH 243")).isEqualTo(Plan.Availability.LOCKED);
        assertThat(p.availability(catalog, "GRMN 355")).isEqualTo(Plan.Availability.CHECK);
        assertThat(p.availability(catalog, "CHEM 444")).isEqualTo(Plan.Availability.CHECK);
        assertThat(p.availability(catalog, "FAKE 101")).isEqualTo(Plan.Availability.CHECK);
    }

    @Test
    void missingListsUncoveredRequiredGroups() {
        Plan p = plan(List.of("ECON 101"));
        assertThat(p.missing(catalog, "ECON 316")).containsExactly("ECON 103");
        assertThat(p.missing(catalog, "CHEM 444")).isEmpty(); // soft groups aren't "missing"
        assertThat(p.missing(catalog, "FAKE 101")).isEmpty();
    }

    @Test
    void creditTotals() {
        assertThat(Plan.creditTotal(catalog, List.of("MATH 241", "ECON 101"))).isEqualTo("7");
        assertThat(Plan.creditTotal(catalog, List.of("MATH 241", "ACCT 166"))).isEqualTo("5–7");
        assertThat(Plan.creditTotal(catalog, List.of("FAKE 101"))).isEqualTo("0");
    }

    @Test
    void nextTermNames() {
        assertThat(Plan.nextTermName(null)).isEqualTo("Fall 2026");
        assertThat(Plan.nextTermName("Fall 2026")).isEqualTo("Spring 2027");
        assertThat(Plan.nextTermName("Spring 2027")).isEqualTo("Fall 2027");
        assertThat(Plan.nextTermName("Summer 2027")).isEqualTo("Fall 2027");
        assertThat(Plan.nextTermName("Winter 2027")).isEqualTo("Spring 2027");
        assertThat(Plan.nextTermName("Autumn 2027")).isEmpty();
        assertThat(Plan.nextTermName("Year 1")).isEmpty();
    }
}

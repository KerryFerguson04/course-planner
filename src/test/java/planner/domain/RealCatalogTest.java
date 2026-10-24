package planner.domain;

import org.junit.jupiter.api.Test;
import planner.TestCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/** Sanity checks against the real catalog.json, so parser changes can't silently lose coverage. */
class RealCatalogTest {
    private final Catalog catalog = TestCatalog.load("/catalog.json");

    @Test
    void loadsAllCourses() {
        assertThat(catalog.all()).hasSize(7436);
    }

    @Test
    void mostPrerequisitesAreCheckable() {
        long withText = catalog.all().stream().filter(c -> !c.preReqText.isEmpty()).count();
        long checkable = catalog.all().stream().filter(c -> !c.prereq().groups().isEmpty()).count();
        assertThat(withText).isEqualTo(2311);
        assertThat(checkable).isGreaterThanOrEqualTo(2089);
    }
}

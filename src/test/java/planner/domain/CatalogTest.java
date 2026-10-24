package planner.domain;

import org.junit.jupiter.api.Test;
import planner.TestCatalog;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogTest {
    private final Catalog catalog = TestCatalog.get();

    @Test
    void loadsEveryCourse() {
        assertThat(catalog.all()).hasSize(19);
    }

    @Test
    void lookupIgnoresSpacesAndCase() {
        assertThat(catalog.get("MATH 241")).isNotNull();
        assertThat(catalog.get("math241")).isSameAs(catalog.get("MATH 241"));
        assertThat(catalog.get(" Math  241 ")).isSameAs(catalog.get("MATH 241"));
        assertThat(catalog.get("MATH 999")).isNull();
    }

    @Test
    void searchMatchesCodePrefixOrTitleWords() {
        assertThat(catalog.search("math 24", 50)).extracting(c -> c.id)
                .containsExactly("MATH 241", "MATH 242", "MATH 243");
        assertThat(catalog.search("chemistry", 50)).extracting(c -> c.id)
                .contains("CHEM 103", "CHEM 104", "CHEM 321");
        assertThat(catalog.search("", 5)).hasSize(5);
        assertThat(catalog.search("nothing matches this", 50)).isEmpty();
    }

    @Test
    void indexesWhichCoursesEachCourseUnlocks() {
        assertThat(catalog.unlockedBy("MATH 241")).contains("MATH 242", "PHYS 207", "PSYC 402");
        assertThat(catalog.unlockedBy("GRMN 355")).isEmpty();
    }

    @Test
    void parsesCreditsAndTerms() {
        Course variable = catalog.get("ACCT 166");
        assertThat(variable.minCredits).isEqualTo(1);
        assertThat(variable.maxCredits).isEqualTo(3);
        assertThat(variable.terms).isEmpty();

        Course calc = catalog.get("MATH 241");
        assertThat(calc.minCredits).isEqualTo(4);
        assertThat(calc.maxCredits).isEqualTo(4);
        assertThat(calc.terms).containsExactly("Fall", "Spring");
        assertThat(calc).hasToString("MATH 241 — Analytic Geometry and Calculus A");
    }

    @Test
    void rejectsMalformedJson() {
        var badJson = new ByteArrayInputStream("{ not json".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> Catalog.load(badJson)).isInstanceOf(java.io.IOException.class);
    }
}

package planner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseApiTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void searchWithoutPlanHasNoBadges() throws Exception {
        mvc.perform(get("/api/courses").param("q", "math 24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("MATH 241"))
                .andExpect(jsonPath("$[0].credits").value("4"))
                .andExpect(jsonPath("$[0]", not(hasKey("availability"))));
    }

    @Test
    void searchLimitIsClamped() throws Exception {
        mvc.perform(get("/api/courses").param("limit", "2")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/courses").param("limit", "-5")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void searchWithPlanShowsBadges() throws Exception {
        JsonNode plan = json.readTree(mvc.perform(post("/api/plans")).andReturn().getResponse().getContentAsString());
        long planId = plan.get("id").asLong();
        mvc.perform(put("/api/plans/" + planId + "/completed/MATH 241"));

        mvc.perform(get("/api/courses").param("q", "math 24").param("planId", String.valueOf(planId)))
                .andExpect(jsonPath("$[0].availability").value("IN_PLAN"))
                .andExpect(jsonPath("$[1].availability").value("READY"))
                .andExpect(jsonPath("$[2].availability").value("LOCKED"))
                .andExpect(jsonPath("$[2].hint").value("Needs MATH 242"));
        mvc.perform(get("/api/courses").param("q", "GRMN").param("planId", String.valueOf(planId)))
                .andExpect(jsonPath("$[0].availability").value("CHECK"));
    }

    @Test
    void searchWithUnknownPlanIs404() throws Exception {
        mvc.perform(get("/api/courses").param("planId", "999999")).andExpect(status().isNotFound());
    }

    @Test
    void courseDetail() throws Exception {
        mvc.perform(get("/api/courses/{id}", "PSYC 402"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mindfulness and the Brain"))
                .andExpect(jsonPath("$.prerequisites.summary").value("PSYC 100 AND (PSYC 209 or STAT 200 or MATH 241)"))
                .andExpect(jsonPath("$.prerequisites.groups[1].anyOf", hasItems("PSYC 209", "STAT 200")))
                .andExpect(jsonPath("$.prerequisites.concurrentOk").value(false));
        mvc.perform(get("/api/courses/{id}", "math241"))
                .andExpect(jsonPath("$.id").value("MATH 241"))
                .andExpect(jsonPath("$.offered", hasItems("Fall", "Spring")))
                .andExpect(jsonPath("$.prerequisiteFor", hasItems("MATH 242", "PHYS 207")));
    }

    @Test
    void unknownCourseIs404() throws Exception {
        mvc.perform(get("/api/courses/{id}", "FAKE 101"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Course FAKE 101 not found"));
    }

    @Test
    void servesTheWebPage() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>Course Planner</title>")));
    }
}

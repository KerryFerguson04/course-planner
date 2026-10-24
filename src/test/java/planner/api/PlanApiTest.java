package planner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests: real controllers, service and database (in-memory
 * H2), using the small test catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanApiTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private long planId;
    private long fallId;

    @BeforeEach
    void createPlan() throws Exception {
        JsonNode plan = body(mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test plan\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/plans/")))
                .andExpect(jsonPath("$.name").value("Test plan"))
                .andExpect(jsonPath("$.semesters", hasSize(1)))
                .andExpect(jsonPath("$.semesters[0].name").value("Fall 2026"))
                .andReturn());
        planId = plan.get("id").asLong();
        fallId = plan.at("/semesters/0/id").asLong();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String plan(String path) {
        return "/api/plans/" + planId + path;
    }

    private long addSemester() throws Exception {
        JsonNode plan = body(mvc.perform(post(plan("/semesters"))).andExpect(status().isCreated()).andReturn());
        JsonNode semesters = plan.get("semesters");
        return semesters.get(semesters.size() - 1).get("id").asLong();
    }

    // ---- Plans ----

    @Test
    void createWithoutBodyUsesDefaultName() throws Exception {
        mvc.perform(post("/api/plans"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My plan"));
    }

    @Test
    void listGetRenameDelete() throws Exception {
        mvc.perform(get("/api/plans")).andExpect(jsonPath("$[?(@.id == " + planId + ")].name").value("Test plan"));
        mvc.perform(get(plan(""))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(planId));

        mvc.perform(patch(plan("")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Renamed \"}"))
                .andExpect(jsonPath("$.name").value("Renamed"));

        mvc.perform(delete(plan(""))).andExpect(status().isNoContent());
        mvc.perform(get(plan(""))).andExpect(status().isNotFound());
    }

    // ---- Semesters ----

    @Test
    void addSemesterSuggestsNextTerm() throws Exception {
        mvc.perform(post(plan("/semesters")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.semesters[1].name").value("Spring 2027"));
        mvc.perform(post(plan("/semesters")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Summer 2027\"}"))
                .andExpect(jsonPath("$.semesters[2].name").value("Summer 2027"));
    }

    @Test
    void addSemesterAfterUnrecognizedNameIsNumbered() throws Exception {
        mvc.perform(patch(plan("/semesters/" + fallId)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Year 1\"}"))
                .andExpect(jsonPath("$.semesters[0].name").value("Year 1"));
        mvc.perform(post(plan("/semesters"))).andExpect(jsonPath("$.semesters[1].name").value("Semester 2"));
    }

    @Test
    void deleteSemester() throws Exception {
        long spring = addSemester();
        mvc.perform(delete(plan("/semesters/" + spring)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesters", hasSize(1)));
        mvc.perform(delete(plan("/semesters/" + spring))).andExpect(status().isNotFound());
    }

    // ---- Courses and checks ----

    @Test
    void courseStatusesAreRecheckedOnEveryChange() throws Exception {
        long spring = addSemester();

        // MATH 242 without MATH 241: error
        mvc.perform(put(plan("/semesters/" + spring + "/courses/MATH 242")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesters[1].courses[0].id").value("MATH 242"))
                .andExpect(jsonPath("$.semesters[1].courses[0].status").value("ERROR"))
                .andExpect(jsonPath("$.semesters[1].courses[0].messages[0]").value("Missing prerequisite: MATH 241"));

        // Add MATH 241 the semester before: now OK, and credits add up
        mvc.perform(put(plan("/semesters/" + fallId + "/courses/math241")))
                .andExpect(jsonPath("$.semesters[0].courses[0].id").value("MATH 241"))
                .andExpect(jsonPath("$.semesters[0].credits").value("4"))
                .andExpect(jsonPath("$.semesters[1].courses[0].status").value("OK"));
    }

    @Test
    void placingACourseMovesIt() throws Exception {
        long spring = addSemester();
        mvc.perform(put(plan("/semesters/" + fallId + "/courses/ECON 101")));
        mvc.perform(put(plan("/semesters/" + spring + "/courses/ECON 101")))
                .andExpect(jsonPath("$.semesters[0].courses", hasSize(0)))
                .andExpect(jsonPath("$.semesters[1].courses[0].id").value("ECON 101"));

        // Placing it again is idempotent
        mvc.perform(put(plan("/semesters/" + spring + "/courses/ECON 101")))
                .andExpect(jsonPath("$.semesters[1].courses", hasSize(1)));
    }

    @Test
    void completedCourses() throws Exception {
        mvc.perform(put(plan("/semesters/" + fallId + "/courses/MATH 241")));
        mvc.perform(put(plan("/completed/MATH 241")))
                .andExpect(jsonPath("$.completed[0].id").value("MATH 241"))
                .andExpect(jsonPath("$.completed[0].status").value("DONE"))
                .andExpect(jsonPath("$.completedCredits").value("4"))
                .andExpect(jsonPath("$.semesters[0].courses", hasSize(0)));

        mvc.perform(delete(plan("/completed/MATH 241"))).andExpect(jsonPath("$.completed", hasSize(0)));
        mvc.perform(delete(plan("/completed/MATH 241"))).andExpect(status().isNotFound());
    }

    @Test
    void removeCourse() throws Exception {
        mvc.perform(put(plan("/semesters/" + fallId + "/courses/ECON 101")));
        mvc.perform(delete(plan("/semesters/" + fallId + "/courses/ECON 101")))
                .andExpect(jsonPath("$.semesters[0].courses", hasSize(0)));
        mvc.perform(delete(plan("/semesters/" + fallId + "/courses/ECON 101")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("ECON 101 is not in Fall 2026"));
    }

    @Test
    void updatedAtChangesOnEdit() throws Exception {
        JsonNode before = body(mvc.perform(get(plan(""))).andReturn());
        Thread.sleep(5);
        JsonNode after = body(mvc.perform(put(plan("/semesters/" + fallId + "/courses/ECON 101"))).andReturn());
        assertThat(Instant.parse(after.get("updatedAt").asText()))
                .isAfter(Instant.parse(before.get("updatedAt").asText()));
    }

    // ---- Errors ----

    @Test
    void unknownIdsAre404ProblemDetails() throws Exception {
        mvc.perform(get("/api/plans/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("Plan 999999 not found"));
        mvc.perform(put(plan("/semesters/999999/courses/MATH 241"))).andExpect(status().isNotFound());
        mvc.perform(put(plan("/semesters/" + fallId + "/courses/FAKE 101")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Course FAKE 101 not found"));
    }

    @Test
    void invalidInputIs400() throws Exception {
        mvc.perform(patch(plan("")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("name must not be blank"));
        mvc.perform(patch(plan("")).contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/plans/not-a-number")).andExpect(status().isBadRequest());
    }
}

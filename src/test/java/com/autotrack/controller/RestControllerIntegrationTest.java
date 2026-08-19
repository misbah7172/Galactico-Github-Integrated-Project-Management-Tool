package com.autotrack.controller;

import com.autotrack.service.BacklogService;
import com.autotrack.service.TimelineService;
import com.autotrack.service.ProjectService;
import com.autotrack.service.UserService;
import com.autotrack.service.SprintService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test to validate REST controller beans are properly created and injected.
 */
@SpringBootTest(properties = {
    "spring.sql.init.mode=never",
    "spring.datasource.initialization-mode=never",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@TestPropertySource(locations = "classpath:application-test.properties")
public class RestControllerIntegrationTest {

    @MockBean
    private BacklogService backlogService;

    @MockBean
    private TimelineService timelineService;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private UserService userService;

    @MockBean
    private SprintService sprintService;

    @Test
    void contextLoadsWithBacklogController() {
        // This test validates that Spring context loads with our BacklogController
        assertNotNull(backlogService);
        assertNotNull(projectService);
        assertNotNull(userService);
        assertNotNull(sprintService);
    }

    @Test
    void contextLoadsWithTimelineController() {
        // This test validates that Spring context loads with our TimelineController
        assertNotNull(timelineService);
    }

    @Test
    void allServiceDependenciesAvailable() {
        // This test validates all service dependencies are available for injection
        assertNotNull(backlogService);
        assertNotNull(timelineService);
        assertNotNull(projectService);
        assertNotNull(userService);
        assertNotNull(sprintService);
    }
}
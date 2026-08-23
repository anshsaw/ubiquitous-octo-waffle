package com.portfoliopilot.controller;

import com.portfoliopilot.controller.admin.AdminUserController;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.security.JwtService;
import com.portfoliopilot.security.UserPrincipal;
import com.portfoliopilot.service.admin.AdminUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the security boundaries the audit flagged:
 * - unauthenticated -> 401
 * - USER -> admin 403, ADMIN -> 200
 * - validation errors are 400 with field messages
 *
 * Uses @WebMvcTest so no MongoDB or real JWT is needed.
 */
@WebMvcTest(
        value = { AuthController.class, ProfileController.class, AdminUserController.class, ProjectController.class },
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class
        })
@Import({ com.portfoliopilot.config.SecurityConfig.class,
        com.portfoliopilot.security.JwtAuthenticationFilter.class,
        com.portfoliopilot.security.RestAuthenticationEntryPoint.class })
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test",
        "spring.data.mongodb.database=test"
})
class SecurityBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    // AuthController dependencies
    @MockBean
    private com.portfoliopilot.service.AuthService authService;
    @MockBean
    private JwtService jwtService;
    // ProfileController
    @MockBean
    private com.portfoliopilot.service.ProfileService profileService;
    // ProjectController
    @MockBean
    private com.portfoliopilot.service.ProjectService projectService;
    // Admin
    @MockBean
    private AdminUserService adminUserService;
    // Other beans pulled by @WebMvcTest via SecurityConfig
    @MockBean
    private MongoTemplate mongoTemplate;
    @MockBean
    private com.portfoliopilot.config.StartupValidator startupValidator;
    @MockBean
    private com.portfoliopilot.service.SkillDictionaryService skillDictionaryService;
    @MockBean
    private com.portfoliopilot.repository.UserRepository userRepository;
    @MockBean
    private com.portfoliopilot.repository.ProfileRepository profileRepository;
    @MockBean
    private com.portfoliopilot.repository.JobAnalysisRepository jobAnalysisRepository;
    @MockBean
    private com.portfoliopilot.repository.PortfolioRepository portfolioRepository;
    @MockBean
    private com.portfoliopilot.repository.ProjectRepository projectRepository;
    @MockBean
    private com.portfoliopilot.repository.ResumeRepository resumeRepository;
    @MockBean
    private com.portfoliopilot.repository.PortfolioTemplateRepository portfolioTemplateRepository;
    @MockBean
    private com.portfoliopilot.repository.SkillDictionaryRepository skillDictionaryRepository;
    @MockBean
    private com.portfoliopilot.repository.AdminLogRepository adminLogRepository;
    @MockBean
    private com.portfoliopilot.repository.RefreshTokenRepository refreshTokenRepository;
    @MockBean
    private com.portfoliopilot.service.OpportunityService opportunityService;
    @MockBean
    private com.portfoliopilot.service.OpportunitySearchService opportunitySearchService;
    @MockBean
    private com.portfoliopilot.service.ResumeService resumeService;
    @MockBean
    private com.portfoliopilot.service.PortfolioService portfolioService;
    @MockBean
    private com.portfoliopilot.service.PublicPortfolioService publicPortfolioService;
    @MockBean
    private com.portfoliopilot.service.TemplateService templateService;
    @MockBean
    private com.portfoliopilot.service.DashboardService dashboardService;

    private static UserPrincipal user(String id, Role role) {
        return new UserPrincipal(id, "test@example.com", "testuser", role);
    }

    @Test
    @DisplayName("GET /api/profile without token -> 401")
    void profileRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/admin/users as USER -> 403")
    void adminRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(user("u1", Role.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/users as ADMIN -> 200")
    void adminAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(user("a1", Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/register with invalid email -> 400 with field error")
    void registerValidation() throws Exception {
        String body = """
                {"name":"A","email":"not-an-email","password":"short"}""";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register with short password -> 400")
    void registerShortPassword() throws Exception {
        String body = """
                {"name":"Test User","email":"t@example.com","password":"abc"}""";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/public/portfolio/{username} is public -> 404 when not found, not 401")
    void publicPortfolioIsPublic() throws Exception {
        // No authentication, unknown username. Should be 404 from service, not 401 from security.
        // We mock the service to throw 404 via the controller's service call, but for a truly
        // unknown user the service would be invoked. So we just check the security rule:
        // /api/public/** is permitAll.
        // Here we call it without auth and expect NOT 401 (either 404 or 200, but not auth failure).
        // Since we didn't mock PublicPortfolioService for this path in this test slice, it will
        // actually be mocked and return null -> but security should let it through.
        // So we verify the path is not blocked:
        var result = mockMvc.perform(get("/api/public/portfolio/does-not-exist")).andReturn();
        int status = result.getResponse().getStatus();
        // Must not be 401 or 403 - those would mean security blocked a public endpoint
        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
    }
}

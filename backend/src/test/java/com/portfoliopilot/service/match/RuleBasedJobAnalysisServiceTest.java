package com.portfoliopilot.service.match;

import com.portfoliopilot.config.MatchingProperties;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.model.SkillDictionaryEntry;
import com.portfoliopilot.model.embedded.Education;
import com.portfoliopilot.model.enums.SkillCategory;
import com.portfoliopilot.repository.SkillDictionaryRepository;
import com.portfoliopilot.service.SkillDictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the match engine.
 *
 * <p>Pure unit tests: no Spring context, no database, no web layer. That is only
 * possible because {@link JobAnalysisService} takes plain records as input, and
 * it is why these run in milliseconds.
 *
 * <p>The dictionary is stubbed with a small, fixed vocabulary so the assertions
 * describe the ALGORITHM rather than whatever happens to be seeded.
 */
class RuleBasedJobAnalysisServiceTest {

    private RuleBasedJobAnalysisService engine;

    @BeforeEach
    void setUp() {
        SkillDictionaryRepository repository = Mockito.mock(SkillDictionaryRepository.class);
        Mockito.when(repository.findByActiveTrue()).thenReturn(List.of(
                entry("Java", "java", List.of("core java"), List.of("spring boot")),
                entry("Spring Boot", "spring boot", List.of("springboot"), List.of("java", "spring")),
                entry("Spring Framework", "spring", List.of("spring framework"), List.of("spring boot")),
                entry("REST API", "rest api", List.of("rest", "restful api"), List.of()),
                entry("MySQL", "mysql", List.of(), List.of()),
                entry("Docker", "docker", List.of(), List.of("kubernetes")),
                entry("Kubernetes", "kubernetes", List.of("k8s"), List.of("docker")),
                entry("AWS", "aws", List.of("amazon web services"), List.of()),
                entry("React", "react", List.of("react js", "reactjs"), List.of()),
                entry("Microservices", "microservices", List.of("microservice"), List.of())
        ));

        SkillDictionaryService dictionary = new SkillDictionaryService(repository);
        JobSkillExtractor extractor = new JobSkillExtractor(dictionary);

        MatchingProperties properties = new MatchingProperties(0.45, 0.30, 0.25, 0.5, 5);
        engine = new RuleBasedJobAnalysisService(extractor, dictionary, properties);
    }

    // ------------------------------------------------------------- fixtures

    private static SkillDictionaryEntry entry(String canonical, String normalized,
                                              List<String> aliases, List<String> related) {
        return SkillDictionaryEntry.builder()
                .canonicalName(canonical)
                .normalizedName(normalized)
                .aliases(aliases)
                .relatedSkills(related)
                .category(SkillCategory.OTHER)
                .weight(0.7)
                .active(true)
                .build();
    }

    private static final String BACKEND_JD = """
            We are hiring a Java Backend Developer.

            Requirements
            - Hands-on experience with Java and Spring Boot
            - Strong REST API design
            - Experience with MySQL

            Nice to have
            - Exposure to Docker
            - Exposure to AWS

            What we offer
            - Free snacks and a Kubernetes-powered coffee machine
            """;

    private CandidateSnapshot candidate(List<String> skills, List<Project> projects) {
        Profile profile = Profile.builder()
                .userId("u1")
                .fullName("Test Candidate")
                .professionalTitle("Backend Developer")
                .skillIndex(skills)
                .education(List.of(Education.builder().degree("BSc").institution("Uni").build()))
                .build();
        return new CandidateSnapshot(profile, projects);
    }

    private static Project project(String id, String title, List<String> normalizedTech) {
        return Project.builder()
                .id(id)
                .userId("u1")
                .title(title)
                .techStackNormalized(normalizedTech)
                .includeInPortfolio(true)
                .build();
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("Skill extraction")
    class Extraction {

        @Test
        @DisplayName("separates required skills from nice-to-haves by section heading")
        void separatesRequiredFromNiceToHave() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java", "spring boot", "rest api", "mysql"), List.of()));

            assertThat(result.strongSkillsNormalized())
                    .containsExactlyInAnyOrder("java", "spring boot", "rest api", "mysql");
            assertThat(result.skillGapsNormalized())
                    .containsExactlyInAnyOrder("docker", "aws");
        }

        @Test
        @DisplayName("ignores the benefits section, so a perk is never mistaken for a requirement")
        void ignoresBenefitsSection() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java"), List.of()));

            // "Kubernetes" appears only under "What we offer".
            assertThat(result.skillGapsNormalized()).doesNotContain("kubernetes");
            assertThat(result.strongSkillsNormalized()).doesNotContain("kubernetes");
        }

        @Test
        @DisplayName("a sub-phrase skill is not double-counted (Spring inside Spring Boot)")
        void doesNotDoubleCountSubPhrases() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java", "spring boot", "rest api", "mysql"), List.of()));

            // The JD never asks for Spring Framework on its own, so reporting it
            // as a gap would be a false negative against the candidate.
            assertThat(result.skillGapsNormalized()).doesNotContain("spring");
        }

        @Test
        @DisplayName("resolves aliases, so ReactJS on the profile satisfies React in the job")
        void resolvesAliases() {
            String jd = """
                    Frontend role.
                    Requirements
                    - Experience with React
                    """;
            MatchResult result = engine.generateMatch(
                    new JobPosting("Frontend Developer", null, jd),
                    candidate(List.of("react"), List.of()));

            assertThat(result.strongSkillsNormalized()).contains("react");
            assertThat(result.skillGapsNormalized()).doesNotContain("react");
        }
    }

    @Nested
    @DisplayName("Scoring")
    class Scoring {

        @Test
        @DisplayName("is deterministic - the same input always produces the same score")
        void isDeterministic() {
            CandidateSnapshot snapshot = candidate(List.of("java", "spring boot"), List.of());
            JobPosting posting = new JobPosting("Java Backend Developer", "Acme", BACKEND_JD);

            MatchResult first = engine.generateMatch(posting, snapshot);
            for (int i = 0; i < 20; i++) {
                assertThat(engine.generateMatch(posting, snapshot).matchScore())
                        .isEqualTo(first.matchScore());
            }
        }

        @Test
        @DisplayName("applies no artificial floor - a poor candidate scores genuinely low")
        void hasNoArtificialFloor() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of(), List.of()));

            // The original client-side mock clamped everything to >= 45%, which
            // made the number meaningless. A candidate with nothing must score low.
            assertThat(result.matchScore()).isLessThan(45);
            assertThat(result.skillsMatch()).isZero();
            assertThat(result.projectsMatch()).isZero();
        }

        @Test
        @DisplayName("all four scores stay within the 0-100 range the DB validator enforces")
        void scoresStayInRange() {
            MatchResult perfect = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java", "spring boot", "rest api", "mysql", "docker", "aws"),
                            List.of(project("p1", "API", List.of("java", "spring boot", "rest api", "mysql")))));

            assertThat(perfect.matchScore()).isBetween(0, 100);
            assertThat(perfect.skillsMatch()).isBetween(0, 100);
            assertThat(perfect.projectsMatch()).isBetween(0, 100);
            assertThat(perfect.requirementsMatch()).isBetween(0, 100);
        }

        @Test
        @DisplayName("a fully-qualified candidate scores higher than a partially-qualified one")
        void betterCandidateScoresHigher() {
            JobPosting posting = new JobPosting("Java Backend Developer", "Acme", BACKEND_JD);

            MatchResult weak = engine.generateMatch(posting, candidate(List.of("java"), List.of()));
            MatchResult strong = engine.generateMatch(posting,
                    candidate(List.of("java", "spring boot", "rest api", "mysql", "docker", "aws"),
                            List.of(project("p1", "API", List.of("java", "spring boot", "rest api", "mysql")))));

            assertThat(strong.matchScore()).isGreaterThan(weak.matchScore());
        }

        @Test
        @DisplayName("the overall score is the configured weighted sum of the three sub-scores")
        void overallIsWeightedSum() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java", "spring boot"),
                            List.of(project("p1", "API", List.of("java", "spring boot")))));

            long expected = Math.round(
                    result.skillsMatch() * 0.45
                            + result.projectsMatch() * 0.30
                            + result.requirementsMatch() * 0.25);

            assertThat(result.matchScore()).isEqualTo((int) expected);
        }

        @Test
        @DisplayName("nice-to-have skills weigh less than hard requirements")
        void niceToHaveWeighsLess() {
            JobPosting posting = new JobPosting("Java Backend Developer", "Acme", BACKEND_JD);

            // Has both nice-to-haves, none of the four requirements.
            MatchResult onlyBonus = engine.generateMatch(posting, candidate(List.of("docker", "aws"), List.of()));
            // Has all four requirements, neither bonus.
            MatchResult onlyRequired = engine.generateMatch(posting,
                    candidate(List.of("java", "spring boot", "rest api", "mysql"), List.of()));

            assertThat(onlyRequired.skillsMatch()).isGreaterThan(onlyBonus.skillsMatch());
        }
    }

    @Nested
    @DisplayName("Project recommendation")
    class Recommendations {

        @Test
        @DisplayName("ranks the more relevant project first")
        void ranksByRelevance() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java", "spring boot", "react"), List.of(
                            project("p-frontend", "React Dashboard", List.of("react")),
                            project("p-backend", "Spring API", List.of("java", "spring boot", "rest api", "mysql")))));

            assertThat(result.recommendedProjects()).isNotEmpty();
            assertThat(result.recommendedProjects().get(0).projectId()).isEqualTo("p-backend");
        }

        @Test
        @DisplayName("excludes projects with no technology overlap")
        void excludesIrrelevantProjects() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("react"), List.of(project("p-frontend", "React Dashboard", List.of("react")))));

            assertThat(result.recommendedProjects()).isEmpty();
        }

        @Test
        @DisplayName("recommendations reference a project id and never embed the document")
        void returnsReferencesWithReasons() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java", "spring boot"),
                            List.of(project("p1", "Spring API", List.of("java", "spring boot")))));

            MatchResult.ProjectMatch match = result.recommendedProjects().get(0);
            assertThat(match.projectId()).isEqualTo("p1");
            assertThat(match.relevanceScore()).isBetween(1, 100);
            assertThat(match.reason()).contains("Java");
            assertThat(match.matchedSkills()).contains("Java", "Spring Boot");
        }

        @Test
        @DisplayName("project technologies count as evidence even when not declared as skills")
        void projectTechCountsAsEvidence() {
            // The candidate never listed MySQL, but shipped a project using it.
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java"), List.of(project("p1", "API", List.of("mysql")))));

            assertThat(result.strongSkillsNormalized()).contains("mysql");
            assertThat(result.skillGapsNormalized()).doesNotContain("mysql");
        }
    }

    @Nested
    @DisplayName("Requirements and summary")
    class RequirementsAndSummary {

        @Test
        @DisplayName("every requirement is explainable - text plus a met flag")
        void requirementsAreExplainable() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java"), List.of()));

            assertThat(result.requirements()).isNotEmpty();
            assertThat(result.requirements())
                    .allSatisfy(r -> assertThat(r.text()).isNotBlank());
            assertThat(result.requirements())
                    .anySatisfy(r -> assertThat(r.text()).contains("Java"));
        }

        @Test
        @DisplayName("the summary names the role, the company and only real skills")
        void summaryUsesOnlyRealData() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Nexora Labs", BACKEND_JD),
                    candidate(List.of("java", "spring boot"),
                            List.of(project("p1", "Spring API", List.of("java", "spring boot")))));

            assertThat(result.tailoredSummary())
                    .contains("Java Backend Developer")
                    .contains("Nexora Labs")
                    .contains("Spring API");
        }

        @Test
        @DisplayName("the engine name is recorded so scores stay comparable across versions")
        void recordsEngineName() {
            MatchResult result = engine.generateMatch(
                    new JobPosting("Java Backend Developer", "Acme", BACKEND_JD),
                    candidate(List.of("java"), List.of()));

            assertThat(result.engine()).isEqualTo("rule-based-v1");
            assertThat(engine.engineName()).isEqualTo("rule-based-v1");
        }
    }
}

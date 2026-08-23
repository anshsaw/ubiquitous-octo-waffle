package com.portfoliopilot.service.admin;

import com.portfoliopilot.dto.admin.AdminDashboardResponse;
import com.portfoliopilot.dto.admin.ChartPoint;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.repository.AdminAnalyticsRepository;
import com.portfoliopilot.repository.JobAnalysisRepository;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.repository.ProjectRepository;
import com.portfoliopilot.repository.ResumeRepository;
import com.portfoliopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Assembles the admin dashboard.
 *
 * <p>Counts use indexed {@code countDocuments}; everything shaped (averages,
 * distributions, top-N) goes through {@link AdminAnalyticsRepository}'s
 * aggregation pipelines. No endpoint here pulls documents into Java to reduce
 * them.
 *
 * <p>These endpoints are admin-only and on-demand. None of them sits on a user
 * request path, and the results are safe to cache for a few minutes.
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    /** Rolling window for averages and top-N charts. */
    private static final int DEFAULT_WINDOW_DAYS = 90;

    private final AdminAnalyticsRepository analyticsRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final JobAnalysisRepository jobAnalysisRepository;
    private final ResumeRepository resumeRepository;
    private final PortfolioRepository portfolioRepository;

    public AdminDashboardResponse stats() {
        AdminAnalyticsRepository.AverageScores averages =
                analyticsRepository.averageScores(30);

        return new AdminDashboardResponse(
                userRepository.countByRoleAndDeletedFalse(Role.USER),
                portfolioRepository.countByPublishedTrueAndDeletedFalse(),
                analyticsRepository.jobsAnalyzedToday(),
                averages.overall(),
                projectRepository.countByDeletedFalse(),
                jobAnalysisRepository.countByDeletedFalse(),
                resumeRepository.countByDeletedFalse(),
                new AdminDashboardResponse.SubScores(
                        averages.skills(), averages.projects(), averages.requirements()),
                analyticsRepository.matchScoreDistribution(),
                analyticsRepository.usersByStatus());
    }

    /** Line-chart series. Gap-filled, so days with no activity still render a zero. */
    public Map<String, List<ChartPoint.DailyPoint>> signupTrends(int days) {
        int window = clampDays(days);
        return Map.of(
                "signups", analyticsRepository.dailySignups(window),
                "analyses", analyticsRepository.dailyAnalyses(window),
                "published", analyticsRepository.dailyPublishedPortfolios(window));
    }

    /** Skill intelligence: the gap chart plus its two supporting tables. */
    public Map<String, Object> skillInsights(int limit) {
        int capped = Math.min(Math.max(1, limit), 50);
        return Map.of(
                "topSkillGaps", analyticsRepository.topSkillGaps(capped, DEFAULT_WINDOW_DAYS),
                "mostRequestedSkills", analyticsRepository.mostRequestedSkills(capped, DEFAULT_WINDOW_DAYS),
                "mostAnalyzedRoles", analyticsRepository.mostAnalyzedRoles(capped, DEFAULT_WINDOW_DAYS));
    }

    /** A chart cannot usefully render 10 000 points, and the query should not try. */
    private int clampDays(int days) {
        return Math.min(Math.max(7, days), 365);
    }
}

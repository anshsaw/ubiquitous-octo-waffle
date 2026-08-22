package com.portfoliopilot.service;

import com.portfoliopilot.dto.dashboard.DashboardResponse;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.User;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Assembles the user dashboard from cheap counts and one cached field - no aggregation. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final ProfileService profileService;
    private final ProjectService projectService;
    private final OpportunityService opportunityService;
    private final PortfolioService portfolioService;

    public DashboardResponse forUser(String userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));

        Profile profile = profileService.requireProfile(userId);

        String publicUrl = portfolioService.published(userId)
                .map(portfolio -> "/portfolio/" + portfolio.username())
                .orElse(null);

        return new DashboardResponse(
                user.getName(),
                user.getUsername(),
                profile.getProfileHealth() == null ? 0 : profile.getProfileHealth(),
                projectService.countOwned(userId),
                profile.getSkills() == null ? 0 : profile.getSkills().size(),
                opportunityService.countForUser(userId),
                portfolioRepository.countByPublishedTrueAndDeletedFalse() > 0 && publicUrl != null ? 1 : 0,
                publicUrl,
                opportunityService.recent(userId));
    }
}

package com.portfoliopilot.service.admin;

import com.portfoliopilot.dto.admin.AdminJobAnalysisResponse;
import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.JobAnalysis;
import com.portfoliopilot.model.User;
import com.portfoliopilot.repository.JobAnalysisRepository;
import com.portfoliopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The admin Job Analyses Log: search, filter, paginate.
 *
 * <p>Owner names are resolved with ONE batched query per page rather than a
 * lookup per row. With 25 rows the naive version would issue 25 extra queries -
 * the classic N+1 that only becomes visible under load.
 */
@Service
@RequiredArgsConstructor
public class AdminJobAnalysisService {

    private final JobAnalysisRepository jobAnalysisRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * @param search    matches job title or company (quoted regex, case-insensitive)
     * @param minScore  inclusive lower bound on matchScore
     * @param maxScore  inclusive upper bound
     * @param from      inclusive lower bound on createdAt
     * @param to        exclusive upper bound
     */
    public PageResponse<AdminJobAnalysisResponse> search(String search,
                                                         Integer minScore,
                                                         Integer maxScore,
                                                         Instant from,
                                                         Instant to,
                                                         Pageable pageable) {

        Criteria criteria = Criteria.where("deleted").is(false);

        if (search != null && !search.isBlank()) {
            // Pattern.quote prevents a crafted term from becoming a ReDoS or a
            // catastrophic full scan.
            String safe = Pattern.quote(search.trim());
            criteria = criteria.orOperator(
                    Criteria.where("job.title").regex(safe, "i"),
                    Criteria.where("job.company").regex(safe, "i"));
        }
        if (minScore != null || maxScore != null) {
            Criteria score = Criteria.where("analysis.matchScore");
            if (minScore != null) {
                score = score.gte(minScore);
            }
            if (maxScore != null) {
                score = score.lte(maxScore);
            }
            criteria = new Criteria().andOperator(criteria, score);
        }
        if (from != null || to != null) {
            Criteria created = Criteria.where("createdAt");
            if (from != null) {
                created = created.gte(from);
            }
            if (to != null) {
                created = created.lt(to);
            }
            criteria = new Criteria().andOperator(criteria, created);
        }

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, JobAnalysis.class);
        List<JobAnalysis> rows = mongoTemplate.find(query.with(pageable), JobAnalysis.class);

        Map<String, String> namesByUserId = resolveOwnerNames(rows);
        Page<JobAnalysis> page = new PageImpl<>(rows, pageable, total);

        return PageResponse.from(page,
                analysis -> AdminJobAnalysisResponse.from(
                        analysis, namesByUserId.get(analysis.getUserId())));
    }

    public AdminJobAnalysisResponse get(String analysisId) {
        JobAnalysis analysis = jobAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job analysis"));

        String ownerName = userRepository.findById(analysis.getUserId())
                .map(User::getName)
                .orElse(null);

        return AdminJobAnalysisResponse.from(analysis, ownerName);
    }

    /** One {@code $in} query for every owner on the page. */
    private Map<String, String> resolveOwnerNames(List<JobAnalysis> rows) {
        Set<String> userIds = rows.stream()
                .map(JobAnalysis::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> names = new HashMap<>();
        userRepository.findAllById(userIds)
                .forEach(user -> names.put(user.getId(), user.getName()));
        return names;
    }

    /** Identity helper kept for readability at the call site. */
    static <T> Function<T, T> identity() {
        return Function.identity();
    }
}

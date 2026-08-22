package com.portfoliopilot.repository;

import com.portfoliopilot.dto.admin.AdminDashboardResponse;
import com.portfoliopilot.dto.admin.ChartPoint;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin analytics, computed with MongoDB aggregation pipelines.
 *
 * <p>These are direct ports of {@code mongodb/aggregations/*.js}, so the numbers
 * the admin panel shows are identical to what {@code npm run stats} prints.
 *
 * <p><strong>Why aggregations and not Java loops.</strong> Every one of these
 * questions ("top skill gaps across all users", "average match score") is a
 * whole-collection reduction. Fetching those documents into the JVM to count
 * them would move megabytes over the wire to produce a handful of integers, and
 * would get slower with every new user. The database does the reduction and
 * returns only the result.
 *
 * <p>Every pipeline begins with a {@code $match} on an indexed field, and every
 * date boundary is computed HERE and passed in as a literal - a boundary
 * computed inside the pipeline is an expression the planner cannot turn into an
 * index bound, which silently degrades it to a collection scan.
 */
@Repository
@RequiredArgsConstructor
public class AdminAnalyticsRepository {

    private final MongoTemplate mongoTemplate;

    // ------------------------------------------------------------- helpers

    private static Instant startOfToday() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant startOfTomorrow() {
        return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant daysAgo(int days) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // -------------------------------------------------------- stat cards

    /** Rolling-window average of the overall match score, plus the three sub-scores. */
    public AverageScores averageScores(int windowDays) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria
                        .where("deleted").is(false)
                        .and("createdAt").gte(daysAgo(windowDays))),
                Aggregation.group()
                        .avg("analysis.matchScore").as("overall")
                        .avg("analysis.skillsMatch").as("skills")
                        .avg("analysis.projectsMatch").as("projects")
                        .avg("analysis.requirementsMatch").as("requirements")
                        .count().as("samples"));

        Document result = mongoTemplate
                .aggregate(aggregation, "jobAnalyses", Document.class)
                .getUniqueMappedResult();

        if (result == null) {
            return new AverageScores(0, 0, 0, 0, 0);
        }
        return new AverageScores(
                round(result.get("overall")),
                round(result.get("skills")),
                round(result.get("projects")),
                round(result.get("requirements")),
                ((Number) result.getOrDefault("samples", 0)).longValue());
    }

    public record AverageScores(int overall, int skills, int projects, int requirements, long samples) {
    }

    /** Distribution of match scores into readable bands. */
    public List<AdminDashboardResponse.Bucket> matchScoreDistribution() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria
                        .where("deleted").is(false)),
                Aggregation.bucket("analysis.matchScore")
                        .withBoundaries(0, 40, 60, 75, 90, 101)
                        .withDefaultBucket("unknown")
                        .andOutputCount().as("count"));

        List<Document> rows = mongoTemplate
                .aggregate(aggregation, "jobAnalyses", Document.class)
                .getMappedResults();

        Map<Object, String> labels = new LinkedHashMap<>();
        labels.put(0, "0-39 poor");
        labels.put(40, "40-59 weak");
        labels.put(60, "60-74 fair");
        labels.put(75, "75-89 strong");
        labels.put(90, "90-100 excellent");

        List<AdminDashboardResponse.Bucket> buckets = new ArrayList<>();
        for (Document row : rows) {
            Object id = row.get("_id");
            String label = labels.getOrDefault(id, String.valueOf(id));
            buckets.add(new AdminDashboardResponse.Bucket(
                    label, ((Number) row.getOrDefault("count", 0)).longValue()));
        }
        return buckets;
    }

    public List<AdminDashboardResponse.StatusCount> usersByStatus() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria
                        .where("role").is("USER").and("deleted").is(false)),
                Aggregation.group("status").count().as("count"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id"));

        return mongoTemplate.aggregate(aggregation, "users", Document.class)
                .getMappedResults().stream()
                .map(d -> new AdminDashboardResponse.StatusCount(
                        String.valueOf(d.get("_id")),
                        ((Number) d.getOrDefault("count", 0)).longValue()))
                .toList();
    }

    // ----------------------------------------------------------- trends

    /**
     * Daily signups over a rolling window.
     *
     * <p>MongoDB returns no row for a day with zero events, but a line chart
     * needs the zero - so the result is gap-filled here.
     */
    public List<ChartPoint.DailyPoint> dailySignups(int days) {
        return dailyCounts("users", "createdAt", days, null);
    }

    /** Daily analysis volume. */
    public List<ChartPoint.DailyPoint> dailyAnalyses(int days) {
        return dailyCounts("jobAnalyses", "createdAt", days, null);
    }

    /** Daily publish events - adoption of the final funnel step. */
    public List<ChartPoint.DailyPoint> dailyPublishedPortfolios(int days) {
        return dailyCounts("portfolios", "publishedAt", days,
                org.springframework.data.mongodb.core.query.Criteria.where("isPublished").is(true));
    }

    private List<ChartPoint.DailyPoint> dailyCounts(String collection,
                                                    String dateField,
                                                    int days,
                                                    org.springframework.data.mongodb.core.query.Criteria extra) {
        var criteria = org.springframework.data.mongodb.core.query.Criteria
                .where("deleted").is(false)
                .and(dateField).gte(daysAgo(days));

        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(criteria));
        if (extra != null) {
            stages.add(Aggregation.match(extra));
        }
        // $dateToString rather than $dateTrunc: wider server compatibility, and a
        // string key maps straight onto a chart axis.
        stages.add(context -> new Document("$group", new Document("_id",
                new Document("$dateToString", new Document("format", "%Y-%m-%d").append("date", "$" + dateField)))
                .append("count", new Document("$sum", 1))));
        stages.add(Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id"));

        AggregationResults<Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(stages), collection, Document.class);

        Map<String, Long> byDate = new LinkedHashMap<>();
        for (Document row : results.getMappedResults()) {
            byDate.put(String.valueOf(row.get("_id")), ((Number) row.getOrDefault("count", 0)).longValue());
        }

        List<ChartPoint.DailyPoint> points = new ArrayList<>();
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            String key = date.toString();
            points.add(new ChartPoint.DailyPoint(
                    key,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    byDate.getOrDefault(key, 0L)));
        }
        return points;
    }

    // ------------------------------------------------------- skill gaps

    /**
     * Top skill gaps across all users.
     *
     * <pre>
     *   $match window -> $project (shrink before $unwind) -> $unwind gaps
     *                 -> $group by skill -> $sort desc -> $limit
     * </pre>
     *
     * <p>Groups on {@code skillGapsNormalized}, never the display array -
     * otherwise "React", "React.js" and "ReactJS" become three separate bars.
     * Ranks by DISTINCT USERS affected rather than raw occurrences, so one power
     * user analysing fifty Docker jobs cannot outweigh twenty people who each
     * lack Docker once.
     */
    public List<ChartPoint.SkillGapPoint> topSkillGaps(int limit, int windowDays) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria
                        .where("deleted").is(false)
                        .and("createdAt").gte(daysAgo(windowDays))),
                Aggregation.project("userId").and("analysis.skillGapsNormalized").as("gaps"),
                Aggregation.unwind("gaps"),
                Aggregation.group("gaps")
                        .count().as("occurrences")
                        .addToSet("userId").as("users"),
                Aggregation.project("occurrences")
                        .and("_id").as("skill")
                        .and("users").size().as("usersAffected"),
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("usersAffected"),
                        org.springframework.data.domain.Sort.Order.desc("occurrences"))),
                Aggregation.limit(limit));

        return mongoTemplate.aggregate(aggregation, "jobAnalyses", Document.class)
                .getMappedResults().stream()
                .map(d -> new ChartPoint.SkillGapPoint(
                        String.valueOf(d.get("skill")),
                        ((Number) d.getOrDefault("occurrences", 0)).longValue(),
                        ((Number) d.getOrDefault("usersAffected", 0)).longValue()))
                .toList();
    }

    /**
     * Most requested skills: the union of strong skills and gaps, i.e. everything
     * the market actually asked for, with the share nobody could meet.
     */
    public List<ChartPoint.SkillDemandPoint> mostRequestedSkills(int limit, int windowDays) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = List.of(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria
                        .where("deleted").is(false)
                        .and("createdAt").gte(daysAgo(windowDays))),
                context -> new Document("$project", new Document()
                        .append("requested", new Document("$setUnion", List.of(
                                new Document("$ifNull", List.of("$analysis.strongSkillsNormalized", List.of())),
                                new Document("$ifNull", List.of("$analysis.skillGapsNormalized", List.of())))))
                        .append("gaps", new Document("$ifNull",
                                List.of("$analysis.skillGapsNormalized", List.of())))),
                Aggregation.unwind("requested"),
                context -> new Document("$group", new Document("_id", "$requested")
                        .append("demandCount", new Document("$sum", 1))
                        .append("gapCount", new Document("$sum",
     new Document("$cond", List.of(new Document("$in", List.of("$requested", "$gaps")), 1, 0))))),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "demandCount"),
                Aggregation.limit(limit));

        return mongoTemplate.aggregate(Aggregation.newAggregation(stages), "jobAnalyses", Document.class)
                .getMappedResults().stream()
                .map(d -> {
                    long demand = ((Number) d.getOrDefault("demandCount", 0)).longValue();
                    long gaps = ((Number) d.getOrDefault("gapCount", 0)).longValue();
                    int ratio = demand == 0 ? 0 : (int) Math.round(gaps * 100.0 / demand);
                    return new ChartPoint.SkillDemandPoint(String.valueOf(d.get("_id")), demand, gaps, ratio);
                })
                .toList();
    }

    /** Most analysed job roles. Groups on the pre-normalised title, so variants collapse. */
    public List<ChartPoint.RolePoint> mostAnalyzedRoles(int limit, int windowDays) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria
                        .where("deleted").is(false)
                        .and("createdAt").gte(daysAgo(windowDays))),
                Aggregation.group("job.normalizedTitle")
                        .count().as("count")
                        .avg("analysis.matchScore").as("avgMatchScore")
                        .first("job.title").as("displayTitle"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "count"),
                Aggregation.limit(limit));

        return mongoTemplate.aggregate(aggregation, "jobAnalyses", Document.class)
                .getMappedResults().stream()
                .map(d -> new ChartPoint.RolePoint(
                        String.valueOf(d.getOrDefault("displayTitle", d.get("_id"))),
                        ((Number) d.getOrDefault("count", 0)).longValue(),
                        round(d.get("avgMatchScore"))))
                .toList();
    }

    // -------------------------------------------------------- simple counts

    public long jobsAnalyzedToday() {
        return mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria
                                .where("deleted").is(false)
                                .and("createdAt").gte(startOfToday()).lt(startOfTomorrow())),
                "jobAnalyses");
    }

    private static int round(Object value) {
        if (!(value instanceof Number number)) {
            return 0;
        }
        return (int) Math.round(number.doubleValue());
    }

    /** Weekday label helper, kept for callers that build their own series. */
    public static String weekdayLabel(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}

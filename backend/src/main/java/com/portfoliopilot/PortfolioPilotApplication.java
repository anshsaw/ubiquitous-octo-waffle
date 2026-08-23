package com.portfoliopilot;

import com.portfoliopilot.config.CorsProperties;
import com.portfoliopilot.config.JobsProperties;
import com.portfoliopilot.config.JwtProperties;
import com.portfoliopilot.config.MatchingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * PortfolioPilot AI - REST API.
 *
 * <p>Backend for an existing static frontend and an existing MongoDB database.
 * Neither is redesigned here: this service adapts to both.
 *
 * <p>Business concept this API is built around:
 * <pre>
 *   ONE PROFILE
 *        -&gt; MANY JOB OPPORTUNITIES
 *        -&gt; JOB-SPECIFIC MATCH ANALYSIS
 *        -&gt; JOB-SPECIFIC PROJECT RECOMMENDATION
 *        -&gt; TAILORED RESUME
 *        -&gt; ADAPTIVE PORTFOLIO
 *        -&gt; PUBLISHED PORTFOLIO
 * </pre>
 *
 * <p>Every endpoint lives under {@code /api}. The frontend owns every other
 * path, so SPA / multi-page navigation is never intercepted.
 */
@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.portfoliopilot.repository")
@EnableConfigurationProperties({
        JwtProperties.class, CorsProperties.class, MatchingProperties.class, JobsProperties.class})
public class PortfolioPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioPilotApplication.class, args);
    }
}

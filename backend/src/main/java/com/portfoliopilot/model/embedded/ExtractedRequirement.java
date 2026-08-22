package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One requirement line parsed out of a job description, marked met or unmet.
 * Backs the "Requirements Match" sub-score and makes that score explainable:
 * the UI can show exactly which requirements drove it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedRequirement {

    private String text;

    private boolean met;

    /** Relative importance, 0..1. A hard requirement outweighs a nice-to-have. */
    private Double weight;
}

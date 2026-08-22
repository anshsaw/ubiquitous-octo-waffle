package com.portfoliopilot.dto.opportunity;

import com.portfoliopilot.model.embedded.ExtractedRequirement;

/**
 * One requirement and whether the candidate meets it.
 *
 * <p>This is what makes the Requirements sub-score explainable instead of a bare
 * number: the UI can list precisely which lines were satisfied.
 */
public record RequirementDto(
        String text,
        boolean met,
        Double weight
) {

    public static RequirementDto from(ExtractedRequirement source) {
        return new RequirementDto(source.getText(), source.isMet(), source.getWeight());
    }
}

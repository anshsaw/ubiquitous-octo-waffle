package com.portfoliopilot.service.match;

/**
 * The job side of a match, decoupled from any HTTP DTO or Mongo document.
 *
 * <p>Keeping the engine's inputs as plain records means the scoring logic can be
 * unit-tested with no Spring context, no database and no web layer.
 */
public record JobPosting(
        String title,
        String company,
        String description
) {

    public String safeTitle() {
        return title == null || title.isBlank() ? "this role" : title.trim();
    }
}

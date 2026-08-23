package com.portfoliopilot.model.enums;

/**
 * Skill proficiency. Mirrors {@code profiles.skills[].proficiency}.
 *
 * <p>The existing frontend stores skill strength as an integer 1-100
 * ({@code level}), while the database stores a four-value enum. The mapping
 * lives here, in one place, so the translation is never ad-hoc.
 */
public enum Proficiency {
    BEGINNER(1),
    INTERMEDIATE(2),
    ADVANCED(3),
    EXPERT(4);

    private final int rank;

    Proficiency(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /**
     * Frontend 1-100 slider -> enum.
     * <pre>
     *   1-40  BEGINNER
     *  41-65  INTERMEDIATE
     *  66-85  ADVANCED
     *  86-100 EXPERT
     * </pre>
     */
    public static Proficiency fromLevel(Integer level) {
        if (level == null) return INTERMEDIATE;
        if (level <= 40) return BEGINNER;
        if (level <= 65) return INTERMEDIATE;
        if (level <= 85) return ADVANCED;
        return EXPERT;
    }

    /**
     * Enum -> representative 1-100 value, so the frontend slider renders at a
     * sensible position after a round trip. Deliberately the midpoint of each
     * band, not the boundary.
     */
    public int toLevel() {
        return switch (this) {
            case BEGINNER -> 30;
            case INTERMEDIATE -> 55;
            case ADVANCED -> 78;
            case EXPERT -> 93;
        };
    }
}

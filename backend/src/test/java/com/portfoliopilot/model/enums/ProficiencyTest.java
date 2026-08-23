package com.portfoliopilot.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend stores skill strength as a 1-100 slider; the database stores a
 * four-value enum. This translation sits on every profile read and write, so it
 * is worth pinning.
 */
class ProficiencyTest {

    @ParameterizedTest(name = "level {0} -> {1}")
    @CsvSource({
            "1,BEGINNER", "40,BEGINNER",
            "41,INTERMEDIATE", "65,INTERMEDIATE",
            "66,ADVANCED", "85,ADVANCED",
            "86,EXPERT", "100,EXPERT"
    })
    @DisplayName("slider values map to the correct band, including at the boundaries")
    void mapsLevelToProficiency(int level, Proficiency expected) {
        assertThat(Proficiency.fromLevel(level)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a missing level defaults to INTERMEDIATE rather than failing")
    void defaultsWhenLevelAbsent() {
        assertThat(Proficiency.fromLevel(null)).isEqualTo(Proficiency.INTERMEDIATE);
    }

    @Test
    @DisplayName("round-tripping enum -> level -> enum is stable")
    void roundTripIsStable() {
        for (Proficiency proficiency : Proficiency.values()) {
            assertThat(Proficiency.fromLevel(proficiency.toLevel()))
                    .as("round trip for %s", proficiency)
                    .isEqualTo(proficiency);
        }
    }

    @Test
    @DisplayName("rank ordering is strictly increasing, which the resume sort relies on")
    void rankIsOrdered() {
        assertThat(Proficiency.BEGINNER.rank())
                .isLessThan(Proficiency.INTERMEDIATE.rank());
        assertThat(Proficiency.INTERMEDIATE.rank())
                .isLessThan(Proficiency.ADVANCED.rank());
        assertThat(Proficiency.ADVANCED.rank())
                .isLessThan(Proficiency.EXPERT.rank());
    }
}

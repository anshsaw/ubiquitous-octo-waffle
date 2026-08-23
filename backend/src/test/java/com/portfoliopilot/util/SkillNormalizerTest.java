package com.portfoliopilot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract between this backend and {@code mongodb/lib/normalize.js}.
 *
 * <p>If these expectations ever change, the JavaScript implementation must change
 * identically - otherwise seeded data and runtime data stop joining, and every
 * skill-gap aggregation silently produces wrong numbers.
 */
class SkillNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "React,react",
            "'  React  ',react",
            "React.js,react js",
            "ReactJS,reactjs",
            "Spring-Boot,spring boot",
            "Spring Boot,spring boot",
            "'Mongo DB',mongo db",
            "Node.JS,node js",
            "CI/CD,ci cd",
            "'C++',c++",
            "'C#',c#",
            "Objective-C,objective c",
            "'  MULTIPLE   SPACES  ',multiple spaces",
            "Café,cafe"
    })
    @DisplayName("normalizeSkill matches the JavaScript implementation")
    void normalizesSkills(String input, String expected) {
        assertThat(SkillNormalizer.normalizeSkill(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("'+' and '#' survive so c++ and c# stay distinct from c")
    void preservesLanguageSymbols() {
        assertThat(SkillNormalizer.normalizeSkill("C++")).isNotEqualTo(SkillNormalizer.normalizeSkill("C"));
        assertThat(SkillNormalizer.normalizeSkill("C#")).isNotEqualTo(SkillNormalizer.normalizeSkill("C"));
    }

    @Test
    @DisplayName("null and blank normalise to empty rather than throwing")
    void handlesNullAndBlank() {
        assertThat(SkillNormalizer.normalizeSkill(null)).isEmpty();
        assertThat(SkillNormalizer.normalizeSkill("   ")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "Demo Student,demo-student",
            "'  Aarav   Sharma  ',aarav-sharma",
            "UPPER CASE,upper-case",
            "weird__name!!,weird-name",
            "'--leading-and-trailing--',leading-and-trailing"
    })
    @DisplayName("normalizeUsername produces a URL-safe handle")
    void normalizesUsernames(String input, String expected) {
        assertThat(SkillNormalizer.normalizeUsername(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("generated usernames satisfy the collection validator pattern")
    void generatedUsernamesAreValid() {
        assertThat(SkillNormalizer.isValidUsername(SkillNormalizer.normalizeUsername("Demo Student"))).isTrue();
        assertThat(SkillNormalizer.isValidUsername("ab")).isFalse();          // too short
        assertThat(SkillNormalizer.isValidUsername("-leading")).isFalse();    // cannot start with a hyphen
        assertThat(SkillNormalizer.isValidUsername("Has Capitals")).isFalse();
    }

    @Test
    @DisplayName("usernames are capped at 30 characters with no trailing hyphen")
    void capsUsernameLength() {
        String result = SkillNormalizer.normalizeUsername("a".repeat(50));
        assertThat(result).hasSize(30);
        assertThat(SkillNormalizer.isValidUsername(result)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "'Senior Java Developer (Remote)','senior java developer'",
            "'Full Stack Engineer (MERN)','full stack engineer'",
            "'Java Backend Developer','java backend developer'"
    })
    @DisplayName("job titles collapse to a groupable key for the roles chart")
    void normalizesJobTitles(String input, String expected) {
        assertThat(SkillNormalizer.normalizeJobTitle(input)).isEqualTo(expected);
    }
}

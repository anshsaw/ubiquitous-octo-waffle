package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.embedded.ContactInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Contact block, used both as request and response.
 *
 * <p>{@code phone} is returned to the owner but is stripped from the public
 * portfolio projection - publishing a portfolio must not publish a phone number
 * the user never chose to share.
 */
public record ContactDto(
        @Size(max = 32) String phone,
        @Email(message = "Public email must be a valid address") @Size(max = 254) String publicEmail,
        @Size(max = 2048) String website,
        @Size(max = 2048) @Schema(example = "https://github.com/demo-student") String github,
        @Size(max = 2048) String linkedin,
        @Size(max = 2048) String twitter
) {

    public static ContactDto from(ContactInfo contact) {
        if (contact == null) {
            return new ContactDto(null, null, null, null, null, null);
        }
        return new ContactDto(
                contact.getPhone(),
                contact.getPublicEmail(),
                contact.getWebsite(),
                contact.getGithub(),
                contact.getLinkedin(),
                contact.getTwitter());
    }

    public ContactInfo toEntity() {
        return ContactInfo.builder()
                .phone(blankToNull(phone))
                .publicEmail(blankToNull(publicEmail))
                .website(blankToNull(website))
                .github(blankToNull(github))
                .linkedin(blankToNull(linkedin))
                .twitter(blankToNull(twitter))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

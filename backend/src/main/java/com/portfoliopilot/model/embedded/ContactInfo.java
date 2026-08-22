package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code profiles.contact} - a single-cardinality value object, never queried
 * independently, so it is embedded rather than referenced.
 *
 * <p>{@code phone} is private by default: the public portfolio projection
 * deliberately omits it. {@code publicEmail} is the address the user chose to
 * publish, which is NOT the account login email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactInfo {

    private String phone;

    private String publicEmail;

    private String website;

    private String github;

    private String linkedin;

    private String twitter;
}

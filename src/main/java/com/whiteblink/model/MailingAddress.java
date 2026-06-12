package com.whiteblink.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailingAddress {
    private String city;
    private String country;
    private String countryCode;
    private String postalCode;
    private String state;
    private String stateCode;
    private String street;
}

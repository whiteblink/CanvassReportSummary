package com.whiteblink.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientR {
    private String accountId;
    private String email;
    private String firstName;
    private String lastName;
    private MailingAddress mailingAddress;
    private String mailingCity;
    private String mailingPostalCode;
    private String mailingStateCode;
    private String mailingStreet;
    private String middleName;
    private String name;
    private String ownerId;
    private String phone;
    private String title;
    private String id;
}

package com.whiteblink.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountR {
    private String name;
    private String ownerId;
    private String id;
    private String website;
    private String phone;
    private String fax;


}

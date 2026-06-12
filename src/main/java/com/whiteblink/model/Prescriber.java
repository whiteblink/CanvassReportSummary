package com.whiteblink.model;

import lombok.Data;

@Data
public class Prescriber {
    private String id;
    private String name;
    private String npiC;
    private String canvassProviderTreatmentC;
    private String followUpCanvassProvider;
}

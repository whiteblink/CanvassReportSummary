package com.whiteblink.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CanvassProvider {
    private String canvassLocationC;
    private String canvassRequestC;
    private String contactDateC;
    private Double distanceC;
    private String facilityAddressC;
    private String facilityCityC;
    private String facilityContactC;
    private LocationC facilityLocationC;
    private String facilityMapLinkC;
    private String facilityNameC;
    private String facilityPhoneC;
    private String facilityStateC;
    private String facilityTypeC;
    private String facilityWebsiteC;
    private String facilityZipcodeC;
    private String latestTreatmentDateC;
    private String name;
    private Boolean requiresReleaseC;
    private String statusC;
    private String canvassOutcomeC;
    private Double totalTreatmentsIdentifiedC;
    private Double weekendTreatmentCountC;
    private String id;
    private String facilityFaxC;
    private String notesC;
    private String searchTextC;
    private String typeC;
    private String canvassProviderTreatmentId;
    private String canvassProviderEmailC;
    private String facilityReleaseTypeC;
    private List<Integer> redFlags =new ArrayList<>();
    private String facilityStateFullName;
    private String canvassCompletedDateC;
    private String recordRetentionTimeframeC;
    private String preferredRequestMethodC;
    private Boolean centralizedRecordsC;
    private String followUpFromC;
    private String facilityNPIC;
}

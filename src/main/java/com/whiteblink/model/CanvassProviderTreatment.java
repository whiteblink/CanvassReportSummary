package com.whiteblink.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CanvassProviderTreatment {
    private String additionalInformationC;
    private CanvassProvider canvassProviderC;
    private String notesC;
    private String prescriberNameC;
    private String prescriptionDosageC;
    private String prescriptionFillLastDateC;
    private String prescriptionFillStartDateC;
    private String prescriptionNameC;
    private Boolean treatmentBeforeDOLC;
    private String treatmentDateC;
    private String treatmentDateAccuracyC;
    private String treatmentTypeTextC;
    private String treatmentYearC;
    private Boolean weekendTreatmentIdentifiedC;
    private String id;
//    private String facilityName;
//    private String facilityTypeC;
//    private Double distanceC;
//    private String canvassCompletedDateC;
    private Boolean excessiveTreatment = false;
    private List<String> timelineColor = new ArrayList<>();
}

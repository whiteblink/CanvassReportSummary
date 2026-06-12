package com.whiteblink.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Timeline {
    private double marginPixel;
    private double marginPixel2;
    private int pageNo;
    private String date;
    private String type;
    private String tag;
    private String treatmentDateType;
    private Boolean isService;
    private String facilityTypeC;
    private List<String> timelineColor = new ArrayList<>();
}

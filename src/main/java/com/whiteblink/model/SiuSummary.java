package com.whiteblink.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SiuSummary {
    private String facilityType;
    private Integer numberOfFacilities;
    private Integer numberOfFacilitiesWithHits;
    private Integer numberOfHitsWithFlags;
    private List<Integer> redFlags =new ArrayList<>();
}

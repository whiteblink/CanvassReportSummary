package com.whiteblink;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.whiteblink.model.*;
import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class DataService {
    private static final Logger LOGGER = Logger.getLogger(DataService.class.getName());

    @SneakyThrows
    public String convertJSONToMailContent(String input) {
        Pattern pattern = Pattern.compile("<\\s*[bB][rR]\\s*>");
        Matcher matcher = pattern.matcher(input);
        input = matcher.replaceAll("\\\\n");
        input = replaceUnicodeCharacters(input);

        //Reading data for all the components
        JSONObject data = new JSONObject(input);
        JSONObject jsonObjectServiceOrder = data.optJSONObject("serviceOrder");
        JSONObject jsonObjectCanvassRequest = data.optJSONObject("canvassRequest");
        JSONObject jsonObjectCanvassLocations = data.optJSONObject("canvassLocations");
        JSONObject jsonObjectCanvassProviders = data.optJSONObject("canvassProviders");
        JSONArray jsonArrayCanvassProviderTreatments = data.optJSONArray("canvassProviderTreatments");

        //Setting data from JSON to POJO
        ServiceOrder serviceOrder = getServiceOrder(jsonObjectServiceOrder);
        CanvassRequest canvassRequest = getCanvassRequest(jsonObjectCanvassRequest);
        List<CanvassLocation> canvassLocations = getCanvassLocations(Objects.requireNonNullElseGet(jsonObjectCanvassLocations, JSONObject::new));
        List<CanvassProvider> canvassProviders = getCanvassProviders(Objects.requireNonNullElseGet(jsonObjectCanvassProviders, JSONObject::new));

        List<CanvassProviderTreatment> canvassProviderTreatments = findExcessiveTreatment(getCanvassProviderTreatments(Objects.requireNonNullElseGet(jsonArrayCanvassProviderTreatments, JSONArray::new), canvassProviders));



        canvassLocations.sort(Comparator.comparing(CanvassLocation::getAddressStartDateYearC, Comparator.reverseOrder()).thenComparing(CanvassLocation::getAddressStartDateMonthC, Comparator.reverseOrder()));

        canvassProviderTreatments.sort(Comparator.comparing(canvassProviderTreatment -> canvassProviderTreatment.getCanvassProviderC().getFacilityNameC()));

        //Locations
        Map<String, List<CanvassProviderTreatment>> canvassTreatmentByProvider = new HashMap<>();
        canvassProviders.stream()
                .filter(provider -> provider.getTotalTreatmentsIdentifiedC() != null
                        && provider.getTotalTreatmentsIdentifiedC() > 0)
                .forEach(provider -> canvassProviderTreatments.stream()
                        .filter(cvt -> cvt.getCanvassProviderC().getId().equals(provider.getId())).forEach(cvt -> {
            provider.setCanvassProviderTreatmentId(cvt.getId());
            if (!canvassTreatmentByProvider.containsKey(provider.getId().trim())) {
                canvassTreatmentByProvider.put(provider.getId().trim(), new ArrayList<>());
            }
            canvassTreatmentByProvider.get(provider.getId().trim()).add(cvt);
        }));


        String mailContent = getMailContent(canvassProviderTreatments,serviceOrder);
        return mailContent;
    }

    public String getMailContent (List<CanvassProviderTreatment> canvassProviderTreatmentList,ServiceOrder serviceOrder) throws ParseException {
        String formattedMail = "";

        String dateOfLoss = serviceOrder.getDateOfLossC();

        List<CanvassProviderTreatment> treatmentsWithIn90Days = new ArrayList<>();
        List<CanvassProviderTreatment> treatmentsWithIn6Months = new ArrayList<>();
        List<CanvassProviderTreatment> treatmentsWithIn1Year = new ArrayList<>();
        List<CanvassProviderTreatment> treatmentsOnWeekEnd = new ArrayList<>();
        List<CanvassProviderTreatment> futureTreatments = new ArrayList<>();
        List<CanvassProviderTreatment> excessiveTreatments = new ArrayList<>();
        List<CanvassProviderTreatment> treatmentWithMaxDistance = new ArrayList<>();

        for(CanvassProviderTreatment canvassProviderTreatment:canvassProviderTreatmentList) {
            if(!canvassProviderTreatment.getTreatmentDateC().isEmpty()) {
                long days = daysBetweenDates(canvassProviderTreatment.getTreatmentDateC(), dateOfLoss);
                if (days <= 90 && days > 0) {
                    treatmentsWithIn90Days.add(canvassProviderTreatment);
                } else if (days <= 180 && days > 90) {
                    treatmentsWithIn6Months.add(canvassProviderTreatment);
                } else if (days <= 365 && days > 180) {
                    treatmentsWithIn1Year.add(canvassProviderTreatment);
                }

                //Treatment on a Weekend
                if (isWeekend(canvassProviderTreatment.getTreatmentDateC())) {
                    treatmentsOnWeekEnd.add(canvassProviderTreatment);
                }

                //Future Treatment
                if (!canvassProviderTreatment.getCanvassProviderC().getCanvassCompletedDateC().isEmpty() && daysBetweenDates(canvassProviderTreatment.getCanvassProviderC().getCanvassCompletedDateC().substring(0, 10), canvassProviderTreatment.getTreatmentDateC()) > 0) {
                    futureTreatments.add(canvassProviderTreatment);
                }
                //excessive Treatment
                if (Boolean.TRUE.equals(canvassProviderTreatment.getExcessiveTreatment())) {
                    excessiveTreatments.add(canvassProviderTreatment);
                }
            }
            //Distance Traveled
            if (canvassProviderTreatment.getCanvassProviderC().getDistanceC() != null && canvassProviderTreatment.getCanvassProviderC().getDistanceC() > 15) {
                treatmentWithMaxDistance.add(canvassProviderTreatment);
            }
        }

        formattedMail+= "<b style=\"text-decoration: underline;\">Subject received treatment(s) within 90 days prior to date of loss at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(treatmentsWithIn90Days)+"\n\n"+
                        "<b style=\"text-decoration: underline;\">Subject received treatment(s) within 6 months prior to date of loss at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(treatmentsWithIn6Months)+"\n\n"+
                        "<b style=\"text-decoration: underline;\">Subject received treatment(s) within 1 year prior to date of loss at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(treatmentsWithIn1Year)+"\n\n"+
                        "<b style=\"text-decoration: underline;\">Subject received treatment(s) on a weekend at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(treatmentsOnWeekEnd)+"\n\n"+
                        "<b style=\"text-decoration: underline;\">Subject received excessive treatment(s) (2+ providers within 18 months) at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(excessiveTreatments)+"\n\n"+
                        "<b style=\"text-decoration: underline;\">Subject is scheduled to receive Future Treatment at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(futureTreatments)+"\n\n"+
                        "<b style=\"text-decoration: underline;\">Subject received Treatment with Distance Traveled (beyond 15 miles of target location) at the following facility(s):</b>\n\n"+
                        getTreatmentInfo(treatmentWithMaxDistance)+"\n\n";

        return formattedMail;
    }

    public String getTreatmentInfo(List<CanvassProviderTreatment> canvassProviderTreatmentList) throws ParseException {
        HashMap<CanvassProvider,List<CanvassProviderTreatment>> canvassProviderAndTreatmentMap = new HashMap<>();

        String finalResponse = "<ul>";
        for (CanvassProviderTreatment treatment : canvassProviderTreatmentList) {
            CanvassProvider provider = treatment.getCanvassProviderC();
            canvassProviderAndTreatmentMap.computeIfAbsent(provider, k -> new ArrayList<>()).add(treatment);
        }

        for(CanvassProvider canvassProvider : canvassProviderAndTreatmentMap.keySet()){
            List<CanvassProviderTreatment> treatmentList = canvassProviderAndTreatmentMap.get(canvassProvider);

            finalResponse+="<li><b>"+ canvassProvider.getFacilityNameC()+"</b>, "
                    +canvassProvider.getFacilityAddressC()+", "+canvassProvider.getFacilityCityC()+", "+canvassProvider.getFacilityStateC()+", "+canvassProvider.getFacilityZipcodeC()+": \n";

            for (int i = 0; i<treatmentList.size(); i++) {
                finalResponse += formatDate(treatmentList.get(i).getTreatmentDateC(),"M/d/yyyy");
                if (i < treatmentList.size() - 1) {
                    finalResponse +=", ";
                }
            }
            finalResponse+="</li>";
        }

        if(canvassProviderTreatmentList.isEmpty()){
            finalResponse+="<li>No Dates Found</li>";
        }
        finalResponse+="</ul>";
        return finalResponse;
    }

    public String formatDate(String date,String outputFormat) throws ParseException {
        if(!date.isEmpty() && date!=null) {
            SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat outputDateFormat = new SimpleDateFormat(outputFormat);
            date = outputDateFormat.format(inputDateFormat.parse(date));
        }
        return date;
    }

    public ServiceOrder getServiceOrder(JSONObject jsonObjectServiceOrder) {
        ServiceOrder serviceOrder = new ServiceOrder();
        JSONObject jsonObjectAccountR = jsonObjectServiceOrder.optJSONObject("Account__r");
        JSONObject jsonObjectClientR = jsonObjectServiceOrder.optJSONObject("Client__r");
        JSONObject jsonObjectMailingAddress = jsonObjectClientR.optJSONObject("MailingAddress");
        AccountR accountR = jsonObjectAccountR != null ? new AccountR(jsonObjectAccountR.optString("Name"), jsonObjectAccountR.optString("OwnerId"), jsonObjectAccountR.optString("Id"), jsonObjectAccountR.optString("Website"), jsonObjectAccountR.optString("Phone"), jsonObjectAccountR.optString("Fax")) : new AccountR();
        MailingAddress mailingAddress = jsonObjectMailingAddress != null ? new MailingAddress(jsonObjectMailingAddress.optString("city"), jsonObjectMailingAddress.optString("country"), jsonObjectMailingAddress.optString("countryCode"), jsonObjectMailingAddress.optString("postalCode"), jsonObjectMailingAddress.optString("state"), jsonObjectMailingAddress.optString("stateCode"), jsonObjectMailingAddress.optString("street")) : new MailingAddress();
        String phone = jsonObjectClientR.optString("Phone");
        phone = phone.replace("(", "").replace(")", "")
                .replace(" ", "").replace("-", "").replace("+1", "");
        ClientR clientR = new ClientR(jsonObjectClientR.optString("AccountId"), jsonObjectClientR.optString("Email"), jsonObjectClientR.optString("FirstName"), jsonObjectClientR.optString("LastName"), mailingAddress, jsonObjectClientR.optString("MailingCity"), jsonObjectClientR.optString("MailingPostalCode"), jsonObjectClientR.optString("MailingStateCode"), jsonObjectClientR.optString("MailingStreet"), jsonObjectClientR.optString("MiddleName"), jsonObjectClientR.optString("Name"), jsonObjectClientR.optString("OwnerId"), phone, jsonObjectClientR.optString("Title"), jsonObjectClientR.optString("Id"));
        serviceOrder.setAccountC(jsonObjectServiceOrder.optString("Account__c"));
        serviceOrder.setAccountR(accountR);
        serviceOrder.setClientR(clientR);
        serviceOrder.setAdditionalInformationC(Util.escape(wrapText(jsonObjectServiceOrder.optString("Additional_Information__c"))));
        serviceOrder.setAliasC(jsonObjectServiceOrder.optString("Alias__c"));
        serviceOrder.setClaimNumberC(jsonObjectServiceOrder.optString("Claim_Number__c"));
        serviceOrder.setClaimantCellC(formatPhone(jsonObjectServiceOrder.optString("Claimant_Cell__c")));
        serviceOrder.setClaimantEmailC(jsonObjectServiceOrder.optString("Claimant_Email__c"));
        serviceOrder.setClaimantEmployerC(jsonObjectServiceOrder.optString("Claimant_Employer__c"));
        serviceOrder.setClaimantPhone2C(jsonObjectServiceOrder.optString("Claimant_Phone_2__c"));
        serviceOrder.setClientC(jsonObjectServiceOrder.optString("Client__c"));
        serviceOrder.setDateLastWorkedC(jsonObjectServiceOrder.optString("Date_Last_Worked__c"));
        serviceOrder.setDateOfBirthC(jsonObjectServiceOrder.optString("Date_of_Birth__c"));
        serviceOrder.setDateOfLossC(jsonObjectServiceOrder.optString("Date_of_Loss__c"));
        serviceOrder.setEmployerContactC(jsonObjectServiceOrder.optString("Employer_Contact__c"));
        serviceOrder.setFileNumberC(jsonObjectServiceOrder.optString("File_Number__c"));
        serviceOrder.setFirstNameC(jsonObjectServiceOrder.optString("First_Name__c"));
        serviceOrder.setLastNameC(jsonObjectServiceOrder.optString("Last_Name__c"));
        serviceOrder.setLossDescriptionC(jsonObjectServiceOrder.optString("Loss_Description__c"));
        if (jsonObjectServiceOrder.has("MRR_Cost__c")) {
            serviceOrder.setMrrCostC(jsonObjectServiceOrder.optDouble("MRR_Cost__c"));
        }
        serviceOrder.setMiddleNameC(jsonObjectServiceOrder.optString("Middle_Name__c"));
        serviceOrder.setName(jsonObjectServiceOrder.optString("Name"));
        serviceOrder.setOrderDateC(jsonObjectServiceOrder.optString("Order_Date__c"));
        serviceOrder.setPolicyNumberC(jsonObjectServiceOrder.optString("Policy_Number__c"));
        serviceOrder.setPositionJobTitleC(jsonObjectServiceOrder.optString("Position_Job_Title__c"));
        serviceOrder.setSsnC(jsonObjectServiceOrder.optString("SSN__c"));
        serviceOrder.setSsnLast4C(jsonObjectServiceOrder.optString("SSN_Last_4__c"));
        serviceOrder.setSsn2C(jsonObjectServiceOrder.optString("SSN2__c"));
        if (jsonObjectServiceOrder.has("Service_Type_Canvass__c")) {
            serviceOrder.setServiceTypeCanvassC(jsonObjectServiceOrder.getBoolean("Service_Type_Canvass__c"));
        }
        if (jsonObjectServiceOrder.has("Service_Type_MRR__c")) {
            serviceOrder.setServiceTypeMRRC(jsonObjectServiceOrder.getBoolean("Service_Type_MRR__c"));
        }
        serviceOrder.setStatusC(jsonObjectServiceOrder.optString("Status__c"));
        serviceOrder.setId(jsonObjectServiceOrder.optString("Id"));
        serviceOrder.setCompletionDateC(jsonObjectServiceOrder.optString("Completion_Date__c"));
        serviceOrder.setReportDatec(jsonObjectServiceOrder.optString("Report_Date__c"));
        serviceOrder.setSubClientNameC(jsonObjectServiceOrder.optString("SubClient_Name__c"));
        serviceOrder.setClientTypeC(jsonObjectServiceOrder.optString("Client_Type__c"));
        serviceOrder.setSubClientHeader(jsonObjectServiceOrder.optString("subClientHeader"));
        serviceOrder.setSubClientFooter(jsonObjectServiceOrder.optString("subClientFooter"));
        serviceOrder.setCanvassRangeStartDateC(jsonObjectServiceOrder.optString("Canvass_Range_Start_Date__c"));
        serviceOrder.setCanvassRangeEndDateC(jsonObjectServiceOrder.optString("Canvass_Range_End_Date__c"));
        serviceOrder.setIdentifiedByC(jsonObjectServiceOrder.optString("Identified_By__c"));
        serviceOrder.setBodyPartC(jsonObjectServiceOrder.optString("Body_Part__c"));
        return serviceOrder;
    }

    public CanvassRequest getCanvassRequest(JSONObject jsonObjectCanvassRequest) {
        CanvassRequest canvassRequest = new CanvassRequest();
        canvassRequest.setCountCardiologyC(jsonObjectCanvassRequest.optDouble("Count_Cardiology__c", 0));
        canvassRequest.setCountChiropracticC(jsonObjectCanvassRequest.optDouble("Count_Chiropractic__c", 0));
        canvassRequest.setCountClinicalLabC(jsonObjectCanvassRequest.optDouble("Count_Clinical_Lab__c", 0));
        canvassRequest.setCountDentalC(jsonObjectCanvassRequest.optDouble("Count_Dental__c", 0));
        canvassRequest.setCountDialysisC(jsonObjectCanvassRequest.optDouble("Count_Dialysis__c", 0));
        canvassRequest.setCountDoctorC(jsonObjectCanvassRequest.optDouble("Count_Doctor__c", 0));
        canvassRequest.setCountENTC(jsonObjectCanvassRequest.optDouble("Count_ENT__c", 0));
        canvassRequest.setCountGymC(jsonObjectCanvassRequest.optDouble("Count_Gym__c", 0));
        canvassRequest.setCountHealthClinicC(jsonObjectCanvassRequest.optDouble("Count_Health_Clinic__c", 0));
        canvassRequest.setCountHospitalC(jsonObjectCanvassRequest.optDouble("Count_Hospital__c", 0));
        canvassRequest.setCountImagingCenterC(jsonObjectCanvassRequest.optDouble("Count_Imaging_Center__c", 0));
        canvassRequest.setCountMailOrderPharmacyC(jsonObjectCanvassRequest.optDouble("Count_Mail_Order_Pharmacy__c", 0));
        canvassRequest.setCountNeurologyC(jsonObjectCanvassRequest.optDouble("Count_Neurology__c", 0));
        canvassRequest.setCountOphthalmologyC(jsonObjectCanvassRequest.optDouble("Count_Ophthalmology__c", 0));
        canvassRequest.setCountOrthopedicC(jsonObjectCanvassRequest.optDouble("Count_Orthopedic__c", 0));
        canvassRequest.setCountOtherC(jsonObjectCanvassRequest.optDouble("Count_Other__c", 0));
        canvassRequest.setCountPainManagementC(jsonObjectCanvassRequest.optDouble("Count_Pain_Management__c", 0));
        canvassRequest.setCountPharmacyC(jsonObjectCanvassRequest.optDouble("Count_Pharmacy__c", 0));
        canvassRequest.setCountPhysicalTherapyC(jsonObjectCanvassRequest.optDouble("Count_Physical_Therapy__c", 0));
        canvassRequest.setCountPodiatryC(jsonObjectCanvassRequest.optDouble("Count_Podiatry__c", 0));
        canvassRequest.setCountUrologyC(jsonObjectCanvassRequest.optDouble("Count_Urology__c", 0));
        canvassRequest.setCountPulmonaryC(jsonObjectCanvassRequest.optDouble("Count_Pulmonary__c", 0));
        canvassRequest.setCountUrgentCareC(jsonObjectCanvassRequest.optDouble("Count_Urgent_Care__c", 0));
        canvassRequest.setCountSurgeryCentersC(jsonObjectCanvassRequest.optDouble("Count_Surgery_Centers__c", 0));
        canvassRequest.setCoverageTypeOtherC(jsonObjectCanvassRequest.optString("Coverage_Type_Other__c"));
        canvassRequest.setCoverageTypeC(jsonObjectCanvassRequest.optString("Coverage_Type__c"));
        canvassRequest.setEarliestTreatmentDateC(jsonObjectCanvassRequest.optString("Earliest_Treatment_Date__c"));
        if (jsonObjectCanvassRequest.has("Excessive_Treatment_Identified__c")) {
            canvassRequest.setExcessiveTreatmentIdentifiedC(jsonObjectCanvassRequest.getBoolean("Excessive_Treatment_Identified__c"));
        }
        if (jsonObjectCanvassRequest.has("Facilities_Contacted__c")) {
            canvassRequest.setFacilitiesContactedC(jsonObjectCanvassRequest.optDouble("Facilities_Contacted__c"));
        }
        canvassRequest.setInsuredPartyC(jsonObjectCanvassRequest.optString("Insured_Party__c"));
        canvassRequest.setLatestTreatmentDateC(jsonObjectCanvassRequest.optString("Latest_Treatment_Date__c"));
        canvassRequest.setLossTypeC(jsonObjectCanvassRequest.optString("Loss_Type__c"));
        canvassRequest.setName(jsonObjectCanvassRequest.optString("Name"));
        canvassRequest.setPackageSelectedC(jsonObjectCanvassRequest.optString("Package_Selected__c"));
        canvassRequest.setServiceOrderC(jsonObjectCanvassRequest.optString("Service_Order__c"));
        if (jsonObjectCanvassRequest.has("Total_Providers_w_Treatments__c")) {
            canvassRequest.setTotalProvidersWTreatmentsC(jsonObjectCanvassRequest.optDouble("Total_Providers_w_Treatments__c"));
        }
        if (jsonObjectCanvassRequest.has("Total_Treatments_Identified__c")) {
            canvassRequest.setTotalTreatmentsIdentifiedC(jsonObjectCanvassRequest.optDouble("Total_Treatments_Identified__c"));
        }
        if (jsonObjectCanvassRequest.has("Treatment_Prior_to_DOL__c")) {
            canvassRequest.setTreatmentPriorToDOLC(jsonObjectCanvassRequest.getBoolean("Treatment_Prior_to_DOL__c"));
        }
        if (jsonObjectCanvassRequest.has("Weekend_Treatment_Count__c")) {
            canvassRequest.setWeekendTreatmentCountC(jsonObjectCanvassRequest.optDouble("Weekend_Treatment_Count__c"));
        }
        canvassRequest.setId(jsonObjectCanvassRequest.optString("Id"));
        return canvassRequest;
    }

    public List<CanvassLocation> getCanvassLocations(JSONObject jsonObjectCanvassLocations) {
        JSONObject stateFullName = getStateFullName();
        Map<String, Object> map = jsonObjectCanvassLocations.toMap();
        List<CanvassLocation> canvassLocationList = new ArrayList<>();
        map.forEach((key, value) -> {
            CanvassLocation canvassLocation = new CanvassLocation();

            JSONObject jsonObjectCanvassLocation = jsonObjectCanvassLocations.optJSONObject(key);
            if (jsonObjectCanvassLocation == null) {
                jsonObjectCanvassLocation = new JSONObject();
            }
            JSONObject jsonObjectGeoLocationC = jsonObjectCanvassLocation.optJSONObject("Geolocation__c");
            if (jsonObjectGeoLocationC != null && jsonObjectGeoLocationC.has("latitude") && jsonObjectGeoLocationC.has("longitude")) {
                LocationC geolocationC = new LocationC();
                geolocationC.setLatitude(jsonObjectGeoLocationC.optDouble("latitude"));
                geolocationC.setLongitude(jsonObjectGeoLocationC.optDouble("longitude"));
                canvassLocation.setGeolocationC(geolocationC);
            }

            canvassLocation.setAddress1C(jsonObjectCanvassLocation.optString("Address_1__c"));
            canvassLocation.setAddress2C(jsonObjectCanvassLocation.optString("Address_2__c"));
            canvassLocation.setAddressEndDateMonthC(jsonObjectCanvassLocation.optString("Address_End_Date_Month__c"));
            canvassLocation.setAddressEndDateYearC(jsonObjectCanvassLocation.optString("Address_End_Date_Year__c"));
            canvassLocation.setAddressEndDateC(jsonObjectCanvassLocation.optString("Address_End_Date__c"));
            canvassLocation.setAddressNoteC(jsonObjectCanvassLocation.optString("Address_Note__c"));
            canvassLocation.setAddressStartDateMonthC(jsonObjectCanvassLocation.optString("Address_Start_Date_Month__c"));
            canvassLocation.setAddressStartDateYearC(jsonObjectCanvassLocation.optString("Address_Start_Date_Year__c"));
            canvassLocation.setAddressStartDateC(jsonObjectCanvassLocation.optString("Address_Start_Date__c"));
            canvassLocation.setAddressTypeC(jsonObjectCanvassLocation.optString("Address_Type__c"));
            canvassLocation.setCanvassRequestC(jsonObjectCanvassLocation.optString("Canvass_Request__c"));
            canvassLocation.setCityC(jsonObjectCanvassLocation.optString("City__c"));
            canvassLocation.setDescriptionC(jsonObjectCanvassLocation.optString("Description__c"));
            canvassLocation.setName(jsonObjectCanvassLocation.optString("Name"));
            canvassLocation.setSourceC(jsonObjectCanvassLocation.optString("Source__c"));
            canvassLocation.setStateC(jsonObjectCanvassLocation.optString("State__c"));
            canvassLocation.setStateFullName(stateFullName.optString(canvassLocation.getStateC()));
            canvassLocation.setZipcodeC(jsonObjectCanvassLocation.optString("Zipcode__c"));
            canvassLocation.setId(jsonObjectCanvassLocation.optString("Id"));
            canvassLocationList.add(canvassLocation);
        });
        return canvassLocationList;
    }

    public List<CanvassProvider> getCanvassProviders(JSONObject jsonObjectCanvassProviders) {
        JSONObject stateFullName = getStateFullName();
        List<CanvassProvider> canvassProviders = new ArrayList<>();
        Map<String, Object> map = jsonObjectCanvassProviders.toMap();
        map.forEach((key, value) -> {
            CanvassProvider canvassProvider = new CanvassProvider();
            JSONObject jsonObjectCanvassProvider = jsonObjectCanvassProviders.optJSONObject(key);
            if (jsonObjectCanvassProvider == null) jsonObjectCanvassProvider = new JSONObject();

            JSONObject jsonObjectFacilityLocationC = jsonObjectCanvassProvider.optJSONObject("Facility_Location__c");
            if (jsonObjectFacilityLocationC != null && jsonObjectFacilityLocationC.has("latitude") && jsonObjectFacilityLocationC.has("longitude")) {
                LocationC facilityLocationC = new LocationC();
                facilityLocationC.setLatitude(jsonObjectFacilityLocationC.optDouble("latitude"));
                facilityLocationC.setLongitude(jsonObjectFacilityLocationC.optDouble("longitude"));
                canvassProvider.setFacilityLocationC(facilityLocationC);
            }
            canvassProvider.setCanvassLocationC(jsonObjectCanvassProvider.optString("Canvass_Location__c"));
            canvassProvider.setCanvassRequestC(jsonObjectCanvassProvider.optString("Canvass_Request__c"));
            canvassProvider.setContactDateC(jsonObjectCanvassProvider.optString("Contact_Date__c"));
            if (jsonObjectCanvassProvider.has("Distance__c")) {
                canvassProvider.setDistanceC(jsonObjectCanvassProvider.optDouble("Distance__c"));
            }
            canvassProvider.setFacilityAddressC(jsonObjectCanvassProvider.optString("Facility_Address__c"));
            canvassProvider.setFacilityCityC(jsonObjectCanvassProvider.optString("Facility_City__c"));
            canvassProvider.setFacilityContactC(jsonObjectCanvassProvider.optString("Facility_Contact__c"));
            canvassProvider.setFacilityMapLinkC(jsonObjectCanvassProvider.optString("Facility_Map_Link__c"));
            canvassProvider.setFacilityNameC(jsonObjectCanvassProvider.optString("Facility_Name__c"));
            canvassProvider.setFacilityPhoneC(formatPhone(jsonObjectCanvassProvider.optString("Facility_Phone__c")));
            canvassProvider.setFacilityStateC(jsonObjectCanvassProvider.optString("Facility_State__c"));
            canvassProvider.setFacilityStateFullName(stateFullName.optString(canvassProvider.getFacilityStateC()));
            canvassProvider.setFacilityTypeC(jsonObjectCanvassProvider.optString("Facility_Type__c"));
            canvassProvider.setFacilityWebsiteC(jsonObjectCanvassProvider.optString("Facility_Website__c"));
            canvassProvider.setFacilityZipcodeC(jsonObjectCanvassProvider.optString("Facility_Zipcode__c"));
            canvassProvider.setLatestTreatmentDateC(jsonObjectCanvassProvider.optString("Latest_Treatment_Date__c"));
            canvassProvider.setName(jsonObjectCanvassProvider.optString("Name"));
            if (jsonObjectCanvassProvider.has("Requires_Release__c")) {
                canvassProvider.setRequiresReleaseC(jsonObjectCanvassProvider.optBoolean("Requires_Release__c"));
            }
            canvassProvider.setStatusC(jsonObjectCanvassProvider.optString("Status__c"));
            canvassProvider.setCanvassOutcomeC(jsonObjectCanvassProvider.optString("Canvass_Outcome__c"));
            if (jsonObjectCanvassProvider.has("Total_Treatments_Identified__c")) {
                canvassProvider.setTotalTreatmentsIdentifiedC(jsonObjectCanvassProvider.optDouble("Total_Treatments_Identified__c"));
            }
            if (jsonObjectCanvassProvider.has("Weekend_Treatment_Count__c")) {
                canvassProvider.setWeekendTreatmentCountC(jsonObjectCanvassProvider.optDouble("Weekend_Treatment_Count__c"));
            }
            canvassProvider.setId(jsonObjectCanvassProvider.optString("Id"));
            canvassProvider.setCanvassCompletedDateC(jsonObjectCanvassProvider.optString("Canvass_Completed_Date__c"));
            canvassProvider.setFacilityFaxC(formatPhone(jsonObjectCanvassProvider.optString("Facility_Fax__c")));
            canvassProvider.setCanvassProviderEmailC(jsonObjectCanvassProvider.optString("Canvass_Provider_Email__c"));
            canvassProvider.setFacilityReleaseTypeC("Provider Specific");
            String notes = wrapText(jsonObjectCanvassProvider.optString("Notes__c"));
            canvassProvider.setNotesC(notes);
            canvassProvider.setSearchTextC(jsonObjectCanvassProvider.optString("Search_Text__c"));
            canvassProvider.setTypeC(jsonObjectCanvassProvider.optString("Type__c"));
            JSONArray redFlagsArray = jsonObjectCanvassProvider.optJSONArray("redFlags");
            List<Integer> redFlags = new ArrayList<>();
            if (redFlagsArray != null) {
                for (int i = 0; i < redFlagsArray.length(); i++) {
                    redFlags.add(redFlagsArray.getInt(i));
                }
            }
            canvassProvider.setRedFlags(redFlags);
            canvassProvider.setRecordRetentionTimeframeC(jsonObjectCanvassProvider.optString("Record_Retention_Timeframe__c"));
            canvassProvider.setPreferredRequestMethodC(jsonObjectCanvassProvider.optString("Preferred_Request_Method__c"));
            canvassProvider.setCentralizedRecordsC(jsonObjectCanvassProvider.optBoolean("Centralized_Records__c"));
            canvassProvider.setFollowUpFromC(jsonObjectCanvassProvider.optString("Follow_Up_From__c"));
            canvassProvider.setFacilityNPIC(jsonObjectCanvassProvider.optString("Facility_NPI__c"));

            canvassProviders.add(canvassProvider);
        });
        return canvassProviders;
    }

    public List<CanvassProviderTreatment> getCanvassProviderTreatments(JSONArray jsonArrayCanvassProviderTreatments,List<CanvassProvider> canvassProviderList) {
        List<CanvassProviderTreatment> canvassProviderTreatments = new ArrayList<>();
        IntStream.range(0, jsonArrayCanvassProviderTreatments.length()).forEach(i -> {
            CanvassProviderTreatment canvassProviderTreatment = new CanvassProviderTreatment();
            JSONObject jsonObject = jsonArrayCanvassProviderTreatments.optJSONObject(i);
            canvassProviderTreatment.setAdditionalInformationC(jsonObject.optString("Additional_Information__c"));
            //canvassProviderTreatment.setCanvassProviderC(jsonObject.optString("Canvass_Provider__c"));
            String notes = wrapText(jsonObject.optString("Notes__c"));
            canvassProviderTreatment.setNotesC(notes);
            canvassProviderTreatment.setPrescriberNameC(jsonObject.optString("Prescriber_Name__c"));
            canvassProviderTreatment.setPrescriptionDosageC(jsonObject.optString("Prescription_Dosage__c"));
            canvassProviderTreatment.setPrescriptionFillLastDateC(jsonObject.optString("Prescription_Fill_Last_Date__c"));
            canvassProviderTreatment.setPrescriptionFillStartDateC(jsonObject.optString("Prescription_Fill_Start_Date__c"));
            canvassProviderTreatment.setPrescriptionNameC(jsonObject.optString("Prescription_Name__c"));
//            canvassProviderTreatment.setFacilityName(jsonObjectCanvassProviders.getJSONObject(canvassProviderTreatment.getCanvassProviderC()).optString("Facility_Name__c"));
//            canvassProviderTreatment.setFacilityTypeC(jsonObjectCanvassProviders.getJSONObject(canvassProviderTreatment.getCanvassProviderC()).optString("Facility_Type__c"));
//            canvassProviderTreatment.setCanvassCompletedDateC(jsonObjectCanvassProviders.getJSONObject(canvassProviderTreatment.getCanvassProviderC()).optString("Canvass_Completed_Date__c"));
//            if (jsonObjectCanvassProviders.getJSONObject(canvassProviderTreatment.getCanvassProviderC()).has("Facility_Type__c")) {
//                canvassProviderTreatment.setDistanceC(jsonObjectCanvassProviders.getJSONObject(canvassProviderTreatment.getCanvassProviderC()).optDouble("Distance__c"));
//            }
            if (jsonObject.has("Treatment_Before_DOL__c")) {
                canvassProviderTreatment.setTreatmentBeforeDOLC(jsonObject.optBoolean("Treatment_Before_DOL__c"));
            }
            canvassProviderTreatment.setTreatmentDateC(jsonObject.optString("Treatment_Date__c"));
            canvassProviderTreatment.setTreatmentDateAccuracyC(jsonObject.optString("Treatment_Date_Accuracy__c"));
            canvassProviderTreatment.setTreatmentTypeTextC(jsonObject.optString("Treatment_Type_Text__c"));
            canvassProviderTreatment.setTreatmentYearC(jsonObject.optString("Treatment_Year__c"));
            if (jsonObject.has("Weekend_Treatment_Identified__c")) {
                canvassProviderTreatment.setWeekendTreatmentIdentifiedC(jsonObject.optBoolean("Weekend_Treatment_Identified__c"));
            }
            canvassProviderTreatment.setId(jsonObject.optString("Id"));
            CanvassProvider canvassProvider = canvassProviderList.stream().filter(CanvassProvider->CanvassProvider.getId().equals(jsonObject.optString("Canvass_Provider__c"))).findFirst().get();
            canvassProviderTreatment.setCanvassProviderC(canvassProvider);
            canvassProviderTreatments.add(canvassProviderTreatment);
        });
        return canvassProviderTreatments;
    }


    private String formatPhone(String phone) {
        if (!phone.isEmpty()) {
            try {
                String temp = phone.replace("(", "").replace(")", "")
                        .replace(" ", "").replace("-", "").replace("+1", "");
                String formatedPhone = temp.substring(0, 3) + "-";
                formatedPhone += temp.substring(3, 6);
                formatedPhone += "-" + temp.substring(6, 10);
                return formatedPhone;
            } catch (Exception e) {
                return phone;
            }
        } else return phone;
    }

    public static String wrapText(String input) {
        input = input.replace("&", "&amp;");
        String prefix = "<span style='white-space: nowrap;display: inline-block;'>";
        String suffix = "</span>";
        String regexForDate = "\\d{1,2}/\\d{1,2}/\\d{2,4}";
        String regexForPhone = "\\d{3}-\\d{3}-\\d{4}";

        Pattern patternForDate = Pattern.compile(regexForDate);
        Matcher matcherForDate = patternForDate.matcher(input);

        while (matcherForDate.find()) {
            String date = matcherForDate.group();
            input = input.replace(date, prefix + date + suffix);
        }
        Pattern patternForPhone = Pattern.compile(regexForPhone);
        Matcher matcherForPhone = patternForPhone.matcher(input);
        while (matcherForPhone.find()) {
            String phone = matcherForPhone.group();
            input = input.replace(phone, prefix + phone + suffix);
        }
        return input;
    }

    public JSONObject getStateFullName() {

        JSONObject stateFullName = new JSONObject();
        stateFullName.put("AL", "Alabama");
        stateFullName.put("AK", "Alaska");
        stateFullName.put("AZ", "Arizona");
        stateFullName.put("AR", "Arkansas");
        stateFullName.put("CA", "California");
        stateFullName.put("CO", "Colorado");
        stateFullName.put("CT", "Connecticut");
        stateFullName.put("DE", "Delaware");
        stateFullName.put("FL", "Florida");
        stateFullName.put("GA", "Georgia");
        stateFullName.put("HI", "Hawaii");
        stateFullName.put("ID", "Idaho");
        stateFullName.put("IL", "Illinois");
        stateFullName.put("IN", "Indiana");
        stateFullName.put("IA", "Iowa");
        stateFullName.put("KS", "Kansas");
        stateFullName.put("KY", "Kentucky");
        stateFullName.put("LA", "Louisiana");
        stateFullName.put("ME", "Maine");
        stateFullName.put("MD", "Maryland");
        stateFullName.put("MA", "Massachusetts");
        stateFullName.put("MI", "Michigan");
        stateFullName.put("MN", "Minnesota");
        stateFullName.put("MS", "Mississippi");
        stateFullName.put("MO", "Missouri");
        stateFullName.put("MT", "Montana");
        stateFullName.put("NE", "Nebraska");
        stateFullName.put("NV", "Nevada");
        stateFullName.put("NH", "New Hampshire");
        stateFullName.put("NJ", "New Jersey");
        stateFullName.put("NM", "New Mexico");
        stateFullName.put("NY", "New York");
        stateFullName.put("NC", "North Carolina");
        stateFullName.put("ND", "North Dakota");
        stateFullName.put("OH", "Ohio");
        stateFullName.put("OK", "Oklahoma");
        stateFullName.put("OR", "Oregon");
        stateFullName.put("PA", "Pennsylvania");
        stateFullName.put("RI", "Rhode Island");
        stateFullName.put("SC", "South Carolina");
        stateFullName.put("SD", "South Dakota");
        stateFullName.put("TN", "Tennessee");
        stateFullName.put("TX", "Texas");
        stateFullName.put("UT", "Utah");
        stateFullName.put("VT", "Vermont");
        stateFullName.put("VA", "Virginia");
        stateFullName.put("WA", "Washington");
        stateFullName.put("WV", "West Virginia");
        stateFullName.put("WI", "Wisconsin");
        stateFullName.put("WY", "Wyoming");
        return stateFullName;
    }

    public static List<CanvassProviderTreatment> filterCanvassProviderTreatments(List<CanvassProviderTreatment> canvassProviderTreatments){
        if(canvassProviderTreatments!=null) {
            return canvassProviderTreatments.stream()
                    .filter(treatment -> treatment.getTreatmentDateC() != null && !treatment.getTreatmentDateC().isEmpty())
                    .sorted(Comparator.comparing(CanvassProviderTreatment::getTreatmentDateC))
                    .collect(Collectors.toList());
        }
        else
            return new ArrayList<>();
    }
    private String replaceUnicodeCharacters(String input) {
        return input.replace("\u0013", "")
                .replace("\\u0013", "")
                .replace("\u0019", "")
                .replace("\\u0019", "");
    }
    public long daysBetweenDates(String date1, String date2) {
        LocalDate d1 = LocalDate.parse(date1);
        LocalDate d2;
        if (date2.length() == 4) { // Only year
            d2 = LocalDate.of(Integer.parseInt(date2), 1, 1);
        } else if (date2.length() == 7) { // Month and year
            String[] parts = date2.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            d2 = LocalDate.of(year, month, 1);
        } else { // Full date
            d2 = LocalDate.parse(date2);
        }
        return ChronoUnit.DAYS.between(d1, d2);
    }
    public static boolean isWeekend(String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public List<CanvassProviderTreatment> findExcessiveTreatment(List<CanvassProviderTreatment> treatments) {

        List<CanvassProviderTreatment> filteredCanvassProviderTreatment = treatments.stream().filter(
                canvassProviderTreatment-> !canvassProviderTreatment.getCanvassProviderC().getFacilityTypeC().isEmpty() &&
                !canvassProviderTreatment.getTreatmentDateC().isEmpty()).collect(Collectors.toList());
        Map<String, Map<LocalDate, Integer>> facilityTypeTreatments = new HashMap<>();

        treatments.sort(Comparator.comparing(CanvassProviderTreatment::getTreatmentDateC));

        // Iterate over all treatments and store treatment dates for each facility type
        for (CanvassProviderTreatment treatment : filteredCanvassProviderTreatment) {
            LocalDate treatmentDate = LocalDate.parse(treatment.getTreatmentDateC());
            Map<LocalDate, Integer> treatmentDates = facilityTypeTreatments.computeIfAbsent(treatment.getCanvassProviderC().getFacilityTypeC(), k -> new HashMap<>());
            treatmentDates.put(treatmentDate, treatmentDates.getOrDefault(treatmentDate, 0) + 1);
        }

        // Iterate again and mark excessive treatments
        for (CanvassProviderTreatment treatment : filteredCanvassProviderTreatment) {
            Map<LocalDate, Integer> treatmentDates = facilityTypeTreatments.get(treatment.getCanvassProviderC().getFacilityTypeC());
            LocalDate treatmentDate = LocalDate.parse(treatment.getTreatmentDateC());
            for (LocalDate date : treatmentDates.keySet()) {
                if (!date.equals(treatmentDate) || treatmentDates.get(treatmentDate)>1) {
                    long monthsDifference = Math.abs(treatmentDate.toEpochDay() - date.toEpochDay()) / 30;
                    if (monthsDifference < 18 || treatmentDates.get(treatmentDate)>1) {
                        treatment.setExcessiveTreatment(true);
                        break;
                    }
                }
            }
        }
        return treatments;
    }

}

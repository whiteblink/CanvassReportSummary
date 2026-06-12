# CanvassReportSummary

AWS Lambda function that generates a **canvass treatment summary** for medical
canvassing services. The Lambda accepts a canvass report as JSON (uploaded as a
multipart `file` field), maps the Salesforce-shaped JSON into POJOs, analyzes the
identified provider treatments against the claim's date of loss, and returns an
HTML‑formatted summary that groups treatments into red‑flag categories
(within 90 days / 6 months / 1 year of the date of loss, weekend treatments,
excessive treatments, future treatments, and treatments beyond 15 miles).

The summary is returned directly in the JSON response body.

## Architecture

```
HTTP caller (multipart/form-data POST, "file" = report JSON)
       │
       ▼
Lambda (App.handleRequest)
       │
       ├─▶ commons-fileupload : base64 body ──▶ extract "file" field (JSON string)
       │
       └─▶ DataService.convertJSONToMailContent
                 │
                 ├─ JSON ──▶ POJOs (ServiceOrder, CanvassRequest,
                 │                   CanvassLocation[], CanvassProvider[],
                 │                   CanvassProviderTreatment[])
                 │
                 ├─ classify treatments vs. Date of Loss:
                 │     • within 90 days   • within 6 months   • within 1 year
                 │     • on a weekend     • excessive (2+ same facility type / 18 mo)
                 │     • future treatment • distance traveled > 15 miles
                 │
                 └─ build HTML summary string
                          │
                          ▼
          200 OK  { "summary": "<html…>" }  (application/json)
```

The Lambda can be invoked over HTTP using a **Lambda Function URL** 
or via **API Gateway**. Because the handler base64‑decodes the
request body and parses a multipart stream, the integration must be configured to
deliver the body as base64 (binary media / `multipart/form-data`).

## Project structure

```
src/main/java/com/whiteblink/
├── App.java                       # Lambda entry point — multipart upload → summary JSON
├── App2.java                      # Local runner — reads a .json file, writes *_mail_content.txt
├── Util.java                      # HTML escaping helper
├── DataService.java               # Core: JSON → POJOs → treatment analysis → HTML summary
└── model/
    ├── GatewayResponse.java       # API Gateway / Function URL response model
    ├── ServiceOrder.java          # Claim / subject details (incl. Date of Loss)
    ├── CanvassRequest.java        # Per-specialty facility counts + request metadata
    ├── CanvassLocation.java       # Subject address history
    ├── CanvassProvider.java       # Facility / provider details
    ├── CanvassProviderTreatment.java  # Individual treatment records
    ├── AccountR.java, ClientR.java, MailingAddress.java, LocationC.java
    ├── Prescriber.java, SiuSummary.java, Timeline.java
    ├── TimeLineForClaimsBureauReport.java
    └── CanvassAdditionalName.java, CanvassAdditionalPhone.java
```

## Build & deploy

Build the shaded jar:

```
mvn clean package shade:shade
```

This produces a fat jar under `target/` containing all dependencies, ready to upload to Lambda.

Create (or update) a Lambda function in AWS using the resulting jar.

Runtime: Java 11 (or any supported Java runtime ≥ 9)

Handler: `com.whiteblink.App::handleRequest`

Expose the function over HTTP using:

Lambda Function URL — enable it on the function, choose an auth type (NONE), and call the generated URL directly with a POST. 
Make sure binary / `multipart/form-data` payloads are delivered base64‑encoded (the handler base64‑decodes the request body).

## API contract

**Request** — `POST` as `multipart/form-data` with a single form field named
`file` whose content is the canvass report JSON. The Lambda base64‑decodes the
incoming body and reads the `file` part. The JSON is shaped like:

```json
{
  "serviceOrder": {
    "Name": "...",
    "Date_of_Loss__c": "yyyy-MM-dd",
    "First_Name__c": "...",
    "Last_Name__c": "...",
    "Claim_Number__c": "...",
    "Account__r": { "Name": "...", "Phone": "...", "Id": "..." },
    "Client__r": {
      "FirstName": "...", "LastName": "...", "Email": "...", "Phone": "...",
      "MailingAddress": {
        "street": "...", "city": "...", "stateCode": "...", "postalCode": "..."
      }
    }
  },
  "canvassRequest": {
    "Count_Hospital__c": 0,
    "Count_Pharmacy__c": 0,
    "Count_Doctor__c": 0,
    "Total_Treatments_Identified__c": 0
    /* …other Count_*__c specialty fields… */
  },
  "canvassLocations": {
    "<locationId>": {
      "Address_1__c": "...", "City__c": "...", "State__c": "...",
      "Zipcode__c": "...", "Address_Start_Date_Year__c": "...",
      "Geolocation__c": { "latitude": 0.0, "longitude": 0.0 }
    }
  },
  "canvassProviders": {
    "<providerId>": {
      "Facility_Name__c": "...", "Facility_Address__c": "...",
      "Facility_City__c": "...", "Facility_State__c": "...",
      "Facility_Zipcode__c": "...", "Facility_Type__c": "...",
      "Distance__c": 0.0, "Canvass_Completed_Date__c": "yyyy-MM-dd",
      "Total_Treatments_Identified__c": 0
    }
  },
  "canvassProviderTreatments": [
    {
      "Id": "...",
      "Canvass_Provider__c": "<providerId>",
      "Treatment_Date__c": "yyyy-MM-dd",
      "Treatment_Type_Text__c": "...",
      "Prescription_Name__c": "..."
    }
  ]
}
```

* `canvassLocations` and `canvassProviders` are **maps** keyed by record Id.
* `canvassProviderTreatments` is an **array**; each item links to a provider via
  `Canvass_Provider__c`.

**Response** — `200 OK` with `Content-Type: application/json` and a body of:

```json
{ "summary": "<html string with the categorized treatment sections>" }
```

The summary uses `<b>`, `<ul>`/`<li>` and `\n` for formatting, so it can be
dropped straight into an email body.

## Tech stack

* **Java 9** (compiled via `maven-compiler-plugin`, `source`/`target` = 9),
  deployable on the Lambda **Java 11** runtime
* **Maven** (fat JAR via `maven-shade-plugin` for deployment)
* **org.json** — JSON parsing
* **commons-fileupload** — multipart request parsing
* **AWS SDK** — `aws-lambda-java-core`, `aws-lambda-java-events`
* **Lombok** — model boilerplate
* **Unirest** — declared HTTP client dependency

## Local run

`App2.java` is a convenience runner for testing the rendering pipeline without
deploying:

1. Put a sample payload JSON file in the project root.
2. Update the input/output filenames in `App2.java` (currently
   `SO-202403-001730 (2) (1).json` → `SO-202403-001730 (2) (1)_mail_content.txt`).
3. Run `App2#main` from your IDE.
4. The generated HTML summary is written to the `*_mail_content.txt` file.

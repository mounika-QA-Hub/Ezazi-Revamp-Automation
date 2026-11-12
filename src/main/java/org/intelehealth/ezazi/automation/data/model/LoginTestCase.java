package org.intelehealth.ezazi.automation.data.model;

import java.util.ArrayList;
import java.util.List;

import org.intelehealth.ezazi.automation.data.TestCaseInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for one login validation case (JSON example). Field names match the JSON keys; unknown
 * keys are ignored so the data team can add columns without breaking the build.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginTestCase implements TestCaseInfo {

    @JsonProperty("TestCaseID")
    private String testCaseId;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Username")
    private String username = "";

    @JsonProperty("Password")
    private String password = "";

    /** Validation messages that MUST be shown; any other known message must NOT be shown. */
    @JsonProperty("ExpectedErrors")
    private List<String> expectedErrors = new ArrayList<>();

    @JsonProperty("Run")
    private String run = "Y";

    @Override
    public String testCaseId() {
        return testCaseId;
    }

    @Override
    public String description() {
        return description;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getExpectedErrors() {
        return expectedErrors;
    }

    public String getRun() {
        return run;
    }

    @Override
    public String toString() {
        return testCaseId + " - " + description;
    }
}

package com.whiteblink.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class GatewayResponse {
    private String body;
    private Integer statusCode;
    private Map<String, String> headers;
    private Boolean base64Encoded;

}
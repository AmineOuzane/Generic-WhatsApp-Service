package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sid.serviceapprobationwhatsapp.service.PayloadCreatorService;
import org.sid.serviceapprobationwhatsapp.service.OtpMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OtpMessageImpl implements OtpMessage {

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Value("${whatsapp.api.token}")
    private String whatsappApiToken;

    private final RestTemplate restTemplate;

    private final PayloadCreatorService payloadCreatorService;

    public OtpMessageImpl(PayloadCreatorService payloadCreatorService, RestTemplate restTemplate) {
        this.payloadCreatorService = payloadCreatorService;
        this.restTemplate = restTemplate;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + whatsappApiToken);
        return headers;
    }

    @Override
    public ResponseEntity<String> sendOtpMessage(String recipientNumber) {

        System.out.println("Retrieved recipient phone number info: " + recipientNumber);

        // Use PayloadCreatorService
        JSONObject requestBody = payloadCreatorService.createBaseRequestBody(recipientNumber);
        JSONObject template = payloadCreatorService.createTemplateObject("envoieotp");

        JSONArray components = new JSONArray();

        // Title Component
        JSONObject titleComponent = new JSONObject();
        titleComponent.put("type", "header");
        components.put(titleComponent);

        // Body Component
        JSONObject bodyComponent = new JSONObject();
        bodyComponent.put("type", "body");
        components.put(bodyComponent);

        template.put("components", components);
        requestBody.put("template", template);

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), createHeaders());
        return restTemplate.postForEntity(whatsappApiUrl, request, String.class);

    }

    @Override
    public ResponseEntity<String> resendOtpMessage(String recipientNumber, String mappingId) {

        System.out.println("Retrieved recipient phone number info: " + recipientNumber);

        // Use PayloadCreatorService
        JSONObject requestBody = payloadCreatorService.createBaseRequestBody(recipientNumber);
        JSONObject template = payloadCreatorService.createTemplateObject("resendit");

        JSONArray components = new JSONArray();

        // Title Component
        JSONObject titleComponent = new JSONObject(); // Create component objects
        titleComponent.put("type", "header");
        components.put(titleComponent);

        // Body Component
        JSONObject bodyComponent = new JSONObject(); // Create component objects
        bodyComponent.put("type", "body");
        components.put(bodyComponent);

        // "Resend OTP" button component
        JSONObject resendButtonComponent = new JSONObject();
        resendButtonComponent.put("type", "button");
        resendButtonComponent.put("sub_type", "quick_reply");
        resendButtonComponent.put("index", "0");
        JSONArray resendParameters = new JSONArray();
        JSONObject resendPayload = new JSONObject();
        resendPayload.put("type", "payload");
        resendPayload.put("payload", "RESEND_" + mappingId);
        resendParameters.put(resendPayload);
        resendButtonComponent.put("parameters", resendParameters);
        components.put(resendButtonComponent);
        template.put("components", components);
        requestBody.put("template", template);

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), createHeaders());
        return restTemplate.postForEntity(whatsappApiUrl, request, String.class);
    }

    @Override
    public ResponseEntity<String> sendTryAgain(String recipientNumber) {
        System.out.println("Retrieved recipient phone number info: " + recipientNumber);

        // Use PayloadCreatorService
        JSONObject requestBody = payloadCreatorService.createBaseRequestBody(recipientNumber);
        JSONObject template = payloadCreatorService.createTemplateObject("retry");

        JSONArray components = new JSONArray();

        // Title Component
        JSONObject titleComponent = new JSONObject();
        titleComponent.put("type", "header");
        components.put(titleComponent);

        // Body Component
        JSONObject bodyComponent = new JSONObject();
        bodyComponent.put("type", "body");
        components.put(bodyComponent);

        template.put("components", components);
        requestBody.put("template", template);

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), createHeaders());
        return restTemplate.postForEntity(whatsappApiUrl, request, String.class);
    }
}

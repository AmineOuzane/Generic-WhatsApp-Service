package org.sid.serviceapprobationwhatsapp.service;

import org.json.JSONObject;

import java.net.http.HttpHeaders;

public interface PayloadCreatorService {

    JSONObject createBaseRequestBody(String recipientNumber);
    JSONObject createTemplateObject(String templateName);
    JSONObject createTextParameter(String text);
    JSONObject createButton(String type, String id, String title);
}

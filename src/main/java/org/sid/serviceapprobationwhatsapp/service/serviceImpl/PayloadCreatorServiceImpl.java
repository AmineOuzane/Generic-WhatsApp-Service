package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import org.json.JSONObject;
import org.sid.serviceapprobationwhatsapp.service.PayloadCreatorService;
import org.springframework.stereotype.Service;

@Service
public class PayloadCreatorServiceImpl implements PayloadCreatorService {

    @Override
    public JSONObject createBaseRequestBody(String recipientNumber) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("messaging_product", "whatsapp");
        requestBody.put("to", recipientNumber);
        requestBody.put("type", "template");
        return requestBody;
    }

    @Override
    public JSONObject createTemplateObject(String templateName) {
        JSONObject template = new JSONObject();
        template.put("name", templateName);
        template.put("language", new JSONObject().put("code", "en"));
        return template;    }

    @Override
    public JSONObject createTextParameter(String text) {
        return new JSONObject().put("type", "text").put("text", text);
    }

    @Override
    public JSONObject createButton(String type, String id, String title) {
        JSONObject button = new JSONObject();
        button.put("type", type);

        JSONObject buttonDetails = new JSONObject();

        // Button type: "button"
        if ("button".equals(type)) {
            buttonDetails.put("title", title);
            buttonDetails.put("payload", id); // payload for regular buttons
        }
        // Button type: "button_reply"
        else if ("button_reply".equals(type)) {
            buttonDetails.put("id", id);
            buttonDetails.put("title", title); // id and title for reply buttons
        } else {
            throw new IllegalArgumentException("Unsupported button type: " + type);
        }

        button.put(type, buttonDetails); // Corrected: Use the type as the key
        return button;
    }
}

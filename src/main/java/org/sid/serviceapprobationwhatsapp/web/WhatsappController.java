package org.sid.serviceapprobationwhatsapp.web;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.service.WhatsAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {

    private final WhatsAppService whatsAppService;

    public WhatsappController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    @PostMapping("/sendMessageButtons")
    public ResponseEntity<String> sendMessageButtons(ApprovalRequest approvalRequest) {
        return whatsAppService.sendMessageWithInteractiveButtons(approvalRequest);
    }
}

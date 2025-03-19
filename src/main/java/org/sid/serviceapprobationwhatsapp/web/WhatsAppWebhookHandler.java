package org.sid.serviceapprobationwhatsapp.web;


import org.sid.serviceapprobationwhatsapp.entities.OtpResendMapping;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.repositories.OtpResendMappingRepository;
import org.sid.serviceapprobationwhatsapp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class WhatsAppWebhookHandler {

    private final OtpVerification otpVerification;
    private final OtpResendMappingRepository OtpResendMappingRepository;
    private final TwilioService twilioService;
    private final ApprovalService approvalService;
    private final MessageIdMappingService messageIdMappingService;

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);

    public WhatsAppWebhookHandler(ApprovalService approvalService, MessageIdMappingService messageIdMappingService, TwilioService twilioService, OtpResendMappingRepository OtpResendMappingRepository, WhatsAppService whatsAppService, RestTemplate restTemplate, ApprovalOtpRepository approvalOtpRepository, ApprovalRequestRepository approvalRequestRepository, OtpResendMappingRepository otpResendMappingRepository, OtpMessage otpMessage, OtpVerification otpVerification) {
        this.approvalService = approvalService;
        this.messageIdMappingService = messageIdMappingService;
        this.twilioService = twilioService;
        this.OtpResendMappingRepository = OtpResendMappingRepository;
        this.otpVerification = otpVerification;
    }

    public final Map<String, String> userCommentState = new ConcurrentHashMap<>();


    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        logger.info("Webhook received!");
        logger.debug("Full payload: {}", payload); // Log the full payload at DEBUG level

        Object entryObj = payload.get("entry");

        if (entryObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) entryObj;

            for (Map<String, Object> entry : entries) {
                Object changesObj = entry.get("changes");

                if (changesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> changes = (List<Map<String, Object>>) changesObj;

                    for (Map<String, Object> change : changes) {
                        Object valueObj = change.get("value");

                        if (valueObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> value = (Map<String, Object>) valueObj;
                            Object messagesObj = value.get("messages");

                            if (messagesObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesObj;

                                for (Map<String, Object> message : messages) {
                                    String messageType = (String) message.get("type");
                                    logger.debug("Message type: {}", messageType);

                                    // Handle Button Messages
                                    if ("button".equals(messageType)) {
                                        logger.info("Processing button message");

                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> button = (Map<String, Object>) message.get("button");
                                        if (button == null) {
                                            logger.warn("Button object is null");
                                        } else {
                                            String buttonPayload = (String) button.get("payload");
                                            String buttonText = (String) button.get("text");
                                            logger.info("Button clicked: {}, Payload: {}", buttonText, buttonPayload); // Logging user action

                                            // --- IDK ---
                                            // --- Resend OTP Logic ---
                                            Optional<OtpResendMapping> mappingOptional = OtpResendMappingRepository.findByMappingId(buttonPayload);
                                            if (mappingOptional.isPresent()) {
                                                OtpResendMapping mapping = mappingOptional.get();

                                                // Expiration case
                                                if (LocalDateTime.now().isAfter(mapping.getExpiration())) {
                                                    OtpResendMappingRepository.delete(mapping);
                                                    return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "Resend link expired."));
                                                }

                                                try {
                                                    twilioService.sendVerificationCode(mapping.getRecipientNumber());
                                                    OtpResendMappingRepository.delete(mapping); // Clean up after resend
                                                    return ResponseEntity.ok(Map.of("message", "OTP resent."));
                                                } catch (Exception e) {
                                                    logger.error("Failed to resend OTP to {}: {}", mapping.getRecipientNumber(), e.getMessage());
                                                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to resend OTP."));
                                                }
                                            }
                                            // --- End Resend OTP Logic ---

                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> context = (Map<String, Object>) message.get("context");
                                            String originalMessageId = context != null ? (String) context.get("id") : null;
                                            logger.debug("Original Message ID: {}", originalMessageId);

                                            messageIdMappingService.logAllMappings();

                                            String approvalId = messageIdMappingService.getApprovalId(originalMessageId);
                                            logger.debug("Approval ID retrieved: {}", approvalId);

                                            logger.debug("****** Click Button Part ***********");

                                            if (approvalId == null) {

                                                logger.warn("No request found for original message ID: {}", originalMessageId);
                                            } else {

                                                // Approval set to PENDING after sending the message and waiting for manager decision
                                                if (buttonPayload.startsWith("APPROVE_")) {
                                                    approvalService.updateStatus(approvalId, statut.Approuver);
                                                    logger.info("La Demande {} a été approuvée !", approvalId);
                                                } else if (buttonPayload.startsWith("REJECT_")) {
                                                    approvalService.updateStatus(approvalId, statut.Rejeter);
                                                    logger.info("La Demande {} a été rejetée !", approvalId);
                                                } else if (buttonPayload.startsWith("ATTENTE_")) {
                                                    approvalService.updateStatus(approvalId, statut.En_Attente);
                                                    logger.info("La Demande {} a été mise en attente !", approvalId);
                                                }

                                            }
                                        }
                                    }
                                    if ("text".equals(messageType)) {
                                        logger.info("Processing text message");

                                        Object textObj = message.get("text");
                                        if (!(textObj instanceof Map)) {
                                            return ResponseEntity.badRequest().body(Map.of("error", "Invalid text message format"));
                                        }
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> text = (Map<String, Object>) textObj;
                                        String messageBody = (String) text.get("body");
                                        if (messageBody == null || messageBody.trim().isEmpty()) {
                                            return ResponseEntity.badRequest().body(Map.of("error", "OTP is empty"));
                                        }
                                        messageBody = messageBody.trim();

                                        String phoneNumber = (String) message.get("from");
                                        if (phoneNumber == null) {
                                            return ResponseEntity.badRequest().body(Map.of("error", "Phone number is missing"));
                                        }
                                        if (!phoneNumber.startsWith("+")) {
                                            phoneNumber = "+" + phoneNumber;
                                        }
                                        logger.debug("Sender phone number: {}", phoneNumber);
                                        logger.debug("Received OTP: {}", messageBody);

                                        return otpVerification.processOtpVerification(phoneNumber, messageBody);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return ResponseEntity.ok(Collections.singletonMap("message", "Processed"));
        }
        // Add a default return statement to handle cases where 'entryObj' is not a List
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload format: Missing entry"));
    }
}
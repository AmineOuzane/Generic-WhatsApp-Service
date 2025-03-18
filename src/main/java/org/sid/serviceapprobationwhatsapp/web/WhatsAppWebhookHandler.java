package org.sid.serviceapprobationwhatsapp.web;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.entities.OtpResendMapping;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.repositories.OtpResendMappingRepository;
import org.sid.serviceapprobationwhatsapp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private final WhatsAppService whatsAppService;
    private final OtpResendMappingRepository OtpResendMappingRepository;
    private final TwilioService twilioService;
    private final ApprovalService approvalService;
    private final MessageIdMappingService messageIdMappingService;
    private final ApprovalOtpRepository approvalOtpRepository;
    private final OtpResendMappingRepository otpResendMappingRepository;
    private final OtpMessage otpMessage;

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);
    private final ApprovalRequestRepository approvalRequestRepository;

    public WhatsAppWebhookHandler(ApprovalService approvalService, MessageIdMappingService messageIdMappingService, TwilioService twilioService, OtpResendMappingRepository OtpResendMappingRepository, WhatsAppService whatsAppService, RestTemplate restTemplate, ApprovalOtpRepository approvalOtpRepository, ApprovalRequestRepository approvalRequestRepository, OtpResendMappingRepository otpResendMappingRepository, OtpMessage otpMessage) {
        this.approvalService = approvalService;
        this.messageIdMappingService = messageIdMappingService;
        this.twilioService = twilioService;
        this.OtpResendMappingRepository = OtpResendMappingRepository;
        this.whatsAppService = whatsAppService;
        // Inject RestTemplate
        this.approvalOtpRepository = approvalOtpRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.otpResendMappingRepository = otpResendMappingRepository;
        this.otpMessage = otpMessage;
    }

    public final Map<String, String> userCommentState = new ConcurrentHashMap<>();


    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        System.out.println("Webhook received!");
        System.out.println("Full payload: " + payload);

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
                                    System.out.println("Message type: " + messageType);

                                    // Handle Button Messages
                                    if ("button".equals(messageType)) {
                                        System.out.println("Processing button message");

                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> button = (Map<String, Object>) message.get("button");
                                        if (button == null) {
                                            System.out.println("Button object is null");
                                        } else {
                                            String buttonPayload = (String) button.get("payload");
                                            String buttonText = (String) button.get("text");
                                            System.out.println("\nButton clicked: " + buttonText + ", Payload: " + buttonPayload);

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
                                            System.out.println("Original Message ID: " + originalMessageId);

                                            messageIdMappingService.logAllMappings();

                                            String approvalId = messageIdMappingService.getApprovalId(originalMessageId);
                                            System.out.println("Approval ID retrieved: " + approvalId);

                                            System.out.println("****** Click Button Part ***********");

                                                if (approvalId == null) {

                                                    System.out.println("No request found for original message ID: " + originalMessageId);
                                                } else {

                                                    // Approval set to PENDING after sending the message and waiting for manager decision
                                                    if (buttonPayload.startsWith("APPROVE_")) {
                                                        approvalService.updateStatus(approvalId, statut.Approuver);
                                                        System.out.println("La Demande " + approvalId + " a été approuvée !");
                                                    } else if (buttonPayload.startsWith("REJECT_")) {
                                                        approvalService.updateStatus(approvalId, statut.Rejeter);
                                                        System.out.println("La Demande " + approvalId + " a été rejetée !");
                                                    } else if (buttonPayload.startsWith("ATTENTE_")) {
                                                        approvalService.updateStatus(approvalId, statut.En_Attente);
                                                        System.out.println("La Demande " + approvalId + " a été mise en attente !");
                                                    }

                                                }
                                        }
                                    }
                                    if ("text".equals(messageType)) {
                                        System.out.println("Processing text message");

                                        Object textObj = message.get("text");
                                        if (textObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> text = (Map<String, Object>) textObj;
                                            String messageBody = (String) text.get("body");

                                            if (messageBody != null) {
                                                messageBody = messageBody.trim();
                                                String phoneNumber = (String) message.get("from");
                                                if (!phoneNumber.startsWith("+")) {
                                                    phoneNumber = "+" + phoneNumber;
                                                }
                                                System.out.println("Sender phone number: " + phoneNumber);
                                                System.out.println("Received OTP: " + messageBody);

                                                Optional<ApprovalOTP> optionalOtpAttempt = approvalOtpRepository
                                                        .findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(phoneNumber, otpStatut.PENDING);
                                                if (optionalOtpAttempt.isEmpty()) {
                                                    System.out.println("OTP invalid");
                                                    return ResponseEntity.badRequest().body(Map.of("error", "OTP invalid"));
                                                }

                                                ApprovalOTP otpAttempt = optionalOtpAttempt.get();
                                                if (otpAttempt.getApprovalRequest() == null) {
                                                    System.out.println("The Approval Request is missing for this OTP.");
                                                    return ResponseEntity.badRequest().body(Map.of("error", "Approval Request is missing for OTP"));
                                                }

                                                ApprovalRequest approvalRequest = otpAttempt.getApprovalRequest();

                                                System.out.println("Processing approval request ID: " + approvalRequest.getId());
                                                System.out.println("code fixing null point exception");

                                                if (otpAttempt.getApprovalRequest() != null) {
                                                    String approvalId = otpAttempt.getApprovalRequest().getId();
                                                    System.out.println("Processing approval request ID: " + approvalId);
                                                    Optional<ApprovalRequest> approvalRequestOptional = approvalRequestRepository.findById(approvalId);
                                                    if (approvalRequestOptional.isPresent()) {
                                                        approvalRequestOptional.get();

                                                        boolean isValid = twilioService.checkVerificationCode(phoneNumber, messageBody, otpAttempt.getVerificationSid());
                                                        if (isValid) {
                                                            otpAttempt.setStatus(otpStatut.APPROVED);
                                                            approvalOtpRepository.save(otpAttempt);
                                                            System.out.println("OTP success");

                                                            whatsAppService.sendMessageWithInteractiveButtons(
                                                                   approvalRequest
                                                            );
                                                        }
//                                                        } else {
//                                                            otpAttempt.setStatus(otpStatut.DENIED);
//                                                            approvalOtpRepository.save(otpAttempt);
//
//                                                            String mappingId = UUID.randomUUID().toString();
//                                                            OtpResendMapping mapping = OtpResendMapping.builder()
//                                                                    .mappingId(mappingId)
//                                                                    .approvalId(String.valueOf(approvalId))
//                                                                    .recipientNumber(phoneNumber)
//                                                                    .expiration(LocalDateTime.now().plusMinutes(5))
//                                                                    .build();
//                                                            otpResendMappingRepository.save(mapping);
//
//                                                            ResponseEntity<String> response = otpMessage.resendOtpMessage(phoneNumber, mappingId);
//                                                            if (response.getStatusCode().is2xxSuccessful()) {
//                                                                System.out.println("OTP invalid, check WhatsApp");
//                                                            } else {
//                                                                System.out.println("Failed to send WhatsApp message. Status: " + response.getStatusCode());
//                                                            }
//                                                        }
                                                    } else {
                                                        System.out.println("Invalid approval ID");
                                                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid approval ID"));
                                                    }
                                                } else {
                                                    System.out.println("The Approval Request Was Null");
                                                    return ResponseEntity.badRequest().body(Map.of("error", "Approval Request is missing for OTP"));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return ResponseEntity.ok(Collections.singletonMap("message", "Processed"));
        }
        return ResponseEntity.badRequest().body(Collections.singletonMap("error", "No valid message received"));
    }
}
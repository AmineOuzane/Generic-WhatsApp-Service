package org.sid.serviceapprobationwhatsapp.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.sid.serviceapprobationwhatsapp.dto.ApprovalRequestDTO;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.entities.VerificationRequest;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.ApprovalService;
import org.sid.serviceapprobationwhatsapp.service.OtpMessage;
import org.sid.serviceapprobationwhatsapp.service.TwilioService;
import org.sid.serviceapprobationwhatsapp.service.WhatsAppService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final OtpMessage otpMessage;
    private final TwilioService twilioService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;
    private final WhatsAppService whatsAppService;
    private final ApprovalOtpRepository approvalOtpRepository;

    public ApprovalController(ApprovalRequestRepository approvalRequestRepository,
                              WhatsAppService whatsAppService,
                              ObjectMapper objectMapper,
                              TwilioService twilioService,
                              OtpMessage otpMessage,
                              ApprovalService approvalService,
                              ApprovalOtpRepository approvalOtpRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.objectMapper = objectMapper;
        this.twilioService = twilioService;
        this.otpMessage = otpMessage;
        this.approvalService = approvalService;
        this.whatsAppService = whatsAppService;
        this.approvalOtpRepository = approvalOtpRepository;
    }

    @Async
    @PostMapping("/register")
    public CompletableFuture<ResponseEntity<?>> registerApprovalRequest(@Valid @RequestBody ApprovalRequestDTO approvalRequestDTO) {
        log.info("Received request to register a new approval: {}", approvalRequestDTO);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String dataJson = objectMapper.writeValueAsString(approvalRequestDTO.getApprovalData());
                String metadataJson = objectMapper.writeValueAsString(approvalRequestDTO.getMetadata());

                ApprovalRequest approvalRequest = ApprovalRequest.builder()
                        .objectType(approvalRequestDTO.getObjectType())
                        .objectId(approvalRequestDTO.getObjectId())
                        .data(dataJson)
                        .origin(approvalRequestDTO.getOrigin())
                        .approvers(approvalRequestDTO.getApprovers())
                        .demandeur(approvalRequestDTO.getDemandeur())
                        .commentaire("")
                        .callbackUrl(approvalRequestDTO.getCallbackUrl())
                        .metadata(metadataJson)
                        .decision(statut.Pending)
                        .requestTimeStamp(LocalDateTime.now())
                        .build();

                ApprovalRequest savedApprovalRequest = approvalRequestRepository.save(approvalRequest);

                for (String approverPhoneNumber : approvalRequestDTO.getApprovers()) {
                    otpMessage.sendOtpMessage(approverPhoneNumber);
                    approvalService.sendOtpAndCreateApprovalOTP(savedApprovalRequest, approverPhoneNumber);
                }

                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("approvalId", savedApprovalRequest.getId(), "message", "Approval request registered. Verification codes sent."));

            } catch (JsonProcessingException e) {
                log.error("Error serializing data or metadata to JSON", e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid data format."));
            } catch (OptimisticLockingFailureException e) {
                log.error("Optimistic locking failure", e);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "The approval request was modified by another user. Please try again."));
            } catch (Exception e) {
                log.error("An unexpected error occurred", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
            }
        });
    }

    @PostMapping("/verify/{approvalId}/{phoneNumber}")
    public ResponseEntity<?> verifyCode(@Valid @RequestBody VerificationRequest request,
                                        @PathVariable String approvalId,
                                        @PathVariable String phoneNumber) {
        try {
            Optional<ApprovalRequest> approvalRequestOptional = approvalRequestRepository.findById(approvalId);
            if (approvalRequestOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Approval not found, Invalid approval ID."));
            }

            ApprovalRequest approvalRequest = approvalRequestOptional.get();

            if (!approvalRequest.getApprovers().contains(phoneNumber)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid phone number for this approval request."));
            }

            Optional<ApprovalOTP> optionalOtpAttempt = approvalOtpRepository.findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(phoneNumber, otpStatut.PENDING);

            if (optionalOtpAttempt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "Invalid action. Access is expired."));
            }

            ApprovalOTP otpAttempt = optionalOtpAttempt.get();

            if (LocalDateTime.now().isAfter(otpAttempt.getExpiration())) {
                otpAttempt.setStatus(otpStatut.EXPIRED);
                approvalOtpRepository.save(otpAttempt);
                otpMessage.resendOtpMessage(phoneNumber,approvalId);
                return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "OTP has expired"));
            }

            boolean isValid = twilioService.checkVerificationCode(phoneNumber, request.getCode(), otpAttempt.getVerificationSid());
            if (isValid) {
                otpAttempt.setStatus(otpStatut.APPROVED);
                approvalOtpRepository.save(otpAttempt);
                whatsAppService.sendMessageWithInteractiveButtons(approvalRequest);

                return ResponseEntity.ok(Map.of("message", "Verification successful. Approval request sent."));

            } else {
                // OTP is invalid, increment invalid attempts
                otpAttempt.setInvalidattempts(otpAttempt.getInvalidattempts() + 1);
                approvalOtpRepository.save(otpAttempt); // Save the updated invalid attempt count

                if (otpAttempt.getInvalidattempts() >= 3) {
                    otpAttempt.setStatus(otpStatut.DENIED);
                    approvalOtpRepository.save(otpAttempt);
                    otpMessage.resendOtpMessage(phoneNumber,approvalId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You have exceeded the maximum OTP attempts"));
                }

                return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP"));
            }
        } catch (Exception e) {
            log.error("An unexpected error occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }
}
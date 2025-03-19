package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.service.OtpMessage;
import org.sid.serviceapprobationwhatsapp.service.OtpVerification;
import org.sid.serviceapprobationwhatsapp.service.TwilioService;
import org.sid.serviceapprobationwhatsapp.service.WhatsAppService;
import org.sid.serviceapprobationwhatsapp.web.WhatsAppWebhookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class OtpVerificationImpl implements OtpVerification {

    private final WhatsAppService whatsAppService;
    private final TwilioService twilioService;
    private final ApprovalOtpRepository approvalOtpRepository;
    private final OtpMessage otpMessage;

    public OtpVerificationImpl(OtpMessage otpMessage, ApprovalOtpRepository approvalOtpRepository, TwilioService twilioService, WhatsAppService whatsAppService) {
        this.otpMessage = otpMessage;
        this.approvalOtpRepository = approvalOtpRepository;
        this.twilioService = twilioService;
        this.whatsAppService = whatsAppService;
    }

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);

    @Override
    public ResponseEntity<?> processOtpVerification(String phoneNumber, String messageBody) {

        Optional<ApprovalOTP> optionalOtpAttempt = approvalOtpRepository
                .findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(phoneNumber, otpStatut.PENDING);

        if (optionalOtpAttempt.isEmpty()) {
            logger.warn("No pending OTP attempt found for phone: {}", phoneNumber);
            return ResponseEntity.badRequest().body(Map.of("error", "OTP introuvable"));
        }

        ApprovalOTP otpAttempt = optionalOtpAttempt.get();

        // Idempotency check: if the OTP attempt status is not PENDING, then it has already been processed.
        if (otpAttempt.getStatus() != otpStatut.PENDING) {
            logger.info("OTP attempt {} already processed with status {}", otpAttempt.getApprovalRequest().getId(), otpAttempt.getStatus());
            return ResponseEntity.ok(Map.of("message", "OTP already processed"));
        }


        ApprovalRequest approvalRequest = otpAttempt.getApprovalRequest();
        if (approvalRequest == null) {
            logger.warn("Approval Request is missing for OTP attempt for phone: {}", phoneNumber);
            return ResponseEntity.badRequest().body(Map.of("error", "Approval Request is missing for OTP"));


        }

        String approvalId = approvalRequest.getId();
        logger.info("Processing approval request ID: {}", approvalId);

        if (!approvalRequest.getApprovers().contains(phoneNumber)) {
            logger.warn("Invalid phone number {} for approval request {}", phoneNumber, approvalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid phone number for this approval request."));
        }

        // Check expiration BEFORE verifying
        if (LocalDateTime.now().isAfter(otpAttempt.getExpiration())) {
            otpAttempt.setStatus(otpStatut.EXPIRED);
            approvalOtpRepository.save(otpAttempt);
            otpMessage.resendOtpMessage(phoneNumber, approvalRequest.getId());
            logger.info("OTP expired for approval ID {}", approvalRequest.getId());

            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "OTP has expired"));
        }

        boolean isValid = twilioService.checkVerificationCode(phoneNumber, messageBody, otpAttempt.getVerificationSid());

        if (isValid) {
            otpAttempt.setStatus(otpStatut.APPROVED);
            approvalOtpRepository.save(otpAttempt);
            logger.info("OTP verified successfully for approval ID {}", approvalId);
            whatsAppService.sendMessageWithInteractiveButtons(approvalRequest);

            return ResponseEntity.ok(Map.of("message", "OTP verified and approval request sent"));
        }
        else {
            otpAttempt.setInvalidattempts(otpAttempt.getInvalidattempts() + 1);
            approvalOtpRepository.save(otpAttempt);

            logger.warn("Invalid OTP attempt {} for approval ID {}", otpAttempt.getInvalidattempts(), approvalId);

            if (otpAttempt.getInvalidattempts() > 3 ) {
                otpAttempt.setStatus(otpStatut.DENIED);
                approvalOtpRepository.save(otpAttempt);
                logger.error("Exceeded maximum OTP attempts for approval ID {}", approvalId);
                otpMessage.resendOtpMessage(phoneNumber, approvalId);

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You have exceeded the maximum OTP attempts"));
            }

            approvalOtpRepository.save(otpAttempt);
            otpMessage.sendTryAgain(phoneNumber);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP. Please try again."));

        }
    }
}

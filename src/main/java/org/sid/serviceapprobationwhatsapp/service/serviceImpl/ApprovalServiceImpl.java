package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import jakarta.persistence.EntityNotFoundException;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.ApprovalService;
import org.sid.serviceapprobationwhatsapp.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final TwilioService twilioService;
    private final ApprovalOtpRepository approvalOtpRepository;

    public ApprovalServiceImpl(ApprovalRequestRepository approvalRequestRepository, TwilioService twilioService, ApprovalOtpRepository approvalOtpRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.twilioService = twilioService;
        this.approvalOtpRepository = approvalOtpRepository;
    }

    private static final Logger logger = LoggerFactory.getLogger(ApprovalServiceImpl.class);

    @Override
    public void updateStatus(String id, statut decision) {
        ApprovalRequest approvalRequest = approvalRequestRepository.findById(id).orElse(null);
        if (approvalRequest != null) {
            approvalRequest.setDecision(decision);
            approvalRequestRepository.save(approvalRequest);
        }
    }

    @Override
    public void sendOtpAndCreateApprovalOTP(ApprovalRequest approvalRequest, String phoneNumber) {

            Optional<ApprovalOTP> existingOtp = approvalOtpRepository
                    .findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(phoneNumber, otpStatut.PENDING);

            if (existingOtp.isPresent()) {
                logger.warn("Pending OTP already exists for phone {}. Not creating a new one.", phoneNumber);

            }
            try {
                String verificationSid = twilioService.sendVerificationCode(phoneNumber);
                logger.info("OTP sent successfully for phone {}", phoneNumber);

                ApprovalOTP otp = ApprovalOTP.builder()
                        .approvalRequest(approvalRequest)
                        .recipientNumber(phoneNumber)
                        .verificationSid(verificationSid)
                        .status(otpStatut.PENDING)
                        .createdAt(LocalDateTime.now())
                        .invalidattempts(0)
                        .expiration(LocalDateTime.now().plusMinutes(5))
                        .build();

                approvalOtpRepository.save(otp);
                logger.info("ApprovalOTP created successfully for phone {}", phoneNumber);
            } catch (Exception e) {
                logger.error("Error sending OTP: {}", e.getMessage());
                throw new RuntimeException("Error sending OTP", e);
            }
    }

    @Override
    public ApprovalRequest saveApprovalRequest(ApprovalRequest approvalRequest) {
        try {
            return approvalRequestRepository.save(approvalRequest);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Log and retry logic
            logger.info("Optimistic locking failure, retrying...");
            return approvalRequestRepository.findById(approvalRequest.getId())
                    .orElseThrow(() -> new EntityNotFoundException("ApprovalRequest not found"));
        }
    }

    @Override
    public ApprovalRequest getApproval(String approvalId) {
        return approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid approval ID: " + approvalId));
    }

    @Override
    public void updateComment(String approvalId, String comment) {
        ApprovalRequest approval = getApproval(approvalId);
        approval.setCommentaire(comment);
        approvalRequestRepository.save(approval);
    }

    @Override
    public ApprovalRequest findPendingCommentApproval(String phoneNumber, List<statut> status) {
        List<ApprovalRequest> list = approvalRequestRepository.findByApproverPhoneNumberAndDecisionIn(phoneNumber, status);

        // Filter to keep only the rejected and pending approved requests
        List<ApprovalRequest> pendingOrRejectedList = list.stream()
                .filter(approvalRequest -> approvalRequest.getDecision() == statut.Pending || approvalRequest.getDecision() == statut.Rejeter)
                .toList();

        if (pendingOrRejectedList.isEmpty()) {
            return null;
        }

        return pendingOrRejectedList.get(0);
    }
}
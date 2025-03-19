package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.twilio.example.ValidationExample.ACCOUNT_SID;
import static com.twilio.example.ValidationExample.AUTH_TOKEN;

@Service
public class TwilioServiceImpl implements TwilioService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.verify.service.sid}")
    private String verifyServiceSid;

    private final ApprovalOtpRepository approvalOtpRepository;

    Logger logger = LoggerFactory.getLogger(TwilioServiceImpl.class);

    public TwilioServiceImpl(ApprovalOtpRepository approvalOtpRepository) {
        this.approvalOtpRepository = approvalOtpRepository;
    }

    // Initialize Twilio de manière global pour la class
    @PostConstruct // Use @PostConstruct for initialization
    public void init() {
        try {
            Twilio.init(accountSid, authToken);
            logger.info("Twilio client initialized successfully.");
        } catch (Exception e) {
            logger.error("Error initializing Twilio client: {}", e.getMessage());
            throw new RuntimeException("Error initializing Twilio client", e);
        }
    }

    @Override
    public String sendVerificationCode(String phoneNumber) {
        // Input validation: Check for null, empty, and "+" prefix.
        if (phoneNumber == null || phoneNumber.trim().isEmpty() || !phoneNumber.startsWith("+")) {
            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        }

        // Expire any previous pending OTPs before sending a new one
        approvalOtpRepository.updateStatusByPhoneNumber(phoneNumber, otpStatut.PENDING, otpStatut.EXPIRED);


        try {
            Verification verification = Verification.creator(
                            verifyServiceSid,
                            phoneNumber,
                            "sms")            // Verification channel (SMS)
                    .create();


            System.out.println("Verification SID: " + verification.getSid()); // Log the SID
            return verification.getSid(); // Track and refer to that particular verification

        } catch (ApiException e) {

            logger.error("Error sending verification code for phone number {}: {}", phoneNumber, e.getMessage(), e);
            // If you don't rethrow, the calling method won't know that sending the verification code failed.
            throw e;
        }
    }

    @Override
    public boolean checkVerificationCode(String phoneNumber, String code, String verificationSid) { // ADDED verificationSid
        // Input validation
        if (phoneNumber == null || phoneNumber.trim().isEmpty() || !phoneNumber.startsWith("+")) {
            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification code cannot be empty");
        }
        if (verificationSid == null || verificationSid.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification SID cannot be empty");
        }

        try {
            VerificationCheck verificationCheck = VerificationCheck.creator(verifyServiceSid)
                    .setTo(phoneNumber)
                    .setCode(code)
                    .create();

            logger.info("Twilio Verification Response: SID={}, Status={}",
                    verificationCheck.getSid(), verificationCheck.getStatus());

            return "approved".equals(verificationCheck.getStatus());

        } catch (ApiException e) {
            System.err.println("Error checking verification code: " + e.getMessage());
            return false; // Indicate failure instead of throwing an exception
        }
    }
}

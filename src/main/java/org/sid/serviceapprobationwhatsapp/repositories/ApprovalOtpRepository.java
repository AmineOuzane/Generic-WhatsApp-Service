package org.sid.serviceapprobationwhatsapp.repositories;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalOtpRepository extends JpaRepository<ApprovalOTP, Long> {

    // Custom query method to find the most recent pending OTP for a given phone number
    Optional<ApprovalOTP> findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, otpStatut status);
}

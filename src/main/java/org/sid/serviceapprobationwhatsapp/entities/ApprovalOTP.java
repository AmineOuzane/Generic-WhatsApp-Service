package org.sid.serviceapprobationwhatsapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_otp")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalOTP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otpId;

    @Column(name = "phone_number", nullable = false)
    private String recipientNumber;

    @Column(name = "verification_sid", nullable = false)
    private String verificationSid; // From Twilio Verify

    @Column(name = "status", nullable = false)
    private otpStatut status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expiration")
    private LocalDateTime expiration;


    @ManyToOne(fetch = FetchType.EAGER) // Use FetchType.LAZY for performance
    @JoinColumn(name = "request_id", referencedColumnName = "id")
    private ApprovalRequest approvalRequest;

//    @Column(name = "whatsapp_message_id")
//    private String whatsappMessageId; // Optional
}
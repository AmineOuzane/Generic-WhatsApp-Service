package org.sid.serviceapprobationwhatsapp.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OtpResendMapping {
    @Id
    private String mappingId;
    private String approvalId;
    private String recipientNumber;
    private LocalDateTime expiration;
}

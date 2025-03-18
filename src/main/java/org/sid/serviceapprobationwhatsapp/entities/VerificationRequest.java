package org.sid.serviceapprobationwhatsapp.entities;

import lombok.Data;

@Data
public class VerificationRequest {
        public String phoneNumber;
        public String code;
}

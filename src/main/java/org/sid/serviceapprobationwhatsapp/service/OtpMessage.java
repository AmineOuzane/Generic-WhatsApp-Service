package org.sid.serviceapprobationwhatsapp.service;

import org.springframework.http.ResponseEntity;

public interface OtpMessage {

    ResponseEntity<String> sendOtpMessage(String recipientNumber);
    ResponseEntity<String> resendOtpMessage(String recipientNumber, String mappingId);
}

package org.sid.serviceapprobationwhatsapp.repositories;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String> {

    @Query("SELECT ar FROM ApprovalRequest ar WHERE :phoneNumber MEMBER OF ar.approvers AND ar.decision IN :statuses")
    List<ApprovalRequest> findByApproverPhoneNumberAndDecisionIn(@Param("phoneNumber") String phoneNumber, @Param("statuses") List<statut> status);

    @Query("SELECT ar FROM ApprovalRequest ar WHERE :approver MEMBER OF ar.approvers")
    List<ApprovalRequest> findByApproversContaining(@Param("approver") String approver);
}

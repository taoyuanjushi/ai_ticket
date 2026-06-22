package com.example.hello_demo.service;

import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketStatusTransitionPolicyTest {

    private final TicketStatusTransitionPolicy policy = new TicketStatusTransitionPolicy();

    @Test
    void allowedTransitionsMatchBusinessRules() {
        assertTrue(policy.canTransit(TicketStatus.OPEN, TicketStatus.PROCESSING));
        assertTrue(policy.canTransit(TicketStatus.OPEN, TicketStatus.CLOSED));
        assertTrue(policy.canTransit(TicketStatus.PROCESSING, TicketStatus.CLOSED));
    }

    @Test
    void sameStatusIsAllowedAsNoop() {
        assertTrue(policy.canTransit(TicketStatus.OPEN, TicketStatus.OPEN));
        assertTrue(policy.canTransit(TicketStatus.PROCESSING, TicketStatus.PROCESSING));
        assertTrue(policy.canTransit(TicketStatus.CLOSED, TicketStatus.CLOSED));
    }

    @Test
    void invalidTransitionsAreRejected() {
        assertFalse(policy.canTransit(TicketStatus.PROCESSING, TicketStatus.OPEN));
        assertFalse(policy.canTransit(TicketStatus.CLOSED, TicketStatus.OPEN));
        assertFalse(policy.canTransit(TicketStatus.CLOSED, TicketStatus.PROCESSING));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy.validateTransition(TicketStatus.CLOSED, TicketStatus.OPEN)
        );
        assertEquals(400, exception.getCode());
        assertEquals("工单状态不允许从 CLOSED 修改为 OPEN", exception.getMessage());
    }

    @Test
    void normalizeAcceptsLowerCaseAndRejectsUnknownStatus() {
        assertEquals(TicketStatus.PROCESSING, policy.normalize(" processing "));
        assertDoesNotThrow(() -> policy.validateTransition("open", "closed"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy.normalize("done")
        );
        assertEquals(400, exception.getCode());
        assertEquals("工单状态不合法", exception.getMessage());
    }
}

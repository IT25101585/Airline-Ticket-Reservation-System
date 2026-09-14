package com.skylanka.ticketmanagement.unit;

import com.skylanka.ticketmanagement.repository.TicketRepository;
import com.skylanka.ticketmanagement.service.TicketNumberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketNumberService Unit Tests")
class TicketNumberServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketNumberService ticketNumberService;

    @Test
    @DisplayName("Generated number starts with SKL- prefix")
    void generatedNumberStartsWithSKL() {
        when(ticketRepository.existsByTicketNumber(any())).thenReturn(false);
        String number = ticketNumberService.generateUniqueTicketNumber();
        assertThat(number).startsWith("SKL-");
    }

    @Test
    @DisplayName("Generated number has correct format SKL-YYYYMMDD-NNNNNN")
    void generatedNumberHasCorrectFormat() {
        when(ticketRepository.existsByTicketNumber(any())).thenReturn(false);
        String number = ticketNumberService.generateUniqueTicketNumber();
        // e.g. SKL-20260914-123456 = 3 + 1 + 8 + 1 + 6 = 19 chars
        assertThat(number).matches("SKL-\\d{8}-\\d{6}");
    }

    @Test
    @DisplayName("Retries on collision and returns unique number")
    void retriesOnCollision() {
        when(ticketRepository.existsByTicketNumber(any()))
                .thenReturn(true)   // first attempt: collision
                .thenReturn(true)   // second attempt: collision
                .thenReturn(false); // third attempt: unique

        String number = ticketNumberService.generateUniqueTicketNumber();
        assertThat(number).isNotNull();
        verify(ticketRepository, times(3)).existsByTicketNumber(any());
    }

    @Test
    @DisplayName("Throws when max retries exceeded")
    void throwsWhenMaxRetriesExceeded() {
        when(ticketRepository.existsByTicketNumber(any())).thenReturn(true); // always collides
        assertThatThrownBy(() -> ticketNumberService.generateUniqueTicketNumber())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique ticket number");
    }
}

package com.skylanka.ticketmanagement.unit;

import com.skylanka.ticketmanagement.repository.TicketRepository;
import com.skylanka.ticketmanagement.service.QRCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QRCodeService Unit Tests")
class QRCodeServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private QRCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qrCodeService, "qrValidateBaseUrl",
                "http://localhost:8080/api/tickets/validate/");
    }

    @Test
    @DisplayName("Generated token is not null or blank")
    void generatedTokenIsNotBlank() {
        when(ticketRepository.existsByQrCodeToken(any())).thenReturn(false);
        String token = qrCodeService.generateUniqueToken();
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("Generated token has sufficient length (>=43 chars for 32-byte base64url)")
    void generatedTokenHasSufficientLength() {
        when(ticketRepository.existsByQrCodeToken(any())).thenReturn(false);
        String token = qrCodeService.generateUniqueToken();
        assertThat(token.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("Two generated tokens are different (high entropy)")
    void twoTokensAreDifferent() {
        when(ticketRepository.existsByQrCodeToken(any())).thenReturn(false);
        String token1 = qrCodeService.generateUniqueToken();
        String token2 = qrCodeService.generateUniqueToken();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("QR code Base64 data is generated successfully")
    void qrCodeBase64IsGenerated() {
        when(ticketRepository.existsByQrCodeToken(any())).thenReturn(false);
        String token  = qrCodeService.generateUniqueToken();
        String base64 = qrCodeService.generateQRCodeBase64(token);
        assertThat(base64).isNotBlank();
        // Base64 chars only
        assertThat(base64).matches("[A-Za-z0-9+/=]+");
    }
}

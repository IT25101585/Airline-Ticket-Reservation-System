package com.skylanka.ticketmanagement.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.skylanka.ticketmanagement.exception.AppException;
import com.skylanka.ticketmanagement.exception.ErrorCode;
import com.skylanka.ticketmanagement.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Service responsible for QR code token generation and QR image creation.
 *
 * Security principles:
 *   - Tokens are 32 cryptographically random bytes (256-bit entropy).
 *   - Tokens do NOT contain any PII or payment data.
 *   - Tokens are checked for DB uniqueness before use.
 *   - Voided/reissued tickets have their tokens invalidated by status check.
 */
@Service
public class QRCodeService {

    private static final int    TOKEN_BYTES  = 32;   // 256-bit entropy
    private static final int    MAX_RETRIES  = 5;
    private static final int    QR_SIZE      = 300;  // pixels

    @Value("${app.ticket.qr-validate-url}")
    private String qrValidateBaseUrl;

    private final SecureRandom     secureRandom = new SecureRandom();
    private final TicketRepository ticketRepository;

    public QRCodeService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Generate a unique, cryptographically secure QR token.
     */
    public String generateUniqueToken() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            byte[] bytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            if (!ticketRepository.existsByQrCodeToken(token)) {
                return token;
            }
        }
        throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.QR_GENERATION_FAILED,
                "Failed to generate a unique QR token after " + MAX_RETRIES + " attempts.");
    }

    /**
     * Generate the QR code image (Base64-encoded PNG) for the given token.
     * The QR content is the validation URL — no sensitive data embedded.
     *
     * @param token the secure token to embed in the QR code
     * @return Base64-encoded PNG string
     */
    public String generateQRCodeBase64(String token) {
        String qrContent = qrValidateBaseUrl + token;

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (WriterException | IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.QR_GENERATION_FAILED,
                    "QR code generation failed: " + e.getMessage());
        }
    }

    /**
     * Decode Base64 QR data to raw bytes (used by PDF generation).
     */
    public byte[] decodeQRCodeData(String base64Data) {
        return Base64.getDecoder().decode(base64Data);
    }
}

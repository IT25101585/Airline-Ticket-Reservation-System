package com.skylanka.air.shared.pdf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeTest {

    @Test
    void ticketValidationCodeFitsVersion4Matrix() {
        boolean[][] m = QrCode.encode("SKY-1A2B3C4D|42|TKT-9F8E7D6C5B4A");
        assertEquals(33, m.length, "a 32-byte payload needs version 4 (33x33) at error-correction level M");
        assertEquals(m.length, m[0].length);
    }

    @Test
    void finderPatternsArePresentInThreeCorners() {
        boolean[][] m = QrCode.encode("hello");
        int n = m.length;
        for (int[] origin : new int[][]{{0, 0}, {0, n - 7}, {n - 7, 0}}) {
            int r = origin[0], c = origin[1];
            for (int i = 0; i < 7; i++) {
                assertTrue(m[r][c + i], "top edge of finder");
                assertTrue(m[r + 6][c + i], "bottom edge of finder");
                assertTrue(m[r + i][c], "left edge of finder");
                assertTrue(m[r + i][c + 6], "right edge of finder");
            }
            assertTrue(m[r + 3][c + 3], "finder centre");
            assertFalse(m[r + 1][c + 1], "finder inner ring");
        }
    }

    @Test
    void sameTextGivesSameCodeAndDifferentTextDiffers() {
        assertArrayEquals(QrCode.encode("A"), QrCode.encode("A"));
        assertFalse(java.util.Arrays.deepEquals(QrCode.encode("A"), QrCode.encode("B")));
    }

    @Test
    void oversizedTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QrCode.encode("x".repeat(400)));
    }
}

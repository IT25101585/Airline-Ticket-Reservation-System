package com.skylanka.air.shared.pdf;

import java.nio.charset.StandardCharsets;

/**
 * Minimal self-contained QR Code (Model 2) encoder: byte mode, error-correction level M,
 * versions 1-10 (up to ~213 bytes of UTF-8 text). It exists so e-tickets can carry a scannable
 * QR code without adding a third-party dependency.
 */
public final class QrCode {

    // Per-version tables for error correction level M, index = version (0 unused).
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5};
    private static final int MAX_VERSION = 10;

    private final int version;
    private final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    private QrCode(int version, byte[] dataCodewords) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        byte[] all = addEccAndInterleave(dataCodewords);
        drawCodewords(all);

        int bestMask = 0;
        int minPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormatBits(m);
            int p = penaltyScore();
            if (p < minPenalty) {
                minPenalty = p;
                bestMask = m;
            }
            applyMask(m); // undo (XOR)
        }
        applyMask(bestMask);
        drawFormatBits(bestMask);
    }

    /** Encodes the text and returns the module matrix, indexed [row][column]; true = dark. */
    public static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int v = 1; v <= MAX_VERSION; v++) {
            int capacityBytes = dataCodewords(v);
            int countBits = v < 10 ? 8 : 16;
            int neededBits = 4 + countBits + data.length * 8;
            if (neededBits <= capacityBytes * 8) {
                QrCode qr = new QrCode(v, buildDataCodewords(data, v, capacityBytes));
                return qr.modules;
            }
        }
        throw new IllegalArgumentException("Text is too long for the QR code (max ~200 bytes).");
    }

    // ---------------------------------------------------------------- data

    private static int rawModules(int v) {
        int result = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int numAlign = v / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (v >= 7) result -= 36;
        }
        return result;
    }

    private static int dataCodewords(int v) {
        return rawModules(v) / 8 - ECC_PER_BLOCK[v] * NUM_BLOCKS[v];
    }

    private static byte[] buildDataCodewords(byte[] data, int v, int capacityBytes) {
        BitBuffer bb = new BitBuffer();
        bb.append(0b0100, 4); // byte mode
        bb.append(data.length, v < 10 ? 8 : 16);
        for (byte b : data) bb.append(b & 0xFF, 8);
        int capacityBits = capacityBytes * 8;
        bb.append(0, Math.min(4, capacityBits - bb.length()));
        bb.append(0, (8 - bb.length() % 8) % 8);
        for (int pad = 0xEC; bb.length() < capacityBits; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
        return bb.toBytes();
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_BLOCKS[version];
        int eccLen = ECC_PER_BLOCK[version];
        int rawCodewords = rawModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = reedSolomonDivisor(eccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int dataLen = shortBlockLen - eccLen + (i < numShortBlocks ? 0 : 1);
            byte[] blockData = java.util.Arrays.copyOfRange(data, k, k + dataLen);
            k += dataLen;
            byte[] block = java.util.Arrays.copyOf(blockData, shortBlockLen + 1);
            byte[] ecc = reedSolomonRemainder(blockData, divisor);
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            blocks[i] = block;
        }

        byte[] result = new byte[rawCodewords];
        for (int i = 0, r = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                // Skip the padding byte in short blocks
                if (i != shortBlockLen - eccLen || j >= numShortBlocks) {
                    result[r++] = blocks[j][i];
                }
            }
        }
        return result;
    }

    private static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMultiply(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMultiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) gfMultiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    // ------------------------------------------------------------- drawing

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);

        int[] align = alignmentPositions();
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0))) {
                    drawAlignment(align[i], align[j]);
                }
            }
        }
        drawFormatBits(0); // reserve the format area
        drawVersion();
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    setFunction(x, y, dist != 2 && dist != 4);
                }
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int numAlign = version / 7 + 2;
        int step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step) result[i] = pos;
        return result;
    }

    private void drawFormatBits(int mask) {
        int data = (0 /* ECC level M */ << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));

        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3;
            int c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0 -> invert = (x + y) % 2 == 0;
                    case 1 -> invert = y % 2 == 0;
                    case 2 -> invert = x % 3 == 0;
                    case 3 -> invert = (x + y) % 3 == 0;
                    case 4 -> invert = (x / 3 + y / 2) % 2 == 0;
                    case 5 -> invert = x * y % 2 + x * y % 3 == 0;
                    case 6 -> invert = (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> invert = ((x + y) % 2 + x * y % 3) % 2 == 0;
                }
                if (!isFunction[y][x] && invert) modules[y][x] = !modules[y][x];
            }
        }
    }

    private int penaltyScore() {
        int result = 0;
        // N1: runs of 5+ same-colour modules in rows and columns
        for (int a = 0; a < size; a++) {
            for (int dir = 0; dir < 2; dir++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = dir == 0 ? modules[a][b] : modules[b][a];
                    boolean prev = dir == 0 ? modules[a][b - 1] : modules[b - 1][a];
                    if (cur == prev) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result++;
                    } else {
                        run = 1;
                    }
                }
            }
        }
        // N2: 2x2 blocks
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        }
        // N3: finder-like 1:1:3:1:1 patterns
        int[] pattern = {1, 0, 1, 1, 1, 0, 1};
        for (int a = 0; a < size; a++) {
            for (int b = 0; b + 7 <= size; b++) {
                boolean rowMatch = true, colMatch = true;
                for (int k = 0; k < 7; k++) {
                    if ((modules[a][b + k] ? 1 : 0) != pattern[k]) rowMatch = false;
                    if ((modules[b + k][a] ? 1 : 0) != pattern[k]) colMatch = false;
                }
                if (rowMatch) result += 40;
                if (colMatch) result += 40;
            }
        }
        // N4: dark/light balance
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += Math.max(k, 0) * 10;
        return result;
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    // ------------------------------------------------------------ bit buffer

    private static final class BitBuffer {
        private final java.util.ArrayList<Boolean> bits = new java.util.ArrayList<>();

        void append(int value, int len) {
            for (int i = len - 1; i >= 0; i--) bits.add(((value >>> i) & 1) != 0);
        }

        int length() {
            return bits.size();
        }

        byte[] toBytes() {
            byte[] out = new byte[(bits.size() + 7) / 8];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i)) out[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
            }
            return out;
        }
    }
}

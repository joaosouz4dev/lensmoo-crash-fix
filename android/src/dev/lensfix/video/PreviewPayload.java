package dev.lensfix.video;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Parses the observed type-2 preview envelope and H.264 parameters without Android dependencies. */
public final class PreviewPayload {
    private PreviewPayload() {}

    public static final class Packet {
        public final byte[] annexB;
        public final byte[] sample;
        public final byte[] sps;
        public final byte[] pps;
        public final boolean keyFrame;
        public final boolean hasPicture;
        public final int width;
        public final int height;

        private Packet(byte[] data, List<byte[]> units) {
            annexB = data;
            byte[] foundSps = null, foundPps = null;
            boolean key = false, picture = false;
            ByteArrayOutputStream samples = new ByteArrayOutputStream(data.length);
            for (byte[] unit : units) {
                int kind = unit[0] & 31;
                if (kind == 7) foundSps = withStartCode(unit);
                else if (kind == 8) foundPps = withStartCode(unit);
                else if (kind != 9) {
                    byte[] normalized = withStartCode(unit);
                    samples.write(normalized, 0, normalized.length);
                }
                key |= kind == 5;
                picture |= kind == 1 || kind == 5;
            }
            sps = foundSps;
            pps = foundPps;
            keyFrame = key;
            hasPicture = picture;
            sample = samples.toByteArray();
            int[] size = foundSps == null ? null : dimensions(Arrays.copyOfRange(foundSps, 4, foundSps.length));
            width = size == null ? 0 : size[0];
            height = size == null ? 0 : size[1];
        }
    }

    /** Returns null for unrelated, truncated, oversized or unsupported packets. */
    public static Packet parse(byte[] packet) {
        if (packet == null || packet.length < 10 || packet.length > 2 * 1024 * 1024 || packet[0] != 2) return null;
        long declared = ((long) packet[1] & 255) | (((long) packet[2] & 255) << 8)
                | (((long) packet[3] & 255) << 16) | (((long) packet[4] & 255) << 24);
        if (declared != packet.length - 5) return null;
        byte[] data = Arrays.copyOfRange(packet, 5, packet.length);
        try {
            List<byte[]> units = split(data);
            if (units.isEmpty()) return null;
            boolean avc = false;
            for (byte[] unit : units) {
                if ((unit[0] & 128) != 0) return null;
                int kind = unit[0] & 31;
                if (kind < 1 || kind > 12) return null;
                avc |= kind == 1 || kind == 5 || kind == 7;
            }
            return avc ? new Packet(data, units) : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static byte[] withStartCode(byte[] nal) {
        byte[] result = new byte[nal.length + 4];
        result[3] = 1;
        System.arraycopy(nal, 0, result, 4, nal.length);
        return result;
    }

    private static int startCodeLength(byte[] data, int at) {
        if (at + 2 >= data.length || data[at] != 0 || data[at + 1] != 0) return 0;
        if (data[at + 2] == 1) return 3;
        return at + 3 < data.length && data[at + 2] == 0 && data[at + 3] == 1 ? 4 : 0;
    }

    public static List<byte[]> split(byte[] data) {
        List<byte[]> result = new ArrayList<>();
        int position = 0;
        while (position < data.length) {
            int prefix = startCodeLength(data, position);
            if (prefix == 0) throw new IllegalArgumentException("Missing Annex-B start code");
            int begin = position + prefix;
            int end = begin;
            while (end < data.length && startCodeLength(data, end) == 0) end++;
            if (begin == end) throw new IllegalArgumentException("Empty NAL unit");
            result.add(Arrays.copyOfRange(data, begin, end));
            position = end;
        }
        return result;
    }

    /** Parse coded size and cropping rather than assuming the tested camera resolution. */
    public static int[] dimensions(byte[] nal) {
        if (nal == null || nal.length < 4 || (nal[0] & 31) != 7) throw new IllegalArgumentException("Missing SPS");
        ByteArrayOutputStream rbsp = new ByteArrayOutputStream(nal.length);
        int zeros = 0;
        for (int i = 1; i < nal.length; i++) {
            int value = nal[i] & 255;
            if (zeros >= 2 && value == 3) { zeros = 0; continue; }
            rbsp.write(value);
            zeros = value == 0 ? zeros + 1 : 0;
        }
        Bits bits = new Bits(rbsp.toByteArray());
        int profile = bits.read(8);
        bits.read(8);
        bits.read(8);
        bits.ue();
        int chroma = 1;
        boolean separate = false;
        if (profile == 100 || profile == 110 || profile == 122 || profile == 244 || profile == 44
                || profile == 83 || profile == 86 || profile == 118 || profile == 128 || profile == 138
                || profile == 139 || profile == 134 || profile == 135) {
            chroma = bits.ue();
            if (chroma > 3) throw new IllegalArgumentException("Invalid chroma format");
            if (chroma == 3) separate = bits.read(1) != 0;
            bits.ue();
            bits.ue();
            bits.read(1);
            if (bits.read(1) != 0) {
                int lists = chroma == 3 ? 12 : 8;
                for (int i = 0; i < lists; i++) {
                    if (bits.read(1) != 0) {
                        int last = 8, next = 8;
                        int count = i < 6 ? 16 : 64;
                        for (int j = 0; j < count; j++) {
                            if (next != 0) next = (last + bits.se() + 256) & 255;
                            last = next == 0 ? last : next;
                        }
                    }
                }
            }
        }
        bits.ue();
        int orderType = bits.ue();
        if (orderType == 0) bits.ue();
        else if (orderType == 1) {
            bits.read(1);
            bits.se();
            bits.se();
            int cycle = bits.ue();
            if (cycle > 256) throw new IllegalArgumentException("Invalid picture order cycle");
            for (int i = 0; i < cycle; i++) bits.se();
        } else if (orderType != 2) throw new IllegalArgumentException("Invalid picture order type");
        bits.ue();
        bits.read(1);
        int widthMbs = bits.ue() + 1;
        int heightMaps = bits.ue() + 1;
        int progressive = bits.read(1);
        if (progressive == 0) bits.read(1);
        bits.read(1);
        int left = 0, right = 0, top = 0, bottom = 0;
        if (bits.read(1) != 0) {
            left = bits.ue(); right = bits.ue(); top = bits.ue(); bottom = bits.ue();
        }
        int arrayType = separate ? 0 : chroma;
        int cropX = arrayType == 0 || arrayType == 3 ? 1 : 2;
        int cropY = (arrayType == 1 ? 2 : 1) * (2 - progressive);
        long width = (long) widthMbs * 16 - (long) cropX * (left + right);
        long height = (long) heightMaps * 16 * (2 - progressive) - (long) cropY * (top + bottom);
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || width * height > 33554432)
            throw new IllegalArgumentException("Invalid coded dimensions");
        return new int[]{(int) width, (int) height};
    }

    private static final class Bits {
        private final byte[] bytes;
        private int position;
        Bits(byte[] bytes) { this.bytes = bytes; }
        int read(int count) {
            if (count < 0 || count > 30 || position + count > bytes.length * 8) throw new IllegalArgumentException("Truncated SPS");
            int value = 0;
            while (count-- > 0) {
                value = (value << 1) | ((bytes[position / 8] >>> (7 - position % 8)) & 1);
                position++;
            }
            return value;
        }
        int ue() {
            int zeros = 0;
            while (read(1) == 0) if (++zeros > 29) throw new IllegalArgumentException("Invalid Exp-Golomb value");
            return (1 << zeros) - 1 + read(zeros);
        }
        int se() { int code = ue(); return (code & 1) == 0 ? -(code / 2) : (code + 1) / 2; }
    }
}

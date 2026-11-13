import dev.lensfix.video.PreviewPayload;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public final class PreviewPayloadTest {
    public static void main(String[] args) throws Exception {
        byte[] stream = Files.readAllBytes(Paths.get(args[0]));
        byte[] wrapped = new byte[stream.length + 5];
        wrapped[0] = 2;
        for (int i = 0; i < 4; i++) wrapped[i + 1] = (byte) (stream.length >>> (8 * i));
        System.arraycopy(stream, 0, wrapped, 5, stream.length);
        PreviewPayload.Packet packet = PreviewPayload.parse(wrapped);
        require(packet != null, "Supported envelope rejected");
        require(Arrays.equals(packet.annexB, stream), "Encoded bytes changed during unwrap");
        require(packet.width == Integer.parseInt(args[1]) && packet.height == Integer.parseInt(args[2]), "Wrong SPS dimensions/cropping");
        require(packet.keyFrame && packet.hasPicture && packet.sps != null && packet.pps != null, "Missing keyframe or codec parameters");
        for (byte[] nal : PreviewPayload.split(packet.sample)) {
            require((nal[0] & 31) != 7 && (nal[0] & 31) != 8, "Codec configuration leaked into MP4 samples");
        }
        byte[] bad = wrapped.clone();
        bad[1] ^= 1;
        require(PreviewPayload.parse(bad) == null, "Mismatched envelope length accepted");
        bad = wrapped.clone();
        bad[0] = 1;
        require(PreviewPayload.parse(bad) == null, "Unrelated media type intercepted");
        bad = wrapped.clone();
        Arrays.fill(bad, 1, 5, (byte) 255);
        require(PreviewPayload.parse(bad) == null, "Unsigned length overflow accepted");
        require(PreviewPayload.parse(new byte[]{(byte)255, (byte)216, 1, 2, 3}) == null, "JPEG path intercepted");
        require(PreviewPayload.parse(Arrays.copyOf(wrapped, 8)) == null, "Truncated payload accepted");
        System.out.println("PASS " + packet.width + "x" + packet.height);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

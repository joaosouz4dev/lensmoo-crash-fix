package dev.lensfix.video;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Local preview and explicit recording for the observed framed H.264 stream. */
public final class VideoSupport {
    private static final String TAG = "LensFixVideo";
    private static final Map<Object, Session> SESSIONS = new IdentityHashMap<>();
    private VideoSupport() {}

    public static boolean handles(byte[] bytes) { return PreviewPayload.parse(bytes) != null; }

    /** True means the packet belongs to the supported video path, including offscreen drops. */
    public static boolean accept(Object fragment, byte[] bytes) {
        PreviewPayload.Packet packet = PreviewPayload.parse(bytes);
        if (packet == null) return false;
        try {
            View root = (View) fragment.getClass().getMethod("getView").invoke(fragment);
            Method open = fragment.getClass().getMethod("isOpenVideoPreview");
            if (root == null || !root.isShown() || !Boolean.TRUE.equals(open.invoke(fragment))) {
                stop(fragment);
                return true;
            }
            synchronized (SESSIONS) {
                Session session = SESSIONS.get(fragment);
                if (session == null) {
                    session = new Session(root);
                    SESSIONS.put(fragment, session);
                }
                session.feed(packet);
            }
        } catch (Exception failure) {
            Log.e(TAG, "Preview bridge unavailable: " + failure.getClass().getSimpleName());
        }
        return true;
    }

    public static void stop(Object fragment) {
        Session session;
        synchronized (SESSIONS) { session = SESSIONS.remove(fragment); }
        if (session != null) session.close();
    }

    private static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.lensmoo.app");
        if (id == 0) id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    private static final class Session {
        final View root;
        final Context context;
        final TextureView texture;
        final View recordParent, recordButton, background, loading;
        final TextView recordLabel;
        final ImageView play;
        final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "LensFixVideo"));
        final MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        volatile boolean closed, recording, requested;
        volatile long recordingAt, lastFrameAt;
        MediaCodec codec;
        Surface surface;
        byte[] sps, pps;
        int width, height, received, rendered, lastHash;
        long lastHashAt;
        boolean announced;
        Recorder recorder;

        Session(View root) {
            this.root = root;
            context = root.getContext();
            texture = (TextureView) find(root, "textureView");
            recordParent = find(root, "ll_camera_shoot_parent");
            recordButton = find(root, "ll_camera_shoot");
            recordLabel = (TextView) find(root, "tv_shoot_video");
            background = find(root, "iv_video_bg");
            loading = find(root, "iv_load_video");
            play = (ImageView) find(root, "iv_play");
            if (texture == null || recordParent == null || recordButton == null || recordLabel == null)
                throw new IllegalArgumentException("Unsupported preview layout");
            root.post(() -> {
                if (closed) return;
                recordParent.setVisibility(View.VISIBLE);
                recordButton.setEnabled(true);
                recordLabel.setMaxLines(2);
                recordLabel.setTextSize(14);
                recordLabel.setText("Gravar vídeo");
                recordButton.setContentDescription("Gravar a prévia no celular, sem áudio");
                recordButton.setOnClickListener(v -> toggleRecording());
                texture.setOnClickListener(v -> {
                    if (play != null) {
                        play.setImageResource(android.R.drawable.ic_media_pause);
                        play.setVisibility(View.VISIBLE);
                        play.postDelayed(() -> { if (!closed) play.setVisibility(View.GONE); }, 2000);
                    }
                });
                root.postDelayed(ticker, 1000);
            });
        }

        final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (closed) return;
                if (recording) {
                    long elapsed = (SystemClock.elapsedRealtime() - recordingAt) / 1000;
                    recordLabel.setText(String.format(Locale.ROOT, "Parar %02d:%02d", elapsed / 60, elapsed % 60));
                    if (SystemClock.elapsedRealtime() - lastFrameAt > 4000) {
                        requested = false;
                        submit(() -> finishRecording("Transmissão interrompida; vídeo salvo."));
                    }
                }
                root.postDelayed(this, 1000);
            }
        };

        void feed(PreviewPayload.Packet packet) {
            if (closed) return;
            long now = SystemClock.elapsedRealtime();
            int hash = Arrays.hashCode(packet.annexB);
            if (lastHash == hash && now - lastHashAt < 20) return;
            lastHash = hash;
            lastHashAt = now;
            lastFrameAt = now;
            submit(() -> process(packet));
        }

        void submit(Runnable action) {
            if (closed) return;
            try { worker.execute(action); }
            catch (RejectedExecutionException stopped) { /* Session closed during callback delivery. */ }
        }

        void process(PreviewPayload.Packet packet) {
            if (closed) return;
            received++;
            if (packet.sps != null) { sps = packet.sps; width = packet.width; height = packet.height; }
            if (packet.pps != null) pps = packet.pps;
            try {
                if (codec == null && sps != null && pps != null && texture.isAvailable()) {
                    MediaFormat format = format();
                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024);
                    surface = new Surface(texture.getSurfaceTexture());
                    codec = MediaCodec.createDecoderByType("video/avc");
                    codec.configure(format, surface, null, 0);
                    codec.start();
                }
                if (codec != null && packet.hasPicture) {
                    drain();
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer input = codec.getInputBuffer(index);
                        if (input == null || input.capacity() < packet.annexB.length)
                            throw new IllegalArgumentException("Preview frame exceeds decoder buffer");
                        input.clear(); input.put(packet.annexB);
                        codec.queueInputBuffer(index, 0, packet.annexB.length, System.nanoTime() / 1000, 0);
                    }
                    drain();
                }
            } catch (Exception failure) {
                Log.w(TAG, "Preview decoding failed: " + failure.getClass().getSimpleName());
                try { if (codec != null) codec.release(); } catch (Exception ignored) {}
                codec = null;
                if (surface != null) { surface.release(); surface = null; }
            }
            // Encoded recording stays independent of the display Surface and decoder.
            try {
                if (requested && recorder == null && packet.keyFrame && sps != null && pps != null) {
                    recorder = new Recorder(context, format(), width, height);
                    recording = true;
                    recordingAt = SystemClock.elapsedRealtime();
                    message("Gravando prévia " + width + " × " + height + ", sem áudio.");
                    label("Parar e salvar");
                }
                if (recorder != null && packet.hasPicture) recorder.write(packet);
            } catch (Exception failure) {
                Log.e(TAG, "Video recording failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                requested = false;
                if (recorder != null) finishRecording(null);
                label("Gravar vídeo");
            }
        }

        MediaFormat format() {
            MediaFormat result = MediaFormat.createVideoFormat("video/avc", width, height);
            result.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
            result.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
            return result;
        }

        void drain() {
            for (int tries = 0; tries < 20; tries++) {
                int output = codec.dequeueOutputBuffer(outputInfo, 0);
                if (output >= 0) {
                    codec.releaseOutputBuffer(output, true);
                    rendered++;
                    if (!announced) {
                        announced = true;
                        Log.i(TAG, "Preview ready " + width + "x" + height);
                        root.post(() -> {
                            if (closed) return;
                            if (background != null) background.setVisibility(View.GONE);
                            if (loading != null) loading.setVisibility(View.GONE);
                            if (play != null) play.setVisibility(View.GONE);
                        });
                    }
                } else if (output != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) break;
            }
        }

        void toggleRecording() {
            if (closed) return;
            submit(() -> {
                if (recorder != null || requested) {
                    requested = false;
                    finishRecording(null);
                } else {
                    requested = true;
                    label("Iniciando...");
                }
            });
        }

        void finishRecording(String notice) {
            Recorder ending = recorder;
            recorder = null;
            requested = false;
            recording = false;
            if (ending != null) {
                boolean saved = ending.finish();
                message(saved ? (notice == null ? "Vídeo salvo em Movies/LensFix." : notice) : "Não foi possível finalizar o vídeo.");
            }
            label("Gravar vídeo");
        }

        void message(String message) { root.post(() -> Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show()); }
        void label(String message) { root.post(() -> { if (!closed) recordLabel.setText(message); }); }

        synchronized void close() {
            if (closed) return;
            closed = true;
            root.removeCallbacks(ticker);
            root.post(() -> {
                recordButton.setOnClickListener(null);
                recordParent.setVisibility(View.GONE);
                texture.setOnClickListener(null);
            });
            worker.execute(() -> {
                finishRecording(null);
                try { if (codec != null) { codec.stop(); codec.release(); } }
                catch (Exception failure) { Log.w(TAG, "Decoder cleanup: " + failure.getClass().getSimpleName()); }
                if (surface != null) surface.release();
                Log.i(TAG, "Preview stopped received=" + received + " rendered=" + rendered);
            });
            worker.shutdown();
        }
    }

    private static final class Recorder {
        final Context context;
        final ContentResolver resolver;
        final Uri uri;
        final ParcelFileDescriptor descriptor;
        final MediaMuxer muxer;
        final int track, width, height;
        long startedUs = -1, lastPts = -1;
        int samples;

        Recorder(Context context, MediaFormat format, int width, int height) throws Exception {
            this.context = context;
            resolver = context.getContentResolver();
            this.width = width; this.height = height;
            String name = "LensFix_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".mp4";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LensFix");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
            }
            Uri inserted = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (inserted == null) throw new IllegalStateException("Video destination unavailable");
            ParcelFileDescriptor opened = null;
            MediaMuxer created = null;
            int added;
            try {
                opened = resolver.openFileDescriptor(inserted, "rw");
                if (opened == null) throw new IllegalStateException("Video file unavailable");
                created = new MediaMuxer(opened.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                added = created.addTrack(format);
                created.start();
            } catch (Exception failure) {
                if (created != null) created.release();
                if (opened != null) opened.close();
                resolver.delete(inserted, null, null);
                throw failure;
            }
            uri = inserted; descriptor = opened; muxer = created; track = added;
        }

        void write(PreviewPayload.Packet packet) {
            long nowUs = System.nanoTime() / 1000;
            if (startedUs < 0) startedUs = nowUs;
            long pts = Math.max(lastPts + 1, nowUs - startedUs);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.set(0, packet.sample.length, pts, packet.keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
            muxer.writeSampleData(track, ByteBuffer.wrap(packet.sample), info);
            lastPts = pts; samples++;
        }

        boolean finish() {
            boolean success = false;
            try {
                if (samples >= 2) { muxer.stop(); success = true; }
            } catch (Exception failure) { Log.e(TAG, "Video finalization: " + failure.getClass().getSimpleName()); }
            finally {
                try { muxer.release(); } catch (Exception ignored) {}
                try { descriptor.close(); } catch (Exception ignored) {}
            }
            if (success) {
                try {
                    ContentValues values = new ContentValues();
                    if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.Video.Media.IS_PENDING, 0);
                    values.put(MediaStore.Video.Media.WIDTH, width);
                    values.put(MediaStore.Video.Media.HEIGHT, height);
                    resolver.update(uri, values, null, null);
                    Log.i(TAG, "Video saved samples=" + samples + " durationUs=" + lastPts + " size=" + width + "x" + height);
                    return true;
                } catch (Exception failure) { Log.e(TAG, "Video publication: " + failure.getClass().getSimpleName()); }
            }
            resolver.delete(uri, null, null);
            return false;
        }
    }
}

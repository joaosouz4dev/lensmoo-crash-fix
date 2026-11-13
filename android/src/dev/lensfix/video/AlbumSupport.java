package dev.lensfix.video;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows this app's saved local videos inside the existing album without changing photo data. */
public final class AlbumSupport {
    private static final String TAG = "LensFixLocalAlbum";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "LensFixAlbum"));
    private AlbumSupport() {}

    public static void refresh(Object fragment) {
        try {
            View root = (View) fragment.getClass().getMethod("getView").invoke(fragment);
            if (root != null) root.post(() -> attach(root));
        } catch (Exception failure) {
            Log.w(TAG, "Album integration unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private static void attach(View root) {
        try {
            LinearLayout wrapper = root.findViewWithTag(TAG);
            State state;
            if (wrapper == null) {
                int id = root.getResources().getIdentifier("recyclerView", "id", "com.lensmoo.app");
                if (id == 0) id = root.getResources().getIdentifier("recyclerView", "id", root.getContext().getPackageName());
                View photos = root.findViewById(id);
                if (photos == null || !(photos.getParent() instanceof ViewGroup)) return;
                ViewGroup parent = (ViewGroup) photos.getParent();
                int index = parent.indexOfChild(photos);
                ViewGroup.LayoutParams original = photos.getLayoutParams();
                wrapper = new LinearLayout(root.getContext());
                wrapper.setId(View.generateViewId());
                wrapper.setTag(TAG);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                LinearLayout section = new LinearLayout(root.getContext());
                section.setOrientation(LinearLayout.VERTICAL);
                state = new State(root, section);
                section.setTag(state);
                parent.removeView(photos);
                parent.addView(wrapper, index, original);
                wrapper.addView(section, new LinearLayout.LayoutParams(-1, -2));
                wrapper.addView(photos, new LinearLayout.LayoutParams(-1, 0, 1));
                wrapper.bringToFront();
            } else {
                state = (State) wrapper.getChildAt(0).getTag();
            }
            state.refresh();
        } catch (Exception failure) {
            Log.w(TAG, "Album layout unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private static final class Clip {
        final Uri uri;
        final long added, duration;
        final int width, height;
        Clip(long id, long added, long duration, int width, int height) {
            uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
            this.added = added; this.duration = duration; this.width = width; this.height = height;
        }
        String title() {
            long seconds = duration / 1000;
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(added * 1000))
                    + String.format(Locale.ROOT, " · %02d:%02d", seconds / 60, seconds % 60);
        }
        String detail() { return width + "x" + height + " · sem áudio"; }
    }

    private static final class State {
        final View root;
        final LinearLayout section;
        final Context context;
        int generation;
        boolean observing;
        final Runnable changed;
        final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                root.removeCallbacks(changed);
                root.postDelayed(changed, 250);
            }
        };
        State(View root, LinearLayout section) {
            this.root = root; this.section = section; context = root.getContext();
            changed = () -> { if (root.isShown()) refresh(); };
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { observe(); refresh(); }
                @Override public void onViewDetachedFromWindow(View view) {
                    if (observing) context.getContentResolver().unregisterContentObserver(observer);
                    observing = false;
                    generation++;
                    root.removeCallbacks(changed);
                }
            });
            observe();
        }

        void observe() {
            if (!observing && root.isAttachedToWindow()) {
                try {
                    context.getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
                    observing = true;
                } catch (Exception unavailable) {
                    Log.w(TAG, "Album observer unavailable: " + unavailable.getClass().getSimpleName());
                }
            }
        }

        void refresh() {
            int revision = ++generation;
            WORKER.execute(() -> {
                List<Clip> clips = new ArrayList<>();
                try {
                    String selection = MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
                    String[] arguments = {"LensFix_%"};
                    if (Build.VERSION.SDK_INT >= 29) {
                        selection += " AND owner_package_name = ? AND relative_path = ? AND is_pending = 0";
                        arguments = new String[]{"LensFix_%", context.getPackageName(), "Movies/LensFix/"};
                    }
                    String[] projection = {"_id", "date_added", "duration", "width", "height"};
                    try (Cursor cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            projection, selection, arguments, "date_added DESC, _id DESC")) {
                        if (cursor != null) while (cursor.moveToNext())
                            clips.add(new Clip(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3), cursor.getInt(4)));
                    }
                } catch (Exception failure) {
                    Log.w(TAG, "Saved video query: " + failure.getClass().getSimpleName());
                }
                root.post(() -> { if (generation == revision && root.isAttachedToWindow()) render(clips, revision); });
            });
        }

        void render(List<Clip> clips, int revision) {
            section.removeAllViews();
            section.setPadding(dp(12), dp(4), dp(12), dp(8));
            LinearLayout heading = new LinearLayout(context);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text("Vídeos gravados", 17);
            title.setTypeface(null, Typeface.BOLD);
            heading.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
            if (!clips.isEmpty()) {
                Button all = new Button(context, null, android.R.attr.borderlessButtonStyle);
                all.setText("Ver todos (" + clips.size() + ")");
                all.setTextSize(13);
                all.setMinHeight(dp(48));
                all.setOnClickListener(v -> {
                    String[] labels = new String[clips.size()];
                    for (int i = 0; i < clips.size(); i++) labels[i] = clips.get(i).title() + "\n" + clips.get(i).detail();
                    new AlertDialog.Builder(context).setTitle("Vídeos gravados")
                            .setItems(labels, (dialog, which) -> play(clips.get(which)))
                            .setNegativeButton("Fechar", null).show();
                });
                heading.addView(all);
            }
            section.addView(heading);
            if (clips.isEmpty()) {
                TextView empty = text("Os vídeos que você gravar aparecerão aqui.", 13);
                empty.setPadding(0, 0, 0, dp(12));
                section.addView(empty);
            } else {
                HorizontalScrollView scroll = new HorizontalScrollView(context);
                LinearLayout row = new LinearLayout(context);
                for (int i = 0; i < Math.min(6, clips.size()); i++) {
                    Clip clip = clips.get(i);
                    LinearLayout card = new LinearLayout(context);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(0, 0, dp(10), 0);
                    card.setFocusable(true);
                    card.setContentDescription("Reproduzir vídeo " + clip.title() + ", " + clip.detail());
                    card.setOnClickListener(v -> play(clip));
                    FrameLayout frame = new FrameLayout(context);
                    ImageView image = new ImageView(context);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    GradientDrawable background = new GradientDrawable();
                    background.setColor(Color.rgb(40, 40, 40));
                    background.setCornerRadius(dp(8));
                    frame.setBackground(background);
                    frame.setClipToOutline(true);
                    frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
                    ImageView icon = new ImageView(context);
                    icon.setImageResource(android.R.drawable.ic_media_play);
                    icon.setColorFilter(Color.WHITE);
                    GradientDrawable playBackground = new GradientDrawable();
                    playBackground.setShape(GradientDrawable.OVAL);
                    playBackground.setColor(Color.argb(170, 0, 0, 0));
                    icon.setBackground(playBackground);
                    icon.setPadding(dp(4), dp(4), dp(4), dp(4));
                    FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
                    frame.addView(icon, centered);
                    card.addView(frame, new LinearLayout.LayoutParams(dp(144), dp(108)));
                    TextView caption = text(clip.title(), 12);
                    caption.setPadding(0, dp(4), 0, 0);
                    card.addView(caption);
                    card.addView(text(clip.detail(), 11));
                    row.addView(card, new LinearLayout.LayoutParams(dp(154), -2));
                    WORKER.execute(() -> {
                        Bitmap thumbnail = thumbnail(clip.uri);
                        root.post(() -> {
                            if (generation == revision && image.isAttachedToWindow()) image.setImageBitmap(thumbnail);
                            else if (thumbnail != null) thumbnail.recycle();
                        });
                    });
                }
                scroll.addView(row);
                section.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
            }
            TextView photos = text("Fotos", 17);
            photos.setTypeface(null, Typeface.BOLD);
            photos.setPadding(0, dp(12), 0, 0);
            section.addView(photos);
            Log.i(TAG, "Album ready videos=" + clips.size());
        }

        Bitmap thumbnail(Uri uri) {
            try {
                if (Build.VERSION.SDK_INT >= 29)
                    return context.getContentResolver().loadThumbnail(uri, new Size(288, 216), null);
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(context, uri);
                    return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } finally { retriever.release(); }
            } catch (Exception unavailable) { return null; }
        }

        void play(Clip clip) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(clip.uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(context, "Instale um reprodutor de vídeo para abrir o MP4.", Toast.LENGTH_LONG).show();
            } catch (Exception unavailable) {
                Toast.makeText(context, "Não foi possível abrir este vídeo.", Toast.LENGTH_LONG).show();
            }
        }

        TextView text(String value, float size) {
            TextView text = new TextView(context);
            text.setText(value); text.setTextSize(size); text.setGravity(Gravity.CENTER_VERTICAL);
            TypedValue color = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true);
            if (color.resourceId != 0) text.setTextColor(context.getColorStateList(color.resourceId));
            else text.setTextColor(color.data);
            return text;
        }
        int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    }
}

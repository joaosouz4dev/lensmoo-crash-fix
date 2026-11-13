# Synthetic preview fixtures

These files contain one frame of an FFmpeg-generated test pattern, with no camera or personal content. Expected coded dimensions were independently checked with ffprobe.

Commands used:

```sh
ffmpeg -f lavfi -i testsrc2=size=320x240:rate=5 -frames:v 1 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -f h264 synthetic-baseline-320x240.h264
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=5 -frames:v 1 -c:v libx264 -profile:v high -pix_fmt yuv420p -f h264 synthetic-high-640x360.h264
```

The second fixture exercises SPS cropping for a height that is not a multiple of 16. The Java checks also reject malformed lengths, truncated packets and unrelated JPEG traffic, and ensure SPS/PPS are excluded from MP4 sample data.

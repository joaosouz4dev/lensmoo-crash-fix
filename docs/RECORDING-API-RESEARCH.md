# Android H.264 preview and MP4 recording integration

Checked on 2026-11-13 against Android API documentation and AOSP implementation.

## Input established by this investigation

The physical-device investigation obtained a real ten-second capture containing 295 distinct 320x240 frames. Each callback packet was `[type=2:1][payload length:LE32][Annex-B H.264]`, with SPS, PPS and IDR NALs. These are local observations, not facts established by Android documentation. Validate the packet length before reading NALs. Recording this live stream is distinct from downloading recordings stored inside the glasses.

## Muxer input format

Pass Annex-B samples to Android's MP4 muxer, normalizing retained NAL prefixes to four bytes `00 00 00 01`. Do not replace prefixes with AVCC lengths first. AOSP's `StripStartcode`, `addMultipleLengthPrefixedSamples_l` and `addLengthPrefixedSample_l` perform the MP4 length-prefix conversion internally. This is an implementation finding, not an explicit promise about every third-party muxer. [AOSP MPEG4Writer.cpp](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/libstagefright/MPEG4Writer.cpp).

Create an AVC track only after obtaining both parameter sets. Put SPS in `csd-0` and PPS in `csd-1`, each retaining a four-byte start code. Decoder configuration accepts this same format; when CSD is supplied in `MediaFormat`, the decoder submits it automatically at start. [MediaCodec codec-specific data](https://developer.android.com/reference/android/media/MediaCodec#CSD).

The muxer receives CSD through `addTrack`, not `writeSampleData`. Therefore remove repeated SPS/PPS NALs from frame sample buffers, retain the frame's VCL payload, then call `addTrack`, `start`, and `writeSampleData` in that order. The FileDescriptor constructor starts at API 26; MP4 accepts a writable descriptor and the caller owns descriptor cleanup. [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer).

## Sample metadata and timestamps

For a freshly packed sample use `BufferInfo.set(0, sample.size, ptsUs, flags)`. Mark an access unit containing IDR NAL type 5 with `BUFFER_FLAG_KEY_FRAME`; codec initialization data is not a video frame. Offset and size specify the exact valid byte range; presentation timestamps use microseconds. [MediaCodec.BufferInfo](https://developer.android.com/reference/android/media/MediaCodec.BufferInfo).

Write each track chronologically. A final empty EOS sample can specify the last sample's duration; otherwise the muxer inherits the preceding sample duration. [MediaMuxer.writeSampleData](https://developer.android.com/reference/android/media/MediaMuxer#writeSampleData(int,java.nio.ByteBuffer,android.media.MediaCodec.BufferInfo)).

Implementation choice for this stream: if transport packets carry no source timestamps, derive relative presentation times from a monotonic arrival clock and force strictly increasing values. Do not infer exact constant frame rate solely from the observed packet count. This is a recommendation, not an Android requirement.

## Preview and worker ownership

Configure an AVC decoder with the SPS/PPS format and a valid output Surface. Feed complete access units and drain/release output buffers promptly; retained buffers can stall decoding. Ordinary decoder input can use flag 0: most decoders do not use the input keyframe flag. [MediaCodec processing and queueInputBuffer](https://developer.android.com/reference/android/media/MediaCodec).

Implementation recommendation: one serial worker should own codec/muxer lifecycle and sample submission. Copy callback buffers before returning to the Bluetooth reader, bound preview backlog, and keep recording failure independent of preview rendering. Wait for queued writes before stop/release and handle Surface destruction on the same owner.

## MediaStore output

On API 29+, insert a `MediaStore.Video` row with MP4 display name, `video/mp4`, `RELATIVE_PATH=Movies/LensFix`, and `IS_PENDING=1`. Open its descriptor, write and finalize the muxer, close resources, then publish with `IS_PENDING=0`. Delete the row on failed or empty recording. Own-media creation needs no broad storage permission on Android 10+. API 26-28 needs a separate legacy-storage or app-specific-file path. [Shared-media storage guide](https://developer.android.com/training/data-storage/shared/media).

`IS_PENDING` itself was introduced at API 29. While pending, only the owner can open the media; leaving it pending does not publish a gallery video. [MediaStore.MediaColumns.IS_PENDING](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#IS_PENDING).

## SPS dimensions

For progressive baseline 4:2:0, derive coded width as `16*(pic_width_in_mbs_minus1+1)` and coded height as `16*(pic_height_in_map_units_minus1+1)`. Crop width by `2*(left+right)` and height by `2*(top+bottom)`. For interlaced streams, coded height additionally multiplies by `2-frame_mbs_only_flag`; crop units also depend on chroma format. [AOSP AVC dimension parser](https://android.googlesource.com/platform/frameworks/base/+/b5d35088cbbbac53b052ec52f199de4ad8b061e7/media/libstagefright/avc_utils.cpp).

Do not apply the baseline shortcut to arbitrary high-profile SPS data. Escaped RBSP bytes require proper emulation-prevention handling during bit parsing; AndroidX provides a parser accepting escaped SPS data and an unescape utility. [AndroidX NalUnitUtil](https://developer.android.com/reference/androidx/media3/container/NalUnitUtil).

## Verification boundary

This research did not execute Android APIs or test a resulting MP4. Required device checks remain: visible preview, finalized playable file, expected duration/dimensions/frame count, stop/disconnect/error cleanup, and gallery visibility. A video-only H.264 stream does not establish audio recording support.

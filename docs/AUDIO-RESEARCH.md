# Audio capture research

Research dates: 2026-11-13 and 2026-11-14. The initial static research inspected a privately available Lensmoo 1.8.0 APK and official Android documentation without device commands. Later sections separately record controlled physical tests performed by the session operator. API symbols identify contracts; no decompiled implementation or test media is included.

Current result: isolated glasses audio is verified through SCO and the protocol Opus stream. Simultaneous audio and video remains unverified. In the latest direct protocol test, opening the camera while audio was active returned status 2, which the original app maps to device busy, and delivered no video frames. The public LensFix 1.1.0 APK remains a silent 320x240 preview recorder.

## What the APK actually contains

| Route | Concrete contract | Evidence and limit |
| --- | --- | --- |
| Bluetooth HFP/SCO | `StarburstScoAudioRecorder.getInstance(Context)`, `startRecording(IBaseAudioRecorder.RecorderStateListener)`, `stopRecording()` | Uses `AudioRecord` source 7 (`VOICE_COMMUNICATION`), 16000 Hz, channel mask 16 (`CHANNEL_IN_MONO`), encoding 2 (`PCM_16BIT`). Calls legacy `startBluetoothSco`, `setBluetoothScoOn(true)`, `setMode(3)`. It waits on SCO state before its audio thread, with a 10-second timeout and retries. Static app behavior, not a successful microphone test on this unit. |
| Second AI engine SCO | `StarburstScoAudioRecorderForEngine2.getInstance(Context)`, `setAudioDataCallback(AudioDataCallback)`, `setRecorderStateListener(RecorderStateListener)`, `startRecording()`, `stopRecording()` | Same 16000 Hz mono PCM16 `AudioRecord` configuration. `AudioDataCallback.onAudioData(byte[])`, `onVadEnd()` deliver AI/VAD events. |
| Bluetooth protocol audio | `UNIWatchMate.INSTANCE.getWmApps().a()` returns the obfuscated `AbAIAssistant` contract | `.b()` is `observeAudioData`, `.e()` is `observeLongChatAudioData`, `.c()` is `observeAudioDataOfNoStorageDevice`. These are Rx observables adapted to Flow by `AssistantManager`. They carry encoded data, not Android `AudioRecord` samples. |
| No-storage protocol decoder | `AssistantManager.processAudioDataOfNoStorageDevice`, `OpusDecoderJni.createDecoder(16000,1)`, `decode(long,byte[],byte[],int)` | Buffers incomplete data across callbacks. Parser requires one byte equal to 40 followed by a 40-byte Opus packet; advances by 41 bytes. The decoder return value is samples per channel and is multiplied by channels and two bytes per sample before forwarding PCM. Invalid length bytes are skipped. Constants are 16000 Hz, one channel, two bytes per sample and max frame buffer 1920 samples. Decoder output duration must come from the returned sample count, not an assumed 40-byte packet duration. |

Primary local evidence: APK classes `AssistantManager`, `AssistantManager$start$5$3`, `$5$6`, `$5$8`, `AssistantManager$processAudioData$2`, `$processAudioDataOfNoStorageDevice$2`, `StarburstAudioRecorder`, both SCO recorder classes, `AbAIAssistant` and its `AppAIAssistant` implementation. This note does not redistribute those files.

`AssistantManager.start()` reads `UNIWatchMate.INSTANCE.getGlassesFunctionSupportState().getSupportScoLink() == 1` to select SCO. It also branches on `getSupportVolcEngine()` and `whichAiEngine()`. `isUsingScoLink()` returns true only when both its `isUsingSco` and `isLongChat` flags are true, so a false result alone does not prove absent SCO capability. The current unit's actual supportScoLink value was not read by this research.

`getWmApps().q()` is the separate `AbVideoPreview` contract. It exposes `toggleAudio(boolean)`, `getAudioState()` and `getObserveAudioState()`, but no audio-data observable. Static builder analysis maps toggle to 5750, query to 5770 and unsolicited state to 5760. Whether enabling this delivers audio to the assistant no-storage observable, a different transport parser or SCO remains unverified. Do not interpret an audio-state acknowledgement as proof that bytes or sound arrived. Neither route proves simultaneous audio and H264 support on firmware 1.0.2.

## Prevent AI/VAD contention

The existing SCO recorders apply noise suppression/gain control when available, cache audio, run energy/VAD logic and can stop themselves after VAD completion. Reusing their AI callbacks as a continuous film recorder requires explicitly dealing with this behavior. A dedicated recorder owned by the video recording session is easier to audit.

Important cleanup behavior from static APK inspection:

- `AssistantManager.stop()` cancels its coroutine scope and clears `isStarted`; it does not itself stop/release an `AudioRecord` or Bluetooth route.
- `stopRecorderByEngine()` calls the Engine2 recorder's `stopRecording()` for engine 2. Its engine 1 branch relies on the SDK to stop recording and does not directly stop the SCO recorder.
- `getScoAudioRecorder()` and `getScoAudioRecorderForEngine2()` expose existing instances. Their `stopRecording()` paths stop/release AudioRecord, interrupt the worker and release the old SCO routing/mode. Calling `getInstance()` solely for cleanup may create a new singleton unnecessarily.
- `ChatViewModel.stopChat(true)` sends the assistant stop request and launches asynchronous session cleanup. `stopPlayAndReset()` stops playback. These must be allowed to finish before selecting a new capture route.
- `stopNoStorageDeviceRecordingIfRecording()` closes the local WAV recording. It does not by itself prove the glasses stopped transmitting or prevent AssistantManager from starting a new recording on later PCM.

Implementation recommendation, not current APK behavior: one explicit microphone/session owner must serialize AI shutdown, disable automatic assistant/VAD restarts for the duration, stop both existing initialized SCO recorders, suspend or detach the assistant audio consumers, and then grant recording ownership. Restore only the previous assistant state after all recording workers, encoders and route requests have ended. Do not let a late AI cleanup call stop SCO after the video recorder starts. Avoid replacing private coroutine scopes or fields using reflection merely to claim exclusivity.

## Android 17 / API 37 route verification

Use `AudioManager.setCommunicationDevice()` (API 31) with the glasses **output/sink** object from `getAvailableCommunicationDevices()`. Android automatically selects the matching input/source. Reject a missing headset or failed request. Observe `OnCommunicationDeviceChangedListener`, then start `AudioRecord`; clear the request on cleanup. The current API reference deprecates `setBluetoothScoOn` at API 37 and states it has already been ineffective for ordinary applications for several releases. [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo))

The official migration guide recommends the communication-device API and confirms HFP compatibility. Wait for the route callback, with a bounded timeout, rather than assuming request acceptance means routing is ready. [Android audio-manager guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager)

While actually recording, require `getRoutedDevice()` to identify the expected headset input, with Bluetooth SCO type for this HFP route. API 36+ also offers `getRoutedDevices()`; on API 37 reject any list containing an unexpected built-in microphone. `getPreferredDevice()` is only a preference and does not prove the active route. A null route is not proof of headset input. Compare the expected headset identity in memory; do not export Bluetooth identifiers. [AudioRecord route APIs](https://developer.android.com/reference/android/media/AudioRecord#getRoutedDevice())

Register `AudioRecordingCallback` before capture and monitor `getAudioDevice()`, `isClientSilenced()` and stream configuration. Abort on silencing or route changes. The platform can resample actual hardware input into the client format, so requesting 16 kHz does not prove the Bluetooth radio codec bandwidth. [Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input)

## Proposed private 10-second test, not executed

1. Keep the test app foreground with RECORD_AUDIO granted. Confirm microphone/session ownership is free and ordinary calls are inactive. Select the glasses communication sink, wait for it and build a dedicated `AudioRecord.Builder` with VOICE_COMMUNICATION, mono PCM16 and requested 16000 Hz. Allocate a buffer at least `getMinBufferSize` and check initialized state.
2. Register routing/recording callbacks. Call `startRecording()`, verify the actual headset input and non-silenced state, and read only positive byte counts for at most ten seconds on one worker. Recheck the route throughout. Do not silently fall back to the phone microphone.
3. Store only a private local PCM/WAV result. For a stronger acoustic check, keep the phone several metres away and have the user make a brief sound near the glasses microphone, then another near the phone. Route evidence is primary; acoustic comparison is supplementary. Do not upload this recording or include it in a public release.
4. Always stop/release AudioRecord, unregister callbacks, clearCommunicationDevice and restore the previous mode/session state in finally. Report only route type, sample count/duration, read errors, silencing and timing, excluding Bluetooth addresses and audio content.
5. Test H264 concurrently only after headset-only capture succeeds. Measure frame loss and interruptions with and without SCO. Protocol Opus is a separate controlled test: subscribe to `.a().c()` before invoking a supported microphone trigger, use a dedicated decoder and require real packets plus decoded samples. The 5750 preview toggle is a candidate trigger, not yet a verified connection to that observable. Stop immediately on AI/session contention.

## AAC plus H264 timing

Implementation proposal: encode the verified PCM using MediaCodec `audio/mp4a-latm`, AAC-LC, mono and the actual client sample rate, with a suitable supported bitrate. Use the encoder output format including AAC codec-specific data for the muxer audio track. Skip codec-config output as a media sample, drain EOS and serialize muxer calls. Add both audio and video tracks before starting the muxer; bound any queue while waiting. AAC output is raw access units for MP4, not an ADTS file stream. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)

Use a shared monotonic origin. `AudioRecord.getTimestamp(ts,TIMEBASE_MONOTONIC)` supplies frame delivery timing; sample frame n can be estimated as `ts.nanoTime + (n - ts.framePosition) * 1000000000 / sampleRate`. Convert both audio and video timestamps to microseconds relative to the same origin. Timestamp audio by accumulated sample frames, never byte count alone or each read's arrival time. [AudioTimestamp](https://developer.android.com/reference/android/media/AudioTimestamp)

Write samples in chronological order within each track. Preserve encoder output timestamps and codec delay behavior. [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer#writeSampleData(int,java.nio.ByteBuffer,android.media.MediaCodec.BufferInfo))

The observed H264 transport has no proven capture timestamp. Its callback arrival includes Bluetooth buffering. Therefore a shared clock alone does not establish lip sync; a visible and audible clap in the private test can measure constant offset and drift. Synthetic frameIndex/fps timestamps can drift against audio if the received frame rate varies. No synchronization accuracy or simultaneous audio/video capability has been validated by this research.


## Exact reflection contracts for a protocol-only audio test

Static follow-up dated 2026-11-14. No command, capture, network request or credential use was performed for this follow-up. The operator reports that SCO alone records clearly, while reopening the preview after SCO was ready produced zero video frames and 219 AAC frames. This establishes the result of that sequence, not impossibility across all firmware or all protocol audio routes.

### Classes and subscriptions in this APK

The actual bytecode names are obfuscated. Resolve them with the app's class loader, rather than a separately bundled RxJava copy:

| Role | Exact binary name and public method |
| --- | --- |
| Consumer interface | `com.android.mltcode.paycertificationapi.ai1`, `void accept(Object)` |
| Observable base | `com.android.mltcode.paycertificationapi.vs7` |
| Subscribe with data/error callbacks | `vs7.j6(ai1 onNext, ai1 onError) -> ra2` |
| Subscribe with data callback only | `vs7.i6(ai1 onNext) -> ra2`; prefer the two-callback form for the test |
| Single base | `com.android.mltcode.paycertificationapi.h9a` |
| Subscribe with success/error callbacks | `h9a.L1(ai1 onSuccess, ai1 onError) -> ra2` |
| Subscribe with success callback only | `h9a.K1(ai1 onSuccess) -> ra2` |
| Disposable interface | `com.android.mltcode.paycertificationapi.ra2`, `void dispose()`, `boolean isDisposed()` |

`j6` delegates to the Observable subscription path; `L1` creates the two-Consumer Single observer and subscribes it. They are subscription operations, not mapping operators. Lookup on the public base classes avoids dependence on the concrete Rx subclass. Java dynamic proxies can implement `ai1`; on `accept`, the data consumer receives the item and the error consumer receives a Throwable. Handle Object methods on proxies sensibly as well. Keep all returned Disposable instances until cleanup.

### Assistant data observables

Use `UNIWatchMate.INSTANCE.getWmApps().a()` to obtain the AI-assistant port. Calling these getters returns the existing observables; these getters do not start a recording or HFP session:

| Getter on assistant port | Semantic name retained in Kotlin metadata | Item |
| --- | --- | --- |
| `b()` | observeAudioData | `byte[]` |
| `c()` | observeAudioDataOfNoStorageDevice | `byte[]` |
| `e()` | observeLongChatAudioData | `byte[]` |

For each observable, call `vs7.j6(dataConsumer,errorConsumer)`. Count callbacks and incoming bytes separately per stream. Do not concatenate b/c/e into one decoder: different streams may carry separate sessions or duplicate content. The app connects b and e to `processAudioData`; c goes to `processAudioDataOfNoStorageDevice`. Both processors use the 40-byte Opus framing described above.

A fresh subscription does not change the glasses capability flags or AI engine choice. It also does not disable pre-existing AssistantManager consumers. If assistant/chat recording becomes active unexpectedly, end the test and release the test's subscriptions rather than reconfiguring capabilities or taking over the assistant decoder.

### Preview microphone control and acknowledgements

`UNIWatchMate.INSTANCE.getWmApps().q()` returns `AbVideoPreview`. Exact contracts:

| Method | Return/item | Meaning observed statically |
| --- | --- | --- |
| `toggleAudio(boolean)` | `h9a`, Single of Integer | Creates a command Single. Subscribe through L1 to execute. |
| `getObserveAudioState()` | `vs7`, Observable of Integer | Observes audio-state notifications. It carries state, not microphone data. |
| `getAudioState()` | `h9a`, Single of Boolean | Query operation executed on subscription; not a passive cached getter. |

`toggleAudio(true)` sends command 5750 with one payload byte equal to 1; false sends the same command with 0. The command is built only inside the Single's subscribed callback (`ulc.a` -> `wlc.e` -> `wlc.p`). Merely invoking toggleAudio and retaining the returned object sends nothing.

The parser's 5750 reply takes payload byte zero as an Integer and completes the pending command Single through `wlc.c(int)`. Unsolicited 5760 payload byte zero is emitted as Integer to the audio-state observable. The 5770 query maps payload byte zero equal to 1 to true and other values to false. Keep the raw command and notification integers in the test report; this inspection did not establish a complete success/error meaning table for all values.

There is one mutable pending audio-command emitter in `wlc.h`, and a separate query emitter in `wlc.i`. Serialize start and stop operations so a new command does not overwrite a pending acknowledgement. Bound waits locally; these methods do not install an explicit timeout in this wrapper. Disposing a Single or data subscription does not send toggleAudio(false) and does not establish that the microphone stopped. Cleanup must explicitly subscribe to the false command and bound its acknowledgement wait, then dispose test subscriptions.

### JNI Opus decoder

Exact class: `com.shenju.opus.OpusDecoderJni`.

| Member | Exact reflection signature |
| --- | --- |
| Singleton | Public static field `a` of the class itself |
| Constructor | Private no-argument constructor; use singleton instead |
| Decoder creation | `createDecoder(int sampleRate, int channels) -> long` |
| Decode | `decode(long handle, byte[] packet, byte[] pcmOutput, int packetLength) -> int` |
| Destruction | `destroyDecoder(long handle) -> void` |

Class initialization loads native library `opus-lib`. Catch reflection and native-loading errors. Create a dedicated handle for the test with arguments 16000 and 1. Never obtain or reuse AssistantManager's private handle. The existing client allocates 1920 samples times one channel times two bytes, giving a 3840-byte PCM buffer; each valid 40-byte packet is passed to decode with final argument 40. It treats a positive returned sample count as samples per channel and consumes count times channels times two output bytes. Reject decoder errors and out-of-bounds output lengths. The managed declaration does not document all native creation/error conventions, so do not dereference an invalid creation result or assume arbitrary return values are valid handles.

Use one decoder worker per chosen stream and serialize all decode/destroy operations. Preserve partial packet bytes across callbacks. The visible framing is one length byte 0x28 followed by 40 Opus bytes, with a one-byte resynchronization skip on an invalid length. Do not pass transport headers or the length byte into decode. Copy callback arrays before asynchronous work if ownership is not guaranteed. Destroy only an owned valid handle, exactly once, after the worker has stopped.

### Bounded test sequence to implement, not executed

1. Require ordinary AI/chat recording to be idle; keep the existing capability and engine configuration. Do not create AudioRecord, request communication routing or call legacy SCO APIs for this test.
2. Subscribe to b/c/e and q.getObserveAudioState before sending anything. For a brief baseline, collect counters only, identifying each stream separately. Decoder creation is local and does not capture audio by itself.
3. Subscribe to q.toggleAudio(true) using L1. Record the raw ack/error and state changes. Limit the active interval to ten seconds. Count byte arrays, bytes, framed Opus packets, decoded PCM sample frames, decode failures and video frames if preview was already active. Do not make AI/start-chat calls as an alternate trigger in the same experiment.
4. Decode an observed stream with its own handle. If multiple streams have bytes, inspect them separately rather than summing sample counts into an apparent duration. Duration is decoded samples per channel divided by 16000. An acknowledgement or state notification without data does not prove a working microphone path.
5. In finally, subscribe to q.toggleAudio(false), wait only within a local timeout, dispose all test subscriptions and stop/destroy owned decoder workers. Keep any audio local and private; counters and status values suffice for shared diagnostics.

No static evidence yet ties 5750 to b, c or e on this firmware. The bounded experiment is specifically intended to establish that missing relationship without HFP and without altering AI capability flags. A no-data result would show that this trigger/observable combination did not deliver audio in the tested state, not that all protocol microphone paths are absent.

Primary local sources: AbVideoPreview, obfuscated AbAIAssistant h, AppAIAssistant f7c, wlc, ulc, vs7, h9a, ai1, ra2, OpusDecoderJni and the two AssistantManager audio processors. These are interface/behavior findings, without redistribution of decompiled implementation.


### Additional side effects and second physical sequence reported by operator

The second physical sequence selected SCO type 7 and validated PCM before calling `presenter.t(true,1,fragment)`. The callback reported `onVideoPreviewIsOpen(status=-1,isOpen=true)` while the previous local flag was false, but no video frames arrived. The test reached its 15-second initialization bound with 219 AAC frames and zero H264 frames; its output row was discarded. Cleanup returned audio mode to 0 with SCO inactive. Therefore this callback alone is not evidence that camera streaming resumed.

A concrete original-app side effect matters for the protocol-only test: `receivePcmDataOfNoStorageDevice(byte[])` checks that PCM is nonempty and automatically calls `startNoStorageDeviceRecording()` when its recording flag is false. It then notifies Flutter and writes PCM to its WAV stream. If the original c() consumer is active, successful protocol microphone activation can therefore create an original-app recording independently of the test collector, even with no chat UI open. The test's added subscriptions do not suppress this behavior. Do not change AI or capability flags merely to hide it; observe whether the original recording starts and perform ordinary recording cleanup if needed.

The original `MainFragment.onAudioIsOpen` updates recording UI and starts a timer whose Consumer calls presenter.q(p82). Its immediate method contains no SCO/AudioRecord startup. This is not proof that every other original assistant listener is inert. Avoid sending overlapping 5750 commands, keep all test callback code exception-safe, copy received arrays before asynchronous processing, and count each observable separately.


## Resultados físicos informados pelo operador

Esta seção registra resultados comunicados pelo operador; a pesquisa estática não executou essas capturas.

| Teste privado | Resultado comunicado | Limite da conclusão |
| --- | --- | --- |
| Microfone SCO isolado por dez segundos | Entrada tipo 7 validada, PCM mono a 16000 Hz, 160000 sample frames capturados. O usuário ouviu áudio claro. | Confirma a rota de microfone isolada nesta unidade. |
| AAC mais H264, tentativa 1 | Ativar SCO foi seguido pelo encerramento da prévia. Zero quadros de vídeo e 52 quadros AAC. | Não demonstrou captura simultânea; não identifica sozinho a causa do encerramento. |
| AAC mais H264, tentativa 2 | Após SCO e PCM estarem prontos, foi solicitada a reabertura da prévia. Callback com status -1 e isOpen true, zero quadros de vídeo e 219 quadros AAC até o limite de inicialização. | O callback não provou retomada dos quadros H264. |

Os arquivos/linhas de saída das duas tentativas de áudio com vídeo foram descartados. A limpeza retornou o modo de áudio a 0 e SCO inativo. Nenhum desses resultados resolve a qualidade HD nem demonstra impossibilidade universal de áudio e vídeo simultâneos.

Os testes posteriores abaixo utilizam 5750 e os observables de protocolo sem HFP. O último argumento JNI de decode foi confirmado como o comprimento do pacote Opus, 40 bytes.

Efeito colateral a observar: se o consumidor original noStorage estiver ativo, receber PCM não vazio pode iniciar automaticamente uma gravação WAV local e notificações Flutter, independentemente do coletor de teste. Adicionar observadores não desativa esse consumidor. Manter áudio privado e tratar a gravação original pela limpeza normal, sem alterar capacidades ou configuração de IA.


## Protocolo sem SCO: áudio recebido e navegação automática

Atualização de 14/11/2026. Resultados físicos informados pelo operador, sem captura executada por esta pesquisa:

- O comando `q.toggleAudio(true)` recebeu ack 0. Cerca de 0,23 segundo depois houve callback de prévia com estado 0/false. Aproximadamente três segundos após o início, o sistema abriu `CustomFlutterActivity` e chegou `audioState = 1`.
- O observable c entregou 22 pacotes Opus válidos; o decoder produziu 7040 sample frames mono a 16000 Hz, equivalentes a 0,44 segundo de PCM. O modo de áudio permaneceu 0, sem SCO. O contador de q.frames registrou três callbacks após o início, sem estabelecer simultaneidade sustentada nem resolução nova.
- A janela pretendida era de dez segundos, mas o teste terminou aproximadamente aos três segundos porque a navegação ocultou a tela de teste. Não registrar isso como dez segundos completos ou ausência de falhas. A limpeza recebeu ack 0 para false e estado de áudio 0.

Isso confirma uma rota de áudio Opus pelo protocolo nesta unidade. Não confirma ainda um arquivo longo de áudio e vídeo simultâneos, nem qualidade HD.

### Cadeia exata que abre a tela de gravação

1. O construtor de `SharedAPPPhotoViewModel` cria `SharedAPPPhotoViewModel$c` e registra esse callback por `i72.F0(z62)`.
2. `F0` guarda um único listener no campo privado `i72.m`; não é uma lista de observadores. Substituí-lo indiscriminadamente alteraria o comportamento global.
3. `i72.j0(p82)` assina `q.getObserveAudioState()` com `i72$j`. Esse Consumer converte estado bruto igual a 1 para o primeiro boolean true de `onAudioIsOpen(ZIZI)` e notifica a tela e o listener global z62.
4. `SharedAPPPhotoViewModel$c.onAudioIsOpen` exige `noStorageDevice == 1`. Quando o primeiro boolean é true, consulta `BusinessApp.isAppForeground()`. Em foreground chama `SharedAPPPhotoViewModel.openRecordingInterface()`; em background apenas marca `isRecordingInBackground` e `shouldOpenRecordingWhenForeground`.
5. `openRecordingInterface()` lança `SharedAPPPhotoViewModel$j`. Ele verifica foreground e rota Flutter. Se já estiver em `/recording_manager`, tenta navegar para `/recording`; se a rota não estiver disponível ou for outra página, inicia Flutter pela rota `/recording`, com fallback semelhante em caso de erro.
6. `FlutterManager.startFlutterActivity` escolhe o engine. O caminho de gravação com engine em cache usa `CustomFlutterActivity.Companion.c(...)`; o caminho de engine novo usa `Companion.a(...)`.

Há também o caminho de retorno ao foreground: `checkAndHandleBackgroundRecording()` lança `SharedAPPPhotoViewModel$d`, verifica os flags e `AssistantManager.isNoStorageDeviceRecording()` e pode chamar a mesma abertura. Um bloqueio experimental apenas em `openRecordingInterface`, limitado à duração do teste, cobre ambas as entradas de navegação sem mudar flags de capacidade ou o motor de IA. A pesquisa não implementou esse bloqueio.

O recebimento de PCM e a gravação WAV pelo AssistantManager são outra cadeia. `sendRecordingStartedToFlutter` envia um evento pelo MethodChannel; a abertura explícita encontrada no Android vem do estado de áudio e do SharedAPPPhotoViewModel acima. Impedir navegação não interrompe automaticamente a gravação WAV original.

### Quem solicita parar a prévia

No caminho analisado, `q.toggleAudio(true)` -> `ulc.a` -> `wlc.e` -> builder 5750 não chama `toggleVideoPreview(false)`. O parser de ack 5750 apenas completa o emitter de áudio. O parser de estado 5760 publica o estado; `i72$j`, `MainFragment.onAudioIsOpen`, o listener global e a abertura de gravação não chamam diretamente o comando de desligar prévia.

A notificação de prévia 5710 é tratada por `wlc.g(NodeData)`, que publica seu payload[0] no observable da prévia. `i72$h` converte o valor 1 para aberto e outros valores para fechado, encaminhando o valor bruto como status. Esse caminho relata estado recebido; ele não envia uma parada como parte da conversão.

A cadeia pública do app para comandar a prévia é `z36.t(boolean,int,p82)` -> `i72.N0(boolean,int,p82)` -> `q.toggleVideoPreview(boolean)`. Os chamadores encontrados na MainFragment são `ivPlayClick(int)` e o callback de confirmação `stateFeatureUI$lambda$28`; eles invertem o flag local da prévia. Não foi localizada nessa árvore uma chamada automática com false motivada apenas pelo estado de áudio.

A prévia já foi reportada false antes da abertura Flutter no teste informado. Portanto suprimir a navegação pode remover a interrupção de lifecycle do teste, mas não explica sozinho o encerramento anterior da câmera. A evidência estática indica que o dispositivo reporta essa transição, sem demonstrar qual decisão interna de firmware a causou. Para atribuir uma parada a um pedido explícito do app, é necessário observar uma emissão 5720 com payload 0 na sequência, não apenas o callback de estado. Nenhuma captura de transporte adicional foi feita por esta pesquisa.

### Guarda da interface ao tentar abrir câmera com áudio ativo

`MainFragment.ivPlayClick(int)` verifica `ivPlay.isClickable()`, conexão e, ao abrir uma prévia fechada, `stateFeatureUI(0)`. Esse último retorna true imediatamente apenas quando áudio e gravação nativa estão ambos inativos. Com `isOpenAudio = true`, mostra `CommBottomConfirmDialog` e retorna false; o primeiro clique pode assim não enviar 5720.

O botão direito desse diálogo usa `stateFeatureUI$lambda$28` para solicitar a prévia. Não foi encontrada nesse callback uma chamada de parada do áudio. Para um experimento que pretende avaliar o dispositivo, zero quadros após `iv_play.performClick()` não prova exclusividade sem demonstrar que o pedido de abertura foi enviado, em vez de apenas surgir esse diálogo. A rota já identificada `presenter.t(true,1,fragment)` envia a solicitação pela mesma camada de SDK, passando ao largo dessa decisão visual; ela não altera capability flags.

Fontes primárias locais adicionais: `SharedAPPPhotoViewModel`, `$c`, `$d`, `$j`, `FlutterManager`, `CustomFlutterActivity$a`, `i72.F0`, `i72.j0`, `i72$j`, `i72$h`, `i72.N0`, `z36.t`, `MainFragment.ivPlayClick`, `stateFeatureUI`, `stateFeatureUI$lambda$28`, `wlc.g` e o builder de áudio. Nenhum código decompilado foi reproduzido.

## Testes físicos posteriores em 14/11/2026

O operador repetiu a janela de dez segundos com um bloqueio temporário da navegação automática, limitado ao teste privado. Não alterou flags de capacidades nem o motor de IA.

| Sequência | Evidência observada | Interpretação |
| --- | --- | --- |
| Áudio primeiro, clique normal de câmera após estado de áudio 1 | 342 pacotes Opus, 109440 sample frames PCM mono a 16000 Hz, 6,84 segundos de áudio após aproximadamente três segundos de conexão. Zero quadros de vídeo. | O clique normal podia abrir a confirmação visual. Esse resultado não testou diretamente a aceitação do comando de câmera. |
| Áudio primeiro, pedido explícito `presenter.t(true,1,fragment)` após estado de áudio 1 | Pedido registrado, callback de câmera com status 2 e fechado; observable da prévia com valor 2. 341 pacotes Opus, 109120 sample frames, 6,82 segundos PCM. Zero quadros de vídeo durante toda a janela e enquanto áudio estava ativo. | O app traduz status 2 como dispositivo ocupado. A câmera não iniciou nessa sequência mesmo com a navegação automática isolada. |

As janelas duraram dez segundos contados desde a solicitação do áudio, incluindo sua inicialização. Não foram dez segundos completos de PCM. O teste direto solicitou explicitamente a parada do áudio, recebeu ack 0 e estado 0, e também enviou fechamento da prévia. Após os testes, o modo Android era 0 e SCO estava inativo. Não foi produzido um MP4 válido de áudio mais vídeo; os WAVs ficaram privados.

A leitura passiva do modelo em cache confirmou `productCode=E13C-1`, `deviceType=E13C-1` e `version=1.0.2`. O objeto `SJUniWatch.getMBasicInfo()` estava ausente nessa leitura, portanto `hard_ver` e `chip_mode` continuam sem confirmação física.

Isso limita a conclusão aos caminhos e à unidade testados. Não demonstra uma impossibilidade universal do chip W20, de outra placa ou de firmware futuro. Não foi localizada uma rota HD nem uma imagem oficial compatível que prometesse resolver a concorrência. Os testes não modificaram o firmware e a versão pública não recebeu o gravador experimental que falhou.

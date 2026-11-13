# XK One Pro: video recording controls and validation limits

Checked on 2026-11-13. Português / English.

## Português

**Resultado na unidade testada, firmware 1.0.2:** o proprietário informou que duas pressões curtas no botão físico direito iniciam **gravação de voz**, e não vídeo. Um clique isolado não deu indicação sonora. A foto acionada pelo aplicativo chegou ao álbum interno do LensFix. Ainda não há gravação de vídeo ou exportação de vídeo validadas.

O guia de operação dentro do próprio Lensmoo 1.8.0 também foi conferido e associa o **botão frontal direito** a foto com um clique, áudio com dois cliques e parada da gravação com um clique durante a gravação. O botão traseiro é outro controle. Essa orientação do aplicativo corresponde ao relato do proprietário; o guia comercial genérico citado abaixo descreve um comportamento diferente.

O firmware informou `noStorageDevice: 1` ao aplicativo. O Lensmoo usa essa capacidade para ocultar controles de vídeo armazenado. Isso descreve o perfil informado pelo dispositivo; não é prova física de ausência de memória nem estabelece se existe transmissão ao vivo utilizável.

Um guia comercial da família XK One Pro descreve duas pressões para iniciar vídeo e uma para parar, mas **essa instrução não corresponde ao comportamento observado nesta unidade**. Não a use como um comando de vídeo validado para esse firmware. [Guia do produto, seção em inglês, página impressa 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

Isso descreve o manual, não uma gravação verificada pelo LensFix. O SDK independente testou uma unidade com firmware 1.0.2: o gesto não produziu mídia armazenada, e os experimentos não conseguiram exportar vídeo ou áudio por Bluetooth SPP. Esse resultado não demonstra impossibilidade em todas as revisões de hardware ou firmware. [Testes físicos do SDK, seções 1, 7 e 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

A correção de encerramento do LensFix não implementa gravação nem exportação de vídeo. Um teste curto no aparelho e a reprodução do arquivo são necessários antes de afirmar que funcionam.

## English

**Result on the tested firmware 1.0.2 unit:** the owner reported that a double short-press of the right physical button starts **voice recording**, not video. A single press gave no audible indication. A photo triggered from the app reached the LensFix internal album. Video recording and video export remain unvalidated.

The built-in operation guide in Lensmoo 1.8.0 was also checked. It assigns the **right front physical button** to a single press for a photo, a double press for audio, and a single press while recording to stop. The rear button is a separate control. The app's guidance matches the owner's report; the generic product-family guide below describes different behavior.

The firmware reported `noStorageDevice: 1`. Lensmoo uses this capability to hide stored-video controls. This is the device-reported profile; it is not physical proof of absent memory and does not establish whether a usable live stream exists.

A product-family guide describes a double press to start video and a single press to stop, but **those instructions do not match the tested unit**. Do not treat them as verified video controls for this firmware. [Product guide, English section, printed page 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

These are documented controls, not LensFix recording validation. The independent SDK tested firmware 1.0.2: the gesture produced no stored media, and video/audio export over Bluetooth SPP was not achieved. This does not establish impossibility across all hardware or firmware revisions. [SDK physical tests, sections 1, 7 and 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

The LensFix crash repair does not implement video recording or export. A short recording and successful file playback are still required to verify those functions.

## Evidence boundaries

| Evidence | What it establishes |
| --- | --- |
| Product-guide PDF | Advertised button controls, including distinct physical and touch controls. |
| Independent SDK experiments | Results for the author's tested unit, not every product sold under the XK One Pro name. |
| LensFix repair | A crash fix. It does not establish camera/video capabilities. |

The negative export result concerns the investigated SPP path. This note does not claim a verified alternative export route or recommend firmware changes.

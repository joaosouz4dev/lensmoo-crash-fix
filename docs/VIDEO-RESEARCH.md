# XK One Pro: video recording controls and validation limits

Checked on 2026-11-13. Português / English.

## Português

**Resultado na unidade testada, firmware 1.0.2:** o proprietário informou que duas pressões curtas no botão físico direito iniciam **gravação de voz**, e não vídeo. Um clique isolado não deu indicação sonora. A foto acionada pelo aplicativo chegou ao álbum interno do LensFix e foi exportada à galeria do Android.

**Prévia ao vivo confirmada:** um diagnóstico temporário recebeu quadros H.264 via Bluetooth. Após remover o cabeçalho `[tipo=2:1 byte][tamanho:4 bytes LE]`, foram reconstruídos 295 quadros distintos em um MP4 de 10 segundos, 320x240, sem áudio. O arquivo foi decodificado integralmente e copiado ao celular em `Movies/LensFix/`, onde o MediaStore o reconheceu como vídeo de 10 segundos. Neste teste o MP4 foi reconstruído no computador; a gravação diretamente pelo novo botão do aplicativo está em desenvolvimento e ainda precisa de validação no aparelho. Nenhuma imagem ou vídeo do teste é publicada neste repositório.

O guia de operação dentro do próprio Lensmoo 1.8.0 também foi conferido e associa o **botão frontal direito** a foto com um clique, áudio com dois cliques e parada da gravação com um clique durante a gravação. O botão traseiro é outro controle. Essa orientação do aplicativo corresponde ao relato do proprietário; o guia comercial genérico citado abaixo descreve um comportamento diferente.

O firmware informou `noStorageDevice: 1` ao aplicativo. O Lensmoo usa essa capacidade para ocultar controles de vídeo armazenado. Isso descreve o perfil informado pelo dispositivo; não é prova física de ausência de memória nem estabelece se existe transmissão ao vivo utilizável.

Um guia comercial da família XK One Pro descreve duas pressões para iniciar vídeo e uma para parar, mas **essa instrução não corresponde ao comportamento observado nesta unidade**. Não a use como um comando de vídeo validado para esse firmware. [Guia do produto, seção em inglês, página impressa 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

**Resultado histórico do SDK independente:** seus testes não obtiveram mídia armazenada com o gesto nem exportação de vídeo/áudio pela rota investigada. Nossa captura posterior da prévia ao vivo amplia esse resultado para a unidade testada aqui, sem comprovar recuperação de gravações armazenadas dentro dos óculos. [Testes físicos do SDK, seções 1, 7 e 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

O APK publicado LensFix 1.0.0 corrige o encerramento do app e ainda não acrescenta um botão de gravação de vídeo. O suporte nativo em desenvolvimento precisa salvar e reproduzir um MP4 diretamente no celular antes de ser anunciado como validado.

## English

**Result on the tested firmware 1.0.2 unit:** the owner reported that a double short-press of the right physical button starts **voice recording**, not video. A single press gave no audible indication. An app-triggered photo reached the internal album and was exported to the Android gallery.

**Live preview confirmed:** temporary instrumentation received H.264 frames over Bluetooth. Removing the `[type=2:1 byte][length:4-byte LE]` header allowed reconstruction of a ten-second, 320x240 silent MP4 with 295 distinct decoded frames. The whole file decoded successfully and was copied back to `Movies/LensFix/`, where Android MediaStore recognized its ten-second duration. This test rebuilt the MP4 on a computer; direct recording through the new app button is in development and still needs device validation. No camera images or videos are published in this repository.

The built-in operation guide in Lensmoo 1.8.0 was also checked. It assigns the **right front physical button** to a single press for a photo, a double press for audio, and a single press while recording to stop. The rear button is a separate control. The app's guidance matches the owner's report; the generic product-family guide below describes different behavior.

The firmware reported `noStorageDevice: 1`. Lensmoo uses this capability to hide stored-video controls. This is the device-reported profile; it is not physical proof of absent memory and does not establish whether a usable live stream exists.

A product-family guide describes a double press to start video and a single press to stop, but **those instructions do not match the tested unit**. Do not treat them as verified video controls for this firmware. [Product guide, English section, printed page 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

**Historical SDK result:** its experiments did not obtain stored media through the gesture or video/audio export through the investigated route. The later live-preview capture here extends those findings for this unit, without proving retrieval of recordings stored inside the glasses. [SDK physical tests, sections 1, 7 and 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

Published LensFix 1.0.0 fixes the crash and does not add a video-recording button. The native recording implementation in development must save and play an MP4 directly on the phone before it is described as validated.

## Evidence boundaries

| Evidence | What it establishes |
| --- | --- |
| Product-guide PDF | Advertised button controls, including distinct physical and touch controls. |
| Independent SDK experiments | Historical results for the author's tested unit; not a universal limitation. |
| LensFix repair | A crash fix. It does not establish camera/video capabilities. |
| Later live-preview diagnostic | A valid ten-second silent H.264 clip from this unit, rebuilt on a computer and transferred back to the phone. |

The live-preview capture is distinct from retrieving older stored recordings. No firmware change was required for this test.

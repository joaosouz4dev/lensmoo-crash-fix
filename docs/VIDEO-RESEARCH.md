# XK One Pro: video recording controls and validation limits

Checked on 2026-11-13. Português / English.

## Português

**Resultado na unidade testada, firmware 1.0.2:** o proprietário informou que duas pressões curtas no botão físico direito iniciam **gravação de voz**, e não vídeo. Um clique isolado não deu indicação sonora. A foto acionada pelo aplicativo chegou ao álbum interno do LensFix e foi exportada à galeria do Android.

**Prévia ao vivo confirmada:** um diagnóstico temporário recebeu quadros H.264 via Bluetooth. Após remover o cabeçalho `[tipo=2:1 byte][tamanho:4 bytes LE]`, foram reconstruídos 295 quadros distintos em um MP4 de 10 segundos, 320x240, sem áudio. Esse primeiro MP4 foi reconstruído no computador e copiado ao celular.

**Gravação direta no celular confirmada:** a implementação nativa posterior acrescenta **Gravar vídeo** à tela do dispositivo. Um teste gerou diretamente no Android um MP4 de **14,12 segundos**, H.264 320x240, sem áudio, com **427 quadros distintos decodificados sem erro**. O MediaStore registrou o arquivo em `Movies/LensFix/`, e o proprietário confirmou que ele abriu no celular. A versão 1.1.0 incorpora esse suporte. Veja [como usar](../README.pt-BR.md#como-gravar-vídeo-nos-xk-one-pro) e [os limites da validação](VALIDATION.md). Nenhuma imagem ou vídeo do teste é publicada neste repositório.

O guia de operação dentro do próprio Lensmoo 1.8.0 também foi conferido e associa o **botão frontal direito** a foto com um clique, áudio com dois cliques e parada da gravação com um clique durante a gravação. O botão traseiro é outro controle. Essa orientação do aplicativo corresponde ao relato do proprietário; o guia comercial genérico citado abaixo descreve um comportamento diferente.

O firmware informou `noStorageDevice: 1` ao aplicativo. O Lensmoo usa essa capacidade para ocultar controles de vídeo armazenado. Isso descreve o perfil informado pelo dispositivo; não é prova física de ausência de memória nem estabelece se existe transmissão ao vivo utilizável.

Um guia comercial da família XK One Pro descreve duas pressões para iniciar vídeo e uma para parar, mas **essa instrução não corresponde ao comportamento observado nesta unidade**. Não a use como um comando de vídeo validado para esse firmware. [Guia do produto, seção em inglês, página impressa 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

**Resultado histórico do SDK independente:** seus testes não obtiveram mídia armazenada com o gesto nem exportação de vídeo/áudio pela rota investigada. Nossa captura posterior da prévia ao vivo amplia esse resultado para a unidade testada aqui, sem comprovar recuperação de gravações armazenadas dentro dos óculos. [Testes físicos do SDK, seções 1, 7 e 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

O LensFix 1.0.0 contém a correção original de encerramento. A gravação da prévia é uma função nova da versão 1.1.0; não é recuperação de arquivos antigos dos óculos nem gravação autônoma com o celular desconectado.

## English

**Result on the tested firmware 1.0.2 unit:** the owner reported that a double short-press of the right physical button starts **voice recording**, not video. A single press gave no audible indication. An app-triggered photo reached the internal album and was exported to the Android gallery.

**Live preview confirmed:** temporary instrumentation received H.264 frames over Bluetooth. Removing the `[type=2:1 byte][length:4-byte LE]` header allowed reconstruction of a ten-second, 320x240 silent MP4 with 295 distinct decoded frames. This first clip was rebuilt on a computer and copied back to the phone.

**Direct phone recording confirmed:** the later native implementation adds **Gravar vídeo** to the device screen. A test created a **14.12-second** H.264 320x240 silent MP4 directly on Android, with **427 distinct frames decoded without errors**. MediaStore registered the file under `Movies/LensFix/`, and the owner confirmed it opened on the phone. Version 1.1.0 includes this feature. See [usage instructions](../README.md#video-recording-on-xk-one-pro) and [validation limits](VALIDATION.md). No camera images or videos are published in this repository.

The built-in operation guide in Lensmoo 1.8.0 was also checked. It assigns the **right front physical button** to a single press for a photo, a double press for audio, and a single press while recording to stop. The rear button is a separate control. The app's guidance matches the owner's report; the generic product-family guide below describes different behavior.

The firmware reported `noStorageDevice: 1`. Lensmoo uses this capability to hide stored-video controls. This is the device-reported profile; it is not physical proof of absent memory and does not establish whether a usable live stream exists.

A product-family guide describes a double press to start video and a single press to stop, but **those instructions do not match the tested unit**. Do not treat them as verified video controls for this firmware. [Product guide, English section, printed page 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

**Historical SDK result:** its experiments did not obtain stored media through the gesture or video/audio export through the investigated route. The later live-preview capture here extends those findings for this unit, without proving retrieval of recordings stored inside the glasses. [SDK physical tests, sections 1, 7 and 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

LensFix 1.0.0 contains the original crash fix. Preview recording is new in version 1.1.0; it does not retrieve older glasses recordings or record autonomously while the phone is disconnected.

## Evidence boundaries

| Evidence | What it establishes |
| --- | --- |
| Product-guide PDF | Advertised button controls, including distinct physical and touch controls. |
| Independent SDK experiments | Historical results for the author's tested unit; not a universal limitation. |
| LensFix repair | A crash fix. It does not establish camera/video capabilities. |
| Later live-preview diagnostic | A valid ten-second silent H.264 clip from this unit, rebuilt on a computer and transferred back to the phone. |
| Native recording test | A valid phone-generated 14.12-second silent MP4, 427 distinct decoded frames, and owner-confirmed opening on the phone. |

The live-preview capture is distinct from retrieving older stored recordings. No firmware change was required for this test.

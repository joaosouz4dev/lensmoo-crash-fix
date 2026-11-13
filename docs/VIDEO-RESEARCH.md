# XK One Pro: video recording controls and validation limits

Checked on 2026-11-13. Português / English.

## Português

Com os óculos ligados e conectados, o guia XK One Pro orienta:

1. Pressione brevemente duas vezes o **botão físico direito** para iniciar vídeo.
2. Pressione brevemente uma vez para parar e salvar.

Não confunda com a superfície de toque direita: dois toques nela avançam a música. Confira o desenho e o manual fornecido com sua unidade. [Guia do produto, seção em inglês, página impressa 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

Isso descreve o manual, não uma gravação verificada pelo LensFix. O SDK independente testou uma unidade com firmware 1.0.2: o gesto não produziu mídia armazenada, e os experimentos não conseguiram exportar vídeo ou áudio por Bluetooth SPP. Esse resultado não demonstra impossibilidade em todas as revisões de hardware ou firmware. [Testes físicos do SDK, seções 1, 7 e 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

A correção de encerramento do LensFix não implementa gravação nem exportação de vídeo. Um teste curto no aparelho e a reprodução do arquivo são necessários antes de afirmar que funcionam.

## English

With the glasses powered on and connected, the XK One Pro guide instructs:

1. Short-press the **right physical button twice** to start video.
2. Short-press once to stop and save.

The right touch surface is different: its double tap skips a music track. Check the diagram and manual supplied with your unit. [Product guide, English section, printed page 2](https://ae01.alicdn.com/kf/Sdaf30abdee104e2fa23e131274ad4d08h.pdf).

These are documented controls, not LensFix recording validation. The independent SDK tested firmware 1.0.2: the gesture produced no stored media, and video/audio export over Bluetooth SPP was not achieved. This does not establish impossibility across all hardware or firmware revisions. [SDK physical tests, sections 1, 7 and 9](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

The LensFix crash repair does not implement video recording or export. A short recording and successful file playback are still required to verify those functions.

## Evidence boundaries

| Evidence | What it establishes |
| --- | --- |
| Product-guide PDF | Advertised button controls, including distinct physical and touch controls. |
| Independent SDK experiments | Results for the author's tested unit, not every product sold under the XK One Pro name. |
| LensFix repair | A crash fix. It does not establish camera/video capabilities. |

The negative export result concerns the investigated SPP path. This note does not claim a verified alternative export route or recommend firmware changes.

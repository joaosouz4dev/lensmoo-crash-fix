# XK One Pro: qualidade de vídeo, gravação nativa e caminhos de exportação

Análise de código e fontes primárias em 2026-11-13, complementada pelos testes físicos controlados descritos abaixo. Nenhum firmware foi alterado.

## Resultado

Não foi encontrada uma rota comprovada de vídeo com resolução superior à prévia de 320x240 observada nesta investigação. Existe um comando distinto de gravação no protocolo usado pelo aplicativo, mas sua presença no SDK não comprova suporte, armazenamento ou resolução maior nesta unidade com firmware 1.0.2 e `noStorageDevice=1`.

Em 14/11/2026, os testes de áudio confirmaram SCO e Opus pelo protocolo separadamente. O pedido direto de abertura da câmera com áudio do protocolo ativo retornou status 2, dispositivo ocupado, e zero quadros de vídeo. A consulta oficial mostrou Sem novas versões para o firmware 1.0.2. Vídeo HD com áudio permanece sem solução comprovada. Ver [resultados de áudio](AUDIO-RESEARCH.md), [consulta de firmware](FIRMWARE-RESEARCH.md) e [variante de hardware](HARDWARE-VARIANT.md).

O aplicativo Android aceita esse modelo como dispositivo sem armazenamento e esconde controles de gravação e importação relacionados a mídia no dispositivo. A gravação MP4 adicionada ao LensFix salva o fluxo recebido pelo telefone. Ela não aumenta a resolução transmitida pela câmera.

## Teste físico comunicado pela investigação principal

Com a prévia ativa, início solicitado às 18:27:05 e parada solicitada cerca de dez segundos depois: o callback após a parada informou status 2; `video_num` e `record_num` permaneceram em zero antes e depois. Isso não demonstrou gravação nativa nem gerou um arquivo de maior resolução verificável. Como esse status corresponde ao aviso de dispositivo ocupado no aplicativo, não é prova de comando não implementado.

O teste foi repetido com fechamento explícito da prévia, confirmado por resposta de estado fechado/status 0. O início nativo foi solicitado às 18:34:42 e a parada dez segundos depois. O callback após parar informou status 0; as consultas imediata e posterior de mídia continuaram com `video_num=0` e `record_num=0`. Uma resposta de parada aceita não comprova que a gravação começou. Também não foi obtido um arquivo nativo nesse cenário. Esses resultados deixam a qualidade maior sem solução comprovada, sem estabelecer a resolução máxima física do sensor.

## Quatro resoluções diferentes

| Elemento | Evidência | Limite da conclusão |
| --- | --- | --- |
| Prévia H.264 recebida nesta investigação | A investigação principal informou 295 quadros distintos em cerca de dez segundos e SPS de 320x240; o usuário confirmou o MP4. | Isso mede a prévia, não a resolução máxima do sensor nem uma gravação armazenada. |
| Formato do decoder original no telefone | `MainFragment.startDecoder()` configura localmente um decoder AVC com 360x240, bitrate de 2.000.000 e 20 fps. | São parâmetros do decoder Android. Não são enviados aos óculos e não selecionam qualidade da câmera. |
| Fotografia JPEG | O SDK independente documenta fotos de 640x480 em sua unidade. | É outro caminho de captura e outro teste; não comprova vídeo 640x480 nem a fotografia máxima desta unidade. |
| Gravação nativa nos óculos | Há controle booleano separado e estado associado no SDK do app. | Não foi obtido nesta pesquisa um arquivo nativo com resolução verificável. |

A leitura de `preview_width` e `preview_height` no parser não equivale a uma API para alterá-los. O helper `wlc.i(width,height,quality)` gera um cabeçalho JPEG local e também não é um comando para configurar o sensor. O comportamento de um decoder Android é descrito em [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec).

## Rotas reais encontradas no aplicativo

As seguintes assinaturas e relações foram obtidas por inspeção privada do Lensmoo 1.8.0. Esta nota contém descrições de interface e resultados da análise, sem código decompilado, dados de usuário ou identificadores de aparelhos.

| Operação | Interface | Builder real / mensagem |
| --- | --- | --- |
| Abrir ou fechar prévia | `AbVideoPreview.toggleVideoPreview(boolean)` | `wlc.u(byte)` constrói `5720`, com um byte de abertura/fechamento. |
| Consultar estado da prévia | `AbVideoPreview.getVideoPreviewState()` | `wlc.t()` constrói `5711`. |
| Iniciar ou parar gravação pelo dispositivo | `AbVideoPreview.toggleVideoPreviewShoot(boolean)` | `wlc.x(byte)` constrói `5780`, com um byte de abertura/fechamento. |
| Consultar estado de gravação | `AbVideoPreview.getVideoPreviewShootState()` | `wlc.w()` constrói `57A0`; o parser também observa notificações `5790`. |
| Tirar foto | `AbVideoPreview.toggleVideoPreviewPicture()` | `wlc.j()` constrói `57B0`. |
| Ler contagens de mídia | `UNIWatchMate.getMediaCountInfo()` | Telemetria de fotos, vídeos e áudios, associada ao caminho `5713`. |

Essas interfaces não recebem largura, altura, bitrate ou perfil H.264. Alguns textos de log citam números diferentes dos efetivamente construídos: o log de prévia menciona `5710`, e o de gravação menciona `102A`. Os bytes dos builders acima são a evidência relevante.

## Clique original e cadeia para um teste controlado posterior

O listener original de `llCameraShoot` é `MainFragment.stateFeatureUI$lambda$29(MainFragment, View)`. Ele inicia o timeout da UI, desabilita o botão e chama `z36.u(!isOpenVideoPreviewShoot, fragment)`.

A cadeia desse presenter é:

`z36.u(boolean, p82)` -> `i72.O0(boolean, p82)` -> `UNIWatchMate.getWmApps().q()` -> `AbVideoPreview.toggleVideoPreviewShoot(boolean)` -> builder `5780`.

O objeto do fragment implementa o callback `p82`. Para um teste posterior, um booleano explícito evita alternar com base em um campo visual desatualizado. O listener não faz uma checagem de `noStorageDevice`; a restrição encontrada está na visibilidade configurada por `showItemsByFunctionSupport()`.

O retorno chega a `MainFragment.onVideoPreviewShootIsOpen(boolean, int, boolean, int)`. Esse método atualiza `isOpenVideoPreviewShoot`, botões e indicador de tempo. Ele não cria um muxer nem escreve um arquivo no telefone. Ao parar, solicita atualização de contagens por `z36.v(p82)` -> `i72.q0(p82)` -> `UNIWatchMate.getMediaCountInfo()`.

### Fechamento explícito da prévia e significado do parâmetro inteiro

O método `z36.t(boolean, int, p82)` encaminha a `i72.N0(boolean, int, p82)`. Nesse ponto, somente o booleano é passado a `AbVideoPreview.toggleVideoPreview(boolean)`. O inteiro fica no objeto de callback `i72$b0` e retorna como o quarto argumento de `onVideoPreviewIsOpen(boolean, int, boolean, int, int)`. Ele participa do tratamento visual de erros e não é enviado ao builder. Portanto não seleciona resolução, modo de câmera ou transporte.

O clique manual de prévia, `stateFeatureUI$lambda$28(MainFragment, View)`, usa `t(!isOpenVideoPreview, 1, fragment)`. A parada explícita equivalente é `t(false, 1, fragment)`, ou a cadeia correspondente `i72.N0(false, 1, fragment)`. Os encerramentos de ciclo de vida em `onStop` e `onHiddenChanged` passam por `ivPlayClick(0)` e usam inteiro zero. Ambos os caminhos resultam no mesmo comando de fechamento `5720` com booleano falso; o inteiro muda apenas o contexto do callback no telefone.

No `q0` inspecionado, o ramo `noStorageDevice=1` escreve um diagnóstico dizendo que não consultará contagens, mas não retorna antes da consulta. Outro detalhe: status 2 no callback de gravação corresponde ao aviso `device_fail_busy`, que indica ocupação do dispositivo. Ele sozinho não comprova ausência de suporte.

Interpretação: essa cadeia é compatível com controle de gravação pelo dispositivo, separado do muxer local de prévia. Ela não demonstra que a gravação usa flash, que está disponível nesta revisão, nem que produz um stream de maior resolução para o telefone.

## Exportação e Wi-Fi

O caminho de biblioteca de fotos inspecionado usa `getWmApps().n()` / `AbPhotoLibrary`, implementado por `xhc`. Ele possui enumeração e transferência de imagens em `7200`, `7310`, `7300` e conclusão `7500`. `7400` é exclusão de foto e não deve ser confundido com download. Não foi identificado nessa interface um método específico que exporte MP4 nativo em maior resolução.

Não foi encontrada configuração de SSID, ponto de acesso, URL RTSP/RTMP ou transporte Wi-Fi dos óculos nos contratos do SDK, no `devicelib` e no fluxo de mídia do aplicativo inspecionados. Uma referência a Wi-Fi no serviço Android informa conectividade do telefone; ela não constitui uma rota de transferência dos óculos. A ausência encontrada no código não prova que todo hardware vendido com esse nome seja incapaz disso.

## Como interpretar o outro SDK

O projeto independente documenta fotos JPEG de 640x480, tentativas sem sucesso de extração de vídeo e ausência de AP Wi-Fi em seu ambiente. Isso descreve os testes daquela unidade e daquele cliente. A prévia H.264 conseguida nesta investigação é evidência posterior de um caminho que funciona nesta configuração. Não se deve manter a conclusão universal de que vídeo por SPP é impossível. [Relatório de validação independente](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/VALIDATION.md).

As próprias notas antigas do SDK contêm rótulos conflitantes para alguns nodes. Por exemplo, a tabela completa identifica `2410` e `2420` como idioma, enquanto uma lista anterior os chama de parâmetros de prévia. Eles não são evidência de ajuste de resolução. [Inventário de nodes](https://github.com/beneduzi/xk-one-pro-sdk/blob/master/docs/NODES.md).

## Previsões que distinguem as possibilidades

Critérios usados nos testes e em qualquer investigação posterior:

1. Registrar contagens e estado antes; usar o mesmo caminho conhecido `toggleVideoPreviewShoot(true)` por intervalo curto e depois `false`, observando código de retorno e estado. Não concluir suporte somente pela UI mudar.
2. Se a contagem de vídeos aumentar, identificar um método de exportação do arquivo e verificar seu codec e dimensões. Contagem maior não comprova qualidade maior.
3. Se não houver arquivo, contagem ou estado de gravação consistente, considerar a rota indisponível nesta configuração. Uma resposta de ocupado deve ser distinguida de uma resposta de comando não implementado ou parâmetro inválido.
4. Se o comando alterar o fluxo recebido, comparar os novos SPS e dimensões. Se permanecerem 320x240, o muxer local continuará gravando essa resolução.

Elevar tamanho de decoder, bitrate de um arquivo reencodado, escala visual ou dimensões declaradas do MP4 não recupera detalhe que não existe nos quadros de origem. A melhoria real depende de obter outro fluxo ou arquivo com mais informação. Os testes físicos acima não usaram comandos desconhecidos, exclusão de mídia, atualização ou alteração de firmware.

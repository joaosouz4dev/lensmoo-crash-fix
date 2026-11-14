# Variante de hardware: W20, SC101 e limites de identificação

Pesquisa em 14/11/2026, somente leitura. Nenhum comando foi enviado aos óculos ou celular, nenhum áudio foi capturado nesta pesquisa, nenhum firmware foi baixado ou instalado e ninguém foi contatado. Os resultados físicos abaixo foram informados pelo operador da sessão.

## Anúncio específico e unidade observada

A página pública do produto foi lida pelo operador no navegador. O extrator desta pesquisa não conseguiu abri-la. A referência preservada contém somente os identificadores públicos do produto e SKU: [produto Temu](https://www.temu.com/br/goods.html?goods_id=608212849949713&sku_id=127303904418947).

| Informação | Origem | O que está estabelecido |
| --- | --- | --- |
| Marca ANJ.GG e vendedor ANJGGSHOP | Anúncio específico, leitura do operador | Identidade comercial declarada, sem identificação independente da placa. |
| Chip Shenju W20 | Anúncio | Compatível com a família de protocolo investigada, mas ainda sem leitura da marcação física ou identificação de chipset desta placa. |
| Sensor SC101, 1280 x 720 a 15 fps | Anúncio | Capacidade anunciada da câmera. Não especifica o sufixo do sensor nem garante vídeo H264 nessa resolução. |
| Memória 32 MB, Bluetooth 5.4, bateria 260 mAh | Anúncio | Valores declarados pelo vendedor, ainda não medidos. Memória sem tipo não distingue RAM, flash de firmware e armazenamento de mídia. |
| Anatel 23173-23-11470 | Anúncio | Número declarado; correspondência oficial com estes óculos não foi confirmada. |
| Tirar fotos, ouvir música e gravar áudio | Texto lido do anúncio | Não foi encontrado no texto um modo específico de gravação de vídeo HD. A indicação de fps do sensor não substitui esse compromisso funcional. |
| Modelo reportado E13C-1, firmware 1.0.2, noStorageDevice = 1 | Estado obtido anteriormente pelo aplicativo/operador | Identificação lógica da unidade. Não prova por si o fabricante da placa, sensor exato ou capacidade física total de memória. |
| Vídeo H264 recebido 320 x 240 | Captura anterior validada pelo operador | Resolução da rota de prévia que efetivamente funcionou. Não mede a resolução máxima do sensor. |

Os arquivos fotográficos finais usam processamento `SRLib.processImage(...,2)` e nomes `-sr.jpeg`, conforme inspeção do aplicativo informada pelo operador. A resolução original anterior a esse processamento ainda não foi medida. Não usar a dimensão da foto processada como prova de captura nativa ou de vídeo disponível.

## O que a Shenju confirma sobre W20

A página oficial W20 descreve um SoC com Bluetooth 5.4 dual-mode, processador RISC32, interface de câmera DVP, codecs JPEG e H264, USB 2.0, interfaces SD/eMMC e flash serial, entre outros periféricos. A página não especifica a resolução/fps máximos do H264 nem demonstra captura simultânea com SCO para uma placa E13C-1. [Shenju W20](https://www.shenjugroup.com/en-US/products/W20-15.html)

Esses são recursos do chip: uma interface SD/eMMC no SoC não significa cartão, memória de mídia ou porta USB de exportação implementados no produto. Inversamente, `noStorageDevice = 1` é uma capacidade lógica reportada pelo SDK e não uma medição da ausência de toda flash na placa. O anúncio de 32 MB não contradiz necessariamente esse flag.

A página oficial foi obtida em HTML por leitura pública. Não foi encontrado nela um link de download de SDK, datasheet completo ou firmware. Buscas direcionadas ao domínio oficial por W20 com SDK e firmware não localizaram um pacote público compatível e identificado para esta unidade. Isso descreve o resultado da pesquisa, sem afirmar que a Shenju não disponha de ferramentas privadas.

## SC101 não identifica um único sensor

O catálogo oficial SmartSens indexa **SC101AP** como sensor de 1 MP, matriz 1288 x 728, interface DVP e máximo de 30 fps. O nome abreviado SC101 do anúncio não permite concluir que esse seja o componente instalado. [Catálogo SmartSens](https://www.smartsenstech.com/products_list)

Há também **SC101IOT** no driver mantido pela Espressif, com saída 1280 x 720 e DVP. A configuração oficial desse driver inclui `SC101IOT_720P_15FPS_ENABLED`, descrita como 720p a 15 fps quando XCLK é 20 MHz, além de uma configuração VGA a 25 fps. [Componente oficial Espressif](https://components.espressif.com/components/espressif/esp_cam_sensor/versions/2.7.0/readme?language=en), [configuração do driver](https://github.com/espressif/esp32-camera/blob/master/Kconfig)

A coincidência entre 720p/15 fps e o anúncio torna SC101IOT uma hipótese concreta, não uma identificação confirmada. O driver Espressif é fonte primária para a implementação em ESP32; ele não é um firmware W20 nem deve ser instalado nestes óculos. Não foi aplicada nenhuma tabela de registradores do sensor.

Mesmo com um sensor 720p confirmado, vídeo nessa qualidade depende de sua configuração no firmware, caminho de processamento, encoder, buffers, transporte e destino de armazenamento. Ampliar os quadros 320 x 240 do fluxo recebido não recupera detalhe nativo de 720p.

## Homologação Anatel

O caminho público de consulta consultado redirecionou para autenticação e não forneceu o certificado nesta pesquisa. Não foi feito login. Portanto permanecem desconhecidos o detentor, modelo, fabricante e anexos efetivamente vinculados a 23173-23-11470. Não usar esse número como chave de firmware ou prova de sensor. [Consulta oficial de produtos](https://sistemas.anatel.gov.br/mosaico/sch/publicView/listarProdutosHomologados.xhtml)

Uma consulta oficial útil precisa relacionar número, modelo e fabricante, com anexos que permitam identificar a placa. O número exibido pelo vendedor, isoladamente, não estabelece essa relação.

## Firmware e rota HD compatível

A tela oficial de atualização já mostrou versão atual 1.0.2 e Sem novas versões; nenhum flash ocorreu. O APK 1.8.1-5 anunciado no canal público não foi obtido porque o CDN retornou 404. Versão de aplicativo e firmware dos óculos são distribuições distintas. Detalhes da consulta e identificadores necessários estão em [FIRMWARE-RESEARCH.md](FIRMWARE-RESEARCH.md).

Até aqui não foi localizado um firmware oficial HD ou um SDK público de firmware com correspondência demonstrada para E13C-1/noStorageDevice1. Para avaliar uma imagem concreta faltam a relação oficial entre `productCode`/`deviceType`, `hard_ver`, `chip_mode`, `prod_mode`, sensor com sufixo, organização de memória e revisão da placa, além da descrição da release e recuperação. O nome W20 ou SC101 sozinho não estabelece compatibilidade.

O SDK independente explica o protocolo do aplicativo, sem fornecer firmware de placa ou uma API comprovada de seleção de resolução. Os contratos de prévia analisados recebem liga/desliga e não width/height/bitrate. Os limites desse projeto e de outra unidade não devem ser tratados como limites físicos universais do W20. [Projeto XK One Pro SDK](https://github.com/beneduzi/xk-one-pro-sdk), [investigação local de vídeo](VIDEO-QUALITY-RESEARCH.md).

## Áudio confirmado e concorrência ainda aberta

O operador confirmou captura privada de dez segundos com entrada Bluetooth SCO, tipo 7, PCM mono a 16000 Hz, e o usuário ouviu áudio claro. Isso confirma uma rota de microfone utilizável nesta unidade, sem demonstrar simultaneidade com a câmera.

No primeiro teste AAC mais H264, ativar SCO foi seguido pelo callback `onVideoPreviewIsOpen` com status 0 e false, sem uma solicitação anterior de parada pelo aplicativo. O resultado continha 52 quadros AAC e zero quadros de vídeo e foi descartado. Esse teste mostra que a prévia encerrou durante aquela sequência. Não identifica ainda se a causa foi política do aplicativo, transição de modo do firmware, recursos compartilhados ou ordem de inicialização.

Nos testes posteriores, reabrir a prévia após conectar SCO não trouxe quadros. O canal Opus sem SCO funcionou isoladamente, mas o pedido explícito de câmera com áudio do protocolo ativo retornou status 2, dispositivo ocupado, e zero quadros de vídeo. O bloqueio temporário da navegação automática foi isolado ao teste privado. Isso não prova impossibilidade de todo firmware ou de toda variante W20. Resultados, durações efetivas e critérios de validação estão em [AUDIO-RESEARCH.md](AUDIO-RESEARCH.md).

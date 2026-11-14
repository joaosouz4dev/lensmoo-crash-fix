# Pesquisa de firmware oficial: XK One Pro / E13C-1

Data: 2026-11-13. Pesquisa somente por leitura. Nenhum comando foi enviado aos óculos ou ao celular, nenhuma conta foi usada, nenhum firmware foi baixado e nenhum terceiro foi contatado nesta pesquisa.

## Resultado e limite atual

O Lensmoo 1.8.0 possui uma consulta de versões separada da instalação de firmware. Foi identificado o serviço OTA usado pelo aplicativo e o esquema de seleção de produto. Ainda não foi localizada uma imagem pública comprovadamente compatível com esta unidade `E13C-1`, firmware `1.0.2`, `noStorageDevice=1`, nem uma nota oficial de versão que prometa vídeo de maior resolução com áudio.

Isso não prova que inexista uma atualização. Na consulta oficial realizada pelo operador, a tela mostrou versão atual 1.0.2 e Sem novas versões. Em 14/11/2026, a leitura passiva confirmou `productCode=E13C-1`, `deviceType=E13C-1` e `version=1.0.2`; o código normalizado da consulta é, portanto, E13C-1. O cache de BasicInfo estava ausente, e a revisão de hardware continua desconhecida. Nenhuma imagem encontrada para outro SKU deve ser tratada como equivalente apenas pelo nome XK One Pro ou pela versão 1.0.2.

## Autoridade das fontes

O Google Play identifica o desenvolvedor do Lensmoo como SHENJU TECHNOLOGY CO., LIMITED e vincula o site `www.shenjugroup.com`. A descrição do desenvolvedor menciona atualizações de firmware e prévia de vídeo, mas não publica uma matriz de placas ou um firmware E13C-1 específico. [Lensmoo no Google Play](https://play.google.com/store/apps/details?id=com.lensmoo.app).

O catálogo WearableMaster descreve uma variante `xk one` com chipset SHENJU W20, FreeRTOS, ROM de 16 MB, RAM declarada de 64 KB, câmera de 1 MP/1280x720, sem flash externo, sem Wi-Fi e com FOTA. É uma ficha comercial de fornecedor, não uma imagem de firmware nem prova de que a unidade E13C-1 use exatamente essa placa. O catálogo também distingue produtos com nomes próximos. [Ficha comercial xk one](https://www.wearablemaster.com/product/ai-smart-glasses-1080p-hd-camera-voice-assistant-wireless-transfer-xk-one-wearablemaster/).

O servidor da Shenju publica uma página de download do **aplicativo** LensMoo, incluindo uma versão 1.8.1-5 na data da consulta. Esse APK não é firmware dos óculos, e atualizar o app não equivale a atualizar a placa. Nenhum arquivo dessa página foi baixado nesta pesquisa. [Página pública de distribuição do LensMoo](https://ota.shenjugroup.com/server/download/A202408233FM6E31I).

O site principal do desenvolvedor não respondeu durante uma tentativa de consulta. Isso limita a pesquisa pública; não estabelece inexistência de suporte ou de imagens internas.

## Endpoint e consulta oficial identificados no aplicativo

Inspeção privada do Lensmoo 1.8.0 identificou:

- Host OTA: `https://ota.shenjugroup.com`.
- Consulta: `POST /api/v1/release/last`.
- Query: nomes de campos `app_id`, `app_secret`, `firmware_version`, `firmware_id`.
- Corpo: metadados do telefone e, no caminho de firmware, campos como `deviceId`, `app_sys`, `app_version`, `app_language`, `app_packagename` e `app_userid`.

Os valores de credenciais, conta, identificadores exclusivos e URLs assinadas não foram transcritos. A existência desse endpoint no app não significa que seja uma API anônima documentada para uso externo. Esta pesquisa não o chamou.

O caminho é `VersionPresenter`, representado no APK por `gub.j(int, boolean, boolean)`, seguido do helper HTTP `kh4.w` e do contrato Retrofit `ns8.a`. O caminho de firmware usa o tipo 1; o caminho de atualização do próprio aplicativo usa outra seleção de versão. O método de consulta não transfere bytes de firmware para os óculos.

## Como o código de firmware é selecionado

O app usa `BleDeviceModel.productCode`; se estiver vazio, usa `BleDeviceModel.deviceType`. Completa o código à direita com hífens até o mínimo de seis caracteres e o converte para maiúsculas. Esse resultado é enviado como `firmware_id`.

Portanto `prod_mode=E13C-1` é uma informação relevante, mas não deve ser substituída manualmente pelo identificador de release sem comparar o cache efetivo. Código de produto, família comercial, revisão de placa e versão de firmware são campos diferentes. Um código de cinco caracteres é normalizado com um hífen final, não com zero.

## Tela que apenas consulta e fronteira de instalação

A Activity é `com.lensmoo.business.ui.device.FwUpActivity`.

No código inspecionado:

1. O construtor lê o dispositivo em cache e define carga mínima de 40%.
2. `initData()` chama a consulta `gub.j(he1.b(), true, false)` e registra os listeners.
3. `onShowUpgrade(BiuVersionInfo, boolean, boolean)` guarda a resposta e atualiza textos, descrição e botões.
4. `handleEvent`, `onStartCheck` e `onCheckVersionFinish` não iniciam ações.

Não foi encontrado `onCreate` próprio, consumo de extras de Intent, `isAutoUpdate` ou `forceUpdate` nessa Activity. A abertura e o retorno de metadados não chamam o downloader nem o transporte OTA nessa árvore de chamadas.

A fronteira seguinte é o botão de atualização: `tvUpgrade` -> diálogo de confirmação -> `clickConfirm()` -> `handleDownload()` -> download. Após o download e a verificação MD5 bem-sucedidos, o callback chama `handleOta()` automaticamente. Portanto a confirmação de atualização não é uma etapa neutra para inspeção de arquivo.

## Metadados que podem ser examinados antes de instalar

O objeto `BiuVersionInfo`, guardado no campo privado `versionInfo` da tela, declara:

| Campo | Utilidade |
| --- | --- |
| `newVersion` | Indica a disponibilidade anunciada pela consulta. |
| `firmware_version` | Versão de destino. |
| `link` | Local do arquivo; inspecionar origem e manter parâmetros privados fora de relatórios públicos. |
| `md5`, `size` | Integridade e tamanho anunciados. |
| `description`, `release_at` | Alterações e data publicadas. |
| `software_info` | Plataforma, nome e identificador de software descritos pelo backend. |
| `customerParams`, `updateType` | Informações adicionais da distribuição. |

`firmware_id` é calculado na requisição; não há um campo correspondente declarado nesse DTO. Uma resposta sem imagem nova ou sem descrição de qualidade não autoriza concluir que firmware melhorará a captura.

## Identificação de hardware necessária

O core do SDK mantém `SJUniWatch.getMBasicInfo()`, cujo objeto `BasicInfo` contém, no esquema: `chip_mode`, `hard_ver`, `prod_mode`, `prod_category`, `prod_subcate`, `prod_date` e `soft_ver`. O DTO comum `WmDeviceInfo` não preserva `chip_mode` e `hard_ver`, por isso apenas ler modelo e versão por esse DTO não identifica a placa completamente.

Para uma correspondência de release são relevantes:

- `productCode` e `deviceType` realmente usados na consulta;
- revisão `hard_ver`, chipset `chip_mode`, família `prod_mode` e versão `soft_ver`;
- variante de armazenamento e capacidades, incluindo `noStorageDevice` e eventual suporte OTA;
- correspondência documentada pelo fornecedor entre esses campos, a placa e o arquivo oferecido.

O app também usa identificadores exclusivos e de conta na requisição; eles devem permanecer na sessão do usuário. Esta nota não contém seus valores. Nenhum valor novo de hardware da unidade foi consultado nesta pesquisa.

## Validação do pacote e recuperação

O caminho visível do aplicativo calcula o MD5 do arquivo baixado e o compara com `BiuVersionInfo.md5`. O transporte `SJTransferFile` possui CRC nos blocos. Esses controles de integridade não demonstram, por si, uma assinatura criptográfica da imagem nem uma verificação de compatibilidade de placa pelo bootloader.

O gerenciador de instalação aceita os formatos `up` e `upex` e os encaminha às categorias OTA correspondentes de `getWmTransferFile`. Não foi identificado no fluxo analisado um procedimento de leitura completa da flash para backup, escolha de slot, rollback, imagem de recuperação ou restauração por bootloader. Isso é ausência de evidência no app inspecionado, não prova de que o fabricante não tenha ferramentas internas.

Reiniciar, restaurar configurações e desfazer pareamento não equivalem a restaurar firmware anterior. O backup local dos APKs também não é backup da flash dos óculos.

## O que uma atualização precisaria demonstrar para este objetivo

A evidência de compatibilidade útil seria uma oferta oficial para o identificador efetivo e a revisão de hardware, com versão, integridade, descrição de alterações e um mecanismo de recuperação conhecido. Para prometer vídeo com qualidade próxima às fotos e áudio, ainda seria necessário comprovar que a imagem habilita o caminho de captura desejado e validar o arquivo resultante.

Uma variante sem flash externo ou sem rádio Wi-Fi não ganha esses componentes com software. Isso não elimina a possibilidade de melhoria de um fluxo já suportado pelo hardware; apenas impede tratar especificações de outros SKUs como garantia para esta unidade.

O SDK independente marca OTA fora de escopo e não fornece uma imagem ou recuperação para esta placa. Essa limitação do projeto não substitui a investigação do canal oficial encontrada acima. [Projeto XK One Pro SDK](https://github.com/beneduzi/xk-one-pro-sdk).


## Subsequent read-only checks reported by the device operator

The official firmware-update screen was consulted and showed current firmware 1.0.2 plus the message Sem novas versoes. No firmware download or flash occurred. This establishes that the app offered no update for its current query at that moment; it does not prove that every hardware revision has the same latest firmware.

The public APK download page advertised app 1.8.1-5, but its CDN download returned HTTP 404 both for the initially observed link and a freshly fetched href, with ordinary User-Agent and Referer headers. That newer APK was not obtained, inspected or installed. App-version availability remains separate from glasses firmware compatibility.


## Consulta hipotética com firmware_version 0.0.0

Complemento de 14/11/2026, somente análise estática do APK 1.8.0. Nenhuma requisição OTA foi enviada e nenhuma credencial foi copiada, exibida ou usada externamente.

**Conclusão:** mudar `firmware_version` de 1.0.2 para 0.0.0 poderia alterar a comparação feita pelo backend, mas o cliente não demonstra que isso ofereceria o próprio firmware 1.0.2. Não há no código inspecionado um contrato documentado de consulta histórica, download da versão atual ou backup. A API se chama `release/last`, não leitura da flash instalada. Mesmo que uma imagem 1.0.2 fosse oferecida, ela poderia ser atualização parcial ou pacote de distribuição, não uma cópia completa da unidade com calibração, configuração e recuperação.

O cliente usa a versão de `BleDeviceModel.version`; quando ela está vazia, o fallback observado é **1.0.0**, não 0.0.0. Não foi encontrada validação local de um mínimo de versão nesse caminho. A aceitação e a semântica de 0.0.0, seleção de pacote completo/delta, permissões e disponibilidade da versão antiga continuam sendo decisões desconhecidas do servidor. Não basta alterar a versão para demonstrar compatibilidade ou acesso.

### Schema construído pelo cliente, sem valores privados

No fluxo de firmware, `gub.j(1, show, hand)` constrói:

| Parte | Campos e origem |
| --- | --- |
| Método/rota | POST `https://ota.shenjugroup.com/api/v1/release/last` |
| Query | `app_id`, `app_secret`, `firmware_version`, `firmware_id` |
| Credenciais do aplicativo | Pares incorporados como constantes/literais no `VersionPresenter` obfuscado `gub`, com seleção conforme o tipo consultado. Não são obtidos do identificador de hardware nem gerados a partir de E13C-1. Valores omitidos. |
| Corpo comum | `model` a partir de fabricante/modelo do telefone; `os` a partir da versão Android; `brand`; `imei_id` a partir de helper `DeviceUtils`. O nome do campo não prova que o conteúdo seja o IMEI físico. |
| Corpo de firmware | `deviceId` do dispositivo em cache; `app_sys` com android; `app_version` da configuração do app; `app_language` do idioma; `app_packagename` da configuração; `app_userid` do usuário em cache. |
| Codificação | `kh4.f(Map)` serializa como JSON com Content-Type `application/json;charset=UTF-8`; `ns8.a(String,RequestBody)` faz POST com URL dinâmica. |

Esse é o conjunto montado pelo app. Não foi identificado um schema oficial que declare quais campos são obrigatórios no servidor, portanto não se pode chamar um corpo reduzido de payload mínimo aceito. Remover conta ou deviceId para tentar contornar uma eventual seleção por usuário não preservaria o fluxo observado.

### Autenticação efetiva e limites de licença

A consulta passa pelo `HttpManager` global `kh4`, cujo interceptor `ye4` aplica cabeçalhos compartilhados. O gerenciador de autenticação `dj4.D(AuthBean)` e a restauração de sessão `dj4.z()` registram **Authorization com Bearer access token** nesse gerenciador; `dj4.h()` remove o cabeçalho no logout. Assim, na sessão autenticada, a consulta OTA também pode carregar a autorização da conta. O método `gub.j` não configura uma exceção anônima para essa rota.

Não foi localizada nesse caminho uma verificação separada de licença de firmware ou um bloqueio local específico contra consulta da versão atual. Isso não revela as regras do backend nem concede uma licença pública de API, de firmware ou de redistribuição. A existência de appId/secret dentro do APK não transforma o serviço em endpoint sem autenticação. Uma análise futura precisaria preservar a sessão, o produto efetivo e as restrições recebidas do serviço, sem publicar credenciais, URLs privadas ou imagens sem licença confirmada.

Atenção operacional concreta: o próprio helper HTTP registra URL e corpo em logs, e `gub.j` também serializa o modelo completo do dispositivo para log. Invocar esse caminho sob instrumentação com coleta indiscriminada pode registrar segredo da aplicação, token, identificadores e conta. Uma leitura de cache deve extrair apenas campos permitidos, sem imprimir o objeto inteiro.

### Identificador real e getters locais, sem tráfego

O cálculo de `firmware_id` usa primeiro **BleDeviceModel.productCode**. Só usa **deviceType** se productCode estiver vazio. Completa à direita com hífens até seis caracteres e aplica maiúsculas. Se o valor efetivo for exatamente E13C-1, já tem seis caracteres e permanece E13C-1. Um prefixo fotográfico observado com esse texto não demonstra qual dos dois campos está preenchido, nem que ambos tenham esse mesmo valor.

Rotas concretas de leitura encontradas no APK:

- `MainFragment.getDeviceInfo()` retorna diretamente o campo `deviceInfo` já guardado na tela. Ler somente `productCode`, `deviceType` e `version`; não serializar o modelo inteiro. A presença da tela não garante que o cache esteja atualizado ou preenchido.
- `com.android.mltcode.paycertificationapi.a72.a.b()` retorna o `BleDeviceModel` de cache usado pelo próprio `gub.j`. Sua chave de preferências é composta internamente pelo namespace do usuário e `device_cache_bind_device`; o método pode retornar um modelo vazio. Não é necessário revelar a conta para ler apenas os três campos selecionados. Comparar esse objeto com o modelo da tela resolve uma eventual divergência de cache antes de qualquer consulta.
- Para a implementação Shenju ativa, `UNIWatchMate.INSTANCE.getWmApps().a()` fornece o `AppAIAssistant` concreto `f7c`; o getter público `f7c.I()` retorna a instância `SJUniWatch`. `SJUniWatch.getMBasicInfo()` apenas retorna seu campo `mBasicInfo`, sem enviar comando. Ler seletivamente `hard_ver`, `chip_mode`, `prod_mode` e `soft_ver` se o objeto existir. Confirmar o tipo concreto antes da chamada; outras implementações do SDK podem não ter `I()`.
- `UNIWatchMate.getDeviceInfo()` é uma operação Rx do dispositivo, e não o getter simples de cache da MainFragment. Não usá-lo como se fosse leitura passiva. `UNIWatchMate.getDeviceModel()` retorna uma enumeração de família SDK, não um identificador preciso de placa.

`BleDeviceModel` não declara um campo específico de revisão de hardware. `deviceId` e `deviceAddress` são identificadores privados e não substituem `hard_ver`/`chip_mode`. A leitura proposta não precisa coletar MAC, identificador exclusivo, usuário ou token. Os valores atuais desses getters não foram consultados nesta pesquisa.

Fontes primárias locais para este complemento: classes do APK `gub`, `gub$a`, `kh4`, `ns8`, `ye4`, `dj4`, `a72`, `BleDeviceModel`, `MainFragment`, `UNIWatchMate`, `f7c`, `SJUniWatch` e `BasicInfo`. Nenhuma implementação decompilada foi reproduzida nesta nota.

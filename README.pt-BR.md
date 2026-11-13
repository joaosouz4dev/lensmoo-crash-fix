# Lensmoo fecha sozinho? LensFix para XK One Pro no Android

Português brasileiro | [English](README.md)

**Correção independente para o Lensmoo 1.8.0 que fecha ao abrir**, com erro `Looper.prepare()` na biblioteca StarBurst. A cópia corrigida aparece como **LensFix** e pode ficar instalada junto com o Lensmoo original.

[**Baixar APK corrigido LensFix (arm64)**](https://github.com/joaosouz4dev/lensmoo-crash-fix/releases/latest/download/LensFix-arm64.apk) · [Versão, instruções e SHA-256](https://github.com/joaosouz4dev/lensmoo-crash-fix/releases/latest) · [Relatar problema](https://github.com/joaosouz4dev/lensmoo-crash-fix/issues/new?template=bug_report.yml)

## O que foi corrigido

No aparelho testado, o serviço de voz StarBurst retornava `4003 ERROR_INVALID_KEY`. Ao tratar esse erro em uma thread de trabalho, o Lensmoo criava um `Handler` sem selecionar um `Looper` e encerrava com:

```text
Can't create handler inside thread that has not called Looper.prepare()
com.starburst.sdk.StarBurstSdk$a.onFailure
```

A correção usa `Handler(Looper.getMainLooper())`. A resposta de chave inválida do fornecedor continua existindo: esta correção evita o encerramento observado, mas não restaura o serviço de voz nem altera suas verificações de licença ou autenticação.

**Teste real:** Samsung Galaxy S23 Ultra (SM-S918B), Android 17 / API 37 e óculos XK One Pro. O proprietário confirmou que o LensFix permanece aberto e conecta aos óculos. Veja [os testes e seus limites](docs/VALIDATION.md).

## Como instalar

1. Baixe **LensFix-arm64.apk** na [página de versões deste projeto](https://github.com/joaosouz4dev/lensmoo-crash-fix/releases/latest). É um APK único instalável.
2. Se desejar, confira o SHA-256 com o arquivo `SHA256SUMS.txt` da mesma versão.
3. Abra o APK no Android e permita a instalação pela fonte escolhida quando o sistema perguntar.
4. Abra o aplicativo **LensFix**. O **Lensmoo** original continua instalado e seus dados não são apagados. A cópia tem dados separados e pode pedir acesso à sua conta do fornecedor.
5. Ative o Bluetooth, permita dispositivos próximos e conecte os óculos.

O APK usa a base Lensmoo **1.8.0, código 80**, com bibliotecas arm64, recursos de idioma inglês, espanhol e português e densidade xxhdpi. O manifesto exige Android 8.0 ou superior; o teste físico cobre somente o aparelho descrito acima. Outras versões do app, arquiteturas, aparelhos e firmwares não foram validados.

## Como gravar vídeo nos XK One Pro

Na unidade testada, com firmware 1.0.2, o proprietário confirmou que **duas pressões curtas no botão físico direito iniciam gravação de voz, e não vídeo**. A instrução de vídeo do guia comercial dessa família não corresponde ao comportamento dessa unidade.

O firmware informou `noStorageDevice: 1`, e o app oculta os controles de vídeo armazenado. Uma transmissão ao vivo utilizável, a gravação e a recuperação de vídeos ainda não foram verificadas no LensFix. Consulte [os testes e a pesquisa sobre vídeo](docs/VIDEO-RESEARCH.md) antes de contar com esse recurso.

## O que ainda não foi resolvido

- O serviço StarBurst pode continuar repetindo o erro `ERROR_INVALID_KEY`. A correção de encerramento não remove essas tentativas nem recupera o serviço.
- Uma foto de teste foi capturada e transferida ao álbum interno do app. A exportação para a galeria do Android, voz e estabilidade prolongada em segundo plano ainda precisam de validação completa.
- O LensFix não acrescenta gravação de vídeo, exportação de vídeo ou atualização de firmware.
- Esta é uma versão independente, assinada separadamente, sem vínculo oficial com Lensmoo ou com o fabricante.

## Código aberto e reprodução da correção

As ferramentas e a documentação deste repositório são abertas sob MIT. O aplicativo Lensmoo e seus componentes de terceiros mantêm seus próprios direitos: o app do fornecedor não se torna código aberto por causa desta correção. Leia [os avisos de terceiros](THIRD_PARTY_NOTICES.md).

O [script de build](scripts/build.py) confere os hashes dos APKs originais, modifica a instrução identificada, recompila somente um DEX e preserva os demais DEX, bibliotecas nativas e assets. A assinatura e o alinhamento do APK final são conferidos. As [instruções de build](docs/BUILD.md) usam APKs obtidos da sua própria instalação.

Crédito ao [XK One Pro SDK, de Gustavo Beneduzi](https://github.com/beneduzi/xk-one-pro-sdk), pela investigação independente dos óculos e do protocolo.

Este projeto documenta buscas como **Lensmoo fechando sozinho**, **Lensmoo parou de funcionar**, **Lensmoo não abre**, **Lensmoo Android 17**, **APK corrigido XK One Pro** e **erro Looper.prepare StarBurstSdk**. A compatibilidade é limitada aos testes documentados; nenhuma posição em mecanismos de busca é garantida.

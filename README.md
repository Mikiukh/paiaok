# Zion — ESP experimental

Versão `0.2.0`. Modificação do APK `Nova_V2_Demo_v2.apk` fornecido para este trabalho.

## Download no Android

Abra [a versão experimental](https://github.com/Mikiukh/paiaok/releases/tag/esp-v0.2.0). Em **Assets**, toque em **Zion.apk**. O repositório é público.

O APK tem uma assinatura de teste diferente da original. O Android pode recusar uma atualização por cima da instalação original. Não remova a instalação existente sem guardar o instalador, dados e credenciais necessários. Não há migração automática de dados.

Se a instalação for aceita, abra o aplicativo e utilize os controles de VPN e ESP que ele já possuía. As autorizações de sobreposição, VPN e o login continuam sendo os do aplicativo original. Se o Android solicitar, a permissão para instalar esse arquivo fica em **Instalar apps desconhecidos** nas configurações do navegador/gerenciador de arquivos.

## Alterações

- Estabilização das coordenadas desenhadas, nos caminhos de dados Java e nativo.
- Históricos separados por personagem e por projeção de cabeça/pés.
- Cache fixo de 256 entradas, sem alocar objetos por amostra no filtro.
- Reinício do histórico após 200 ms sem atualização, mudança de orientação ou salto grande de posição.
- Rejeição de coordenadas NaN/infinitas antes do desenho.
- Linhas com contorno escuro e objetos Paint reutilizados.
- Previsão Java de movimento de até 80 ms da versão anterior preservada.

O filtro usa uma constante de resposta de 18 ms, que acrescenta atraso visual. Ele não recupera dados de posição ausentes nem corrige câmera, FOV ou protocolo incorretos.

## O que foi verificado

- Testes executáveis do filtro Java: movimento, identificação, rotação, expiração, relógio, troca de câmera, colisões/lotação do cache e valores inválidos.
- Resposta sintética comparada a 30, 60 e 120 Hz.
- Redução de ruído em uma sequência sintética; isso não mede precisão em jogo.
- 29 casos das instruções de previsão da versão anterior.
- Compilação Smali, leitura posterior do DEX, integridade ZIP, alinhamento e assinaturas APK v1/v2/v3.
- Somente `EspEntityStore$Entity` e `EspOverlayView` foram alteradas; `ZionScreenFilter` foi acrescentada.
- Manifesto, permissões, recursos, demais DEX e bibliotecas nativas preservados byte a byte; apenas `classes3.dex` e as assinaturas mudaram.

**Não houve instalação em Android nem teste no Free Fire. Não há medição de FPS ou rastreamento em partida.** A autenticação e as verificações de integridade foram mantidas e podem rejeitar uma versão reconstruída. Não foi acrescentado aimbot, root ou bypass de anticheat.

Veja `validation.json` e o `SHA256SUMS` anexado à versão.

## Código e reconstrução

O repositório contém o filtro e seus testes, a alteração Smali e um reconstrutor. O APK original não está incluído. A reconstrução aceita somente o arquivo de origem com SHA-256:

`701faec99b61efe8883518569ab4e741c858776b625020e055ebcefa0ac8f54e`

Ferramentas usadas: Java 21, Python 3, GNU patch, [Apktool 2.11.1](https://github.com/iBotPeaches/Apktool/releases/tag/v2.11.1) e [Uber APK Signer 1.3.0](https://github.com/patrickfav/uber-apk-signer/releases/tag/v1.3.0).

```bash
python3 rebuild.py Nova_V2_Demo_v2.apk \
  --apktool /caminho/apktool_2.11.1.jar \
  --signer /caminho/uber-apk-signer-1.3.0.jar \
  --output build/Zion.apk
```

O reconstrutor não sobrescreve a origem nem um arquivo de saída existente. A assinatura é a de teste incorporada ao Uber APK Signer, não uma chave de produção. Hashes da reconstrução podem variar por metadados e assinatura.

Para executar os testes do filtro com um JDK:

```bash
mkdir -p classes
javac -source 8 -target 8 -d classes \
  src/com/mducdz/fakeping/engine/ZionScreenFilter.java \
  tests/ZionScreenFilterTest.java
java -cp classes ZionScreenFilterTest
```

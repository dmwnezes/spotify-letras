# Sintonia

App Android que mostra a letra da música que está tocando no Spotify, sincronizada linha a linha, com um visualizer que reage ao som.

- **Letras:** letra sincronizada ou modo só player (capa grande), visualizer com 4 temas (Brilhos, Ondas, Partículas, Retrô) e controles.
- **Karaokê:** a linha atual acende palavra por palavra (com a marcação real quando existe, ou estimada).
- **Busca:** procure qualquer música e toque na hora ou coloque na fila.
- **Aparência:** tema claro, escuro ou automático (cores tiradas da capa) e fonte/tamanho da letra.
- **Atalhos:** segure o ícone do app para abrir Letra, Buscar, Caderno ou Quiz.
- **Tradução:** letras em outro idioma ganham a tradução em português abaixo de cada linha (feita no celular, grátis).
- **Tela de bloqueio e widget:** a linha atual aparece numa notificação e num widget da tela inicial.
- **Caderno:** segure uma linha para salvar o trecho, compartilhar como imagem ou anotar um momento no diário musical.
- **Compartilhar:** cartão "tocando agora" para Stories, ou um vídeo (Stories 9:16 ou quadrado) com capa e letra de um trecho de até 30 s.
- **Perfil:** artistas, músicas e gêneros mais ouvidos (dados ao vivo do Spotify).
- **Histórico:** lê o "Histórico de streaming estendido" do Spotify e mostra estatísticas ano a ano, recordes, descobertas, uma retrospectiva em texto e outra animada estilo Stories. Exporta um resumo em texto (para mandar ao Claude) ou um backup .json.
- **Quiz:** adivinhe a música por um trecho da letra, usando as músicas que você mais ouve.

## Instalar no celular

Baixe a versão mais recente direto no celular:

**https://github.com/dmwnezes/spotify-letras/releases/latest/download/Sintonia.apk**

O Android vai pedir para permitir a instalação de apps desta fonte (o navegador). Aceite e instale. Novas versões instalam por cima da anterior.

## Configurar o Spotify (uma vez só)

1. Entre em [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) e toque em **Create app**.
2. Preencha nome e descrição como quiser.
3. Em **Redirect URIs**, adicione exatamente: `sintonia://callback`
4. Em **Which API/SDKs are you planning to use?**, marque **Web API**.
5. Salve. Em **Settings**, copie o **Client ID**.
6. Abra o Sintonia, cole o Client ID e toque em **Entrar com Spotify**.

Se o login der erro 403, abra **User Management** no painel do app e adicione o e-mail da sua conta do Spotify.

## Como funciona

- **Letras:** vêm do [LRCLIB](https://lrclib.net), base aberta e gratuita de letras sincronizadas.
- **Música tocando:** o app consulta a Web API do Spotify a cada ~2 s e estima a posição entre uma consulta e outra.
- **Visualizer:** usa o recurso do Android que lê o espectro do som que sai do celular. Por isso pede a permissão de "gravar áudio" — nada é gravado nem enviado. Se o aparelho bloquear, cai para uma animação nas cores da capa.
- **Ajuste de sincronia:** o botão de ajuste (ícone de controles) adianta ou atrasa a letra em passos de 0,25 s.

## Histórico completo

1. Em spotify.com → Conta → Privacidade, peça o **Histórico de streaming estendido**.
2. Quando chegar o e-mail, baixe o .zip no celular.
3. Na aba **Histórico**, toque em **Ler arquivo** e escolha o .zip.

Tudo é calculado no celular. O backup exportado pode ser aberto de novo pelo mesmo botão.

## Estrutura

```
app/src/main/java/com/dmwnezes/sintonia/
├── MainActivity.kt        abas Letras / Perfil / Histórico / Caderno
├── AppGraph.kt            peças compartilhadas (login, Spotify, letras)
├── Playback.kt            o que está tocando, letra, cores e tradução
├── AppViewModel.kt        estado das telas e preferências
├── live/                  notificação da tela de bloqueio e widget
├── translate/             tradução no celular (ML Kit)
├── notebook/              caderno (trechos e diário) e imagem do trecho
├── wrapped/               retrospectiva animada
├── quiz/                  quiz da letra
├── data/                  login (PKCE) e Web API do Spotify
├── lyrics/                LRCLIB e leitura do formato LRC
├── history/               leitura e estatísticas do histórico do Spotify
├── share/                 vídeo de compartilhamento (MediaCodec)
├── viz/AudioSpectrum.kt   leitura do espectro de áudio
└── ui/                    telas e visualizer
```

Cada `push` na branch `main` compila o APK no GitHub Actions e publica em **Releases**.

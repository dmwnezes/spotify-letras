# Sintonia

App Android que mostra a letra da música que está tocando no Spotify, sincronizada linha a linha, com um visualizer que reage ao som. Também tem uma aba de **Perfil Musical** com seus artistas, músicas e gêneros mais ouvidos.

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

## Estrutura

```
app/src/main/java/com/dmwnezes/sintonia/
├── MainActivity.kt        abas Letras / Perfil Musical
├── AppViewModel.kt        estado do app, consultas e controles
├── data/                  login (PKCE) e Web API do Spotify
├── lyrics/                LRCLIB e leitura do formato LRC
├── viz/AudioSpectrum.kt   leitura do espectro de áudio
└── ui/                    telas e visualizer
```

Cada `push` na branch `main` compila o APK no GitHub Actions e publica em **Releases**.

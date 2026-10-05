# ⚡ Sv Booster — Game Booster para Android

App Android (Kotlin + Jetpack Compose) que deixa os jogos mais lisos abaixando a resolução e
travando o FPS de um jogo escolhido, com várias ferramentas extras.

## Funções

**Por jogo (perfil salvo para cada um)**
- Resolução do jogo: 100% → 30% (menos pixels = GPU mais folgada = mais FPS e menos calor)
- Limite de FPS: 30 / 40 / 45 / 60 / 90 / 120 ou sem limite
- Modo do sistema: Desempenho / Padrão / Bateria (Game Mode do Android)
- Predefinições: Equilibrado, Máx. FPS, Celular fraco, Economia
- Limpar RAM, Não perturbe, desligar animações, forçar Hz máximo
- **Forçar resolução**: para jogos que ignoram a redução por app — baixa a resolução da tela inteira
  enquanto o jogo está aberto e volta ao normal sozinho quando você sai dele
- Botão **BOOST & JOGAR**: aplica tudo, reinicia e abre o jogo, com relatório do que funcionou

**Ferramentas**
- Limpeza de RAM com MB liberados
- Teste de ping (Google, Cloudflare, AWS São Paulo, AWS EUA ou servidor próprio) com jitter e perda
- Resolução global (tela inteira) — para jogos que ignoram a redução por app
- Mira personalizada sobre o jogo (cruz, ponto, círculo; 6 cores; tamanho ajustável)
- HUD flutuante: temperatura, RAM, bateria e hora
- “Restaurar tudo” (também na notificação e no bloco **Restaurar jogo** das Configurações Rápidas)
- HUD arrastável com **temperatura da CPU** (com Shizuku/root) e da bateria
- **Atalho na tela inicial** por jogo: 1 toque = Boost & jogar

**Arquivos**
- Acesso a todo o armazenamento (permissão “Acesso a todos os arquivos”)
- Android/data e Android/obb via Shizuku/root
- Navegar, editar arquivos de texto (.json, .xml, .ini, .cfg…), renomear, apagar, criar pasta
- Backup automático `.bak` antes de salvar uma edição

## Por que precisa do Shizuku

O Android não permite que um app comum mude a resolução/FPS de outro app. O
[Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) dá ao app o
mesmo poder de um comando ADB, **sem root**. Root também funciona.
Sem nenhum dos dois, o app roda em **modo básico** (limpar RAM, não perturbe, HUD, mira, ping).

Resolução/FPS por jogo usa `cmd game set` (Android 13+) ou `device_config game_overlay` (Android 12).
Alguns fabricantes/jogos ignoram — aí use a **Resolução global**.

## Como gerar o APK

### Opção 1 — Android Studio (PC)
1. Abra a pasta `SvBooster` no Android Studio (Ladybug ou mais novo).
2. Espere o Gradle sincronizar e clique em ▶ Run, ou *Build → Build APK(s)*.

### Opção 2 — GitHub (sem instalar nada, dá até pelo celular)
1. Crie um repositório no GitHub e envie todos os arquivos desta pasta.
2. Abra a aba **Actions** → o workflow **Build APK** roda sozinho.
3. Quando terminar (~5 min), vá em **Releases** no repositório e baixe o `SvBooster.apk`
   direto pelo celular. Permita “instalar apps desconhecidos” quando o Android pedir.

## Estrutura
```
app/src/main/java/com/turbo/gamebooster/
  MainActivity.kt          navegação e abas
  shell/Shell.kt           comandos via Shizuku/root
  core/Data.kt             perfis, apps instalados, RAM/bateria
  core/Booster.kt          boost, limpeza, ajustes do sistema, ping
  overlay/OverlayService.kt HUD e mira sobre o jogo
  ui/                      telas (Jogos, Perfil, Ferramentas, Ajustes)
```

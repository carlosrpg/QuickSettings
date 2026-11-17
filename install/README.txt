QuickSettings - first-time setup on the car (Windows)
(Português abaixo)

What it does
  Finds adb (installs Google Platform Tools with winget if missing), waits for the car,
  copies the latest QuickSettings APK to the car's Download folder if the app is not there
  yet, then applies the adb-only permissions that are still missing. Safe to run again.
  Messages follow the Windows display language (English or Brazilian Portuguese).

How to use
  1. Unzip this folder anywhere on a Windows PC with internet access.
  2. On the car, enable ADB in the developer / engineering menu.
  3. Connect the PC to the car with a USB cable.
  4. Double-click First-Install.cmd. Whenever you have to do something, a yellow
     "ACTION NEEDED" box appears (with a beep) saying what to do and which key to press:
     allow USB debugging on the car, install the APK from the car's file manager
     (Download folder) and open QuickSettings once, then press Enter on the PC.
  5. Finally, in QuickSettings' settings screen, tap Grant next to Microphone (voice search).

At the end a summary lists every check as OK, APPLIED, FAILED or PENDING (something you
still have to do on the car), and the last line says whether everything is done.

Options (from a terminal): First-Install.cmd -Serial <adb serial> -TimeoutMinutes 20 -Lang en|pt-BR
The manual commands are in the project README:
  https://github.com/carlosrpg/QuickSettings#install-on-the-car


QuickSettings - primeira instalação na central multimídia (Windows)

O que faz
  Encontra o adb (instala o Google Platform Tools com o winget se faltar), aguarda a central
  multimídia, copia o APK mais recente do QuickSettings para a pasta Download da central se o
  app ainda não estiver lá, e aplica as permissões via adb que ainda faltam. Pode rodar de novo
  sem problema. As mensagens seguem o idioma do Windows (português do Brasil ou inglês).

Como usar
  1. Descompacte esta pasta em qualquer lugar de um PC com Windows e acesso à internet.
  2. Na central multimídia, ative o ADB no menu de desenvolvedor / engenharia.
  3. Ligue o PC à central com um cabo USB.
  4. Dê dois cliques em First-Install.cmd. Sempre que você precisar fazer algo, aparece uma
     caixa amarela "AÇÃO NECESSÁRIA" (com um bipe) dizendo o que fazer e qual tecla apertar:
     permitir a depuração USB na central, instalar o APK pelo gerenciador de arquivos da
     central (pasta Download) e abrir o QuickSettings uma vez, depois apertar Enter no PC.
  5. Por fim, na tela de configurações do QuickSettings, toque em Permitir ao lado de
     Microfone (busca por voz).

No final, um resumo mostra cada verificação como OK, APLICADO, FALHOU ou PENDENTE (algo que
você ainda precisa fazer na central), e a última linha diz se está tudo pronto.

Opções (pelo terminal): First-Install.cmd -Serial <serial do adb> -TimeoutMinutes 20 -Lang en|pt-BR
Os comandos manuais estão no README do projeto (em inglês):
  https://github.com/carlosrpg/QuickSettings#install-on-the-car

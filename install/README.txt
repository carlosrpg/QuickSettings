QuickSettings - first-time setup on the car (Windows)

What it does
  Finds adb (installs Google Platform Tools with winget if missing), waits for the car,
  copies the latest QuickSettings APK to the car's Download folder if the app is not there
  yet, then applies the adb-only permissions that are still missing. Safe to run again.

How to use
  1. Unzip this folder anywhere on a Windows PC with internet access.
  2. On the car, enable ADB in the developer / engineering menu.
  3. Connect the PC to the car with a USB cable.
  4. Double-click First-Install.cmd and follow the prompts. If the car asks to allow USB
     debugging, accept it. If asked, install the APK from the car's file manager
     (Download folder), open QuickSettings once, then press Enter on the PC.
  5. Finally, in QuickSettings' settings screen, tap Grant next to Microphone (voice search).

Options (from a terminal): First-Install.cmd -Serial <adb serial> -TimeoutMinutes 20
Each permission prints OK, APPLIED or FAILED. The manual commands are in the project README:
  https://github.com/carlosrpg/QuickSettings#install-on-the-car

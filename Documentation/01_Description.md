# Bluetooth HID Device: Google Play listing

Project name: Bluetooth HID Device

## App title (max 30 characters)

Bluetooth Keyboard: HID Pad

## Short description (max 80 characters)

Phone as a Bluetooth keyboard, macro pad & password sender. No PC software.

## Full description (max 4000 characters)

Bluetooth Keyboard: HID Pad turns your Android phone into a wireless Bluetooth keyboard and macro pad for your PC. No receiver, no drivers, no software on the computer: pair once, then type, send text or run saved presets on Windows, Linux, macOS, Android TV and any other device that accepts a Bluetooth keyboard.

BT Keyboard & Password Sender: open your password manager (for example KeePass or a KeePass fork), choose a password, tap Share / "Send via HID" and the app types it on the PC like a real keyboard. It works where paste does not: login screens, BIOS and boot menus, remote consoles, virtual machines and locked-down work PCs.

🔐 Password Sender
• Share to type: send any text from any Android app straight to the computer.
• Credential presets: type login, Tab, password and Enter in one tap, with a confirmation before it runs.
• Automation: other apps and scripts can send text to the PC with a SEND_TEXT intent.

⌨️ Keyboard & Macro Pad
• Phone keyboard: type on the PC with your phone's keyboard or send the clipboard in one tap.
• Presets and macros: launch apps, press shortcuts, type text and add delays in one tap.
• Categories: organize presets in colour-coded groups, in list or grid view.
• Backup: export and import all presets as a JSON file.
• Always connected: a background service keeps the session alive.

🖱️ Mouse Keys Control
• Pointer pad: move the Windows pointer in 8 directions, click, drag and use the left and right buttons from your phone.
• Hold to accelerate: arrow buttons stay pressed while your finger is down.

🌐 Browser Control (optional)
• Local web panel: run, edit and reorder presets from a computer on the same Wi-Fi or hotspot.
• Protected: access needs a private token, and sensitive presets cannot be run or viewed from the web.

📱 Compatibility
• Android 9 or newer. Your phone must support the Bluetooth HID Device profile (some manufacturers disable it).
• Keyboard only: pointer control uses Windows Mouse Keys, not a touchpad.
• Keep the computer on the English layout for commands and symbols.

---

## Notes for the developer (do not publish)

- Every claim above comes from the current code: keyboard-only HID descriptor, presets in RoomDB, JSON import/export, credential and sensitive presets with confirmation, Mouse Keys card and key hold, LAN web panel (NanoHTTPD, token), `ACTION_SEND` text/plain share target, exported `HidTextService` with `SEND_TEXT`, foreground service, minSdk 28.
- Structure follows the top Play listings in this niche (hook, grouped bullets "Feature: benefit", compatibility, short requirements). No keyword block, no "best / #1 / rate us" claims, no step-by-step instructions, no competitor names.
- Emoji are used only on section headers (competitors use none; this is a design choice).
- "Send via HID" and the KeePass mention depend on your KeePass fork. Check the wording, and add its name if it has its own brand.
- Windows, Linux, macOS and Android TV compatibility is based on the standard Bluetooth keyboard profile. Test on each host before listing it.
- Data safety form must match: Bluetooth, local network web server, Crashlytics if enabled.
- Presets with passwords are stored as plain text in the local database today. If that changes, say so here.
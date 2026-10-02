"""Builds app/src/main/assets/databases/presets_seed.db, the prepackaged Room database.

Run from the repo root:  python tools/build_presets_seed_db.py

IDs are fixed and never reused: category N, its presets N*100 + 1.., actions preset*10 + i.
Built-in rows live below USER_ID_START; sqlite_sequence starts user rows from there.
CREATE statements must match PresetDatabase_Impl.createAllTables() and DB_VERSION must match
@Database(version) in PresetDatabase.kt, otherwise Room rejects the asset.
"""

import json
import os
import sqlite3

DB_VERSION = 4
USER_ID_START = 100_000
CREATED_AT = 1_790_899_200_000

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "databases", "presets_seed.db")

SCHEMA = [
    "CREATE TABLE IF NOT EXISTS `preset_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, `colorArgb` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `riskLevel` TEXT NOT NULL, `requiresConfirmation` INTEGER NOT NULL, `isSensitive` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `preset_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_presets_categoryId` ON `presets` (`categoryId`)",
    "CREATE TABLE IF NOT EXISTS `preset_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `presetId` INTEGER NOT NULL, `type` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`presetId`) REFERENCES `presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_preset_actions_presetId` ON `preset_actions` (`presetId`)",
]

# Key names HidDeviceManager.keyNameToUsageId accepts after PresetShortcutParser.normalizeKey.
KNOWN_KEYS = (
    {chr(c) for c in range(ord("A"), ord("Z") + 1)}
    | {str(d) for d in range(10)}
    | {f"F{i}" for i in range(1, 13)}
    | {"ENTER", "ESCAPE", "BACKSPACE", "TAB", "SPACE", "DELETE", "PRTSC",
       "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGEUP", "PAGEDOWN", "INSERT",
       "-", "=", "[", "]", "\\", ";", "'", "`", ",", ".", "/"}
)
KNOWN_MODIFIERS = {"CTRL", "SHIFT", "ALT", "WIN", "RCTRL", "RSHIFT", "RALT", "RWIN"}


def cmd(title, description, command):
    return title, description, "RunWindowsCommand", {"command": command}


def keys(title, description, shortcut):
    """Same output as PresetShortcutParser.parse for the shortcuts used here."""
    parts = [p.strip() for p in shortcut.split("+") if p.strip()]
    key = {"esc": "ESCAPE"}.get(parts[-1].lower(), parts[-1].upper())
    modifiers = [{"control": "CTRL"}.get(m.lower(), m.upper()) for m in parts[:-1]]
    if key not in KNOWN_KEYS:
        raise ValueError(f"{title}: unknown key {key}")
    for modifier in modifiers:
        if modifier not in KNOWN_MODIFIERS:
            raise ValueError(f"{title}: unknown modifier {modifier}")
    if not modifiers:
        return title, description, "KeyPress", {"key": key}
    return title, description, "KeyCombo", {"modifier": "+".join(modifiers), "key": key}


CATEGORIES = [
    (1, "Home", [
        cmd("Calculator", "Windows Calculator", "calc"),
        cmd("Notepad", "Windows Notepad", "notepad"),
        keys("Ctrl+Alt+Del", "Security screen (Ctrl+Alt+Del)", "ctrl+alt+delete"),
        keys("Task Manager", "Task Manager (Ctrl+Shift+Esc)", "ctrl+shift+escape"),
        keys("Explorer", "File Explorer (Win+E)", "win+e"),
        keys("Settings", "Windows Settings (Win+I)", "win+i"),
        keys("Quick Link Menu", "Power user menu (Win+X)", "win+x"),
        keys("Show Desktop", "Minimize all / restore (Win+D)", "win+d"),
        keys("Lock PC", "Lock the workstation (Win+L)", "win+l"),
        keys("Snipping Tool", "Region screenshot (Win+Shift+S)", "win+shift+s"),
        keys("Clipboard History", "Clipboard history (Win+V)", "win+v"),
        keys("Emoji Panel", "Emoji panel (Win+.)", "win+."),
        keys("Task View", "Task View (Win+Tab)", "win+tab"),
        keys("Next Desktop", "Next virtual desktop (Ctrl+Win+Right)", "ctrl+win+right"),
        keys("Project", "Display mode (Win+P)", "win+p"),
        keys("Search", "Windows Search (Win+S)", "win+s"),
        cmd("Network Adapters", "Network Connections (ncpa.cpl)", "ncpa.cpl"),
        cmd("Wi-Fi Settings", "Settings: Wi-Fi", "ms-settings:network-wifi"),
        cmd("Display Settings", "Settings: Display", "ms-settings:display"),
        cmd("Control Panel", "Classic Control Panel", "control"),
        cmd("Device Manager", "Device Manager (devmgmt.msc)", "devmgmt.msc"),
        cmd("System Properties", "System Properties (sysdm.cpl)", "sysdm.cpl"),
        cmd("Programs and Features", "Uninstall programs (appwiz.cpl)", "appwiz.cpl"),
        cmd("Services", "Windows Services (services.msc)", "services.msc"),
        cmd("Disk Management", "Disk Management (diskmgmt.msc)", "diskmgmt.msc"),
        cmd("Event Viewer", "Event Viewer (eventvwr.msc)", "eventvwr.msc"),
        cmd("Sound", "Sound devices (mmsys.cpl)", "mmsys.cpl"),
        cmd("Power Options", "Power Options (powercfg.cpl)", "powercfg.cpl"),
        cmd("System Information", "System Information (msinfo32)", "msinfo32"),
        cmd("DirectX Diagnostic", "DirectX Diagnostic Tool (dxdiag)", "dxdiag"),
        cmd("Command Prompt", "cmd", "cmd"),
        cmd("PowerShell", "Windows PowerShell", "powershell"),
        cmd("Registry Editor", "Registry Editor (UAC prompt)", "regedit"),
    ]),
    (2, "Work", [
        cmd("Firefox Profile Manager", "Open Firefox profile selector", "firefox -p"),
        cmd("Task Manager", "Open Windows Task Manager", "taskmgr"),
    ]),
    (3, "Programming", [
        cmd("Android Studio", "Launch Android Studio from PATH/App Paths", "studio64"),
        cmd("Visual Studio Code", "Launch VS Code", "code"),
    ]),
    (4, "Cursor IDE", [
        keys("AI Chat", "Open AI Chat (Ctrl+L)", "ctrl+l"),
        keys("Inline Edit", "Inline AI edit (Ctrl+K)", "ctrl+k"),
        keys("Agent/Composer", "Open Agent / Composer (Ctrl+I)", "ctrl+i"),
        keys("Accept Change", "Accept suggestion or inline change (Tab)", "tab"),
        keys("Reject Change", "Reject or dismiss (Escape)", "escape"),
        keys("Accept All", "Accept all suggested changes (Ctrl+Enter)", "ctrl+enter"),
        keys("Next Diff", "Next chat / change (Ctrl+])", "ctrl+]"),
        keys("Prev Diff", "Previous chat / change (Ctrl+[)", "ctrl+["),
        keys("Terminal", "Toggle integrated terminal (Ctrl+`)", "ctrl+`"),
        keys("Command Palette", "Command palette (Ctrl+Shift+P)", "ctrl+shift+p"),
        keys("Quick Open", "Quick open file (Ctrl+P)", "ctrl+p"),
        keys("New Chat", "New chat (Ctrl+N)", "ctrl+n"),
    ]),
    (5, "Streamer deck", [
        cmd("Open OBS", "Launch OBS. Default install path",
            "\"C:\\Program Files\\obs-studio\\bin\\64bit\\obs64.exe\""),
        keys("Game Bar", "Windows capture bar (Win+G)", "win+g"),
        keys("Record", "Start and stop Game Bar recording (Win+Alt+R)", "win+alt+r"),
        keys("Last 30s", "Last 30 seconds, if background recording is on (Win+Alt+G)", "win+alt+g"),
        keys("Mute mic", "Game Bar microphone (Win+Alt+M)", "win+alt+m"),
        keys("Screenshot", "Game Bar screenshot (Win+Alt+PrtScn)", "win+alt+prtsc"),
        keys("Scene 1", "OBS scene. Assign Ctrl+F1 once in OBS", "ctrl+f1"),
        keys("Scene 2", "OBS scene. Assign Ctrl+F2 once in OBS", "ctrl+f2"),
        keys("Scene 3", "OBS scene. Assign Ctrl+F3 once in OBS", "ctrl+f3"),
        keys("Go live", "OBS stream. Assign Start Streaming to Ctrl+F9 once", "ctrl+f9"),
    ]),
]


def build():
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    if os.path.exists(OUTPUT):
        os.remove(OUTPUT)

    db = sqlite3.connect(OUTPUT)
    for statement in SCHEMA:
        db.execute(statement)

    for category_order, (category_id, category_title, presets) in enumerate(CATEGORIES):
        db.execute(
            "INSERT INTO preset_categories (id, title, sortOrder, isBuiltIn, colorArgb, createdAt) "
            "VALUES (?, ?, ?, 1, 0, ?)",
            (category_id, category_title, category_order, CREATED_AT),
        )
        if len(presets) > 99:
            raise ValueError(f"{category_title}: more than 99 presets breaks the ID scheme")
        for preset_order, (title, description, action_type, payload) in enumerate(presets):
            preset_id = category_id * 100 + preset_order + 1
            db.execute(
                "INSERT INTO presets (id, categoryId, title, description, riskLevel, "
                "requiresConfirmation, isSensitive, isBuiltIn, sortOrder, createdAt) "
                "VALUES (?, ?, ?, ?, 'normal', 0, 0, 1, ?, ?)",
                (preset_id, category_id, title, description, preset_order, CREATED_AT),
            )
            db.execute(
                "INSERT INTO preset_actions (id, presetId, type, payloadJson, sortOrder) "
                "VALUES (?, ?, ?, ?, 0)",
                (preset_id * 10, preset_id, action_type,
                 json.dumps(payload, ensure_ascii=False, separators=(",", ":"))),
            )

    for table in ("preset_categories", "presets", "preset_actions"):
        db.execute("DELETE FROM sqlite_sequence WHERE name = ?", (table,))
        db.execute("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", (table, USER_ID_START - 1))

    db.execute(f"PRAGMA user_version = {DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    build()

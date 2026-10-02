# Android Bluetooth HID Device Roadmap

Этот файл — канонический roadmap проекта. Он показывает, какие требования выполнены,
какие файлы подтверждают фактическое состояние проекта, и какие шаги остаются следующему ИИ-агенту.

## Current Verified State

- [x] `MasterPrompt.md` содержит компактный главный контекст проекта, стек, ключевые решения и журнал ошибок/решений.
- [x] Корневой `Roadmap.md` не нужен; единственный roadmap должен находиться в `Documentation/Roadmap.md`.
- [x] HID-ядро реализовано через `BluetoothProfile.HID_DEVICE` в `app/src/main/java/com/walhalla/bluetoothhiddevice/HidDeviceManager.kt`.
- [x] UI использует Compose/Material 3 в `app/src/main/java/com/walhalla/bluetoothhiddevice/HidScreen.kt`.
- [x] Состояние экрана хранится в `HidUiState` и прокидывается через `HidViewModel`.

## Completed Requirements

### Paired Device Row: Connect/Disconnect

- [x] Проблема: строка bonded device показывала только кнопку `Connect` и не различала, какое именно устройство уже подключено.
- [x] Решение: `HidUiState` хранит `connectedDeviceAddress`, а `BondedDeviceRow` получает row-level `isConnected`.
- [x] Поведение: если адрес строки совпадает с `connectedDeviceAddress`, кнопка показывает `Disconnect`; иначе показывает `Connect`.
- [x] UI: добавлены тематические Bluetooth Material icons через `material-icons-extended`.

### Host Calculator Shortcut

- [x] Проблема: рядом с `Send Test 'A' Key` нужна кнопка для проверки системного shortcut: `Win+R`, ввод `calc`, `Enter`.
- [x] Решение: добавлена кнопка `Win+R calc`; она активна только при `uiState.isConnected`.
- [x] HID-поведение: отправляется Left GUI modifier `0x08` + клавиша `R`, затем команда `calc\n`.
- [x] Ограничение: сценарий рассчитан на Windows host; на других ОС поведение зависит от системных shortcuts.
- [ ] Проверка на реальном Windows host: подтвердить, что открывается Calculator.

### RoomDB Preset System

- [x] Проблема: hardcoded пресеты не позволяют пользователю создавать категории `Дом`, `Работа`, `Программирование`, хранить свои сценарии, импортировать/экспортировать их и добавлять sensitive ввод.
- [x] Решение: добавлена модель `PresetCategory -> Preset -> PresetAction` на RoomDB.
- [x] UI: добавлена вкладка `Presets`, category chips, preset cards, add dialog, toolbar menu из RoomDB, import/export actions.
- [x] Macro actions: `RunWindowsCommand`, `TypeText`, `TypeSensitiveText`, `Credential`, `KeyCombo`, `KeyPress`, `Delay`.
- [x] Credential preset: в редакторе показываются два поля `Login` и `Password`; запуск вводит login, `Tab`, password, `Enter`.
- [x] Credential safety: credential presets всегда помечаются `isSensitive`, требуют подтверждение перед запуском и сохраняются plaintext в RoomDB в рамках текущего решения по sensitive data.
- [x] Безопасность: sensitive значения на первом этапе хранятся в RoomDB plaintext по решению пользователя, но помечаются `isSensitive`, требуют подтверждение перед запуском и предупреждают перед экспортом.
- [x] Импорт/экспорт: JSON через системные `OpenDocument` и `CreateDocument`.
- [x] UX: при добавлении пресета `title` и `description` предзаполнены значением формата `Preset-123`; ViewModel также применяет fallback при пустых строках.
- [x] UX: у айтемов пресетов добавлены Material icons по типу первого действия (`RunWindowsCommand`, `TypeText`, `TypeSensitiveText`, `KeyCombo`, `KeyPress`, `Delay`).
- [x] Items: у preset items добавлены действия `Copy`, `Edit` и `Delete`; copy создаёт новый custom preset в той же категории с теми же actions, edit открывает редактор с текущими значениями, delete удаляет custom preset и actions после подтверждения.
- [x] Items: встроенные seed presets помечены `PresetEntity.isBuiltIn`. Их можно запускать, копировать и редактировать. Удаление встроенного пресета по-прежнему закрыто.
- [x] Группы: категории пресетов — чипы с переносом строк, без горизонтальной прокрутки. Все группы видны сразу.
- [x] Группы: добавлен механизм добавления пользовательских групп с default title формата `Group-123`.
- [x] Группы: пользовательские группы можно удалять; встроенные `Home`, `Work`, `Programming` помечены `isBuiltIn` и не удаляются.
- [x] UI: действия вкладки `Presets` собраны в одну кнопку внизу справа. Меню сверху вниз: выбранная группа, `View`, `All presets`. Описание: `Documentation/presets-tab.md`.
- [x] RoomDB: добавлена миграция v1 -> v2 для `preset_categories.isBuiltIn`.
- [x] RoomDB: добавлена миграция v2 -> v3 для `presets.isBuiltIn`.
- [x] RoomDB: миграция v3 -> v4 добавляет `preset_categories.colorArgb`. `0` — без тинта. Цвет уходит в JSON импорта и экспорта.
- [x] Цвет группы: круглая кнопка `Palette` в меню. Выбранный цвет тонирует карточки пресетов этой группы: фон смешивается с цветом, иконка действия берёт его напрямую. Встроенную группу тоже можно перекрасить.
- [x] Встроенная группа `Home`: тестовые сочетания Windows (`Ctrl+Alt+Del`, `Ctrl+Shift+Esc`, `Win+E/I/X/D/L/V/./Tab/P/S`, `Win+Shift+S`, `Ctrl+Win+Right`) и апплеты через `Win+R` (`ncpa.cpl`, `ms-settings:*`, `devmgmt.msc`, `services.msc` и др.).
- [ ] Проверка на Windows host: пройти все пресеты `Home`. Команды `Win+R` печатаются US-кодами, на хосте должна быть английская раскладка.
- [x] Встроенная группа `Streamer deck`: открытие OBS, шорткаты Game Bar и условные шорткаты сцен OBS. Удалить группу нельзя.
- [x] Навигация: выбранный таб переживает поворот (`rememberSaveable`). Кнопка назад с `Presets` или `Type` возвращает на `Devices`, со вкладки `Devices` закрывает экран.
- [x] Build note: для текущей связки AGP 9 built-in Kotlin + KSP добавлен `android.disallowKotlinSourceSets=false` в `gradle.properties`.
- [x] HID experiment: composite descriptor (`Report ID 1` keyboard, `Report ID 2` mouse) был проверочно добавлен и затем откатан; пользователь подтвердил, что в keyboard-only режиме ввод пресетов быстрее. Composite может требовать re-pairing для честного теста, но не должен быть режимом по умолчанию.
- [x] GitHub publishing: добавлен `README.md` с описанием проекта, возможностей, требований, сборки, использования, ограничений и ссылок на внутреннюю документацию.
- [x] UI: `Current Status` перенесён из отдельной карточки в `TopAppBar` как subtext под названием приложения; цвет subtext отражает connected/error/neutral состояние.
- [x] UI: preset item cards сделаны компактнее: меньше padding/spacing, короткая кнопка `Run`, action icons уменьшены, description обрезается в одну строку.
- [x] Background keepalive: UI теперь bind'ится к `HidForegroundService`, сервис владеет единственным `HidDeviceManager`, а `MainActivity.onStop()` переводит уже подключённую HID-сессию в foreground service, чтобы связь не обрывалась при сворачивании.
- [ ] Проверка на устройстве: добавить custom preset, экспортировать JSON, импортировать обратно, запустить обычный и sensitive preset.
- [ ] Проверка на устройстве: подключиться к Windows host, свернуть приложение, подождать 1-2 минуты, вернуться и запустить preset без переподключения.

Файлы для проверки:
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidScreen.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidViewModel.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidDeviceManager.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/MainActivity.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/presets/`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `README.md`
- `MasterPrompt.md`

## Next Work

- [ ] Проверить реальное подключение/отключение на Android-устройстве с Bluetooth HID Device support.
- [ ] Проверить кнопку `Win+R calc` на Windows host с фактическим Bluetooth HID подключением.
- [ ] Проверить toolbar presets на Windows host: `firefox -p`, `studio64`, `code`, `taskmgr`.
- [ ] Проверить RoomDB preset flow: add, run, sensitive confirm, JSON export, JSON import.
- [ ] Проверить background keepalive на разных Android versions/OEM battery modes; при необходимости добавить подсказку про отключение battery optimization.
- [ ] Проверить UX для `STATE_CONNECTING` и `STATE_DISCONNECTING`: при необходимости добавить промежуточное состояние строки.
- [ ] Решить deprecated warning для `BluetoothAdapter.getDefaultAdapter()` без ломки minSdk/API behavior.
- [ ] Довести Persistent Mode/Foreground Service до полностью проверенного сценария фоновой работы.

### Keyboard coverage: полная эмуляция клавиш

Отправка: `keyNameToUsageId` и `modifierNameToByte` в `HidDeviceManager.kt`. Форма: `ShortcutKeys` и `ShortcutDraft` в `presets/PresetShortcutDraft.kt`. Строка пресета: `PresetShortcutParser`.

Без правки дескриптора:

- [x] Баг F10–F12: проверка `upper.length == 2` отсекала трёхсимвольные имена, `sendKeyComboBlocking` молча возвращал `false`. Теперь `2..3`.
- [x] Навигация: Home `0x4A`, End `0x4D`, Page Up `0x4B`, Page Down `0x4E`, Insert `0x49`.
- [x] Переключатели: Caps Lock `0x39`, Scroll Lock `0x47`, Pause/Break `0x48`, Num Lock `0x53`.
- [x] Numpad: Num0–Num9, Num+, Num−, Num*, Num/, Num., NumEnter (`0x54`–`0x63`).
- [x] Клавиша контекстного меню (Application) `0x65`.
- [x] Правые модификаторы: Right Ctrl `0x10`, Right Shift `0x20`, Right Alt / AltGr `0x40`, Right Win `0x80`.
- [x] Форма `ShortcutKeys` догоняет менеджер: PrtScn, `\ ; ' , . /`, группы `Nav`, `System`, `Numpad`, чипы `RCtrl`, `RShift`, `AltGr`, `RWin`. Ряд групп прокручивается, форма открывается на группе выбранной клавиши.
- [x] Парсер: `+` как клавиша (`ctrl++` → Num+), имена с пробелом (`page up`), алиасы (`pgup`, `esc`, `altgr`) приводятся к одному токену. `ShortcutKeys.findByToken` использует тот же `normalizeKey`. Тест: `PresetShortcutParserTest`.
- [ ] Проверка на хосте: F10–F12, Home/End/PgUp/PgDn, Numpad (Num Lock включён), AltGr, Menu, Edit встроенного `Screenshot` показывает Win + Alt + PrtScn.
- [ ] Пауза между нажатием и отпусканием в `sendKey`: добавлять, только если хост пропускает сочетания. На `Ctrl+Alt+Del` без паузы работает.

Нужна правка дескриптора (re-pairing хоста, отдельное решение):

- [ ] F13–F24 (`0x68`–`0x73`): Logical Maximum и Usage Maximum массива клавиш сейчас `0x65`.
- [ ] Мультимедиа: громкость, Play/Pause, Next, Mute — страница Consumer Control, отдельный report ID.
- [ ] Системные: Sleep, Power — страница System Control, отдельный report ID.

Ввод текста (`charToKeyCode`): латиница, цифры, символы US-раскладки, кириллица ЙЦУКЕН. Кириллица печатается, только если на хосте русская раскладка.

### Future: одновременные клавиши и удержание (не блокирует релиз)

Сейчас `sendKey` шлёт отчёт «модификаторы в `report[0]` + одна клавиша в `report[2]`» и сразу отпускает. Любые модификаторы с одной клавишей уже работают (`Ctrl+Alt+Del` проверен на хосте). Не работают сценарии ниже.

- [ ] Две и больше обычных клавиш одновременно (6KRO, `report[2..7]`). Пример: `W+D` в игре — движение по диагонали.
- [ ] Удержание между нажатиями: действия `KeyDown` / `KeyUp` и зажатое состояние в `HidDeviceManager`. Пример: `Alt` зажат, `Tab` ×3, `Alt` отпущен — выбор третьего окна в Alt+Tab.
- [ ] Долгое удержание с автоповтором хоста. Пример: зажатый `Backspace` или стрелка.
- [ ] Alt-коды: зажатый `Alt` + цифры Numpad. Пример: `Alt+0169` → `©`. Нужен и Numpad.

## UI Quality Checklist

Этот раздел применяется к будущим экранам и формам с пользовательским вводом.

### Form Validation & Interactive Feedback

Форма — это диалог с пользователем. Диалог должен быть вежливым, предсказуемым и не допускать бессмысленных действий.

- [ ] Кнопка действия (`Submit`, `Save`, `Send`) программно неактивна (`enabled = false`), пока все обязательные поля не проходят базовую валидацию.
- [ ] Для сложных форм используются маски или автоматическое форматирование, если это снижает риск ошибки пользователя.
- [ ] Валидация происходит при потере фокуса или при попытке сабмита, но не появляется без понятного действия пользователя.
- [ ] Ошибка отображается не только красной обводкой: цвет, текст, иконка и анимация должны быть согласованы с Material 3.
- [ ] Сообщение об ошибке конструктивное и вежливое: не `Ошибка`, а конкретное действие для исправления.

### Tabs, Toolbar, Presets Deck

Портрет: `TopAppBar` со статусом и ряд табов `Devices`, `Presets`, `Type`. Альбом: верхней панели нет, слева узкий столбец. Вкладка `Presets` — один `LazyVerticalGrid`. Подробности кнопок: `Documentation/presets-tab.md`.

- [x] Табы `Devices`, `Presets` и `Type` показывают тематическую иконку. В портрете иконка стоит рядом с названием.
- [x] На вкладке `Devices` кнопки `Make Discoverable` и `Reset HID Service` стоят по центру. У первой иконка `BluetoothSearching`, у второй `RestartAlt` на `errorContainer`.
- [x] Статус красится по смыслу, в тулбаре и в лампочке альбома. Подключено — `primary`. `App Registered (Ready)` — `tertiary`. Инициализация, подключение и отключение — `secondary`. Ожидание и `Disconnected` — нейтральный. Ошибка, Bluetooth выключен, нет разрешения, unregister и failed — `error`.
- [x] В альбоме `TopAppBar` скрыт. Слева столбец шириной 48 dp. Сверху лампочка статуса 18 dp, глиф 12 dp: это не кнопка. Подключено — `primaryContainer`, ошибка — `errorContainer`, обычное состояние — `surfaceVariant` и `outline`.
- [x] Кнопки столбца `Devices`, `Presets`, `Type` и меню команд выровнены по вертикальному центру. Активная иконка — `primary`, остальные — `onSurfaceVariant`.
- [x] Внизу столбца кнопка того же меню, что плавающая кнопка в портрете. В альбоме плавающая кнопка не показывается.
- [x] Вкладка `Presets` сверху вниз: карточка чипов групп на всю ширину экрана, без боковых отступов, затем карточки пресетов на всю ширину. Между табами и первой карточкой 12 dp.
- [x] Чипы групп переносятся на новую строку, без горизонтального скролла. Плюс новой группы — контурный кружок в этом ряду.
- [x] Сетка — один `LazyVerticalGrid`, без второго скролла. `List` — одна колонка. `×2` / `×3` / `×4` — сторона кнопки 160 / 104 / 76 dp. Столбцов столько, сколько влезает в ширину экрана, зазор 8 dp.
- [x] Кнопка меню в портрете — внизу справа. На ней иконка текущего вида. Контейнер меню скруглён на 28 dp.
- [x] Меню сверху вниз, каждая группа своим рядом. Сначала имя выбранной группы (`titleSmall`, жирный, `primary`, у встроенной суффикс ` * `) и круглые кнопки: цвет, добавить пресет, удалить группу. Удаление встроенной группы видно и неактивно. Затем `View`: чипы `List`, `×2`, `×3`, `×4`. Затем `All presets`: чипы `Import` и `Export` всего файла.
- [x] Цвета карточек, иконок и текста берутся из Material 3 `colorScheme`. Тинт группы смешивается с цветом карточки, без отдельной палитры на light и dark.

### HID Text Input

Отдельная вкладка `Type`. Тачпад в этот релиз не входит. HID шлёт код клавиши US-раскладки и модификаторы, не символ Unicode и не язык клавиатуры телефона. Хост рисует глиф по своей активной раскладке.

- [x] Кнопка отправляет текущий текст буфера обмена по HID. Пустой буфер не шлёт ничего.
- [x] Многострочное поле: тап открывает системную клавиатуру телефона, введённый текст уходит на хост.
- [x] Кодировка символа — текущая US-карта `charToKeyCode`. Обе стороны EN: символ на хосте совпадает с символом в поле и в буфере.
- [x] Раскладка телефона не переносится на хост. Китайский IME на телефоне и русская раскладка на хосте не дают иероглиф: в HID нет такого кода клавиши. На хост попадает только то, что можно нажать как клавишу выбранной карты; остальное пропускается.
- [x] Русские буквы в `charToKeyCode` идут кодами клавиш ЙЦУКЕН. На хосте с русской раскладкой `Ф` приходит как `Ф`, с английской — как `A`. Другая языковая пара по-прежнему требует свою карту.

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
- [x] Macro actions: `RunWindowsCommand`, `TypeText`, `TypeSensitiveText`, `KeyCombo`, `KeyPress`, `Delay`.
- [x] Безопасность: sensitive значения на первом этапе хранятся в RoomDB plaintext по решению пользователя, но помечаются `isSensitive`, требуют подтверждение перед запуском и предупреждают перед экспортом.
- [x] Импорт/экспорт: JSON через системные `OpenDocument` и `CreateDocument`.
- [x] UX: при добавлении пресета `title` и `description` предзаполнены значением формата `Preset-123`; ViewModel также применяет fallback при пустых строках.
- [x] UX: у айтемов пресетов добавлены Material icons по типу первого действия (`RunWindowsCommand`, `TypeText`, `TypeSensitiveText`, `KeyCombo`, `KeyPress`, `Delay`).
- [x] Группы: категории пресетов отображаются чипами в две строки с горизонтальной прокруткой.
- [x] Группы: добавлен механизм добавления пользовательских групп с default title формата `Group-123`.
- [x] Группы: пользовательские группы можно удалять; встроенные `Дом`, `Работа`, `Программирование` помечены `isBuiltIn` и не удаляются.
- [x] RoomDB: добавлена миграция v1 -> v2 для `preset_categories.isBuiltIn`.
- [x] Build note: для текущей связки AGP 9 built-in Kotlin + KSP добавлен `android.disallowKotlinSourceSets=false` в `gradle.properties`.
- [ ] Проверка на устройстве: добавить custom preset, экспортировать JSON, импортировать обратно, запустить обычный и sensitive preset.

Файлы для проверки:
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidScreen.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidViewModel.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidDeviceManager.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/MainActivity.kt`
- `app/src/main/java/com/walhalla/bluetoothhiddevice/presets/`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `MasterPrompt.md`

## Next Work

- [ ] Проверить реальное подключение/отключение на Android-устройстве с Bluetooth HID Device support.
- [ ] Проверить кнопку `Win+R calc` на Windows host с фактическим Bluetooth HID подключением.
- [ ] Проверить toolbar presets на Windows host: `firefox -p`, `studio64`, `code`, `taskmgr`.
- [ ] Проверить RoomDB preset flow: add, run, sensitive confirm, JSON export, JSON import.
- [ ] Следующий этап: реализовать третью вкладку `Keyboard/Touchpad` по отдельному плану, если пользователь снова подтвердит.
- [ ] Проверить UX для `STATE_CONNECTING` и `STATE_DISCONNECTING`: при необходимости добавить промежуточное состояние строки.
- [ ] Решить deprecated warning для `BluetoothAdapter.getDefaultAdapter()` без ломки minSdk/API behavior.
- [ ] Довести Persistent Mode/Foreground Service до полностью проверенного сценария фоновой работы.

## UI Quality Checklist

Этот раздел применяется к будущим экранам и формам с пользовательским вводом.

### Form Validation & Interactive Feedback

Форма — это диалог с пользователем. Диалог должен быть вежливым, предсказуемым и не допускать бессмысленных действий.

- [ ] Кнопка действия (`Submit`, `Save`, `Send`) программно неактивна (`enabled = false`), пока все обязательные поля не проходят базовую валидацию.
- [ ] Для сложных форм используются маски или автоматическое форматирование, если это снижает риск ошибки пользователя.
- [ ] Валидация происходит при потере фокуса или при попытке сабмита, но не появляется без понятного действия пользователя.
- [ ] Ошибка отображается не только красной обводкой: цвет, текст, иконка и анимация должны быть согласованы с Material 3.
- [ ] Сообщение об ошибке конструктивное и вежливое: не `Ошибка`, а конкретное действие для исправления.

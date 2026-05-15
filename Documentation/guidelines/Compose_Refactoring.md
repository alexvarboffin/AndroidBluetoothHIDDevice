# 📱 Material 3 и оптимизация для ИИ

| ❌ Устаревшие | ✅ Актуальные |
|------------|------------|
| `Divider` | `HorizontalDivider` |
| `Icons.Default.ArrowBack` | `Icons.AutoMirrored.Filled.ArrowBack` |
| `CircularProgressIndicator` | `CircularProgressIndicator(strokeCap = StrokeCap.Round)` |
| `FloatingActionButton` | `FloatingActionButton(elevation = FloatingActionButtonDefaults.elevation())` |
| `TopAppBar` | `TopAppBar()` с М3 параметрами |
| `BottomNavigation` | `NavigationBar` |
| `TabRow` | `SecondaryTabRow` или `PrimaryTabRow` (в зависимости от уровня важности) |

1. **Краткость**: Минимум текста, максимум контекста
2. **Структура**: Используй ключевые слова в начале предложений
3. **Специфика**: Конкретные запросы вместо общих указаний
4. **Примеры**: Добавляй сниппеты кода для контекста
5. **Форматирование**: Используй маркеры, заголовки, эмодзи

```
Измени:
- Замени Divider на HorizontalDivider
- Используй Icons.AutoMirrored.Filled вместо Icons.Default
- Замени TabRow на SecondaryTabRow


Контекст:
```kotlin
// Пример текущего кода для контекста
```

```

- Используй [M3 Color System](https://m3.material.io/styles/color/the-color-system/color-roles)
- Применяй `contentColorFor()` для автоматического подбора цвета контента
- Предпочитай композиционный подход: маленькие переиспользуемые компоненты 
=======
Всегда разделяй на Screen / Content / Widgets / Dialog.

Никогда не открывай Dialog внутри UI компонента.

UI компонент не знает про ViewModel.

Все действия — через callbacks.

State делится на:

Screen state (из ViewModel)

UI state (локальный remember)

Overlay живут только в root Box/Scaffold.

Если компонент нельзя использовать в Preview — он разбит неправильно.

Если компонент нельзя переиспользовать в другом экране — он разбит неправильно.

Экспериментальные API изолируются в wrapper.

Большой composable всегда режется минимум на 3 части.
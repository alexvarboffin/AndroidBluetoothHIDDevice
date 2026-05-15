Скрины:
Скрины не принимают навигатор, а колбеком отстукивают наверх.
Не писать все состояния скрина единым блоком кода, а разбивать на функции
Пример:
- Неправильно
// Отображаем состояние загрузки
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
- Правильно
// Отображаем состояние загрузки
        if (uiState.isLoading) {
ContentLoadingIndicator()
}            

Это позволит каждое состояние тестировать, и под каждое делать Preview если необходимо

1. пиши все полученные функции в классе скрина

1. пиши все полученные функции в классе скрина
2. CircularProgressIndicator() - должен быть по центру экрана
3. Если функция принимает `Modifier` в качестве параметра, он должен быть первым необязательным параметром. Это стандартная конвенция в Jetpack Compose, которая улучшает читаемость и консистентность кода.

- Неправильно:
  ```kotlin
  @Composable
  fun MyComponent(text: String, modifier: Modifier = Modifier) { ... }
  ```

- Правильно:
  ```kotlin
  @Composable
  fun MyComponent(modifier: Modifier = Modifier, text: String) { ... }
  ```

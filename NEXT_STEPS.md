# Zenith OTT — Следующие шаги после первой зелёной сборки

Когда GitHub Actions покажет зелёный билд — значит проект компилируется. Дальше:

---

## Этап 1: Проверка на устройстве (день 1)

### 1.1 Скачать APK

```
GitHub → Actions → последний билд → Artifacts → Zenith-TV-Debug-APK → Download
```

### 1.2 Установить на телефон/TV

```bash
adb install app-debug.apk
```

Или через USB на Android TV:
```bash
adb connect <TV_IP>:5555
adb install app-debug.apk
```

### 1.3 Что проверить

| Экран | Что смотреть | Ожидание |
|---|---|---|
| Setup | Ввод M3U URL | Переход на Home |
| Home | Список фильмов | Карточки с постерами |
| Detail | Информация о фильме | Постер, описание, кнопка Play |
| Player | Воспроизведение HLS | Видео + контролы |
| Favorites | Добавить/удалить | Сохраняется |
| History | Просмотр записи | Добавляется автоматически |
| Settings | Выход | Возврат на Setup |

### 1.4 Известные ограничения (не баги)

- Phone-экраны могут выглядеть иначе чем TV
- Плагины не тестировались (нет сервера плагинов)
- TMDB может не работать без API ключа
- QR-сканер требует реальную камеру

---

## Этап 2: Фикс runtime-ошибок (день 2-3)

После проверки на устройстве будут видны:
- Crash при открытии экрана → читать Logcat
- Белый экран → проблема с ViewModel/DI
- Нет данных → проблема с API/кэшем

```bash
# Смотреть логи крашей
adb logcat -s AndroidRuntime:E
```

---

## Этап 3: Добавить недостающие компоненты (неделя 1)

### 3.1 Подключить PlayerController к PlayerScreen

Сейчас PlayerScreen использует `useController = false` — нет контролов.
Нужно подключить `PlayerController.kt` и `QualityMenuOverlay.kt` (уже в проекте).

### 3.2 Подключить CatalogRow

`CatalogRow.kt` есть но не используется в HomeScreen. Нужно добавить горизонтальные ряды каталога.

### 3.3 Настроить ProGuard

`app/proguard-rules.pro` пустой. Для релиза нужны правила для:
- Retrofit (keep API interfaces)
- Room (keep entities)
- Gson (keep serialized fields)
- ExoPlayer

---

## Этап 4: Релизная подготовка (неделя 2)

### 4.1 Настроить release keystore

```bash
# Сгенерировать keystore
keytool -genkeypair -v -storetype PKCS12 \
  -keystore zenith-release.jks \
  -alias zenith \
  -keyalg RSA -keysize 2048 \
  -validity 10000

# Закодировать в base64 для GitHub Secrets
base64 -i zenith-release.jks | tr -d '\n'
```

Добавить в GitHub Secrets:
- `KEYSTORE_BASE64` — base64 keystore
- `KEY_ALIAS` — alias (например `zenith`)
- `KEY_PASSWORD` — пароль ключа
- `STORE_PASSWORD` — пароль keystore

### 4.2 Создать первый релиз

```
GitHub → Releases → Create new release
Tag: v1.0.0
Title: Zenith OTT v1.0.0
```

CI автоматически соберёт signed APK + AAB.

### 4.3 Подготовить описание

- Скриншоты экранов
- Описание функционала
- Системные требования (Android 8.0+, 2GB RAM)
- Ссылка на APK/AAB

---

## Этап 5: Публикация (неделя 3+)

### Вариант A: GitHub Releases (бесплатно)

Просто загрузить APK в релиз на GitHub. Любой может скачать и установить.

### Вариант B: F-Droid (бесплатно)

Open-source маркет. Нужно:
1. Убрать все проприетарные зависимости (TMDB API?)
2. Добавить метаданные для F-Droid
3. Подать заявку

### Вариант C: Google Play ($25)

1. Зарегистрировать аккаунт разработчика ($25 один раз)
2. Подготовить AAB (уже собирается в CI)
3. Заполнить описание, скриншоты
4. Пройти review (1-7 дней)

---

## Этап 6: Долгосрочное развитие

### Приоритеты

| Задача | Приоритет | Сложность |
|---|---|---|
| Player controls | Высокая | Средняя |
| Unit тесты | Высокая | Низкая |
| ProGuard правила | Высокая | Низкая |
| Phone UI polish | Средняя | Средняя |
| Plugin system | Средняя | Высокая |
| TMDB integration | Средняя | Средняя |
| Sync между устройствами | Низкая | Высокая |
| F-Droid / Play Store | Низкая | Средняя |

### Регулярные задачи

- Обновлять зависимости (Dependabot уже настроен)
- Проверять Issues на GitHub
- Добавлять тесты для новых фич
- Обновлять документацию

---

## Чек-лист после каждой сборки

```
✅ GitHub Actions зелёный
✅ APK скачивается из Artifacts
✅ Устанавливается на устройство
✅ Основные экраны открываются
✅ Нет crash в Logcat
✅ Данные загружаются (или graceful error)
```

Если всё ✅ — можно делать релиз.

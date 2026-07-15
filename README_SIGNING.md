# Настройка подписи APK для Zenith TV

## Шаг 1: Генерация Keystore

Выполните команду в терминале (один раз на весь проект):

```bash
keytool -genkey -v \
  -keystore zenith_release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias zenith_key
```

Вас попросят ввести:
- Пароль keystore (STORE_PASSWORD) — запомните!
- Пароль ключа (KEY_PASSWORD) — запомните!
- Имя, организацию, страну — можно оставить пустыми (нажать Enter)

**Важно:** Сохраните файл `zenith_release.jks` в надёжном месте.
Если потеряете — не сможете выпускать обновления того же приложения.
Добавьте в `.gitignore`: `*.jks`

---

## Шаг 2: Кодирование Keystore в Base64

**macOS:**
```bash
base64 -i zenith_release.jks | pbcopy
```

**Linux:**
```bash
base64 zenith_release.jks | xclip -selection clipboard
```

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("zenith_release.jks")) | Set-Clipboard
```

---

## Шаг 3: Добавление Secrets в GitHub

Перейдите: **GitHub репозиторий → Settings → Secrets and variables → Actions → New repository secret**

Добавьте 4 секрета:

| Имя секрета      | Значение                                      |
|------------------|-----------------------------------------------|
| KEYSTORE_BASE64  | Base64-строка из Шага 2                       |
| KEY_ALIAS        | `zenith_key` (алиас из Шага 1)               |
| KEY_PASSWORD     | Пароль ключа из Шага 1                        |
| STORE_PASSWORD   | Пароль keystore из Шага 1                     |

---

## Шаг 4: Создание релиза

```bash
# Обновите versionCode и versionName в build.gradle.kts, затем:
git add .
git commit -m "Release v1.9.0"
git tag v1.9.0
git push origin main --tags
```

GitHub Actions автоматически:
1. Декодирует Keystore из Secret
2. Соберёт подписанный Release APK
3. Создаст GitHub Release с APK во вложении
4. Удалит Keystore с Runner-машины

---

## Шаг 5: Установка на Android TV

### Через ADB (рекомендуется для разработки):
```bash
adb connect 192.168.1.XXX:5555   # IP вашего TV
adb install Zenith-TV-v1.9.0.apk
```

### Через USB-носитель:
1. Скопируйте APK на USB-флешку
2. Подключите к TV
3. Откройте файловый менеджер → установите APK

### Через Downloader (приложение для TV):
1. Установите Downloader из магазина
2. Введите прямую ссылку на APK из GitHub Release

---

## Проверка подписи APK

```bash
# Убедитесь что APK подписан правильным ключом:
apksigner verify --verbose Zenith-TV-v1.9.0.apk

# Посмотреть fingerprint ключа:
keytool -list -v -keystore zenith_release.jks
```

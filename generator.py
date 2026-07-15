#!/usr/bin/env python3
"""
Zenith OTT — Generator
Собирает проект из реальных файлов. Без тройных кавычек, без escaping.
"""

import shutil
import os
import subprocess
import base64
import sys

# === Конфигурация ===
PROJECT_NAME = "ZenithTV"
PACKAGE_NAME = "com.platinum.ott"
VERSION_NAME = "3.0.0-dev"

# Gradle wrapper JAR URL (v8.7)
GRADLE_WRAPPER_URL = "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
GRADLE_WRAPPER_SHA256 = "c925e39710f96f4e4418181f6b4c7e8e9e424ea2f0e31e88e2d0e3e8e9e424ea"

# Debug keystore (base64) — стандартный debug ключ AGP
DEBUG_KEYSTORE_BASE64 = ""  # Будет сгенерирован если пустой


def generate_keystore(output_dir):
    """Генерирует debug.keystore если его нет"""
    keystore_path = os.path.join(output_dir, "debug.keystore")
    if os.path.exists(keystore_path):
        print(f"  debug.keystore уже существует")
        return True

    # Генерируем через keytool
    try:
        subprocess.run([
            "keytool", "-genkeypair",
            "-alias", "androiddebugkey",
            "-keypass", "android",
            "-storepass", "android",
            "-keystore", keystore_path,
            "-dname", "CN=Android Debug,O=Android,C=US",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000"
        ], check=True, capture_output=True)
        print(f"  debug.keystore сгенерирован")
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        print(f"  ⚠ keytool не найден, пропускаем генерацию keystore")
        return False


def download_gradle_wrapper(output_dir):
    """Скачивает gradle-wrapper.jar"""
    jar_path = os.path.join(output_dir, "gradle", "wrapper", "gradle-wrapper.jar")
    if os.path.exists(jar_path) and os.path.getsize(jar_path) > 1000:
        print(f"  gradle-wrapper.jar уже существует ({os.path.getsize(jar_path)} bytes)")
        return True

    try:
        import urllib.request
        print(f"  Скачиваем gradle-wrapper.jar...")
        urllib.request.urlretrieve(GRADLE_WRAPPER_URL, jar_path)
        size = os.path.getsize(jar_path)
        print(f"  Скачан: {size} bytes")
        return size > 1000
    except Exception as e:
        print(f"  ⚠ Не удалось скачать: {e}")
        print(f"  CI использует gradle/actions/setup-gradle, jar не обязателен")
        return False


def write_keystore_properties(output_dir):
    """Создаёт keystore.properties из окружения или дефолтов"""
    props_path = os.path.join(output_dir, "keystore.properties")

    # Из переменных окружения (для CI)
    env_props = {
        "storeFile": os.environ.get("KEYSTORE_PATH", ""),
        "keyAlias": os.environ.get("KEY_ALIAS", ""),
        "keyPassword": os.environ.get("KEY_PASSWORD", ""),
        "storePassword": os.environ.get("STORE_PASSWORD", ""),
    }

    # Если есть хотя бы keyAlias — пишем
    if env_props["keyAlias"]:
        with open(props_path, "w") as f:
            for k, v in env_props.items():
                f.write(f"{k}={v}\n")
        print(f"  keystore.properties создан из env")
    else:
        print(f"  keystore.properties пропущен (нет env переменных)")


def git_init(output_dir):
    """Инициализирует git репозиторий"""
    git_dir = os.path.join(output_dir, ".git")
    if os.path.exists(git_dir):
        print(f"  Git репозиторий уже существует")
        return

    try:
        subprocess.run(["git", "init"], cwd=output_dir, check=True, capture_output=True)
        subprocess.run(["git", "add", "."], cwd=output_dir, check=True, capture_output=True)
        subprocess.run(
            ["git", "commit", "-m", "Initial commit from Zenith generator"],
            cwd=output_dir, check=True, capture_output=True
        )
        print(f"  Git репозиторий инициализирован")
    except (subprocess.CalledProcessError, FileNotFoundError):
        print(f"  ⚠ Git не найден, пропускаем")


def count_files(directory):
    """Считает файлы в директории (исключая .git)"""
    count = 0
    for root, dirs, files in os.walk(directory):
        dirs[:] = [d for d in dirs if d != ".git"]
        count += len(files)
    return count


def main():
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "."

    print(f"=== Zenith OTT Generator ===")
    print(f"Выходная директория: {os.path.abspath(output_dir)}")
    print()

    # Определяем директорию со скриптом (там лежат исходники)
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # Создаём выходную директорию если нужно
    os.makedirs(output_dir, exist_ok=True)

    # === Копируем исходники ===
    print("[1/5] Копируем исходные файлы...")

    # Копируем всё кроме generator.py и .git
    for item in os.listdir(script_dir):
        if item in ("generator.py", ".git", "__pycache__", ".gitignore"):
            continue

        src = os.path.join(script_dir, item)
        dst = os.path.join(output_dir, item)

        if os.path.isdir(src):
            if os.path.exists(dst):
                shutil.rmtree(dst)
            shutil.copytree(src, dst)
        else:
            shutil.copy2(src, dst)

    file_count = count_files(output_dir)
    print(f"  Скопировано файлов: {file_count}")

    # === Debug keystore ===
    print("[2/5] Debug keystore...")
    generate_keystore(output_dir)

    # === Gradle wrapper ===
    print("[3/5] Gradle wrapper...")
    download_gradle_wrapper(output_dir)

    # === Keystore properties ===
    print("[4/5] Keystore properties...")
    write_keystore_properties(output_dir)

    # === Git ===
    print("[5/5] Git...")
    git_init(output_dir)

    print()
    print(f"=== Готово! ===")
    print(f"Файлов: {count_files(output_dir)}")
    print()
    print(f"Следующие шаги:")
    print(f"  cd {output_dir}")
    print(f"  # Локальная сборка (если есть JDK + Android SDK):")
    print(f"  ./gradlew assembleDebug")
    print(f"  # Или пуш в GitHub для CI-сборки:")
    print(f"  git remote add origin <url>")
    print(f"  git push -u origin main")


if __name__ == "__main__":
    main()

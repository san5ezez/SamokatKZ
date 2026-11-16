<div align="center">

# 🛴 SamokatKZ

**Умный навигатор для электросамокатов, адаптированный под ПДД Республики Казахстан**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Yandex MapKit](https://img.shields.io/badge/Yandex%20MapKit-4.45.0--full-FFCC00?style=for-the-badge&logoColor=black)](https://yandex.ru/dev/mapkit/)
[![Min SDK](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![GitHub Actions](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)](.github/workflows/android.yml)

</div>

---

## ✨ О проекте

**SamokatKZ** — это Android-приложение на **Kotlin** и **Jetpack Compose** (архитектура **MVVM**), созданное по эргономике Яндекс Карт специально для водителей электросамокатов в Казахстане (стартовая карта — Алматы с сетью велодорожек).

Навигатор решает главную проблему стандартных вело- и автонавигаторов: он учитывает **ограничения ПДД РК для СИМ**, исключает выезд на проезжую часть для несовершеннолетних, снижает расчётную скорость на тротуарах до **6 км/ч**, показывает рельеф маршрута и строит объезды пробок и ДТП.

---

## 🚀 Ключевые возможности

### 🗺️ Интерфейс в стиле Яндекс Карт
- **Полноэкранная карта** (`edge-to-edge`) с плавным управлением камерой, центром по умолчанию в **Алматы** и живым трекингом через **GPS + `UserLocationLayer`**.
- **Полупрозрачный поиск** с подсказками в реальном времени (`SuggestSession` / `SearchManager`), быстрыми точками города и выбором цели долгим нажатием на карту.
- **Плавающие кнопки (FAB) справа**: слои карты, масштаб `+ / −` и центрирование на текущем местоположении.
- **Выдвижная панель (`BottomSheetScaffold`)** с горизонтальной лентой альтернативных маршрутов и переключением вариантов прямо на карте по нажатию на линию.

### ⚖️ Два режима маршрутизации под законы РК

| Режим | Для кого | Логика построения маршрута | Скоростной режим |
| :--- | :--- | :--- | :--- |
| 🛡️ **Фулл Легал** | Лица до 18 лет и строгое соблюдение ПДД | Только официальные велодорожки (`BicycleRouterV2`). При разрыве велосети участок автоматически перестраивается на `PedestrianRouter` со **спешиванием** (`0%` проезжей части). | **25 км/ч** (вело) / **6 км/ч** (спешивание) |
| ⚡ **Полулегальный** | Быстрые городские поездки (шорткаты) | Комбинированный велосипедно-пешеходный граф (`createScooterRouter()` + `PedestrianRouter`), связывающий велополосы через тротуары и парки без долгих объездов. | **25 км/ч** (вело) / **6 км/ч** (тротуары) |

### 📈 Телеметрия, рельеф и безопасность
- **Индикатор ПДД РК (`25 / 6 км/ч`)**: постоянный знак ограничения скорости и контекстные напоминания на экране (спешивание на переходах, запрет выезда на ПЧ до 18 лет).
- **Точный расчёт ETA**: время в пути пересчитывается по каждому сегменту маршрута с учётом лимита **25 км/ч** на велодорожках и **6 км/ч** на тротуарах / при спешивании.
- **Интерактивный график перепада высот**: Canvas-профиль рельефа (`ElevationData`) с общим набором и сбросом высоты, максимальным уклоном и просмотром отметки высоты по касанию.
- **Пробки, ДТП и перекрытия**: активный `TrafficLayer`, маркеры дорожных событий (`DrivingRoute.events` и тяжёлых заторов) и автоматический расчёт альтернативного **маршрута объезда опасных участков**.
- **Тема «День / Ночь»**: системная тема по умолчанию с ручным переключателем **Авто / День / Ночь** в меню слоёв, синхронно переключающим ночной режим карты MapKit.

---

## 🏗️ Архитектура (MVVM)

```text
┌────────────────────────────────────────────────────────────────────────┐
│                       UI Layer (Jetpack Compose)                       │
│  YandexMapView  ·  TopHudOverlay  ·  RightMapControls  ·  BottomSheet  │
│  ElevationProfileChart (Canvas)  ·  LayersDialog (День / Ночь / Слои)  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ StateFlow<NavigatorUiState>
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    ViewModel (NavigatorViewModel)                      │
│   Дебаунс поиска · Переключение режимов ПДД РК · Камера и скорость     │
└───────────┬───────────────────────┬────────────────────────┬───────────┘
            │                       │                        │
            ▼                       ▼                        ▼
┌───────────────────┐   ┌───────────────────────┐   ┌────────────────────┐
│ SearchRepository  │   │ScooterRoutingRepository│  │Location & Prefs    │
│ SearchManager     │   │BicycleRouterV2        │   │FusedLocationClient │
│ SuggestSession    │   │PedestrianRouter       │   │DataStore           │
│ Reverse Geocode   │   │DrivingRouter (Events) │   │ScooterMetrics      │
└───────────────────┘   └───────────────────────┘   └────────────────────┘
```

---

## 🛠️ Технологический стек

- **Язык**: Kotlin `2.2`
- **UI**: Jetpack Compose, Material Design 3 (`BottomSheetScaffold`, `edge-to-edge`), локальные шрифты `Space Grotesk` + `Inter`
- **Карты и роутинг**: Yandex MapKit SDK `com.yandex.android:maps.mobile:4.45.0-full`
- **Геолокация и настройки**: Google Play Services Location (`FusedLocationProviderClient`), Jetpack DataStore Preferences
- **Асинхронность**: Kotlin Coroutines & `StateFlow`
- **Тестирование**: JUnit 4, Robolectric

---

## ⚙️ Быстрый старт и настройка API-ключа

Ключ Yandex MapKit не хранится в открытом виде в исходном коде — он подставляется через `Secrets Gradle Plugin` в `BuildConfig.YANDEX_MAPKIT_API_KEY`.

### 1. Настройка секрета
- **В Google AI Studio**: откройте панель **Secrets** и добавьте переменную:
  ```text
  YANDEX_MAPKIT_API_KEY=ваш_ключ_mapkit
  ```
- **Локально**: скопируйте `.env.example` в `.env` (файл `.env` добавлен в `.gitignore`) и укажите ключ:
  ```properties
  YANDEX_MAPKIT_API_KEY=ваш_ключ_mapkit
  ```

### 2. Сборка и тестирование

```bash
# Сборка отладочного APK
./gradlew assembleDebug

# Запуск модульных и Robolectric-тестов
./gradlew testDebugUnitTest
```

---

## 🤖 CI/CD (GitHub Actions)

В репозитории настроен готовый workflow [`.github/workflows/android.yml`](.github/workflows/android.yml). При каждом `push` в ветку `main` он автоматически:

1. Клонирует репозиторий (`actions/checkout@v4`).
2. Устанавливает **JDK 17** (`temurin`) с кэшированием Gradle.
3. Подхватывает секрет `YANDEX_MAPKIT_API_KEY` из **GitHub Repository Secrets** (при наличии).
4. Собирает APK командой `./gradlew assembleDebug`.
5. Загружает `app/build/outputs/apk/debug/app-debug.apk` через `actions/upload-artifact@v4` для скачивания в виде zip-архива со вкладки **Actions**.

> **Важно по безопасности:** не публикуйте собранный с рабочим API-ключом APK в открытый доступ — ключи в `BuildConfig` могут быть извлечены при декомпиляции. Ограничивайте ключ в кабинете разработчика Яндекс по `applicationId` и SHA-1 подписи.

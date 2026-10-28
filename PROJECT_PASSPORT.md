# Паспорт Telegram Archive — QA Work

Репозиторий https://github.com/Caprifan2078/telegram-archive-android-; рабочая ветка `qa/work-modular-audit-00-18`. Продолжать от актуального HEAD ветки, не от main. Основа QA-00: 9caf6ca; код исправления диалога: a4db7ce; последующий тест прокрутки: 27fcb4e. Данные Telegram в GitHub не публиковать.

Существующее локальное Android-приложение Java/SQLite для просмотра Telegram-экспортов. Gradle/Android, minSdk 26, targetSdk 35; конфигурация workQa: applicationId org.openarchive.telegram.qa.workqa20261028, versionCode 51 при значении по умолчанию, debug-подпись. Это значения конфигурации, не независимо разобранного manifest APK. Совместимость ключа пользовательской установки не подтверждена; удаление приложения запрещено.

Состояние 19 модулей: docs/QA_MODULAR_MASTER_MATRIX_20261028_RU.md; детальные результаты QA-00: docs/qa/modules/QA-00_REPORT_RU.md. Остальные разделы нельзя считать полностью проверенными.

Обновление 29.10.2026: актуальная рабочая ветка та же; код пустого чата 4efdb30, объединённый CI a916ce6. [40786330996](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/40786330996), commit a916ce6: Android 11/API30, **165 тестов в XML: 164 PASS, 1 SKIPPED, 0 FAIL, 0 errors**. Пропущен SignedUpgradeSentinelTest: не задана двухфазная проверка с совместимым ключом. Набор содержит тесты импорта, архивов, резервных копий, миграций SQLite, просмотра файлов, медиа, поиска, аналитики, UI и QA-00/01; не означает полное покрытие модулей или проверку реального телефона. Лог runner ошибочно сообщает 166 после 165, поэтому считать по XML. Gradle JVM и build прошли.

# Post-Work review — 28 октября 2026

## База
Исходная ветка: `qa/work-backup-envelope-20261028`, коммит `a2b43e41ae5f20aa62ccabf1f88afef33923d9a7`. Работа Work — 11 коммитов поверх `qa/backup-dryrun-integrity-v2-20261027`; `main` не затронута.

## Подтверждённые проверки Work
- 54/54 Android 11 media/import tests: https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/40727906817
- 26 synthetic-backup tests on Android 11 and 26 on Android 15: https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/40680150735
- Android 8+ isolated debug-signed `workQa` APK produced. APK SHA-256: `99ac06dac3a7370dcf56823a163b5804fcdc97578bcdb68c58cecdf382a3ed1c`. Это APK от родительской ветки Work, а не от новой исправленной ветки.

## Дополнительный дефект и исправление
`BackupVerifier.scanBackup()` (полный dry-run) отвергал неверные `chats[*].prefix` и `chatIndex`, тогда как быстрая `BackupVerifier.verifyStream()` проверяла только предел индексов медиакаталогов, пропуская некорректное описание корневых папок при валидных ZIP CRC.

- Добавлена проверка `chatIndex == i` и `prefix == "media/chat-"+i+"/"` в быстром verifier, без распаковки живых БД.
- Добавлен `fastVerifyRejectsWrongChatRootPrefixWithoutChangingData`: синтетический ZIP пересобирается с валидным CRC, но подменённым префиксом в JSON; verifier обязан бросить IOException; исходные данные пользователя не изменяются.
- Тесты новой ветки: https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/40740538471. Статус сверить по фактическому завершению CI: нельзя обозначать VERIFIED до прохождения.
- Ветка `qa/postwork-backup-structure-20261028`. `main` не изменялась.

## Открытые вопросы
1. Быстрая полная проверка не раскрывает вложенный `metadata.zip` и не проверяет его SQLite-целостность. Существующая `simulate()` проводит значительно более глубокую проверку. Нужен отдельный безопасный дизайн для глубокой верификации гигантских бэкапов.
2. Не проверены установка на физический телефон, сохранение личных данных при подписанном обновлении, резервные копии 9+ ГБ и все реальные Telegram-кодеки.
3. Отладочную WorkQA APK не использовать как обновление основной установки — отдельный package name и debug signature.

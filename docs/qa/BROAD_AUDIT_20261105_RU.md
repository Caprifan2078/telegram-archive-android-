# Широкая проверка 05.11.2026

Основа приложения: bcbd14cbb55911f3ee3894d4d1df7af3cec099d1; отдельная QA-ветка. main не менялась.

## Широкий Android-регресс до новой правки

[41936784312](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41936784312), commit 4a2079c: Android 11, XML: 183 теста, 182 PASS, 1 SKIPPED, 0 FAIL/ERROR. Пропущен SignedUpgradeSentinelTest: совместимое обновление не проверено. JVM и сборка PASS. Это синтетический автоматический набор, а не ручной проход всех функций и не проверка физического телефона. Лог runner пишет 184, точное число взято из XML.

## Импорт изображений

При чтении кода выявлена ошибка: findMedia не индексировал прямые изображения с URL-фрагментом и прямые APNG; isPicture расходился с supported/kindFromExtension. Исправление 0f6f53dc; тест 07498dab использует синтетический HTML с двумя прямыми img. [41937714864](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41937714864): MediaClassifierTest 2/2 PASS на Android 11, JVM и сборка PASS. Физическое отображение APNG и полная работа с архивом этими проверками не подтверждены; данные реального пользователя не использовались.

Общий регресс после правки запущен: [41936927663](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41936927663); статус при записи не завершён. Без проверки нельзя объявлять результат.

## Уточнение старого результата

[40740538471](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/40740538471): BackupDryRunRegressionTest + ArchiveIntegrationTest, по 27 тестов на Android 11 и 15, оба jobs SUCCESS. Это не проверка восстановления больших реальных архивов, потери питания либо совместимости пользовательской подписи.

P0: старая установка с пользовательскими данными не удаляется; совместимость подписи реальной установленной APK и обновление с сохранением данных не подтверждены.

## Синтетическое подписанное обновление

[41939112641](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41939112641), код приложения соответствует исправлению импорта: Android 11; старый и новый stableQa APK сгенерированы с одним временным ключом; запись тестовой базы и проверка после adb install -r прошли (по 1/1). Сохранены синтетическое сообщение, избранное, локальный профиль и тестовая настройка. Проверено, что сборка stableQa без параметров подписи отказывает. Это не проверка ключа и обновления фактически установленной пользовательской APK, не полноценная проверка backup. Временный ключ уничтожается в CI; пользовательские данные не использовались.

## Широкий прогон после исправления

[41936927663](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41936927663), commit b4125f2e: Android 11, XML 184 теста: 183 PASS, 1 SKIPPED, 0 FAIL/ERROR. Пропущен двухфазный SignedUpgradeSentinelTest, отдельно выполненный успешно в 41939112641 на том же коде приложения с временным ключом. Полноценный пользовательский ключ и update реального приложения не проверены. Набор синтетический; все функции и физический телефон не покрывает.

Android 15: [41940596589](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41940596589) запущен на commit e3399ad8, итог при записи неизвестен.

## Android 15

[41940596589](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41940596589), commit e3399ad8: 44/44 PASS по XML, 0 skipped/failure/error, Android 15/API35. Семь классов: ImportRollbackRegressionTest, BackupDryRunRegressionTest, BackupIntegrityAndroid15RegressionTest, SearchLifecycleRegressionTest, MediaClassifierTest, MissingMediaPlaybackRegressionTest, ConversationPdfExporterTest. Синтетические данные; не реальный телефон. Проверка проходила до новой защиты импорта нескольких чатов.

## ZIP с несколькими чатами

Аудит показал, что findChatRoot выбирал первую папку, содержащую messages*.html, не сообщая о других найденных корнях. Код не доказывает частоту на пользовательских архивах. Правка cfcd04a предупреждает об обнаружении нескольких переписок и останавливает импорт; одновременный импорт всех чатов не реализован. Тест 21ed33f проверяет отказ без оставшихся файлов и приём одного чата из нескольких частей. [41940905418](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41940905418) пока выполняется: до завершения не объявлять исправление проверенным.


### Проверка защиты
[41940905418](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41940905418), commit a2d885f0, Android 11: 18/18 PASS, 0 skipped/fail/error по XML. Включает MultipleChatArchiveRegressionTest, ImportRollbackRegressionTest, SevenZTelegramImportRegressionTest, EncryptedTelegramImportRegressionTest, RarCompatibilityRegressionTest. Два новых теста проверяют отказ от ZIP с несколькими чатами с удалением временной папки, и принятие одного чата с messages.html и messages2.html. Проверка искусственная, данные пользователя не затронуты. Поддержка одновременного импорта нескольких переписок не реализована.

[41942028916](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41942028916), commit 48e74a70, Android 15: те же 18/18 тестов PASS, 0 skipped/fail/error по XML, включая оба теста ZIP. Синтетические данные; не проверка физического телефона.

Широкий Android 11 после двух правок: [41941892493](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41941892493), commit 24551457, на момент записи ещё выполняется.

## Общая проверка после обеих правок

[41941892493](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41941892493), commit 24551457: Android 11, 186 тестов по XML: 185 PASS, 1 SKIPPED, 0 FAIL/ERROR. Пропущен двухфазный SignedUpgradeSentinelTest; отдельный [41939112641](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41939112641) подтвердил только synthetic upgrade с временным ключом до правки обнаружения корней ZIP. Совместимость обновления пользовательской APK и сохранность реальных данных не доказаны. Общий набор автоматический на синтетике, не полный ручной проход, не проверка физического устройства; ни один модуль не считать закрытым только по количеству тестов.

## Медиа и аналитика на Android 15

[41943538341](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/41943538341), commit c747b633: 21/21 PASS, 0 skipped/fail/error по XML, Android 15/API35. Классы MediaPlaybackTest, RoundPlayerActivityTest, PlaybackLifecycleTest, MediaAssetsRegressionTest, Qa00MediaLifecycleTest, AnalyticsIntegrationTest, LightAnalyticsVisualTest, RelationshipAnalyticsExplanationUiTest. Синтетические медиа и данные; результаты не покрывают все кодеки, все разделы аналитики, физическое устройство или большой пользовательский архив.

## Основные сценарии на Android 8

[42017298021](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/42017298021), commit 816daa16: Android 8/API26, 42/42 PASS, 0 skipped/fail/error по XML. ImportRollbackRegressionTest, BackupDryRunRegressionTest, SearchLifecycleRegressionTest, MediaClassifierTest, MissingMediaPlaybackRegressionTest и ConversationPdfExporterTest. Только синтетические данные на эмуляторе; проверку остальных функций и физического телефона это не заменяет.

## 05.11 — ZIP и медиатест Android 8

[42021377244](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/42021377244): Android 11, 19/19 PASS по XML, 0 skipped/errors/failures. Новый ArchiveEndToEndRegressionTest создаёт синтетический ZIP с одним чатом и двумя HTML, распаковывает и импортирует, проверяет поиск по тексту и разрешение файла-вложения. Файл-вложение содержит тестовые байты; реальное декодирование фото и реальный архив пользователя не проверены. UI выбора архива этим тестом не покрыт.

[42021276342](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/42021276342): Android 8, XML 20 PASS, 1 FAIL, 0 skipped. MediaAssetsRegressionTest.voiceOpusSeekPauseResume: после seekTo(500), ожидания 80 мс и pause позиция оказалась ниже 350 мс. Это framework MediaPlayer test, не проверка экранного плеера; возможна асинхронность seek, пока не доказана причина или дефект приложения. Сохранить FAIL. Диагностика OpusSeekDiagnosticTest ожидает onSeekComplete и повторяет исходные медиа-тесты; её результат ещё не установлен. Проверки выполняются на синтетике.

### Открытый функциональный пробел: перемотка голосовых
Анализ текущего кода MainActivity.voice/startAudio и playerStrip выявил кнопку воспроизведения и таймер, но не обнаружил пользовательского ползунка или кнопок перемотки голосового (в отличие от видеокружка и отдельного музыкального плеера). Это расходится с пользовательским требованием. Не объявлять исправленным; требуется подтвердить на экране с реальным синтетическим OGG, реализовать удобную перемотку с сохранением pause/play и проверить Android 8/11/15. Framework-тест seek сам по себе не проверяет интерфейс приложения.


### Диагностика OGG/Opus на Android 8
[42022560015](https://github.com/Caprifan2078/telegram-archive-android-/actions/runs/42022560015), commit d7cc6f4: 6/6 PASS по XML, включая исходный упавший MediaAssetsRegressionTest.voiceOpusSeekPauseResume и новый тест ожидания события onSeekComplete с позицией не ниже 350 мс после seekTo(500). Первый FAIL сохраняется в истории: возможна гонка теста, но единственный повтор не устанавливает первопричину. Это framework MediaPlayer, не интерфейс приложения. В cdccd7b тест теперь ожидает завершение seek, а не полагается на 80 мс; повтор после изменения ещё требуется. Код приложения не менялся. Отсутствие пользовательской перемотки голосовых остаётся открытым.

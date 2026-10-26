package org.openarchive.telegram;

/** Truthful copy for synthetic, offline analytics. Keep synced with rendered metrics. */
final class AnalyticsDisplayCopy {
    private AnalyticsDisplayCopy() {}
    static final String ANALYSIS_INTRO =
        "Анализ — это подсчёт сообщений и вложений из локального экспорта. " +
        "Проценты показывают долю отправленных сообщений, а не адресатов или отношение к людям. " +
        "Отсутствующие даты и медиа могут ограничивать отдельные показатели.";
    static final String HEATMAP_DESCRIPTION =
        "Тепловая карта: Пн–Вс по строкам и шесть интервалов времени по 4 часа " +
        "(00:00–03:59, 04:00–07:59 … 20:00–23:59) по столбцам. " +
        "В каждой клетке — точное количество сообщений, не оценка от 0 до 10. " +
        "Цвет показывает относительную активность в рамках этой переписки.";
}

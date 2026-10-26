package org.openarchive.telegram;
import java.util.HashSet;
/** Deterministic offline taxonomy, independent of Activity and private data. */
final class HelpCatalog {
 static final class Shelf {
  final String title,subtitle;final int[] chapterIds;
  Shelf(String title,String subtitle,int... ids){this.title=title;this.subtitle=subtitle;chapterIds=ids;}
 }
 static final class Group {
  final String title,subtitle;final Shelf[] shelves;
  Group(String title,String subtitle,Shelf... shelves){this.title=title;this.subtitle=subtitle;this.shelves=shelves;}
 }
 static final Group[] GROUPS={
  new Group("Первые шаги","Начни с импорта и безопасного знакомства с читалкой",
   new Shelf("Первый запуск","Начать с нуля и найти главные кнопки",1,16,33),
   new Shelf("Основные действия","Чаты, поиск и избранное",2,40),
   new Shelf("Проверка после обновления","Ничего не потерять при обновлении",20)),
  new Group("Экспорт и импорт","Четыре отдельных маршрута: от Telegram до нового телефона",
   new Shelf("Экспорт из Telegram","Что скачать и какие медиа включить",28),
   new Shelf("Импорт в Telegram Archive","Папка, ZIP, RAR и недостающие медиа",3),
   new Shelf("Резервные копии","Полная, компактная и капсула избранного",4,15),
   new Shelf("Переезд на новый телефон","Пошаговый перенос и проверка",27)),
  new Group("Диалоги и воспоминания","Сравнивай два чата, историю и статистику",
   new Shelf("Сравнение двух чатов","Показатели и общая шкала",29,30),
   new Shelf("Воспоминания и этапы","Личные события, этапы и повторяющиеся фразы",21,24,25,26,38,39),
   new Shelf("Поиск и графики","Статистика, активность и паузы",11,17,31),
   new Shelf("Цитаты и источники","Как сохранить часть текста и найти оригинал",18)),
  new Group("Медиа и плеер","Фото, видео, кружки, голосовые и коллекции",
   new Shelf("Просмотр медиа","Видео, GIF, стикеры и кинолента",5,22,23,35,37,43),
   new Shelf("Музыкальный плеер","Песни, очередь, обложки и скорость",42),
   new Shelf("Сохранение файлов","Одиночное и массовое сохранение, голосовые",6,7,10),
   new Shelf("Коллекции и дубликаты","Свои подборки и проверка SHA-256",8,9)),
  new Group("Оформление и безопасность","Красивый интерфейс, скорость и защита",
   new Shelf("Темы и кнопки","Настройки внешнего вида и новый видеоплеер",12,32,34,36),
   new Shelf("Шрифты и импорт","Системный, классические, свои TTF и OTF",41),
   new Shelf("Жесты и плавность","Анимации, настройки памяти и навигация",13,19),
   new Shelf("Диагностика","Что сделать при сбое без потери архива",14))
 };
 static int[] findChapter(int id){
  for(int i=0;i<GROUPS.length;i++)for(int j=0;j<GROUPS[i].shelves.length;j++)
   for(int chapter:GROUPS[i].shelves[j].chapterIds)if(chapter==id)return new int[]{i,j};
  return null;
 }
 static boolean hasEveryChapter(){
  HashSet<Integer> seen=new HashSet<>();
  for(Group g:GROUPS)for(Shelf s:g.shelves)for(int id:s.chapterIds)seen.add(id);
  if(seen.size()!=HelpContent.all().size())return false;
  for(int i=1;i<=HelpContent.all().size();i++)if(!seen.contains(i))return false;
  return true;
 }
}

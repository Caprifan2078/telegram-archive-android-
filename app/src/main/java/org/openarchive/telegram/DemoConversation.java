package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Synthetic training model. Never references a real archive, MessageDb or network. */
final class DemoConversation {
    enum Kind { TEXT, PHOTO, VIDEO, ROUND, VOICE, MUSIC, STICKER, GIF, FILE }
    static final class Item {
        final long id;
        final String author, text, asset;
        final Kind kind;
        boolean pinned, saved;
        Item(long id, String author, Kind kind, String text, String asset) {
            this.id=id;this.author=author;this.kind=kind;this.text=text;this.asset=asset;
        }
    }
    private final List<Item> items=new ArrayList<>();
    private Long lastReply=null;
    private int initialSize;
    DemoConversation(){
        add("Архивчик",Kind.TEXT,"Привет! Это учебный чат. Здесь НЕТ личных сообщений.",null);
        add("Ты",Kind.TEXT,"Проведи пальцем, нажми или удерживай сообщение, чтобы потренироваться.",null);
        add("Архивчик",Kind.PHOTO,"Фотография — нажми, чтобы открыть на весь экран.","tutorial/photo.jpg");
        add("Ты",Kind.PHOTO,"Вторая фотография: увеличивай и рассматривай.","tutorial/photo2.jpg");
        add("Архивчик",Kind.VIDEO,"Обычное видео: попробуй воспроизвести и поставить на паузу.","tutorial/clip.mp4");
        add("Ты",Kind.ROUND,"Кружок: тоже локальное видео, но в круглом окне.","tutorial/clip.mp4");
        add("Архивчик",Kind.VOICE,"Голосовое: короткий демонстрационный звук.","tutorial/voice.ogg");
        add("Архивчик",Kind.MUSIC,"Музыка: тестовая мелодия без авторских треков.","tutorial/music.ogg");
        add("Ты",Kind.STICKER,"Стикер — нажми, чтобы открыть картинку.","tutorial/sticker.png");
        add("Архивчик",Kind.GIF,"GIF — короткая анимация, сохранённая в приложении.","tutorial/demo.gif");
        add("Ты",Kind.FILE,"Документ: пример файла. Никаких внешних загрузок.","tutorial/README.txt");
        add("Архивчик",Kind.TEXT,"Попробуй закрепить это сообщение долгим нажатием, а потом открепить.",null);
        add("Ты",Kind.TEXT,"Теперь попробуй «Ответить», «Цитировать» и «В избранное».",null);
        initialSize=items.size();
    }
    /** Second fully synthetic scenario; no account, archive or real messages. */
    DemoConversation(int scenario){
        this();
        if(scenario!=1)return;
        items.clear();
        add("Путешественник",Kind.TEXT,"Дорогой друг! Сегодня я отправился искать библиотеку, о которой слышал столько легенд.",null);
        add("Хранитель",Kind.TEXT,"Всякая дорога начинается с вопроса. Какую историю ты ищешь?",null);
        add("Путешественник",Kind.PHOTO,"Вот дорога из нашей вымышленной истории.","tutorial/photo.jpg");
        add("Хранитель",Kind.VOICE,"Записал голосовое приветствие, попробуй воспроизвести.","tutorial/voice.ogg");
        add("Путешественник",Kind.STICKER,"Вместо печати — маленькая картинка.","tutorial/sticker.png");
        add("Хранитель",Kind.MUSIC,"Эта мелодия звучит в зале книг.","tutorial/music.ogg");
        add("Путешественник",Kind.VIDEO,"Короткий фрагмент пути.","tutorial/clip.mp4");
        add("Хранитель",Kind.ROUND,"Здесь же можно испытать кружок.","tutorial/clip.mp4");
        add("Путешественник",Kind.GIF,"А это движущаяся открытка.","tutorial/demo.gif");
        add("Хранитель",Kind.FILE,"Письмо из архива с инструкцией.","tutorial/README.txt");
        add("Путешественник",Kind.TEXT,"Любопытно, сколько тропинок приводит к одному воспоминанию?",null);
        add("Хранитель",Kind.PHOTO,"Ещё одна иллюстрация для галереи.","tutorial/photo2.jpg");
        add("Путешественник",Kind.TEXT,"Закрепи одну фразу, ответь на другую и добавь обе в избранное.",null);
        initialSize=items.size();
    }
    private void add(String author,Kind kind,String text,String asset){
        items.add(new Item(items.size()+1,author,kind,text,asset));
    }
    boolean appendText(String input){
        if(input==null)return false;
        String value=input.trim();
        if(value.isEmpty()||value.length()>2000||items.size()>=100)return false;
        add("Ты",Kind.TEXT,value,null);return true;
    }
    List<Item> items(){return Collections.unmodifiableList(items);}
    Item get(long id){if(id<1||id>items.size())return null;return items.get((int)id-1);}
    boolean togglePin(long id){Item e=get(id);if(e==null)return false;e.pinned=!e.pinned;return e.pinned;}
    boolean toggleSave(long id){Item e=get(id);if(e==null)return false;e.saved=!e.saved;return e.saved;}
    void reply(long id){if(get(id)!=null)lastReply=id;}
    Long lastReply(){return lastReply;}
    int pinnedCount(){int n=0;for(Item i:items)if(i.pinned)n++;return n;}
    int savedCount(){int n=0;for(Item i:items)if(i.saved)n++;return n;}
    void reset(){
        while(items.size()>initialSize)items.remove(items.size()-1);
        for(Item i:items){i.pinned=false;i.saved=false;}
        lastReply=null;
    }
}

package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Encrypted, export-only snapshot of favorite message texts. No media bytes, no restore operation. */
final class FavoritesCapsule {
    static final int MAX_ITEMS=50_000;
    static int save(Context context,Uri file,SQLiteDatabase database,char[] password)throws Exception{
        if(password==null||password.length<8)throw new IOException("Нужен пароль минимум из 8 символов");
        try(Cursor counter=database.rawQuery("SELECT COUNT(*) FROM messages m INNER JOIN favorites f ON f.message_id=m.id",null)){
            if(counter.moveToFirst()&&counter.getInt(0)>MAX_ITEMS)
                throw new IOException("Слишком много избранных сообщений для одной капсулы");
        }
        OutputStream destination=context.getContentResolver().openOutputStream(file,"w");
        if(destination==null)throw new IOException("Невозможно создать файл капсулы");
        try(OutputStream raw=destination;
            ZipOutputStream zip=new ZipOutputStream(SecureArchive.encryptedOutput(raw,password))){
            JSONObject manifest=new JSONObject();
            manifest.put("format","telegram-archive-favorites-capsule");
            manifest.put("version",1);
            manifest.put("scope","favorite-message-text-only");
            manifest.put("mediaIncluded",false);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("messages.jsonl"));
            int total=0;
            try(Cursor c=database.rawQuery(
                "SELECT m.id,m.author,m.date_text,m.body FROM messages m " +
                "INNER JOIN favorites f ON m.id=f.message_id ORDER BY m.id",null)){
                while(c.moveToNext()){
                    if(++total>MAX_ITEMS)throw new IOException("Превышен лимит избранных сообщений");
                    JSONObject item=new JSONObject();
                    item.put("id",c.getLong(0));
                    item.put("author",c.isNull(1)?"":c.getString(1));
                    item.put("date",c.isNull(2)?"":c.getString(2));
                    item.put("text",c.isNull(3)?"":c.getString(3));
                    zip.write(item.toString().getBytes(StandardCharsets.UTF_8));
                    zip.write('\n');
                }
            }
            zip.closeEntry();
            zip.finish();
            return total;
        }
    }
    private FavoritesCapsule(){}
}

package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Durable undo journal for backup import. Only new, randomly named resources are ever removed.
 * A registry commit is the publication point: a published job is retained after a crash.
 * No user-created/existing database, photo, or registry record is overwritten by recovery. */
final class RestoreJournal {
    private static final String DIR="pending_restores";
    // Prevent a recreated Activity from treating an ongoing worker as a crashed job.
    private static final Set<String> ACTIVE=Collections.synchronizedSet(new HashSet<>());
    private final Context ctx;
    private final File record;
    private final JSONObject data;
    final String id;
    private RestoreJournal(Context context,File path,JSONObject state)throws Exception {
        ctx=context;record=path;data=state;id=state.getString("id");
    }
    private static File directory(Context ctx)throws IOException {
        File dir=new File(ctx.getFilesDir(),DIR);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удаётся создать журнал восстановления");
        return dir;
    }
    static synchronized RestoreJournal begin(Context ctx,File mediaStaging)throws Exception {
        if(!ACTIVE.isEmpty())throw new IOException("Другое восстановление ещё выполняется");
        String id=UUID.randomUUID().toString().replace("-","");
        JSONObject data=new JSONObject();data.put("version",1);data.put("id",id);
        data.put("dbKeys",new JSONArray());data.put("assets",new JSONArray());
        if(mediaStaging!=null){
            File base=new File(ctx.getFilesDir(),"telegram_imports").getCanonicalFile();
            File stage=mediaStaging.getCanonicalFile();
            if(!stage.getParentFile().equals(base)||!RestoreRecoveryPolicy.safeStage(stage.getName()))
                throw new IOException("Недопустимый каталог восстанавливаемых медиа");
            data.put("staging",stage.getName());
        }
        RestoreJournal job=new RestoreJournal(ctx,new File(directory(ctx),"restore-"+id+".json"),data);
        job.persist();ACTIVE.add(id);return job;
    }
    void recordKeys(List<String> keys)throws Exception {
        JSONArray array=new JSONArray();
        for(String key:keys){
            if(!RestoreRecoveryPolicy.safeKey(key))throw new IOException("Недопустимый ключ восстановления");
            array.put(key);
        }
        if(array.length()==0)throw new IOException("Нет ключей восстановления");
        data.put("dbKeys",array);persist();
    }
    void recordAsset(String name)throws Exception {
        if(!RestoreRecoveryPolicy.safeAsset(name))throw new IOException("Недопустимое имя изображения");
        JSONArray assets=data.getJSONArray("assets");
        for(int i=0;i<assets.length();i++)if(name.equals(assets.getString(i)))return;
        assets.put(name);persist();
    }
    private void persist()throws Exception {
        File temp=new File(record.getParentFile(),record.getName()+".tmp");
        if(temp.exists()&&!temp.delete())throw new IOException("Остался временный журнал");
        try(FileOutputStream out=new FileOutputStream(temp)){
            out.write(data.toString().getBytes("UTF-8"));out.getFD().sync();
        }
        if(!temp.renameTo(record))throw new IOException("Не удалось записать журнал восстановления");
    }
    static void recordNote(SQLiteDatabase db,String id,long noteId){
        db.execSQL("CREATE TABLE IF NOT EXISTS restore_note_refs (restore_id TEXT NOT NULL, note_id INTEGER NOT NULL, PRIMARY KEY(restore_id,note_id))");
        db.execSQL("INSERT INTO restore_note_refs(restore_id,note_id) VALUES (?,?)",new Object[]{id,noteId});
    }
    private static void cleanNoteRefs(Context ctx,String id,boolean undo)throws Exception {
        File path=ctx.getDatabasePath("global_saved_notes.db");
        if(!path.isFile())return;
        SavedStore helper=new SavedStore(ctx);
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            db.beginTransaction();
            try{
                // Legacy notes DBs never had this table.
                try(Cursor check=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='restore_note_refs'",null)){
                    if(!check.moveToFirst()){db.setTransactionSuccessful();return;}
                }
                if(undo){
                    ArrayList<String> ids=new ArrayList<>();
                    try(Cursor c=db.rawQuery("SELECT note_id FROM restore_note_refs WHERE restore_id=?",new String[]{id})){
                        while(c.moveToNext())ids.add(c.getString(0));
                    }
                    for(String noteId:ids)db.delete("saved_notes","id=?",new String[]{noteId});
                }
                db.delete("restore_note_refs","restore_id=?",new String[]{id});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }finally{helper.close();}
    }
    private static void deleteDatabase(Context ctx,String key)throws IOException {
        if(!RestoreRecoveryPolicy.safeKey(key))throw new IOException("Повреждённый ключ в журнале");
        String name="telegram_"+key+".db";
        // Database handles are not opened for restored chat keys during recovery.
        if(!ctx.deleteDatabase(name)){
            File f=ctx.getDatabasePath(name);
            if(f.exists())throw new IOException("Не удалось удалить незавершённую базу");
        }
        File temp=new File(ctx.getDatabasePath(name).getParentFile(),name+".restoring");
        if(temp.exists()&&!temp.delete())throw new IOException("Не удалось удалить временную базу");
    }
    private static void removeMedia(Context ctx,JSONObject data)throws Exception {
        String staged=data.optString("staging","");
        if(staged.isEmpty())return;
        if(!RestoreRecoveryPolicy.safeStage(staged))throw new IOException("Повреждённый путь медиа в журнале");
        File base=new File(ctx.getFilesDir(),"telegram_imports").getCanonicalFile();
        File target=new File(base,staged).getCanonicalFile();
        if(!target.getParentFile().equals(base))throw new IOException("Каталог медиа вне разрешённой папки");
        if(target.exists())ArchiveFileImport.deleteTree(target);
        if(target.exists())throw new IOException("Не удалось очистить медиа незавершённого восстановления");
    }
    private static void cleanCache(Context ctx,String id)throws IOException {
        if(!RestoreRecoveryPolicy.safeKey(id))throw new IOException("Неверный ID журнала");
        File[] files=ctx.getCacheDir().listFiles();
        if(files==null)throw new IOException("Не удаётся прочитать временные базы");
        String prefix="tgrestore-"+id+"-";
        for(File candidate:files){
            String name=candidate.getName();
            if(name.startsWith(prefix)&&name.endsWith(".sqlite")&&candidate.isFile()
                    &&!candidate.delete())throw new IOException("Не удалось удалить временную базу");
        }
    }
    static synchronized void recoverAll(Context ctx,SharedPreferences prefs)throws Exception {
        if(!ACTIVE.isEmpty())throw new IOException("Восстановление ещё работает в другой активности. Откройте приложение после завершения операции");
        File dir=directory(ctx);File[] files=dir.listFiles();
        if(files==null)throw new IOException("Не удаётся прочитать журналы восстановления");
        for(File f:files){
            if(f.getName().matches("restore-[a-f0-9]{32}\\.json\\.tmp")){
                // Each journal update is durable *before* its corresponding resource is
                // touched. An interrupted temporary write therefore never authorizes
                // external file changes; only the last renamed .json is authoritative.
                if(!f.delete())throw new IOException("Не удалось убрать временный журнал");
                continue;
            }
            if(f.getName().matches("restore-[a-f0-9]{32}\\.json"))recover(ctx,prefs,f);
        }
    }
    static void recover(Context ctx,SharedPreferences prefs,RestoreJournal job)throws Exception {
        if(job!=null&&job.record.exists())recover(ctx,prefs,job.record);
    }
    private static void recover(Context ctx,SharedPreferences prefs,File path)throws Exception {
        byte[] bytes;
        try(InputStream in=new FileInputStream(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Слишком большой журнал");out.write(b,0,n);}bytes=out.toByteArray();
        }
        JSONObject data=new JSONObject(new String(bytes,"UTF-8"));
        if(data.getInt("version")!=1)throw new IOException("Неизвестная версия журнала");
        String id=data.getString("id");
        if(!path.getName().equals("restore-"+id+".json")||!id.matches("[a-f0-9]{32}"))throw new IOException("Повреждённый журнал");
        JSONArray keys=data.getJSONArray("dbKeys"),assets=data.getJSONArray("assets");
        JSONArray registry=new JSONArray(prefs.getString("archive_registry","[]"));
        Set<String> publishedKeys=new HashSet<>();
        for(int i=0;i<registry.length();i++)publishedKeys.add(registry.getJSONObject(i).optString("key"));
        ArrayList<String> importedKeys=new ArrayList<>();
        for(int i=0;i<keys.length();i++)importedKeys.add(keys.getString(i));
        RestoreRecoveryPolicy.State state;
        try{state=RestoreRecoveryPolicy.state(importedKeys,publishedKeys);}
        catch(IllegalArgumentException invalid){throw new IOException("Повреждён список ключей в журнале",invalid);}
        if(state==RestoreRecoveryPolicy.State.AMBIGUOUS)
            throw new IOException("Частично опубликованное восстановление — ручная проверка обязательна");
        boolean committed=state==RestoreRecoveryPolicy.State.COMMITTED;
        if(committed){
            // Publication alone is not proof that the restored payload still exists.
            // Fail closed rather than silently certifying a broken restored chat.
            for(String key:importedKeys){
                if(!ctx.getDatabasePath("telegram_"+key+".db").isFile())
                    throw new IOException("Опубликованная база восстановления отсутствует: "+key);
            }
            String staged=data.optString("staging","");
            if(!staged.isEmpty()){
                if(!RestoreRecoveryPolicy.safeStage(staged)||
                    !new File(new File(ctx.getFilesDir(),"telegram_imports"),staged).isDirectory())
                    throw new IOException("Отсутствует каталог медиа завершённого восстановления");
            }
            File imgDir=new File(ctx.getFilesDir(),"personal_images");
            for(int i=0;i<assets.length();i++){
                String name=assets.getString(i);
                if(!RestoreRecoveryPolicy.safeAsset(name)||!new File(imgDir,name).isFile())
                    throw new IOException("Отсутствует аватар завершённого восстановления");
            }
        }
        cleanNoteRefs(ctx,id,!committed);
        if(!committed){
            for(int i=0;i<keys.length();i++)deleteDatabase(ctx,keys.getString(i));
            File destination=new File(ctx.getFilesDir(),"personal_images");
            for(int i=0;i<assets.length();i++){
                String name=assets.getString(i);
                if(!RestoreRecoveryPolicy.safeAsset(name))throw new IOException("Недопустимое имя изображения в журнале");
                File file=new File(destination,name);if(file.exists()&&!file.delete())throw new IOException("Не удалось удалить временный аватар");
                File temp=new File(destination,name+".restoring");
                if(temp.exists()&&!temp.delete())throw new IOException("Не удалось удалить временный аватар");
            }
            removeMedia(ctx,data);
        }
        cleanCache(ctx,id);
        if(!path.delete())throw new IOException("Не удалось удалить закрытый журнал");
        ACTIVE.remove(id);
    }
}

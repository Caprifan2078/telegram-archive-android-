package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Writes backup through SAF; read-back verification is required before reporting success. */
final class BackupExportGate {
    interface Progress { void update(String message); }
    interface Target {
        OutputStream openWrite() throws IOException;
        InputStream openRead() throws IOException;
    }
    private BackupExportGate() { }

    static BackupVerifier.Result saveAndVerify(Context context, SharedPreferences prefs,
            Uri destination, boolean full, char[] password, Progress progress) throws Exception {
        return saveAndVerify(context, prefs, new Target() {
            @Override public OutputStream openWrite() throws IOException {
                return context.getContentResolver().openOutputStream(destination, "w");
            }
            @Override public InputStream openRead() throws IOException {
                return context.getContentResolver().openInputStream(destination);
            }
        }, full, password, progress);
    }

    static BackupVerifier.Result saveAndVerify(Context context, SharedPreferences prefs,
            Target target, boolean full, char[] password, Progress progress) throws Exception {
        try(OutputStream raw=target.openWrite()) {
            if(raw==null)throw new IOException("Нет доступа к месту сохранения");
            OutputStream output=password==null?raw:SecureArchive.encryptedOutput(raw,password);
            if(full) FullBackup.export(context,prefs,output,progress::update);
            else ArchiveBackup.write(context,prefs,output,progress::update);
        }
        // Deliberately the legacy write-only behavior for RED QA: no read-back yet.
        return new BackupVerifier.Result();
    }
}

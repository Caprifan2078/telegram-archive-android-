package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Data-safety regression: an inaccessible source tree must not produce a "complete" empty backup. */
@RunWith(AndroidJUnit4.class)
public final class FullBackupSourceAccessRegressionTest {
    private final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static JSONArray registry(String key, File source) throws Exception {
        return new JSONArray().put(new JSONObject()
                .put("key", key).put("name", "Synthetic")
                .put("tree", "local:" + source.getAbsolutePath()));
    }

    @Test public void missingMediaDirectoryMustFailInsteadOfProducingFullBackup() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "");
        File missing = new File(ctx.getCacheDir(), "deleted-source-" + key);
        assertFalse("Test source must not exist", missing.exists());
        assertUnavailableSourceFails(key, missing);
    }

    @Test public void regularFileMasqueradingAsMediaDirectoryMustFail() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "");
        File invalid = new File(ctx.getCacheDir(), "not-a-directory-" + key);
        assertTrue(invalid.createNewFile());
        try {
            assertUnavailableSourceFails(key, invalid);
        } finally { assertTrue(invalid.delete()); }
    }

    private void assertUnavailableSourceFails(String key, File source) throws Exception {
        SharedPreferences prefs = ctx.getSharedPreferences("full-source-" + key, Context.MODE_PRIVATE);
        MessageDb db = new MessageDb(ctx, key);
        try {
            SQLiteDatabase sql = db.getWritableDatabase();
            db.clearStage(sql);
            long id = db.stage(sql, "Synthetic", "2024-01-01", "Original chat", null, "");
            db.publishStaged(sql);
            db.toggleFavorite(id);
            String before = registry(key, source).toString();
            assertTrue(prefs.edit().putString("archive_registry", before).commit());

            ByteArrayOutputStream destination = new ByteArrayOutputStream();
            try {
                FullBackup.export(ctx, prefs, destination, ignored -> {});
                fail("Unreachable source media must not be presented as a full backup");
            } catch (IllegalStateException expected) {
                assertTrue("Error must explain inaccessible source: " + expected,
                        expected.getMessage() != null && expected.getMessage().contains("папк"));
            }
            assertEquals(before, prefs.getString("archive_registry", ""));
            assertEquals(1, db.totalMessages());
            assertTrue(db.isFavorite(id));
        } finally {
            db.close();
            ctx.deleteDatabase("telegram_" + key + ".db");
            prefs.edit().clear().commit();
        }
    }

    @Test public void inaccessibleSafTreeMustNotLookLikeAnEmptyFolder() {
        String authority = "org.openarchive.no.provider." + UUID.randomUUID().toString().replace("-", "");
        Uri tree = DocumentsContract.buildTreeDocumentUri(authority, "missing");
        DocumentFolder folder = new DocumentFolder(ctx.getContentResolver(), tree);
        try {
            folder.children(folder.rootId());
            fail("Unavailable SAF document provider must not look like an empty directory");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains("папк"));
        }
    }

    @Test public void legitimatelyEmptyReadableMediaDirectoryIsStillAllowed() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "");
        File source = new File(ctx.getCacheDir(), "empty-source-" + key);
        assertTrue(source.mkdir());
        SharedPreferences prefs = ctx.getSharedPreferences("full-source-" + key, Context.MODE_PRIVATE);
        MessageDb db = new MessageDb(ctx, key);
        try {
            SQLiteDatabase sql = db.getWritableDatabase();
            db.clearStage(sql);
            db.stage(sql, "Synthetic", "2024-01-01", "Empty media is legitimate", null, "");
            db.publishStaged(sql);
            assertTrue(prefs.edit().putString("archive_registry", registry(key, source).toString()).commit());
            ByteArrayOutputStream destination = new ByteArrayOutputStream();
            FullBackup.export(ctx, prefs, destination, ignored -> {});
            JSONObject index = null;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(destination.toByteArray()))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!"full-manifest.json".equals(entry.getName())) continue;
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = zip.read(buffer)) != -1) bytes.write(buffer, 0, n);
                    index = new JSONObject(bytes.toString("UTF-8"));
                    zip.closeEntry();
                }
            }
            assertNotNull("Valid full backup must include a manifest", index);
            assertEquals(1, index.getJSONArray("chats").length());
            assertEquals(0, index.getInt("files"));
        } finally {
            db.close();
            ctx.deleteDatabase("telegram_" + key + ".db");
            prefs.edit().clear().commit();
            assertTrue(source.delete());
        }
    }
}

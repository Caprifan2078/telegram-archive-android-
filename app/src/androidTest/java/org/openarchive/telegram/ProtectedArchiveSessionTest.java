package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ProtectedArchiveSessionTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final byte[] TEXT="Synthetic confidential message".getBytes(StandardCharsets.UTF_8);
    private File createZip(char[] password)throws Exception{
        File input=File.createTempFile("protected-test-input-",".html",context.getCacheDir());
        File archive=File.createTempFile("protected-test-",".zip",context.getCacheDir());
        if(!archive.delete())throw new IOException("Fixture path occupied");
        try{
            Files.write(input.toPath(),TEXT);
            try(ZipFile zip=new ZipFile(archive,password)){
                ZipParameters p=new ZipParameters();
                p.setFileNameInZip("Chat/messages.html");
                p.setEncryptFiles(true);p.setEncryptionMethod(EncryptionMethod.AES);
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                zip.addFile(input,p);
                p.setFileNameInZip("Chat/photos/photo.jpg");zip.addFile(input,p);
            }
            return archive;
        }catch(Exception ex){archive.delete();throw ex;}
        finally{input.delete();}
    }
    @Test public void aesZipReadsOnlyRequestedEntriesAndLeavesOriginalUnchanged()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        try{
            byte[] before=Files.readAllBytes(zip.toPath());
            ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",secret);
            try{
                assertFalse(session.usesContainerCopy());
                assertEquals(2,session.list().size());
                Arrays.fill(secret,'\0'); // session owns its own copy
                assertArrayEquals(TEXT,session.read("Chat/messages.html",1024));
                assertArrayEquals(TEXT,session.read("Chat/photos/photo.jpg",1024));
                try{session.read("Chat/messages.html",2);fail("limit must fail");}
                catch(IOException expected){}
                try{session.read("../Chat/messages.html",1024);fail("unsafe path");}
                catch(IOException expected){}
            }finally{session.close();}
            try{session.read("Chat/messages.html",1024);fail("locked session must reject reads");}
            catch(IOException expected){}
            assertArrayEquals(before,Files.readAllBytes(zip.toPath()));
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void wrongPasswordCannotReadProtectedMessage()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        try{
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip","incorrect".toCharArray())){
                try{session.read("Chat/messages.html",1024);fail("Wrong key accepted");}
                catch(IOException expected){}
            }
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",null)){
                try{session.read("Chat/messages.html",1024);fail("Missing key accepted");}
                catch(IOException expected){}
            }
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void ramMediaSupportsRandomAccessAndRefusesReadsAfterLock()throws Exception{
        byte[] input=new byte[600000];
        new java.util.Random(71).nextBytes(input);
        ProtectedMediaSource source=new ProtectedMediaSource(input.length);
        try{
            try{source.getSize();fail("Unsealed source must not be readable");}catch(IOException expected){}
            OutputStream out=source.sink();out.write(input);
            try{out.write(1);fail("Limit must be enforced");}catch(IOException expected){}
            source.seal();assertEquals(input.length,source.getSize());
            byte[] actual=new byte[330000];
            int count=source.readAt(250000,actual,7,300000);
            assertEquals(300000,count);
            assertArrayEquals(Arrays.copyOfRange(input,250000,550000),Arrays.copyOfRange(actual,7,300007));
            assertEquals(10,source.readAt(input.length-10,actual,0,20));
            assertEquals(-1,source.readAt(input.length,actual,0,1));
            assertEquals(0,source.readAt(input.length,actual,0,0));
        }finally{source.close();}
        try{source.readAt(0,new byte[1],0,1);fail("Locked source must refuse reads");}catch(IOException expected){}
        source.close();
    }

    @Test public void encryptedArchiveAttachmentCanFeedSeekableMediaWithoutPlaintextFile()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",secret);
            ProtectedMediaSource media=ProtectedMediaSource.load(session,"Chat/photos/photo.jpg",1024)){
            byte[] output=new byte[TEXT.length];
            assertEquals(TEXT.length,media.readAt(0,output,0,output.length));
            assertArrayEquals(TEXT,output);
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void largeEncryptedCacheSupportsSeekingAndIsDeletedWhenLocked()throws Exception{
        int block=ProtectedEncryptedMediaSource.BLOCK;
        byte[] input=new byte[block+1000];new java.util.Random(19).nextBytes(input);
        ProtectedEncryptedMediaSource cache=new ProtectedEncryptedMediaSource(context.getCacheDir(),input.length);
        File file=cache.cacheFileForTest();
        try{
            cache.sink().write(input);cache.seal();
            assertEquals(input.length,cache.getSize());
            byte[] onDisk=Files.readAllBytes(file.toPath());
            assertEquals(input.length+2*ProtectedEncryptedMediaSource.TAG,onDisk.length);
            assertFalse(Arrays.equals(Arrays.copyOf(input,128),Arrays.copyOf(onDisk,128)));
            byte[] actual=new byte[1500];
            assertEquals(1500,cache.readAt(block-500,actual,0,1500));
            assertArrayEquals(Arrays.copyOfRange(input,block-500,block+1000),actual);
            try(RandomAccessFile edit=new RandomAccessFile(file,"rw")){
                edit.seek(0);edit.write(onDisk[0]^1);
            }
            // The last cached block is block 1; reading block 0 must authenticate again.
            try{cache.readAt(0,new byte[16],0,16);fail("Modified ciphertext must fail authentication");}
            catch(IOException expected){}
        }finally{cache.close();}
        assertFalse(file.exists());
        try{cache.getSize();fail("Locked cache readable");}catch(IOException expected){}
    }
    @Test public void jsonChatReadsRichTextAndVoiceReferences()throws Exception{
        String json="{\"name\":\"Synthetic chat\",\"messages\":[{\"from\":\"Test\",\"date\":\"2026-01-01T00:00:00\",\"text\":[\"Hello \",{\"type\":\"bold\",\"text\":\"world\"}],\"file\":\"voice_messages/a.ogg\",\"media_type\":\"voice_message\"},{\"actor\":\"Test\",\"action\":\"join_group\",\"text\":\"\"}]}";
        java.util.List<ProtectedJsonChat.Message> messages=ProtectedJsonChat.parse(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2,messages.size());
        assertTrue(messages.get(0).display.contains("Hello world"));
        assertEquals("voice",messages.get(0).media.get(0).kind);
        assertEquals("voice_messages/a.ogg",messages.get(0).media.get(0).path);
        assertTrue(messages.get(1).display.contains("join_group"));
        try{ProtectedJsonChat.parse(new ByteArrayInputStream("{\"chats\":{\"list\":[]}}".getBytes(StandardCharsets.UTF_8)));
            fail("Account-level export is not a single chat");}
        catch(IOException expected){}
    }
    @Test public void protectedSessionUsesOneBoundedCopyForNonseekableSaf()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        java.util.Set<String> before=protectedCopies();
        try{
            String encoded=android.util.Base64.encodeToString(Files.readAllBytes(zip.toPath()),
                android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING|android.util.Base64.URL_SAFE);
            Uri uri=Uri.parse("content://"+ArchivePipeTestProvider.AUTHORITY+"/fixture/"+encoded);
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,uri,"zip",secret)){
                assertTrue(session.usesContainerCopy());
                assertEquals(before.size()+1,protectedCopies().size());
                assertArrayEquals(TEXT,session.read("Chat/messages.html",1024));
                assertArrayEquals(TEXT,session.read("Chat/photos/photo.jpg",1024));
                assertEquals(before.size()+1,protectedCopies().size());
            }
            assertEquals(before,protectedCopies());
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    private java.util.Set<String> protectedCopies(){
        String[] files=context.getCacheDir().list((dir,name)->name.startsWith("protected-container-"));
        return files==null?new java.util.HashSet<>():new java.util.HashSet<>(Arrays.asList(files));
    }
    @Test public void androidDecoderCanOpenAndSeekRamOnlyWav()throws Exception{
        int bytes=16000*2;
        java.nio.ByteBuffer wav=java.nio.ByteBuffer.allocate(44+bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+bytes);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        wav.putShort((short)1).putShort((short)1).putInt(16000).putInt(32000);
        wav.putShort((short)2).putShort((short)16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
        for(int i=0;i<16000;i++)wav.putShort((short)(Math.sin(i*2*Math.PI*440/16000)*1000));
        ProtectedMediaSource source=new ProtectedMediaSource(wav.capacity());
        android.media.MediaPlayer player=new android.media.MediaPlayer();
        try{
            source.sink().write(wav.array());source.seal();
            player.setDataSource(source);player.prepare();
            assertTrue(player.getDuration()>=900&&player.getDuration()<=1100);
            player.seekTo(500);player.start();player.pause();
        }finally{player.release();source.close();}
    }
    // Independent upstream synthetic fixtures from junrar/junrar v8.1.2:
    // src/test/resources/com/github/junrar/password/rar{4,5}-password-junrar.rar
    // The fixture password is "junrar"; no user archive or credential is used.
    private static final String RAR4="UmFyIRoHAM+QcwAADQAAAAAAAADqWnQklDMAIAAAAAYAAAADBPcp4veq81AdMwkApIEAAGZpbGUxLnR4dFioM3YCpGjgAMAsmDgcSsnwnUn6kzM4BlpBbF+uQO0D7ORxNIz3Z4nsScQ9ewBABwA=";
    private static final String RAR5="UmFyIRoHAQAzkrXlCgEFBgAFAQGAgABfIc6zUQIDMaAABoYApIMCY0kUXxnn0+uAAwEJZmlsZTEudHh0MAEAAw+rgP7jvy7bm3QlOdS3YZwFYG45NDcQ1tsYPE/SbYZEVJmc4Qhprj8yi5S4gj56t2Cqe0X/M28NnKG0ccGCC/yKLzwOLmAr44eAnrJUHXdWUQMFBAA=";
    private static final String RAR5_HEADERS="UmFyIRoHAQAYOJrPIQQAAAEPprqRs1Vs70VeAnJr65GiUWzJnBs88EB6pEDZCDMNebpM1FRWRidOoP8NEKunwvQXSE6qyWZSmFdTmJz5B4PRrGmc/9wgf07nAr0VnT/SUD7KGRm04mC2+uJap3bok3fPNwjtWnVbqxga+30ke8uVJYZkiuuGhz7dmPmsjcbbifv8JRtif4lMcFsoiFclxaKGgHWEAQ5iUr3A418NqLr87fq2lB4LpFyCVjVgrfNS3Ou5IdI1MBz0SPemsbqYy9mjOR0uTISWgDqgl9qBApSZxzov0pm4HbMDGpR2jEAzLnvZFtWMyUYPoVpguDM=";
    private byte[] verifyRar(String fixture)throws Exception{
        File rar=File.createTempFile("protected-independent-",".rar",context.getCacheDir());
        byte[] original=android.util.Base64.decode(fixture,android.util.Base64.DEFAULT);
        Files.write(rar.toPath(),original);char[] secret="junrar".toCharArray();
        try{
            byte[] decoded;
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(rar),"rar",secret)){
                assertFalse(session.usesContainerCopy());
                assertFalse(session.list().isEmpty());
                ProtectedArchiveSession.Entry target=null;
                for(ProtectedArchiveSession.Entry e:session.list())if(e.name.endsWith("file1.txt")){target=e;break;}
                assertNotNull("Known fixture member file1.txt",target);
                assertTrue(target.encrypted);
                decoded=session.read(target.name,1024);
                assertTrue(decoded.length>0);
            }
            boolean rejected=false;
            try(ProtectedArchiveSession wrong=ProtectedArchiveSession.open(context,Uri.fromFile(rar),"rar","wrong-fixture-key".toCharArray())){
                for(ProtectedArchiveSession.Entry e:wrong.list())if(e.name.endsWith("file1.txt"))wrong.read(e.name,1024);
            }catch(Exception expected){rejected=true;}
            assertTrue("Wrong RAR password must fail",rejected);
            assertArrayEquals(original,Files.readAllBytes(rar.toPath()));
            return decoded;
        }finally{rar.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void independentRar4AndRar5PasswordFixturesDecodeSameContent()throws Exception{
        assertArrayEquals(verifyRar(RAR4),verifyRar(RAR5));
    }
    @Test public void independentRar5EncryptedHeadersRequireCorrectPassword()throws Exception{
        assertArrayEquals(verifyRar(RAR5),verifyRar(RAR5_HEADERS));
    }
    @Test public void protectedUiOpensChatAndLocksWithoutCreatingPlaintextDatabase()throws Exception{
        File html=File.createTempFile("protected-ui-synthetic-",".html",context.getCacheDir());
        File zip=File.createTempFile("protected-ui-synthetic-",".zip",context.getCacheDir());
        assertTrue(zip.delete());char[] password="protected-ui-test".toCharArray();
        String marker="SyntheticProtectedChatMarker";
        android.content.SharedPreferences prefs=context.getSharedPreferences("protected_archive_pins",Context.MODE_PRIVATE);
        String old=prefs.getString("items",null);
        android.app.Activity activity=null;
        try{
            Files.write(html.toPath(),("<html><body><div class=\"history\"><div class=\"message default\" id=\"message7\"><div class=\"body\"><div class=\"from_name\">Test</div><div class=\"text\">"+marker+"</div></div></div></div></body></html>").getBytes(StandardCharsets.UTF_8));
            try(ZipFile writer=new ZipFile(zip,password)){
                ZipParameters p=new ZipParameters();p.setFileNameInZip("messages.html");
                p.setEncryptFiles(true);p.setEncryptionMethod(EncryptionMethod.AES);
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);writer.addFile(html,p);
            }
            org.json.JSONObject pinned=new org.json.JSONObject();
            pinned.put("uri",Uri.fromFile(zip).toString());pinned.put("name","Synthetic secure fixture");
            assertTrue(prefs.edit().putString("items",new org.json.JSONArray().put(pinned).toString()).commit());
            java.util.Set<String> databases=new java.util.HashSet<>(Arrays.asList(context.databaseList()));
            android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
            activity=instrumentation.startActivitySync(new android.content.Intent(context,ProtectedArchiveActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            // Inspect actual Android widgets in the target process. FLAG_SECURE
            // stays enabled; accessibility text may be transformed to all caps.
            final android.app.Activity screen=activity;
            instrumentation.runOnMainSync(()->assertTrue(
                (screen.getWindow().getAttributes().flags&
                    android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0));
            android.view.View pinnedButton=awaitText(screen,"Synthetic secure fixture",10000);
            assertNotNull("Pinned archive is present in actual activity hierarchy",pinnedButton);
            instrumentation.runOnMainSync(()->{
                assertTrue("Pinned archive button must be visible and enabled",pinnedButton.isShown()&&pinnedButton.isEnabled());
                assertTrue(pinnedButton.performClick());
            });
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText.class))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.replaceText("wrong-password"),
                    androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withText("Открыть"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click());
            // The error dialog is produced by an archive worker; wait for it
            // using Espresso's real dialog root rather than disabling FLAG_SECURE.
            long retryUntil=android.os.SystemClock.uptimeMillis()+15000;
            boolean retried=false;
            while(!retried&&android.os.SystemClock.uptimeMillis()<retryUntil){
                try{
                    androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withText("Повторить"))
                        .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                        .perform(androidx.test.espresso.action.ViewActions.click());
                    retried=true;
                }catch(androidx.test.espresso.NoMatchingViewException|androidx.test.espresso.NoMatchingRootException e){
                    android.os.SystemClock.sleep(100);
                }
            }
            assertTrue("Wrong password must show a retry dialog",retried);
            instrumentation.runOnMainSync(()->assertNull(findText(screen.getWindow().getDecorView(),marker)));
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText.class))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.replaceText("protected-ui-test"),
                    androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withText("Открыть"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click());
            android.view.View message=awaitText(screen,marker,15000);
            assertNotNull("Message appears after correct password",message);
            android.view.View lock=awaitText(screen,"Заблокировать",5000);
            assertNotNull("Lock action available",lock);
            instrumentation.runOnMainSync(lock::performClick);
            assertNotNull("Archive remains pinned",awaitText(screen,"Synthetic secure fixture",5000));
            instrumentation.runOnMainSync(()->assertNull(
                "Message removed on lock",findText(screen.getWindow().getDecorView(),marker)));
            assertEquals(databases,new java.util.HashSet<>(Arrays.asList(context.databaseList())));
        }finally{
            if(activity!=null){final android.app.Activity close=activity;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(close::finish);}
            if(old==null)prefs.edit().remove("items").commit();else prefs.edit().putString("items",old).commit();
            html.delete();zip.delete();Arrays.fill(password,'\0');
        }
    }
    private android.view.View awaitText(android.app.Activity activity,String text,long timeout)throws Exception{
        long until=android.os.SystemClock.uptimeMillis()+timeout;
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        java.util.concurrent.atomic.AtomicReference<android.view.View> result=new java.util.concurrent.atomic.AtomicReference<>();
        do{
            instrumentation.runOnMainSync(()->result.set(findText(activity.getWindow().getDecorView(),text)));
            if(result.get()!=null)return result.get();
            android.os.SystemClock.sleep(100);
        }while(android.os.SystemClock.uptimeMillis()<until);
        return null;
    }
    private static android.view.View findText(android.view.View view,String text){
        if(view.getVisibility()!=android.view.View.VISIBLE)return null;
        if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText()
            .toString().toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT)))return view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int j=0;j<group.getChildCount();j++){
                android.view.View found=findText(group.getChildAt(j),text);if(found!=null)return found;
            }
        }
        return null;
    }
    // Synthetic one-second, 32x32 red H.264 MP4; generated with FFmpeg,
    // not taken from a user archive. Validated by ffprobe and a full decode.
    private static final String VIDEO_FIXTURE="AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAN1bW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAA+gAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAqB0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAA+gAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAACAAAAAgAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAAPoAAAQAAABAAAAAAIYbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAAoAAAAKABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABw21pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAAYNzdGJsAAAAv3N0c2QAAAAAAAAAAQAAAK9hdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAACAAIABIAAAASAAAAAAAAAABFUxhdmM2MS4xOS4xMDEgbGlieDI2NAAAAAAAAAAAAAAAGP//AAAANWF2Y0MBZAAK/+EAGGdkAAqs2UlsBEAAAAMAQAAAAwKDxIllgAEABmjr48siwP34+AAAAAAQcGFzcAAAAAEAAAABAAAAFGJ0cnQAAAAAAAAYCAAAAAAAAAAYc3R0cwAAAAAAAAABAAAABQAACAAAAAAUc3RzcwAAAAAAAAABAAAAAQAAADhjdHRzAAAAAAAAAAUAAAABAAAQAAAAAAEAACgAAAAAAQAAEAAAAAABAAAAAAAAAAEAAAgAAAAAHHN0c2MAAAAAAAAAAQAAAAEAAAAFAAAAAQAAAChzdHN6AAAAAAAAAAAAAAAFAAAC0AAAAA0AAAAMAAAADAAAAAwAAAAUc3RjbwAAAAAAAAABAAADpQAAAGF1ZHRhAAAAWW1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAG1kaXJhcHBsAAAAAAAAAAAAAAAALGlsc3QAAAAkqXRvbwAAABxkYXRhAAAAAQAAAABMYXZmNjEuNy4xMDMAAAAIZnJlZQAAAwltZGF0AAACrQYF//+p3EXpvebZSLeWLNgg2SPu73gyNjQgLSBjb3JlIDE2NCByMzEwOCAzMWUxOWY5IC0gSC4yNjQvTVBFRy00IEFWQyBjb2RlYyAtIENvcHlsZWZ0IDIwMDMtMjAyMyAtIGh0dHA6Ly93d3cudmlkZW9sYW4ub3JnL3gyNjQuaHRtbCAtIG9wdGlvbnM6IGNhYmFjPTEgcmVmPTMgZGVibG9jaz0xOjA6MCBhbmFseXNlPTB4MzoweDExMyBtZT1oZXggc3VibWU9NyBwc3k9MSBwc3lfcmQ9MS4wMDowLjAwIG1peGVkX3JlZj0xIG1lX3JhbmdlPTE2IGNocm9tYV9tZT0xIHRyZWxsaXM9MSA4eDhkY3Q9MSBjcW09MCBkZWFkem9uZT0yMSwxMSBmYXN0X3Bza2lwPTEgY2hyb21hX3FwX29mZnNldD0tMiB0aHJlYWRzPTEgbG9va2FoZWFkX3RocmVhZHM9MSBzbGljZWRfdGhyZWFkcz0wIG5yPTAgZGVjaW1hdGU9MSBpbnRlcmxhY2VkPTAgYmx1cmF5X2NvbXBhdD0wIGNvbnN0cmFpbmVkX2ludHJhPTAgYmZyYW1lcz0zIGJfcHlyYW1pZD0yIGJfYWRhcHQ9MSBiX2JpYXM9MCBkaXJlY3Q9MSB3ZWlnaHRiPTEgb3Blbl9nb3A9MCB3ZWlnaHRwPTIga2V5aW50PTI1MCBrZXlpbnRfbWluPTUgc2NlbmVjdXQ9NDAgaW50cmFfcmVmcmVzaD0wIHJjX2xvb2thaGVhZD00MCByYz1jcmYgbWJ0cmVlPTEgY3JmPTIzLjAgcWNvbXA9MC42MCBxcG1pbj0wIHFwbWF4PTY5IHFwc3RlcD00IGlwX3JhdGlvPTEuNDAgYXE9MToxLjAwAIAAAAAbZYiEABL//ujJ/MsrL+PUN7NGKbNJpxuwcC/hAAAACUGaJGxD//6rgAAAAAhBnkJ4gh81IQAAAAgBnmF0Q/9EQAAAAAgBnmNqQ/9EQQ==";
    @Test public void protectedVideoDecodesFromEncryptedArchiveAndEncryptedCache()throws Exception{
        byte[] mp4=android.util.Base64.decode(VIDEO_FIXTURE,android.util.Base64.DEFAULT);
        assertEquals(1702,mp4.length);
        File input=File.createTempFile("synthetic-protected-video-",".mp4",context.getCacheDir());
        File zip=File.createTempFile("synthetic-protected-video-",".zip",context.getCacheDir());
        assertTrue(zip.delete());
        char[] secret="synthetic-video-password".toCharArray();
        try{
            Files.write(input.toPath(),mp4);
            try(ZipFile writer=new ZipFile(zip,secret)){
                ZipParameters p=new ZipParameters();p.setFileNameInZip("Chat/video_files/test.mp4");
                p.setEncryptFiles(true);p.setEncryptionMethod(EncryptionMethod.AES);
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);writer.addFile(input,p);
            }
            assertTrue(input.delete()); // no plaintext fixture remains during decode
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",secret)){
                try(ProtectedMediaSource ram=ProtectedMediaSource.load(session,"Chat/video_files/test.mp4",4096)){
                    verifyDecodedFrame(ram);
                }
                try(ProtectedEncryptedMediaSource encrypted=ProtectedEncryptedMediaSource.load(context,session,"Chat/video_files/test.mp4",4096)){
                    verifyDecodedFrame(encrypted);
                }
            }
        }finally{input.delete();zip.delete();Arrays.fill(secret,'\0');Arrays.fill(mp4,(byte)0);}
    }
    private static void verifyDecodedFrame(android.media.MediaDataSource source)throws Exception{
        android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();
        try{
            retriever.setDataSource(source);
            assertEquals("32",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("32",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            android.graphics.Bitmap frame=retriever.getFrameAtTime(400000,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST);
            assertNotNull("H.264 decoder must return an actual frame",frame);
            try{
                int color=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);
                assertTrue("Expected red synthetic frame",android.graphics.Color.red(color)>180
                    &&android.graphics.Color.green(color)<80&&android.graphics.Color.blue(color)<80);
            }finally{frame.recycle();}
        }finally{retriever.release();}
    }
    @Test public void corruptArchiveAndMissingEntriesFailWithoutPublishingData()throws Exception{
        char[] secret="synthetic-password".toCharArray();File valid=createZip(secret);
        File broken=File.createTempFile("protected-truncated-",".zip",context.getCacheDir());
        try{
            byte[] bytes=Files.readAllBytes(valid.toPath());
            Files.write(broken.toPath(),Arrays.copyOf(bytes,Math.min(30,bytes.length)));
            boolean rejected=false;
            try(ProtectedArchiveSession invalid=ProtectedArchiveSession.open(context,Uri.fromFile(broken),"zip",secret)){
                invalid.read("Chat/messages.html",1024);
            }catch(Exception expected){rejected=true;}
            assertTrue("Truncated ZIP must be rejected",rejected);
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(valid),"zip",secret)){
                assertEquals(TEXT.length,session.entrySize("Chat/messages.html"));
                for(String invalid:new String[]{"Chat/missing.jpg","../Chat/messages.html","/Chat/messages.html","Chat//messages.html"}){
                    try{session.entrySize(invalid);fail("Invalid entry unexpectedly resolved");}catch(IOException expected){}
                }
                assertArrayEquals(TEXT,session.read("Chat/messages.html",TEXT.length));
                try{session.read("Chat/messages.html",TEXT.length-1);fail("Entry over limit");}catch(IOException expected){}
            }
            assertArrayEquals(bytes,Files.readAllBytes(valid.toPath()));
        }finally{valid.delete();broken.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void cancellationDiscardsEncryptedCacheAndSessionRemainsUsable()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        java.util.Set<String> before=protectedMediaCaches();
        try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",secret)){
            java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
            Thread cancelled=new Thread(()->{
                Thread.currentThread().interrupt();
                try(ProtectedEncryptedMediaSource result=ProtectedEncryptedMediaSource.load(
                    context,session,"Chat/photos/photo.jpg",1024)){
                    failure.set(new AssertionError("Cancelled read published cache"));
                }catch(Exception expected){}catch(Throwable unexpected){failure.set(unexpected);}
            },"synthetic-cancelled-protected-reader");
            cancelled.start();cancelled.join(10000);
            assertFalse("Cancelled reader must finish",cancelled.isAlive());
            assertNull(failure.get());
            assertEquals(before,protectedMediaCaches());
            assertArrayEquals(TEXT,session.read("Chat/messages.html",1024));
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    private java.util.Set<String> protectedMediaCaches(){
        String[] names=context.getCacheDir().list((dir,name)->name.startsWith("protected-media-")&&name.endsWith(".enc"));
        return names==null?new java.util.HashSet<>():new java.util.HashSet<>(Arrays.asList(names));
    }
    @Test public void localMediaResolverRejectsTraversalAndPreservesEncodedNames()throws Exception{
        File dir=java.nio.file.Files.createTempDirectory(context.getCacheDir().toPath(),"audit-media-").toFile();
        File nested=new File(dir,"photos");assertTrue(nested.mkdir());
        File photo=new File(nested,"example#1.jpg");Files.write(photo.toPath(),TEXT);
        try{
            DocumentFolder folder=new DocumentFolder(context.getContentResolver(),dir);
            assertEquals(Uri.fromFile(photo),folder.resolve("./photos/example%231.jpg?unused=1"));
            for(String path:new String[]{"photos/../photos/example%231.jpg","photos/%2e%2e/photos/example%231.jpg",
                "photos//example%231.jpg","photos/example%231.jpg/","/photos/example%231.jpg","photos/./example%231.jpg"}){
                assertNull("Unsafe or ambiguous relative path",folder.resolve(path));
            }
        }finally{photo.delete();nested.delete();dir.delete();}
    }
}

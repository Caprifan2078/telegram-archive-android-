package org.openarchive.telegram;

import android.net.Uri;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Real encrypted RAR4/RAR5 test files from junrar 8.1.2 upstream fixtures.
 * Each archive holds a tiny file1.txt, not a Telegram chat, so a successful
 * decryption must reach "messages.html not found" validation.
 */
@RunWith(AndroidJUnit4.class)
public final class QaPasswordRarImportTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);

    private void check(String encoded, boolean correct) throws Exception {
        MainActivity a=activity.getActivity();
        File input=File.createTempFile("qa-encrypted-rar-", ".rar",a.getCacheDir());
        try {
            try(FileOutputStream out=new FileOutputStream(input)){
                out.write(Base64.decode(encoded,Base64.DEFAULT));
            }
            String key=UUID.randomUUID().toString().replace("-","");
            File output=new File(new File(a.getFilesDir(),"telegram_imports"),key);
            char[] password=(correct?"junrar":"wrong password").toCharArray();
            try {
                ArchiveFileImport.extract(a,Uri.fromFile(input),"rar",key,password,progress->{});
                fail("Fixture is not Telegram HTML; importer should refuse registration");
            }catch(Exception failure){
                final String message=String.valueOf(failure.getMessage());
                if(correct){
                    assertTrue("Wrong error after correct RAR password: "+message,
                        message.contains("messages.html"));
                }else{
                    assertFalse("Incorrect password must fail before chat validation",
                        message.contains("messages.html"));
                }
            }finally {
                java.util.Arrays.fill(password,'\0');
                assertFalse("Partial extraction must be rolled back",output.exists());
            }
        }finally{input.delete();}
    }
    @Test public void rar4EncryptedHeaderCorrectPassword()throws Exception {
        check(RAR4_HEADER,true);
    }
    @Test public void rar5EncryptedHeaderCorrectPassword()throws Exception {
        check(RAR5_HEADER,true);
    }
    @Test public void rar4EncryptedFileCorrectPassword()throws Exception {
        check(RAR4_FILE,true);
    }
    @Test public void rar5EncryptedFileCorrectPassword()throws Exception {
        check(RAR5_FILE,true);
    }
    @Test public void rar5RejectsWrongPasswordWithoutPublishingChat()throws Exception {
        check(RAR5_HEADER,false);
    }
    private static final String RAR4_HEADER="UmFyIRoHAM6Zc4AADQAAAAAAAAC5PWsXQCo7KTFMRRa743HEkQGnfYjGSpKHt825oSyxCV2WDs3/AhC15uLyC1vdqD3ZMfc1QT5xF6bbvZU5vlm7C1ewuX2zpB/y0IluICILdJAyPey4XXsY0qQOAhCYeXgNFqlwuBxUOrk9axdAKjspjUYtP7PIL6k2eg0gENTs9Q==";
    private static final String RAR5_HEADER="UmFyIRoHAQAYOJrPIQQAAAEPprqRs1Vs70VeAnJr65GiUWzJnBs88EB6pEDZCDMNebpM1FRWRidOoP8NEKunwvQXSE6qyWZSmFdTmJz5B4PRrGmc/9wgf07nAr0VnT/SUD7KGRm04mC2+uJap3bok3fPNwjtWnVbqxga+30ke8uVJYZkiuuGhz7dmPmsjcbbifv8JRtif4lMcFsoiFclxaKGgHWEAQ5iUr3A418NqLr87fq2lB4LpFyCVjVgrfNS3Ou5IdI1MBz0SPemsbqYy9mjOR0uTISWgDqgl9qBApSZxzov0pm4HbMDGpR2jEAzLnvZFtWMyUYPoVpguDM=";
    private static final String RAR4_FILE="UmFyIRoHAM+QcwAADQAAAAAAAADqWnQklDMAIAAAAAYAAAADBPcp4veq81AdMwkApIEAAGZpbGUxLnR4dFioM3YCpGjgAMAsmDgcSsnwnUn6kzM4BlpBbF+uQO0D7ORxNIz3Z4nsScQ9ewBABwA=";
    private static final String RAR5_FILE="UmFyIRoHAQAzkrXlCgEFBgAFAQGAgABfIc6zUQIDMaAABoYApIMCY0kUXxnn0+uAAwEJZmlsZTEudHh0MAEAAw+rgP7jvy7bm3QlOdS3YZwFYG45NDcQ1tsYPE/SbYZEVJmc4Qhprj8yi5S4gj56t2Cqe0X/M28NnKG0ccGCC/yKLzwOLmAr44eAnrJUHXdWUQMFBAA=";
}

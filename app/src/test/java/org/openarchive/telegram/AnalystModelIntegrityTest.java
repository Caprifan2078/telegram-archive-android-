package org.openarchive.telegram;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AnalystModelIntegrityTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String SHA="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    @Test public void correctBytesAccepted() throws Exception {
        File f=temp.newFile();
        Files.write(f.toPath(),new byte[]{97,98,99});
        assertTrue(AnalystModelDownload.matches(f,3,SHA,new AtomicBoolean()));
    }
    @Test public void sameLengthCorruptionRejected() throws Exception {
        File f=temp.newFile();
        Files.write(f.toPath(),new byte[]{97,98,100});
        assertFalse(AnalystModelDownload.matches(f,3,SHA,new AtomicBoolean()));
    }
    @Test public void truncatedOrOversizedFileRejected() throws Exception {
        File f=temp.newFile();
        Files.write(f.toPath(),new byte[]{97,98});
        assertFalse(AnalystModelDownload.matches(f,3,SHA,new AtomicBoolean()));
        Files.write(f.toPath(),new byte[]{97,98,99,100});
        assertFalse(AnalystModelDownload.matches(f,3,SHA,new AtomicBoolean()));
    }
    @Test public void cancellationAndMissingFileRejected() throws Exception {
        File f=temp.newFile();
        Files.write(f.toPath(),new byte[]{97,98,99});
        assertFalse(AnalystModelDownload.matches(f,3,SHA,new AtomicBoolean(true)));
        assertTrue(f.delete());
        assertFalse(AnalystModelDownload.matches(f,3,SHA,new AtomicBoolean()));
    }
    @Test public void directoryAndInvalidExpectedLengthRejected() throws Exception {
        assertFalse(AnalystModelDownload.matches(temp.newFolder(),3,SHA,new AtomicBoolean()));
        assertFalse(AnalystModelDownload.matches(temp.newFile(),0,SHA,new AtomicBoolean()));
    }
}

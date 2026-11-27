package org.openarchive.telegram;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalystAutoModelTest {
    private final File dir=new File(".");
    private final AnalystDeviceFit plenty=new AnalystDeviceFit(12_000_000_000L,16_000_000_000L,false);
    @Test public void sizeBoundariesAndManualConsent(){
        assertEquals(8,AnalystAutoModel.sizeCandidate(10_000));
        assertEquals(0,AnalystAutoModel.sizeCandidate(10_001));
        assertEquals(0,AnalystAutoModel.sizeCandidate(100_000));
        assertEquals(10,AnalystAutoModel.sizeCandidate(100_001));
        assertEquals(8,AnalystAutoModel.choose(10_000,true,false,plenty,dir));
        assertEquals(0,AnalystAutoModel.choose(50_000,true,false,plenty,dir));
        assertEquals(10,AnalystAutoModel.choose(2_000_000,true,false,plenty,dir));
        assertEquals(10,AnalystAutoModel.choose(10_000,false,false,plenty,dir));
        assertEquals(10,AnalystAutoModel.choose(10_000,true,true,plenty,dir));
    }
    @Test public void ramAndComplexQuestionsFailSafely(){
        AnalystDeviceFit small=new AnalystDeviceFit(2_000_000_000L,4_000_000_000L,false);
        assertEquals(8,AnalystAutoModel.choose(10_000,true,false,small,dir));
        assertEquals(-1,AnalystAutoModel.choose(10_000,false,false,small,dir));
        assertEquals(-1,AnalystAutoModel.choose(2_000_000,true,false,small,dir));
        assertEquals(-1,AnalystAutoModel.choose(10_000,true,true,small,dir));
        assertEquals(-1,AnalystAutoModel.choose(10_000,true,false,new AnalystDeviceFit(12_000_000_000L,16_000_000_000L,true),dir));
    }
}

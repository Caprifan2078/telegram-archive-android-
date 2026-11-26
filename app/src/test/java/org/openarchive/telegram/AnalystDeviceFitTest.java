package org.openarchive.telegram;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnalystDeviceFitTest {
    @Test public void fitUsesAvailableNotTotalRam(){
        File dir=new File("not-installed");
        AnalystDeviceFit fit=new AnalystDeviceFit(2500000000L,12000000000L,false);
        assertTrue(fit.fits(0,AnalystModelCatalog.file(dir,0)));
        assertFalse(fit.fits(6,AnalystModelCatalog.file(dir,6)));
        assertTrue(fit.offerLightweight(dir,6));
        assertFalse(fit.offerLightweight(dir,0));
    }
    @Test public void highPressureBlocksEvenIfAvailableRamLooksAdequate(){
        File dir=new File("not-installed");
        AnalystDeviceFit fit=new AnalystDeviceFit(8000000000L,12000000000L,true);
        assertFalse(fit.fits(0,AnalystModelCatalog.file(dir,0)));
        assertFalse(fit.offerLightweight(dir,6));
    }
    @Test public void customWithoutFileIsUnknownAndCatalogThresholdsAreConsistent(){
        File dir=new File("not-installed");
        AnalystDeviceFit fit=new AnalystDeviceFit(6000000000L,12000000000L,false);
        assertFalse(fit.fits(AnalystModelCatalog.CUSTOM,AnalystModelCatalog.file(dir,AnalystModelCatalog.CUSTOM)));
        for(int i=0;i<AnalystModelCatalog.CUSTOM;i++){
            File file=AnalystModelCatalog.file(dir,i);
            long need=fit.required(i,file);
            assertTrue(new AnalystDeviceFit(need,12000000000L,false).fits(i,file));
            assertFalse(new AnalystDeviceFit(need-1,12000000000L,false).fits(i,file));
        }
    }
}

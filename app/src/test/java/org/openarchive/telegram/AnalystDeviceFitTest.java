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
        assertTrue(fit.offerLightweight(dir,0));
        assertEquals(8,fit.lightweightAlternative(dir,6));
        assertEquals(0,fit.lightweightAlternative(dir,8));
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
    @Test public void modelFiltersOnlyShowMatchingCandidates(){
        File dir=new File("not-installed");
        AnalystDeviceFit fit=new AnalystDeviceFit(2000000000L,8000000000L,false);
        assertArrayEquals(new int[]{8,0},AnalystModelCatalog.filteredPickerOrder(dir,6,1,fit));
        assertArrayEquals(new int[]{10,2,6,7},AnalystModelCatalog.filteredPickerOrder(dir,0,2,fit));
        assertArrayEquals(new int[]{9},AnalystModelCatalog.filteredPickerOrder(dir,0,4,fit));
        assertArrayEquals(AnalystModelCatalog.pickerOrder(),AnalystModelCatalog.filteredPickerOrder(dir,0,3,fit));
        assertEquals(0,AnalystModelCatalog.filteredPickerOrder(dir,0,1,new AnalystDeviceFit(8000000000L,8000000000L,true)).length);
    }
}

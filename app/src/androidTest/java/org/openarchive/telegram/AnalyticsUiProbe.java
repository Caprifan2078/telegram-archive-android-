package org.openarchive.telegram;
import androidx.test.uiautomator.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
/** Bounded real UI checks with diagnostic hierarchy when a window is missing. Synthetic fixtures only. */
final class AnalyticsUiProbe {
    static UiObject2 require(UiDevice device,BySelector selector,String message){
        UiObject2 node=device.wait(Until.findObject(selector),20000);
        if(node==null){
            android.util.Log.e("AnalyticsUiProbe",message+"; foreground="+device.getCurrentPackageName());
            try{
                ByteArrayOutputStream out=new ByteArrayOutputStream();device.dumpWindowHierarchy(out);
                String xml=new String(out.toByteArray(),StandardCharsets.UTF_8);
                for(int i=0;i<Math.min(24000,xml.length());i+=1800)
                    android.util.Log.e("AnalyticsUiProbe",xml.substring(i,Math.min(xml.length(),i+1800)));
            }catch(Exception error){android.util.Log.e("AnalyticsUiProbe","Hierarchy unavailable",error);}
        }
        assertNotNull(message,node);return node;
    }
    private AnalyticsUiProbe(){}
}

package org.openarchive.telegram;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Real Android Lottie parser and first-frame renderer: synthetic local Telegram TGS. */
@RunWith(AndroidJUnit4.class)
public class TgsRenderingIntegrationTest {
    @Test public void compressedStickerRendersVisiblePixelsOffline() throws Exception {
        String json;
        try(InputStream source=InstrumentationRegistry.getInstrumentation().getContext()
            .getAssets().open("offline-sticker.tgs")){
            json=TgsPayload.decode(source);
        }
        JSONObject manifest=new JSONObject(json);
        assertEquals(512,manifest.getInt("w"));
        assertEquals(60,manifest.getInt("fr"));
        LottieResult<LottieComposition> parsed=LottieCompositionFactory.fromJsonStringSync(json,null);
        if(parsed.getException()!=null)throw new AssertionError(parsed.getException());
        assertNotNull(parsed.getValue());
        LottieDrawable drawable=new LottieDrawable();
        assertTrue(drawable.setComposition(parsed.getValue()));
        drawable.setBounds(0,0,256,256);
        drawable.setProgress(0.08f);
        Bitmap output=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
        try{
            drawable.draw(new Canvas(output));
            int colored=0;
            for(int y=32;y<224;y+=12)for(int x=32;x<224;x+=12){
                if((output.getPixel(x,y)>>>24)!=0)colored++;
            }
            assertTrue("TGS frame must be visible, not a blank tile",colored>10);
        }finally{drawable.clearComposition();output.recycle();}
    }
}

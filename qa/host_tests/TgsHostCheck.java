package org.openarchive.telegram;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/** Platform-independent smoke and hostile-input checks for the production TGS decoder. */
public final class TgsHostCheck {
    private static byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(GZIPOutputStream gzip=new GZIPOutputStream(bytes)){
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
    private static void reject(byte[] payload) throws Exception {
        try {
            TgsPayload.decode(new ByteArrayInputStream(payload));
            throw new AssertionError("Invalid TGS accepted");
        } catch(IOException expected) {
            // The production decoder must reject malformed or oversized payloads.
        }
    }
    public static void main(String[] args) throws Exception {
        String json="{\"v\":\"5.6.6\",\"fr\":60,\"layers\":[],\"label\":\"Привет 😀\"}";
        String result=TgsPayload.decode(new ByteArrayInputStream(gzip(json)));
        if(!json.equals(result))throw new AssertionError("UTF-8 TGS roundtrip failed");

        reject(new byte[]{1,2,3});
        reject("{}".getBytes(StandardCharsets.UTF_8));

        char[] inflated=new char[TgsPayload.MAX_JSON+100];
        Arrays.fill(inflated,'x');
        reject(gzip(new String(inflated)));

        byte[] random=new byte[TgsPayload.MAX_COMPRESSED+32768];
        new Random(42).nextBytes(random);
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(GZIPOutputStream out=new GZIPOutputStream(compressed)){out.write(random);}
        if(compressed.size()<=TgsPayload.MAX_COMPRESSED)
            throw new AssertionError("Compressed-size regression fixture is too small");
        reject(compressed.toByteArray());

        System.out.println("PASS TGS: Unicode, invalid gzip, raw JSON, inflation and compressed-size limits");
    }
}

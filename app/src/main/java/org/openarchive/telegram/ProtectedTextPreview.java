package org.openarchive.telegram;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;

/** Strict bounded text decoding; never writes a decrypted attachment to storage. */
final class ProtectedTextPreview {
    static String decode(byte[] bytes)throws IOException{
        if(bytes.length>256*1024)throw new IOException("Text preview limit");
        Charset charset=StandardCharsets.UTF_8;int offset=0;
        if(bytes.length>=3&&(bytes[0]&255)==239&&(bytes[1]&255)==187&&(bytes[2]&255)==191)offset=3;
        else if(bytes.length>=2&&(bytes[0]&255)==255&&(bytes[1]&255)==254){charset=StandardCharsets.UTF_16LE;offset=2;}
        else if(bytes.length>=2&&(bytes[0]&255)==254&&(bytes[1]&255)==255){charset=StandardCharsets.UTF_16BE;offset=2;}
        String value=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes,offset,bytes.length-offset)).toString();
        if(value.indexOf(0)>=0)throw new IOException("Not a text document");
        return value;
    }
}

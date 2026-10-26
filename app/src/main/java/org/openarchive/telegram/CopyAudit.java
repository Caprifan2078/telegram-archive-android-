package org.openarchive.telegram;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** Byte-exact verification of a staged SQLite database after copy to durable storage.
 * No message text, filesystem paths or user data are emitted in reports. */
final class CopyAudit {
    private CopyAudit(){}
    static byte[] digest(File file) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file),65536)){
            byte[] data=new byte[65536];int n;
            while((n=in.read(data))!=-1)hash.update(data,0,n);
        }
        return hash.digest();
    }
    static void requireIdentical(File source,File target) throws Exception {
        if(!source.isFile()||!target.isFile()||source.length()!=target.length()
            ||!Arrays.equals(digest(source),digest(target)))
            throw new IOException("Копия базы данных не совпала с оригиналом: восстановление отменено");
    }
}

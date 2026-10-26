package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

public class CopyAuditTest {
 @Test public void byteExactDatabasePasses()throws Exception {
    File original=File.createTempFile("tg-audit-src",".db");
    File target=File.createTempFile("tg-audit-dst",".db");
    try{byte[] input=new byte[65537];new java.util.Random(1942).nextBytes(input);
      Files.write(original.toPath(),input);Files.write(target.toPath(),input);
      CopyAudit.requireIdentical(original,target);
      input[4096]^=1;Files.write(target.toPath(),input);
      try{CopyAudit.requireIdentical(original,target);fail("Modified copy passed");}
      catch(IOException expected){}
    }finally{original.delete();target.delete();}
 }
 @Test public void missingAndShortCopiesFail()throws Exception {
    File src=File.createTempFile("tg-audit-src",".db");File dst=File.createTempFile("tg-audit-dst",".db");
    try{Files.write(src.toPath(),new byte[]{1,2,3});
      Files.write(dst.toPath(),new byte[]{1});
      try{CopyAudit.requireIdentical(src,dst);fail("Truncation passed");}catch(IOException expected){}
      dst.delete();
      try{CopyAudit.requireIdentical(src,dst);fail("Missing copy passed");}catch(IOException expected){}
    }finally{src.delete();dst.delete();}
 }
}

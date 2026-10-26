package org.openarchive.telegram;
/** Device-memory-only performance policy. No measurements leave the phone. */
final class PerformanceAdvisor {
    static int mode(int memoryClassMb,boolean lowRam){
        return lowRam||memoryClassMb<192?2:1;
    }
    static int thumbnailCacheBytes(int memoryClassMb,int preference){
        int capacity=(preference==2||memoryClassMb<192)?4:memoryClassMb>=384?16:8;
        return capacity*1024*1024;
    }
}

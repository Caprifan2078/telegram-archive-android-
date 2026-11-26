package org.openarchive.telegram;

/** Single palette for every native workspace. Pure Java for contrast regression tests. */
final class ViewerPalette {
    final int background,surface,raised,text,muted,accent,incoming,outgoing,chip,highlight;
    final boolean light;
    private ViewerPalette(int bg,int surface,int raised,int text,int muted,int accent,int in,int out,int chip,int highlight,boolean light){
        this.background=bg;this.surface=surface;this.raised=raised;this.text=text;this.muted=muted;this.accent=accent;
        this.incoming=in;this.outgoing=out;this.chip=chip;this.highlight=highlight;this.light=light;
    }
    static ViewerPalette of(int index){
        if(index==1)return new ViewerPalette(0xff101216,0xff1e222a,0xff303642,0xfff5f5f7,0xffb8bdc7,0xffb5b9ff,0xff2a303b,0xff2b514a,0xff393e4b,0xff474b80,false);
        if(index==2)return new ViewerPalette(0xfff3f6fa,0xffffffff,0xffe5ebf3,0xff172b43,0xff506780,0xff1468a4,0xffe2e9f2,0xffc5eade,0xffdce8f4,0xffb3d9ed,true);
        return new ViewerPalette(0xff0b111b,0xff172231,0xff203044,0xfff1f5fa,0xffaebdce,0xff81caff,0xff24364b,0xff24554f,0xff29465f,0xff326e8f,false);
    }
}

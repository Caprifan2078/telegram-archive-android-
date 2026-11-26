package org.openarchive.telegram;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
/** Retains the app-wide chosen typography in dialog titles, actions and custom dialog layouts. */
final class FontDialog extends Dialog {
    FontDialog(Context c){super(c);}
    FontDialog(Context c,int theme){super(c,theme);}
    @Override public void show(){super.show();FontStyler.bind(this);}
    /** Theme all app-owned alert actions consistently; keep system document providers intact. */
    static void decorate(AlertDialog dialog){
        ViewerPalette palette=ViewerUi.palette(dialog.getContext());
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            android.widget.Button b=dialog.getButton(which);
            if(b==null)continue;
            ViewerUi.styleButton(b,which==AlertDialog.BUTTON_POSITIVE?palette.chip:palette.surface,
                which==AlertDialog.BUTTON_POSITIVE?palette.accent:palette.text,palette.light);
            b.setPadding(ViewerUi.dp(b.getContext(),12),0,ViewerUi.dp(b.getContext(),12),0);
        }
        FontStyler.bind(dialog);
    }
    static void date(Context c,android.app.DatePickerDialog.OnDateSetListener listener,int year,int month,int day){
        android.app.DatePickerDialog picker=new android.app.DatePickerDialog(c,Builder.themeFor(c),listener,year,month,day);
        picker.show();decorate(picker);
    }
    static final class Builder extends AlertDialog.Builder {
        Builder(Context c){super(c,themeFor(c));}
        static int themeFor(Context c){int index=ViewerUi.themeIndex(c);return index==2?R.style.ArchiveAlertDialogLight:index==1?R.style.ArchiveAlertDialogGraphite:R.style.ArchiveAlertDialog;}
        Builder(Context c,int theme){super(c,theme);}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            dialog.setOnShowListener(ignored->decorate(dialog));
            return dialog;
        }
        @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    }
}

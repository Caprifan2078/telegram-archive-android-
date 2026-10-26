package org.openarchive.telegram;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
/** Retains the app-wide chosen typography in dialog titles, actions and custom dialog layouts. */
final class FontDialog extends Dialog {
    FontDialog(Context c){super(c);}
    FontDialog(Context c,int theme){super(c,theme);}
    @Override public void show(){super.show();FontStyler.bind(this);}
    static final class Builder extends AlertDialog.Builder {
        Builder(Context c){super(c);}
        Builder(Context c,int theme){super(c,theme);}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            dialog.setOnShowListener(ignored->FontStyler.bind(dialog));
            return dialog;
        }
        @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    }
}

package io.github.lemonteaau.zuiiconhelper;
import android.app.Dialog;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Smartisan's 48dp header, white body, 12dp corners and 54dp split action row. */
final class PanelDialog extends Dialog {
    Button positive,negative;
    PanelDialog(Context context){super(context);requestWindowFeature(Window.FEATURE_NO_TITLE);}
    Button getButton(int which){return which==DialogInterface.BUTTON_POSITIVE?positive:negative;}
    static final class Builder {
        final Context context;String title="",message;String[] items;View view;String positive,negative;DialogInterface.OnClickListener yes,no,itemClick;
        Builder(Context c){context=c;}
        Builder setTitle(String s){title=s;return this;}Builder setMessage(String s){message=s;return this;}Builder setView(View v){view=v;return this;}
        Builder setPositiveButton(String s,DialogInterface.OnClickListener l){positive=s;yes=l;return this;}Builder setNegativeButton(String s,DialogInterface.OnClickListener l){negative=s;no=l;return this;}
        Builder setItems(String[] s,DialogInterface.OnClickListener l){items=s;itemClick=l;return this;}
        PanelDialog show(){PanelDialog d=create();d.show();return d;}
        PanelDialog create(){
            PanelDialog d=new PanelDialog(context);LinearLayout box=new LinearLayout(context);box.setOrientation(1);box.setBackground(SmartisanStyle.surface(context,Color.WHITE,12,0));box.setClipToOutline(true);
            TextView heading=SmartisanStyle.text(context,title,16,SmartisanStyle.TITLE);heading.setTypeface(null,1);heading.setGravity(Gravity.CENTER);heading.setPadding(dp(16),0,dp(16),0);heading.setBackgroundColor(SmartisanStyle.PANEL);box.addView(heading,new LinearLayout.LayoutParams(-1,dp(48)));box.addView(SmartisanStyle.divider(context,0xffe8e8e8));
            if(view!=null)box.addView(view,new LinearLayout.LayoutParams(-1,-2));
            else if(items!=null){LinearLayout rows=new LinearLayout(context);rows.setOrientation(1);for(int i=0;i<items.length;i++){final int n=i;TextView row=SmartisanStyle.text(context,items[i],15,SmartisanStyle.TEXT);row.setPadding(dp(20),0,dp(20),0);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundResource(android.R.drawable.list_selector_background);row.setOnClickListener(v->{d.dismiss();if(itemClick!=null)itemClick.onClick(d,n);});rows.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));if(i<items.length-1)rows.addView(SmartisanStyle.divider(context,SmartisanStyle.DIVIDER));}box.addView(rows);}
            else if(message!=null){ScrollView scroll=new ScrollView(context);TextView body=SmartisanStyle.text(context,message,14,SmartisanStyle.SECONDARY);body.setLineSpacing(dp(6),1);body.setPadding(dp(24),dp(22),dp(24),dp(22));scroll.addView(body);box.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(420),Math.max(dp(90),message.length()/28*dp(24)+dp(60)))));}
            if(positive!=null||negative!=null){box.addView(SmartisanStyle.divider(context,0xffe8e8e8));LinearLayout actions=new LinearLayout(context);actions.setBaselineAligned(false);actions.setBackgroundColor(SmartisanStyle.PANEL);if(negative!=null){d.negative=action(negative,SmartisanStyle.TITLE);d.negative.setOnClickListener(v->{if(no!=null)no.onClick(d,DialogInterface.BUTTON_NEGATIVE);d.dismiss();});actions.addView(d.negative,new LinearLayout.LayoutParams(0,dp(54),1));}if(positive!=null&&negative!=null){View line=new View(context);line.setBackgroundColor(SmartisanStyle.DIVIDER);actions.addView(line,new LinearLayout.LayoutParams(dp(1),dp(54)));}if(positive!=null){d.positive=action(positive,SmartisanStyle.BLUE);d.positive.setOnClickListener(v->{if(yes!=null)yes.onClick(d,DialogInterface.BUTTON_POSITIVE);d.dismiss();});actions.addView(d.positive,new LinearLayout.LayoutParams(0,dp(54),1));}box.addView(actions);}
            d.setContentView(box);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=d.getWindow().getAttributes();a.dimAmount=.6f;a.width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(40),dp(560));a.height=-2;d.getWindow().setAttributes(a);return d;
        }
        int dp(float n){return SmartisanStyle.dp(context,n);}
        Button action(String label,int color){Button b=new Button(context);b.setText(label);SmartisanStyle.type(b,14,color,true);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackgroundResource(android.R.drawable.list_selector_background);b.setStateListAnimator(null);return b;}
    }
}

package io.github.lemonteaau.zuiiconhelper;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Tokens from Paix Chan's Smartisan OS UI Kit (Unofficial, CC BY 4.0), adapted to Android density units. */
final class SmartisanStyle {
    static final int BACKGROUND=0xfff5f5f5,TEXT=0xff333333,SECONDARY=0xff8a8a8a,TITLE=0xff636363,BLUE=0xff5b87eb,BORDER=0xffe4e4e4,DIVIDER=0xffefefef,
        BAR_LINE=0xffdcdcdc,PANEL=0xfff8f8f8,SELECTED_FILL=0xffeef3fd,SELECTED_LINE=0xffb9cbf4,PRESSED=0xfff0f0f0;
    static final int CONTENT_MAX_DP=720;

    static int dp(Context c,float v){return (int)(c.getResources().getDisplayMetrics().density*v+.5f);}
    static GradientDrawable surface(Context c,int color,float radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(border!=0)d.setStroke(Math.max(1,dp(c,.8f)),border);return d;}
    static GradientDrawable gradient(Context c,int top,int bottom,int border,float radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});d.setCornerRadius(dp(c,radius));d.setStroke(Math.max(1,dp(c,.8f)),border);return d;}

    /** Large buttons: the kit's blue elevated button, or its white secondary button. */
    static Drawable button(Context c,boolean primary){StateListDrawable d=new StateListDrawable();
        if(primary){d.addState(new int[]{-android.R.attr.state_enabled},lifted(c,gradient(c,0xffcddaf9,0xffc8d6f8,0xffc2d0f6,6),0));d.addState(new int[]{android.R.attr.state_pressed},lifted(c,gradient(c,0xff4d76e3,0xff4a73e2,0xff3a62d8,6),0));d.addState(new int[]{},lifted(c,gradient(c,0xff6690ee,0xff5883eb,0xff4a74e3,6),0x24305fd0));}
        else {d.addState(new int[]{android.R.attr.state_pressed},lifted(c,surface(c,PRESSED,6,0xffd6d6d6),0));d.addState(new int[]{},lifted(c,gradient(c,0xffffffff,0xfffbfbfb,0xffdadada,6),0x14000000));}return d;
    }
    /** Draws the kit's soft drop shadow inside the button's own bounds, so no parent can clip it. */
    static Drawable lifted(Context c,Drawable face,int shadow){GradientDrawable s=surface(c,shadow,6,0);LayerDrawable l=new LayerDrawable(new Drawable[]{s,face});int lift=dp(c,2);l.setLayerInset(0,0,lift,0,0);l.setLayerInset(1,0,0,0,lift);return l;}
    /** Grouped list surface: white, hairline border and the kit's very soft bottom shadow. */
    static Drawable card(Context c){GradientDrawable shadow=surface(c,0x0c000000,10,0);GradientDrawable body=surface(c,Color.WHITE,10,BORDER);LayerDrawable l=new LayerDrawable(new Drawable[]{shadow,body});l.setLayerInset(0,0,dp(c,1),0,0);l.setLayerInset(1,0,0,0,dp(c,1));return l;}
    static void card(View v){v.setBackground(card(v.getContext()));v.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,Outline o){o.setRoundRect(0,0,view.getWidth(),view.getHeight()-dp(view.getContext(),1),dp(view.getContext(),10));}});v.setClipToOutline(true);}
    /** Icon choice tile: blank when idle, light blue with a hairline when selected. */
    static Drawable tile(Context c,boolean selected){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},surface(c,selected?0xffe2eafc:PRESSED,8,selected?SELECTED_LINE:0));d.addState(new int[]{},selected?surface(c,SELECTED_FILL,8,SELECTED_LINE):new ColorDrawable(Color.TRANSPARENT));return d;}
    static Drawable row(Context c){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(PRESSED));d.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));return d;}

    static ImageView icon(Context c,String name,int size){ImageView v=new ImageView(c);v.setImageResource(c.getResources().getIdentifier("sm_"+name,"drawable",c.getPackageName()));v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    static void type(TextView t,int size,int color,boolean bold){t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);}
    static TextView text(Context c,String title,int size,int color){TextView t=new TextView(c);type(t,size,color,false);t.setText(title);return t;}
    static View divider(Context c,int color){View v=new View(c);v.setBackgroundColor(color);v.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(1,dp(c,.8f))));return v;}
    static TextView caption(Context c,String s){TextView t=text(c,s,13,SECONDARY);t.setPadding(dp(c,16),0,0,dp(c,7));return t;}

    /** Segmented button from the kit: white strip, the chosen segment is a small blue pill. */
    static LinearLayout segmented(Context c,String[] labels,int selected,java.util.function.IntConsumer pick){
        LinearLayout bar=new LinearLayout(c);bar.setBaselineAligned(false);bar.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));bar.setBackground(surface(c,Color.WHITE,6,BORDER));
        TextView[] items=new TextView[labels.length];
        Runnable[] paint=new Runnable[1];int[] current={selected};
        paint[0]=()->{for(int i=0;i<items.length;i++){boolean on=i==current[0];type(items[i],13,on?Color.WHITE:0xff555555,true);items[i].setBackground(on?gradient(c,0xff6690ee,0xff5883eb,0xff4a74e3,4):row(c));}};
        for(int i=0;i<labels.length;i++){final int n=i;TextView t=text(c,labels[i],13,0xff555555);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setContentDescription(labels[i]);t.setOnClickListener(v->{current[0]=n;paint[0].run();pick.accept(n);});items[i]=t;bar.addView(t,new LinearLayout.LayoutParams(0,-1,1));}
        paint[0].run();return bar;
    }

    /** Lays its child out at most CONTENT_MAX_DP wide, centred — keeps phone-sized controls readable on the tablet. */
    static final class Centered extends FrameLayout {
        Centered(Context c,View child,int height){super(c);addView(child,new FrameLayout.LayoutParams(-1,height,Gravity.CENTER_HORIZONTAL));}
        @Override protected void onMeasure(int w,int h){int max=dp(getContext(),CONTENT_MAX_DP),size=MeasureSpec.getSize(w);View child=getChildAt(0);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)child.getLayoutParams();lp.width=size>max?max:-1;super.onMeasure(w,h);}
    }
}

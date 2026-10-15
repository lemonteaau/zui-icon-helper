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
    /** Smartisan's icon tile (Settings → 应用图标): white square, hairline border, soft bottom shadow; grey when empty. */
    static Drawable iconTile(Context c,boolean empty){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},tileFace(c,0xffeeeeee));d.addState(new int[]{},tileFace(c,empty?0xfff8f8f8:Color.WHITE));return d;}
    static Drawable tileFace(Context c,int fill){GradientDrawable shadow=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x22000000});shadow.setCornerRadius(dp(c,4));LayerDrawable l=new LayerDrawable(new Drawable[]{shadow,surface(c,fill,3.5f,0xffe3e3e3)});l.setLayerInset(0,0,dp(c,1.5f),0,0);l.setLayerInset(1,0,0,0,dp(c,1.5f));return l;}
    /** One row of a grouped card drawn row by row: the first and last rows carry the rounded corners, each bottom border doubles as the divider. */
    static Drawable segment(Context c,boolean first,boolean last){float r=dp(c,10);int line=Math.max(1,dp(c,.8f));GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setStroke(line,BORDER);g.setCornerRadii(new float[]{first?r:0,first?r:0,first?r:0,first?r:0,last?r:0,last?r:0,last?r:0,last?r:0});return first?g:new InsetDrawable(g,0,-line,0,0);}
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

    /** Equal-width cells, as many columns as fit, with hairlines between rows like Smartisan's icon pages. */
    static final class Grid extends ViewGroup {
        final int cell;final Paint line=new Paint();int columns=1;final java.util.List<Integer> rows=new java.util.ArrayList<>();
        Grid(Context c,int cellDp){super(c);cell=dp(c,cellDp);line.setColor(DIVIDER);setWillNotDraw(false);}
        @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w)-getPaddingLeft()-getPaddingRight();columns=Math.max(1,width/cell);int cw=width/columns,height=0,row=0,n=getChildCount();
            for(int i=0;i<n;i++){View v=getChildAt(i);v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));row=Math.max(row,v.getMeasuredHeight());if(i%columns==columns-1||i==n-1){height+=row;row=0;}}
            setMeasuredDimension(MeasureSpec.getSize(w),height+getPaddingTop()+getPaddingBottom());}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int cw=(getWidth()-getPaddingLeft()-getPaddingRight())/columns,y=getPaddingTop(),row=0,n=getChildCount();rows.clear();
            for(int i=0;i<n;i++){View v=getChildAt(i);int x=getPaddingLeft()+(i%columns)*cw;v.layout(x,y,x+cw,y+v.getMeasuredHeight());row=Math.max(row,v.getMeasuredHeight());if(i%columns==columns-1||i==n-1){y+=row;row=0;if(i<n-1)rows.add(y);}}}
        @Override protected void onDraw(Canvas canvas){for(int y:rows)canvas.drawRect(0,y,getWidth(),y+Math.max(1,dp(getContext(),.8f)),line);}
    }

    /** Lays its child out at most CONTENT_MAX_DP wide, centred — keeps phone-sized controls readable on the tablet. */
    static final class Centered extends FrameLayout {
        Centered(Context c,View child,int height){super(c);addView(child,new FrameLayout.LayoutParams(-1,height,Gravity.CENTER_HORIZONTAL));}
        @Override protected void onMeasure(int w,int h){int max=dp(getContext(),CONTENT_MAX_DP),size=MeasureSpec.getSize(w);View child=getChildAt(0);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)child.getLayoutParams();lp.width=size>max?max:-1;super.onMeasure(w,h);}
    }
}

package io.github.lemonteaau.zuiiconhelper;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** The kit's slider: thin grey track, blue fill and a white round thumb with a soft shadow. */
final class SmartisanSeekBar extends SeekBar {
    final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG),ring=new Paint(Paint.ANTI_ALIAS_FLAG);
    SmartisanSeekBar(Context context){
        super(context);setLayerType(LAYER_TYPE_SOFTWARE,null);setBackground(null);setThumb(null);setProgressDrawable(null);
        track.setStrokeCap(Paint.Cap.ROUND);thumb.setColor(Color.WHITE);thumb.setShadowLayer(dp(3),0,dp(1),0x40000000);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(Math.max(1,dp(.8f)));ring.setColor(0xffdedede);
    }
    int dp(float v){return SmartisanStyle.dp(getContext(),v);}
    float inset(){return dp(14);}
    @Override protected synchronized void onDraw(Canvas canvas){
        float left=inset(),right=getWidth()-inset(),middle=getHeight()/2f,pos=left+(right-left)*getProgress()/Math.max(1f,getMax());
        track.setStrokeWidth(dp(4));track.setColor(0xffe6e6e6);canvas.drawLine(left,middle,right,middle,track);
        track.setColor(SmartisanStyle.BLUE);canvas.drawLine(left,middle,pos,middle,track);
        canvas.drawCircle(pos,middle,dp(11),thumb);canvas.drawCircle(pos,middle,dp(11),ring);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){getParent().requestDisallowInterceptTouchEvent(true);float p=(event.getX()-inset())/Math.max(1,getWidth()-2*inset());setProgress(Math.round(Math.max(0,Math.min(1,p))*getMax()));invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);performClick();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

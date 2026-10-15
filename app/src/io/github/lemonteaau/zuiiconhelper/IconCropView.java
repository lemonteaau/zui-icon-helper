package io.github.lemonteaau.zuiiconhelper;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** The preview and PNG export share exactly the same transform. */
final class IconCropView extends View {
    final Bitmap source; final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    float zoom=1,offsetX=0,offsetY=0,downX,downY;boolean fill;int turns;
    IconCropView(Context c,Bitmap bitmap){super(c);source=bitmap;setContentDescription("图标裁切预览，可拖动图片调整位置");setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void reset(boolean crop){fill=crop;zoom=1;offsetX=offsetY=0;invalidate();}
    void rotate(){turns=(turns+1)%4;offsetX=offsetY=0;invalidate();}
    void zoom(float value){zoom=value;clamp();invalidate();}
    float scale(float side){int w=turns%2==0?source.getWidth():source.getHeight(),h=turns%2==0?source.getHeight():source.getWidth();return side/(fill?Math.min(w,h):Math.max(w,h))*zoom;}
    void drawIcon(Canvas c,float side){c.save();c.translate(side*(.5f+offsetX),side*(.5f+offsetY));c.rotate(90*turns);float s=scale(side);c.scale(s,s);c.drawBitmap(source,-source.getWidth()/2f,-source.getHeight()/2f,paint);c.restore();}
    Bitmap export(){Bitmap out=Bitmap.createBitmap(CustomIcons.OUTPUT_SIZE,CustomIcons.OUTPUT_SIZE,Bitmap.Config.ARGB_8888);drawIcon(new Canvas(out),CustomIcons.OUTPUT_SIZE);return out;}
    protected void onMeasure(int w,int h){int size=Math.min(MeasureSpec.getSize(w),MeasureSpec.getSize(h));setMeasuredDimension(size,size);}
    protected void onDraw(Canvas c){super.onDraw(c);float side=getWidth(),cell=side/12;for(int y=0;y<12;y++)for(int x=0;x<12;x++){paint.setColor((x+y)%2==0?0xffeeeeee:0xffffffff);c.drawRect(x*cell,y*cell,(x+1)*cell,(y+1)*cell,paint);}drawIcon(c,side);paint.setColor(0xffbbbbbb);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);c.drawRect(1,1,side-1,side-1,paint);paint.setStyle(Paint.Style.FILL);}
    void clamp(){offsetX=Math.max(-.8f,Math.min(.8f,offsetX));offsetY=Math.max(-.8f,Math.min(.8f,offsetY));}
    @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;case MotionEvent.ACTION_MOVE:offsetX+=(e.getX()-downX)/getWidth();offsetY+=(e.getY()-downY)/getWidth();downX=e.getX();downY=e.getY();clamp();invalidate();return true;case MotionEvent.ACTION_UP:performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}

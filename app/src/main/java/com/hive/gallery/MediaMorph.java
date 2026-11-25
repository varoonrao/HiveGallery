package com.hive.gallery;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** One bitmap, one continuously interpolated mask and image matrix; no view snapshot. */
final class MediaMorph extends View {
    private final Bitmap bitmap,tileBitmap;private final RectF start,end,box=new RectF();private final Path path=new Path();private final Matrix matrix=new Matrix();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final float radius;private float t;
    MediaMorph(Context context,Bitmap b,Bitmap tile,RectF from,RectF to,float corner){super(context);bitmap=b;tileBitmap=tile;start=new RectF(from);end=new RectF(to);radius=corner;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void progress(float value){t=Math.max(0,Math.min(1,value));invalidate();}
    float progress(){return t;}
    static RectF fit(Bitmap b,RectF r){if(b==null||r.isEmpty())return new RectF();float scale=Math.min(r.width()/b.getWidth(),r.height()/b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;return new RectF(r.centerX()-w/2,r.centerY()-h/2,r.centerX()+w/2,r.centerY()+h/2);}
    private static float lerp(float a,float b,float f){return a+(b-a)*f;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(bitmap==null||bitmap.isRecycled())return;box.set(lerp(start.left,end.left,t),lerp(start.top,end.top,t),lerp(start.right,end.right,t),lerp(start.bottom,end.bottom,t));float w=box.width(),h=box.height(),x=box.centerX(),y=box.centerY();
        // Six vertices remain stable throughout: the top/bottom tips widen into straight edges.
        float[] xs={x,box.right,box.right,x,box.left,box.left};float[] ys={box.top,box.top+h*(1-t)*.25f,box.bottom-h*(1-t)*.25f,box.bottom,box.bottom-h*(1-t)*.25f,box.top+h*(1-t)*.25f};
        path.reset();float r=radius*(1-t);for(int i=0;i<6;i++){int a=(i+5)%6,b=(i+1)%6;float f=Math.min(.4f,r/Math.max(.001f,(float)Math.hypot(xs[a]-xs[i],ys[a]-ys[i]))),g=Math.min(.4f,r/Math.max(.001f,(float)Math.hypot(xs[b]-xs[i],ys[b]-ys[i])));float ax=lerp(xs[i],xs[a],f),ay=lerp(ys[i],ys[a],f),bx=lerp(xs[i],xs[b],g),by=lerp(ys[i],ys[b],g);if(i==0)path.moveTo(ax,ay);else path.lineTo(ax,ay);path.quadTo(xs[i],ys[i],bx,by);}path.close();
        c.save();c.clipPath(path);drawCrop(c,bitmap,255);if(tileBitmap!=null&&tileBitmap!=bitmap&&!tileBitmap.isRecycled()&&t<.42f)drawCrop(c,tileBitmap,Math.round(255*(1-t/.42f)));c.restore();paint.setAlpha(255);
    }
    private void drawCrop(Canvas canvas,Bitmap b,int alpha){float scale=Math.max(box.width()/b.getWidth(),box.height()/b.getHeight());matrix.reset();matrix.setScale(scale,scale);matrix.postTranslate(box.centerX()-b.getWidth()*scale/2,box.centerY()-b.getHeight()*scale/2);paint.setAlpha(alpha);canvas.drawBitmap(b,matrix,paint);}
}

package com.hive.gallery;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

final class GlassBar extends View {
    private final HoneycombView gallery;
    private final RenderNode blur=new RenderNode("Hive glass");
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private View source; private boolean light, motion=true; private int count=4; private float x=0,goal=0,v=0; private long last=0;
    GlassBar(Context c,HoneycombView gallery){super(c);this.gallery=gallery;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void source(View v){source=v;invalidate();}
    void theme(boolean l){light=l;invalidate();}
    void motion(boolean m){motion=m;invalidate();}
    void tab(int index,int count){this.count=count;goal=index;invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),r=h/2;long now=SystemClock.uptimeMillis();float dt=last==0?1f/60:Math.min(.025f,(now-last)/1000f);last=now;
        if(motion&&ValueAnimator.areAnimatorsEnabled()){v+=(310*(goal-x)-19*v)*dt;x+=v*dt;}else{x=goal;v=0;}
        if(Math.abs(goal-x)<.0005f&&Math.abs(v)<.006f){x=goal;v=0;last=0;}else postInvalidateOnAnimation();
        RectF bounds=new RectF(.7f,.7f,w-.7f,h-.7f);clip.reset();clip.addRoundRect(bounds,r,r,Path.Direction.CW);c.save();c.clipPath(clip);
        int[] loc=new int[2],g=new int[2];getLocationInWindow(loc);gallery.getLocationInWindow(g);if(source!=null&&android.os.Build.VERSION.SDK_INT>=31&&c.isHardwareAccelerated()&&getWidth()>0&&getHeight()>0){source.getLocationInWindow(g);blur.setPosition(0,0,getWidth(),getHeight());RecordingCanvas rc=blur.beginRecording(getWidth(),getHeight());rc.translate(g[0]-loc[0],g[1]-loc[1]);source.draw(rc);blur.endRecording();blur.setRenderEffect(RenderEffect.createBlurEffect(dp(12),dp(12),Shader.TileMode.CLAMP));c.drawRenderNode(blur);}else if(source==null)gallery.drawBackdrop(c,loc[0]-g[0],loc[1]-g[1],getWidth(),getHeight(),blur);
        p.setShader(null);p.setColor(light?0x99ffffff:0x64000000);c.drawRect(bounds,p);
        p.setShader(new LinearGradient(0,0,w,h,new int[]{0x36ffffff,0x0affffff,0x25ffffff},null,Shader.TileMode.CLAMP));c.drawRect(bounds,p);p.setShader(null);
        float pad=dp(5),step=(w-pad*2)/count,stretch=Math.min(.25f,Math.abs(v)*.017f),cx=pad+step*(x+.5f),bw=step*(1+stretch),bh=(h-pad*2)*(1-stretch*.44f);
        RectF bubble=new RectF(cx-bw/2,h/2-bh/2,cx+bw/2,h/2+bh/2);
        p.setShader(new LinearGradient(bubble.left,bubble.top,bubble.right,bubble.bottom,new int[]{light?0xddffffff:0x38ffffff,light?0x77ffffff:0x0affffff,0x36ffffff},null,Shader.TileMode.CLAMP));c.drawRoundRect(bubble,bh/2,bh/2,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.8f));p.setColor(0x66ffffff);c.drawRoundRect(bubble,bh/2,bh/2,p);p.setStyle(Paint.Style.FILL);c.restore();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.8f));p.setColor(light?0xddffffff:0x66ffffff);c.drawRoundRect(bounds,r,r,p);p.setStyle(Paint.Style.FILL);
    }
}

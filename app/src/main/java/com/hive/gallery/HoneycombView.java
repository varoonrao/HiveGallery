package com.hive.gallery;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/** Layout and entry transforms are independent: motion does not change media order. */
final class HoneycombView extends View {
    interface Listener { void open(Media m); void hold(Media m); void date(String s); void page(int index,int count); void redrawGlass(); }
    static final int PAGE_SIZE=132;
    private final Listener listener; private final ThumbnailCache thumbs;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path path=new Path(); private final RectF rect=new RectF(),srcDest=new RectF(); private final Rect srcRect=new Rect();
    private final ArrayList<Page> pages=new ArrayList<>(); private final ArrayList<Hit> hits=new ArrayList<>();
    private final Set<String> favorites=new HashSet<>(); private final Set<String> selected=new HashSet<>();
    private List<Media> media=Collections.emptyList(); private Scene outgoing;
    private int index=0, bg=Color.BLACK, fg=Color.WHITE, muted=0xff96969e; private boolean selecting=false, light=false, motion=true, grouped=true;
    private float focus=1f, bounce=.66f, gap=4, radius=5, stagger=22, entryDuration=520;
    private float x=0,xGoal=0,xV=0; private long lastFrame=0,entryStart=0,exitStart=0; private float entryFloor=0;
    private final float density; private final GestureDetector detector; private VelocityTracker tracker; private float downX,downY,startX,startY; private int axis=0; private boolean dragging=false; private boolean longPressed=false;
    private boolean viewerFrozen; private long frozenAt; private String hiddenUri="";
    private String lastDate=""; private long drawnAt=0, frameId=0, backdropFrame=-1; private int backdropLeft,backdropTop,backdropW,backdropH;
    private final android.graphics.drawable.Drawable heartIcon,checkIcon,playIcon;
    private static final class Row { List<Media> items; String label; int type; float base,h,center,y; float[] widths,cxs; Row(String label,int type,float h){this.label=label;this.type=type;this.h=h;} Row(List<Media> items,int slots){this.items=items;type=0;widths=new float[items.size()];cxs=new float[items.size()];this.slots=slots;} int slots; }
    private static final class Page { final ArrayList<Row> rows=new ArrayList<>(); float pos,goal,v,max; }
    private static final class Scene { Bitmap bitmap; Scene(Bitmap b){bitmap=b;} }
    private static final class Hit { Media m; RectF r; Hit(Media m,RectF r){this.m=m;this.r=r;} }
    HoneycombView(Context c,ThumbnailCache cache,Listener l){super(c);listener=l;thumbs=cache;density=getResources().getDisplayMetrics().density;heartIcon=c.getDrawable(R.drawable.ic_heart).mutate();checkIcon=c.getDrawable(R.drawable.ic_check).mutate();playIcon=c.getDrawable(R.drawable.ic_play).mutate();setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        detector=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(android.view.MotionEvent e){return true;} @Override public boolean onSingleTapUp(android.view.MotionEvent e){if(axis==0&&!longPressed){Media m=hit(e.getX(),e.getY());if(m!=null){performClick();listener.open(m);}}return true;} @Override public void onLongPress(android.view.MotionEvent e){if(axis==0){Media m=hit(e.getX(),e.getY());if(m!=null){longPressed=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);listener.hold(m);}}}});
    }
    float dp(float v){return v*density;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.ScrollView");info.setScrollable(true);info.setContentDescription("Honeycomb gallery. Use volume-free navigation controls or swipe. Open media using accessible list in menu.");info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle b){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){if(!pages.isEmpty()){Page pg=pages.get(index);pg.goal=clamp(pg.goal+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*getHeight()*.65f,0,pg.max);invalidate();}return true;}return super.performAccessibilityAction(action,b);}
    RectF boundsInWindow(Media media){
        if(!isShown()||getAlpha()<.5f||media==null)return null;
        for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(hit.m.uri.equals(media.uri)){
            RectF r=new RectF(hit.r);int[] loc=new int[2];getLocationInWindow(loc);r.offset(loc[0],loc[1]);return r;
        }}return null;
    }
    void freezeForViewer(boolean freeze){
        if(freeze==viewerFrozen)return;
        viewerFrozen=freeze;
        if(freeze){frozenAt=drawnAt==0?SystemClock.uptimeMillis():drawnAt;dragging=false;}
        else{xGoal=index*getWidth();xV=0;for(Page pg:pages){pg.goal=clamp(pg.pos,0,pg.max);pg.v=0;}if(entryStart!=0)entryStart+=SystemClock.uptimeMillis()-frozenAt;if(outgoing!=null)exitStart+=SystemClock.uptimeMillis()-frozenAt;lastFrame=0;}
        invalidate();
    }
    void hideForMorph(Media m){hiddenUri=m==null?"":m.uri.toString();invalidate();}
    float morphRadius(){return dp(radius);}
    void theme(boolean light){this.light=light;bg=light?0xfff8f8fa:Color.BLACK;fg=light?0xff151518:Color.WHITE;muted=light?0xff68686f:0xff96969e;invalidate();}
    void settings(float focus,float bounce,float gap,float radius,float stagger,float duration,boolean motion){this.focus=focus;this.bounce=bounce;this.gap=gap;this.radius=radius;this.stagger=stagger;this.entryDuration=duration;this.motion=motion;invalidate();}
    void marks(Set<String> fav,Set<String> sel,boolean selecting){favorites.clear();favorites.addAll(fav);selected.clear();selected.addAll(sel);this.selecting=selecting;invalidate();}
    int pageIndex(){return index;} int pageCount(){return pages.size();}
    int firstVisibleIndex(){return index*PAGE_SIZE;}
    float scrollPosition(){return pages.isEmpty()?0:pages.get(index).pos;}
    void restore(int page,float scroll){if(pages.isEmpty())return;index=Math.max(0,Math.min(page,pages.size()-1));x=xGoal=index*getWidth();xV=0;Page p=pages.get(index);p.pos=p.goal=clamp(scroll,0,p.max);p.v=0;listener.page(index,pages.size());invalidate();}
    void replay(){entryFloor=0;entryStart=SystemClock.uptimeMillis();invalidate();}
    void grouping(boolean grouped){this.grouped=grouped;backdropFrame=-1;}
    void setMedia(List<Media> list,boolean animate){
        if(animate&&animations()&&getWidth()>0&&getHeight()>0&&!pages.isEmpty()){
            if(outgoing!=null){outgoing=null;}
            try{Bitmap b=Bitmap.createBitmap(Math.max(1,getWidth()/2),Math.max(1,getHeight()/2),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.scale(.5f,.5f);drawContent(c,SystemClock.uptimeMillis(),false);outgoing=new Scene(b);exitStart=SystemClock.uptimeMillis();}catch(RuntimeException ignored){}
        }
        media=new ArrayList<>(list);pages.clear();index=0;x=xGoal=xV=0;lastDate="";
        for(int i=0;i<media.size();i+=PAGE_SIZE){Page pg=new Page();List<Media> sub=media.subList(i,Math.min(i+PAGE_SIZE,media.size()));String yr="",mo="",day="";int j=0;
            if(!grouped){int r=0;while(j<sub.size()){int slots=(r++%2==0?5:6),to=Math.min(sub.size(),j+slots);pg.rows.add(new Row(new ArrayList<>(sub.subList(j,to)),slots));j=to;}}
            while(j<sub.size()){Media m=sub.get(j);if(!yr.equals(m.year)){yr=m.year;pg.rows.add(new Row(yr,3,dp(38)));}if(!mo.equals(m.month)){mo=m.month;pg.rows.add(new Row(m.displayDate("MMMM"),2,dp(28)));}day=m.day;int end=j+1;while(end<sub.size()&&sub.get(end).day.equals(day))end++;pg.rows.add(new Row(m.displayDate("EEE, d MMM")+"   ·   "+(end-j)+" items",1,dp(26)));int r=0;while(j<end){int slots=(r++%2==0?5:6),to=Math.min(end,j+slots);pg.rows.add(new Row(new ArrayList<>(sub.subList(j,to)),slots));j=to;}}
            float y=dp(12);for(Row row:pg.rows){float rh=row.type==0?dp(71):row.h;row.base=y+rh/2;y+=rh+dp(5);}pg.max=Math.max(0,y-getHeight()*.42f);pages.add(pg);
        }
        entryFloor=0;entryStart=animate?SystemClock.uptimeMillis():0;listener.page(index,pages.size());invalidate();
    }
    void movePage(int d){if(pages.isEmpty())return;int next=Math.max(0,Math.min(index+d,pages.size()-1));if(next!=index){entryFloor=Math.abs(next*getWidth()-x)<getWidth()*.85f?1f:0f;index=next;entryStart=SystemClock.uptimeMillis();listener.page(index,pages.size());performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}xGoal=index*getWidth();invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);x=xGoal=index*w;xV=0;for(Page pg:pages){if(!pg.rows.isEmpty()){Row last=pg.rows.get(pg.rows.size()-1);pg.max=Math.max(0,last.base+dp(38)-h*.42f);pg.goal=pg.pos=clamp(pg.pos,0,pg.max);}}backdropFrame=-1;invalidate();}
    private static float clamp(float x,float a,float b){return Math.max(a,Math.min(b,x));}
    private static float exp(float v){return (float)Math.exp(-v*v);}
    private static float lerp(float a,float b,float f){return a+(b-a)*f;}
    private boolean animations(){return motion&&ValueAnimator.areAnimatorsEnabled();}
    private boolean integrate(long now){float dt=lastFrame==0?1f/60:clamp((now-lastFrame)/1000f,.001f,.025f);lastFrame=now;boolean moving=false;
        if(!animations()){x=xGoal;xV=0;for(Page pg:pages){pg.pos=pg.goal;pg.v=0;}return false;}
        float damp=25-bounce*9.5f;
        xV+=( (dragging&&axis==1?330:175)*(xGoal-x)-(dragging&&axis==1?34:damp)*xV)*dt;x+=xV*dt;
        if(Math.abs(xGoal-x)<.25f&&Math.abs(xV)<1){x=xGoal;xV=0;}else moving=true;
        for(Page pg:pages){pg.v+=((dragging&&axis==2?330:170)*(pg.goal-pg.pos)-(dragging&&axis==2?34:damp)*pg.v)*dt;pg.pos+=pg.v*dt;if(Math.abs(pg.goal-pg.pos)<.25f&&Math.abs(pg.v)<1){pg.pos=pg.goal;pg.v=0;}else moving=true;}
        return moving;
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);long now=viewerFrozen?frozenAt:SystemClock.uptimeMillis();boolean moving=!viewerFrozen&&integrate(now);hits.clear();thumbs.beginFrame(this::postInvalidateOnAnimation);
        // Main gallery draws directly to the view canvas. The glass never re-records this view's display list.
        drawContent(canvas,now,true);thumbs.endFrame();drawnAt=now;frameId++;
        listener.redrawGlass();
        if(!viewerFrozen&&(moving||dragging||(animations()&&entryStart!=0&&now-entryStart<entryDuration+330)||(outgoing!=null))){postInvalidateOnAnimation();}else lastFrame=0;
    }
    /** Glass has its own clipped display list; reading cached media cannot enqueue thumbnail work. */
    void drawBackdrop(Canvas c,int left,int top,int w,int h,RenderNode blurNode){
        if(Build.VERSION.SDK_INT<31||!c.isHardwareAccelerated()||drawnAt==0||w<=0||h<=0)return;
        if(backdropFrame!=frameId||left!=backdropLeft||top!=backdropTop||w!=backdropW||h!=backdropH||!blurNode.hasDisplayList()){
            blurNode.setPosition(0,0,w,h);RecordingCanvas rc=blurNode.beginRecording(w,h);try{rc.translate(-left,-top);drawContent(rc,drawnAt,false);}finally{blurNode.endRecording();}
            blurNode.setRenderEffect(RenderEffect.createBlurEffect(dp(12),dp(12),Shader.TileMode.CLAMP));backdropFrame=frameId;backdropLeft=left;backdropTop=top;backdropW=w;backdropH=h;
        }
        c.drawRenderNode(blurNode);
    }
    private void drawContent(Canvas c,long now,boolean register){c.drawColor(bg);if(pages.isEmpty()){outgoing=null;p.setColor(muted);p.setTextSize(dp(14));p.setTextAlign(Paint.Align.CENTER);c.drawText("No media to show",getWidth()/2f,getHeight()*.43f,p);p.setTextAlign(Paint.Align.LEFT);return;}
        if(outgoing!=null){float t=clamp((now-exitStart)/230f,0,1);if(t>=1){outgoing=null;}else{p.setAlpha((int)(255*(1-t)));c.save();c.scale(1-.04f*t,1-.04f*t,getWidth()/2f,getHeight()/2f);c.drawBitmap(outgoing.bitmap,null,new RectF(0,0,getWidth(),getHeight()),p);c.restore();p.setAlpha(255);}}
        for(int i=Math.max(0,index-1);i<=Math.min(pages.size()-1,index+1);i++){float dx=i*getWidth()-x;if(Math.abs(dx)>=getWidth())continue;drawPage(c,pages.get(i),dx,i,now,register);}
    }
    private void drawPage(Canvas c,Page pg,float dx,int pageIndex,long now,boolean register){float w=getWidth(),h=getHeight(),fy=h*.42f,anchor=pg.pos+fy,g=dp(gap);float pageFactor=exp(dx/(w*.85f));ArrayList<Row> rows=pg.rows;if(rows.isEmpty())return;
        for(Row r:rows){if(r.type!=0)continue;float f=exp((r.base-anchor)/Math.max(dp(95),h*.25f))*focus*pageFactor;int n=r.slots;float trail=(w-dp(24)-5*g)/6,packed=n*trail+(n-1)*g,total=lerp(packed,w-dp(20),f);float sum=0;float[] weights=new float[n];for(int j=0;j<n;j++){float xx=(j-(n-1)/2f)*w/n+dx;weights[j]=1+f*(.30f+1.45f*exp(xx/(w*.35f))-1);sum+=weights[j];}float actual=0;for(int j=0;j<r.items.size();j++){r.widths[j]=Math.max(dp(12),(total-(n-1)*g)*weights[j]/sum);actual+=r.widths[j];}actual+=(r.items.size()-1)*g;float xx=(w-actual)/2;r.h=0;for(int j=0;j<r.items.size();j++){r.cxs[j]=xx+r.widths[j]/2;xx+=r.widths[j]+g;r.h=Math.max(r.h,r.widths[j]*1.1547f);}}
        rows.get(0).center=dp(12)+rows.get(0).h/2;for(int i=1;i<rows.size();i++){Row a=rows.get(i-1),b=rows.get(i);float sep=(a.h+b.h)/2+dp(a.type==0&&b.type==0?gap:7);if(a.type==0&&b.type==0){sep=0;for(int j=0;j<a.items.size();j++)for(int k=0;k<b.items.size();k++){float l=Math.max(a.cxs[j]-a.widths[j]/2,b.cxs[k]-b.widths[k]/2),rr=Math.min(a.cxs[j]+a.widths[j]/2,b.cxs[k]+b.widths[k]/2);if(l>rr)continue;for(float xx:new float[]{l,rr,clamp(a.cxs[j],l,rr),clamp(b.cxs[k],l,rr)}){sep=Math.max(sep,(a.widths[j]+b.widths[k])*.57735f-(Math.abs(xx-a.cxs[j])+Math.abs(xx-b.cxs[k]))*.57735f);}}sep=Math.max(dp(20),sep+g+dp(2));}b.center=a.center+sep;}
        int j=0;while(j<rows.size()-1&&rows.get(j+1).base<anchor)j++;Row a=rows.get(j),b=rows.get(Math.min(j+1,rows.size()-1));float ref=lerp(a.center,b.center,a==b?0:clamp((anchor-a.base)/(b.base-a.base),0,1));if(anchor>b.base&&a==b)ref+=anchor-b.base;ref=lerp(fy,ref,clamp(pg.pos/dp(90),0,1))+Math.min(0,pg.pos)*.7f;
        c.save();c.translate(dx,0);c.clipRect(0,0,w,h);float nearest=Float.MAX_VALUE;String date="";
        for(Row r:rows){float rel=r.center-ref;float yy=fy+rel+clamp(pg.v/6500,-.05f,.05f)*bounce*rel*exp(rel/dp(220));r.y=yy;if(yy+r.h/2<0||yy-r.h/2>h)continue;
            if(r.type!=0){float alpha=entryAlpha(now,Math.abs(yy-fy)*.3f/density,pageIndex);p.setColor(fg);p.setAlpha((int)(255*alpha));p.setTextSize(dp(r.type==3?30:r.type==2?16:12));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));float base=yy-(p.ascent()+p.descent())/2;c.drawText(r.label,dp(20),base,p);float end=dp(30)+p.measureText(r.label);p.setColor(light?0x22000000:0x33ffffff);p.setAlpha((int)(45*alpha));if(end<w-dp(20))c.drawRect(end,yy,w-dp(20),yy+dp(1),p);p.setAlpha(255);continue;}
            if(pageIndex==index&&Math.abs(yy-fy)<nearest){nearest=Math.abs(yy-fy);date=r.items.get(0).displayDate("d MMM yyyy");}
            for(int k=0;k<r.items.size();k++){Media m=r.items.get(k);float cx=r.cxs[k],tw=r.widths[k],th=tw*1.1547f;if(cx+dx+tw*.56f<=0||cx+dx-tw*.56f>=w)continue;float dist=(float)Math.hypot(cx-w/2,yy-fy)/density;float delay=Math.min(330,dist/18*stagger);float t=entryProgress(now,delay,pageIndex);if(t<=0){if(register)thumbs.visible(m);continue;}float scale=entryScale(t);float alpha=Math.min(1,t*3.8f);float shift=(1-Math.min(1,t*2))*.10f;float tx=cx+(cx-w/2)*shift,ty=yy+(yy-fy)*shift;drawTile(c,m,tx,ty,tw*scale,th*scale,alpha,register);if(register&&pageIndex==index&&alpha>.5f)hits.add(new Hit(m,new RectF(tx+dx-tw*scale/2,ty-th*scale/2,tx+dx+tw*scale/2,ty+th*scale/2)));}
        }
        c.restore();if(register&&pageIndex==index&&!date.isEmpty()&&grouped&&!date.equals(lastDate)){lastDate=date;listener.date(date);}
    }
    private float entryProgress(long now,float delay,int page){if(!animations()||entryStart==0||page!=index)return 1;return Math.max(entryFloor,clamp((now-entryStart-delay)/Math.max(1,entryDuration),0,1));}
    private float entryAlpha(long now,float delay,int page){return Math.min(1,entryProgress(now,delay,page)*3);}
    private float entryScale(float t){if(t>=1)return 1;return 1-(float)(.34*Math.exp(-6*t)*Math.cos(13*t));}
    private void roundedHex(float cx,float cy,float w,float h){float[] xs={cx,cx+w/2,cx+w/2,cx,cx-w/2,cx-w/2},ys={cy-h/2,cy-h/4,cy+h/4,cy+h/2,cy+h/4,cy-h/4};path.reset();float r=Math.min(dp(radius),w*.17f);for(int i=0;i<6;i++){int a=(i+5)%6,b=(i+1)%6;float da=(float)Math.hypot(xs[a]-xs[i],ys[a]-ys[i]),db=(float)Math.hypot(xs[b]-xs[i],ys[b]-ys[i]);float ax=xs[i]+(xs[a]-xs[i])*r/da,ay=ys[i]+(ys[a]-ys[i])*r/da,bx=xs[i]+(xs[b]-xs[i])*r/db,by=ys[i]+(ys[b]-ys[i])*r/db;if(i==0)path.moveTo(ax,ay);else path.lineTo(ax,ay);path.quadTo(xs[i],ys[i],bx,by);}path.close();}
    private void drawTile(Canvas c,Media m,float cx,float cy,float w,float h,float alpha,boolean register){if(m.uri.toString().equals(hiddenUri)){if(register)thumbs.visible(m);return;}roundedHex(cx,cy,w,h);rect.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);c.save();c.clipPath(path);p.setColor(0xff303237);p.setAlpha((int)(255*alpha));c.drawRect(rect,p);Bitmap bitmap=register?thumbs.visible(m):thumbs.peek(m);if(bitmap!=null&&!bitmap.isRecycled()){float ratio=w/h,br=bitmap.getWidth()/(float)bitmap.getHeight();if(br>ratio){int sw=(int)(bitmap.getHeight()*ratio);srcRect.set((bitmap.getWidth()-sw)/2,0,(bitmap.getWidth()+sw)/2,bitmap.getHeight());}else{int sh=(int)(bitmap.getWidth()/ratio);srcRect.set(0,(bitmap.getHeight()-sh)/2,bitmap.getWidth(),(bitmap.getHeight()+sh)/2);}p.setAlpha((int)(255*alpha));c.drawBitmap(bitmap,srcRect,rect,p);}
        if(m.video&&w>dp(38)){float yy=cy+h*.24f;String label=String.format(Locale.US,"%d:%02d",m.duration/60000,(m.duration/1000)%60);p.setTextSize(dp(9));p.setTypeface(Typeface.DEFAULT);float textW=p.measureText(label),iconW=dp(9),space=dp(3),pad=dp(4),total=textW+iconW+space+pad*2;float left=cx-total/2;p.setColor(0x99000000);p.setAlpha(Math.round(alpha*170));c.drawRoundRect(left,yy-dp(7),left+total,yy+dp(7),dp(4),dp(4),p);drawIcon(c,playIcon,left+pad+iconW/2,yy,iconW,alpha);p.setColor(Color.WHITE);p.setAlpha(Math.round(alpha*255));p.setTextAlign(Paint.Align.LEFT);c.drawText(label,left+pad+iconW+space,yy-(p.ascent()+p.descent())/2,p);}
        String key=m.uri.toString();if((selecting||favorites.contains(key))&&w>dp(28)){float bx=cx+w*.29f,by=cy+h*.16f;p.setColor(selecting&&selected.contains(key)?0xff397df4:0xaa000000);c.drawCircle(bx,by,dp(8),p);if(!selecting||selected.contains(key))drawIcon(c,selecting?checkIcon:heartIcon,bx,by,dp(11),alpha);else{p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));c.drawCircle(bx,by,dp(4),p);p.setStyle(Paint.Style.FILL);}}c.restore();p.setAlpha(255);
    }
    private void drawIcon(Canvas c,android.graphics.drawable.Drawable d,float x,float y,float size,float alpha){int half=Math.max(1,Math.round(size/2));d.setBounds(Math.round(x)-half,Math.round(y)-half,Math.round(x)+half,Math.round(y)+half);d.setAlpha(Math.round(alpha*255));d.draw(c);}
    private Media hit(float x,float y){for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.r.contains(x,y)){float xx=Math.abs(x-h.r.centerX())/(h.r.width()/2),yy=Math.abs(y-h.r.centerY())/(h.r.height()/2);if(xx<=1&&yy<=1-xx*.5f)return h.m;}}return null;}
    private float resist(float v,float min,float max){return v<min?min+(v-min)*.3f:v>max?max+(v-max)*.3f:v;}
    @Override public boolean onTouchEvent(MotionEvent e){if(pages.isEmpty())return false;if(viewerFrozen)return true;detector.onTouchEvent(e);Page pg=pages.get(index);switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN: if(tracker!=null)tracker.recycle();tracker=VelocityTracker.obtain();tracker.addMovement(e);downX=e.getX();downY=e.getY();startX=x;startY=pg.pos;axis=0;dragging=true;longPressed=false;pg.goal=pg.pos;pg.v=0;xGoal=x;xV=0;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
        case MotionEvent.ACTION_MOVE:if(tracker!=null)tracker.addMovement(e);if(longPressed)return true;float dx=e.getX()-downX,dy=e.getY()-downY;if(axis==0&&Math.hypot(dx,dy)>ViewConfiguration.get(getContext()).getScaledTouchSlop())axis=Math.abs(dx)>Math.abs(dy)*1.05?1:2;if(axis==1)xGoal=resist(startX-dx,0,(pages.size()-1)*getWidth());if(axis==2)pg.goal=resist(startY-dy,0,pg.max);invalidate();return true;
        case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:float vx=0,vy=0;if(tracker!=null){tracker.addMovement(e);tracker.computeCurrentVelocity(1000);vx=tracker.getXVelocity();vy=tracker.getYVelocity();tracker.recycle();tracker=null;}dragging=false;if(viewerFrozen){axis=0;return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){vx=vy=0;}if(axis==1){float projected=xGoal-clamp(vx,-dp(2600),dp(2600))*.13f;int next=Math.round(projected/Math.max(1,getWidth()));movePage(Math.max(-1,Math.min(1,next-index)));xV=-vx*.22f;}else{xGoal=index*getWidth();}if(axis==2){pg.goal=clamp(pg.goal-clamp(vy,-dp(3300),dp(3300))*.20f,0,pg.max);pg.v=-vy*.28f;}axis=0;invalidate();return true;
    }return true;}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=View.VISIBLE&&thumbs!=null)thumbs.clearVisible();}
    void dispose(){if(tracker!=null){tracker.recycle();tracker=null;}if(outgoing!=null){outgoing=null;}}
}

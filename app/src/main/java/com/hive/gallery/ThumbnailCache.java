package com.hive.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import java.util.*;
import java.util.concurrent.*;

/** UI-thread-owned cache; keep the current visible working set outside the LRU. */
final class ThumbnailCache {
    private static final int SIZE=320;
    private final Context context;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> cache;
    private final Map<String,Bitmap> visible=new HashMap<>();
    private final Set<String> wanted=new HashSet<>();
    private final Map<String,Pending> pending=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private boolean closed;
    private Runnable redraw;
    private boolean redrawQueued;
    private final Runnable dispatch=()->{redrawQueued=false;if(!closed&&redraw!=null)redraw.run();};
    private static final class Pending { final boolean gallery; final LinkedHashSet<Runnable> callbacks=new LinkedHashSet<>(); Future<?> future; Pending(boolean gallery){this.gallery=gallery;} }
    ThumbnailCache(Context c){context=c.getApplicationContext();int budget=(int)Math.min(96L*1024*1024,Math.max(16L*1024*1024,Runtime.getRuntime().maxMemory()/6));cache=new LruCache<String,Bitmap>(budget){@Override protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};}
    void beginFrame(Runnable redraw){this.redraw=redraw;wanted.clear();}
    Bitmap visible(Media m){String k=m.uri.toString();wanted.add(k);Bitmap b=visible.get(k);if(b==null)b=cache.get(k);if(b!=null){visible.put(k,b);return b;}request(m,null,true);return null;}
    Bitmap peek(Media m){String k=m.uri.toString();Bitmap b=visible.get(k);return b!=null?b:cache.get(k);}
    void endFrame(){visible.keySet().retainAll(wanted);Iterator<Map.Entry<String,Pending>> it=pending.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Pending> e=it.next();Pending p=e.getValue();if(p.gallery&&p.callbacks.isEmpty()&&!wanted.contains(e.getKey())&&p.future!=null&&workers.getQueue().remove(p.future)){p.future.cancel(false);it.remove();}}}
    void clearVisible(){wanted.clear();endFrame();}
    Bitmap get(Media m,Runnable callback){Bitmap b=peek(m);if(b==null)request(m,callback,false);return b;}
    private void request(Media m,Runnable callback,boolean gallery){String k=m.uri.toString();if(closed||failed.contains(k))return;Pending existing=pending.get(k);if(existing!=null){if(callback!=null&&existing.callbacks.size()<8)existing.callbacks.add(callback);return;}Pending p=new Pending(gallery);if(callback!=null)p.callbacks.add(callback);pending.put(k,p);
        try{p.future=workers.submit(()->{Bitmap b=null;try{b=context.getContentResolver().loadThumbnail(m.uri,new Size(SIZE,SIZE),null);if(b!=null){int w=b.getWidth(),h=b.getHeight();float scale=Math.min(1f,SIZE/(float)Math.max(w,h));if(scale<1f)b=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);if(b.getConfig()!=Bitmap.Config.ARGB_8888)b=b.copy(Bitmap.Config.ARGB_8888,false);}}catch(Exception ignored){}final Bitmap decoded=b;ui.post(()->complete(k,p,decoded));});}catch(RejectedExecutionException ignored){pending.remove(k);}}
    private void complete(String k,Pending p,Bitmap b){if(closed||pending.get(k)!=p)return;pending.remove(k);if(b==null)failed.add(k);else{cache.put(k,b);if(wanted.contains(k))visible.put(k,b);}if(wanted.contains(k)&&!redrawQueued){redrawQueued=true;ui.post(dispatch);}for(Runnable callback:p.callbacks)callback.run();}
    void resetFailures(){failed.clear();}
    void close(){closed=true;workers.shutdownNow();ui.removeCallbacksAndMessages(null);pending.clear();failed.clear();visible.clear();wanted.clear();cache.evictAll();}
}

package com.hive.gallery;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.util.Size;
import java.util.*;
import java.util.concurrent.*;

final class ThumbnailCache {
    private final Context context;
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(40*1024*1024){@Override protected int sizeOf(String k,Bitmap v){return v.getAllocationByteCount();}};
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final Map<String,List<Runnable>> pending=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    private volatile boolean closed;
    ThumbnailCache(Context c){context=c.getApplicationContext();}
    Bitmap get(Media m,Runnable redraw){
        String k=m.uri.toString(); synchronized(pending){Bitmap b=cache.get(k);if(b!=null||closed||failed.contains(k))return b;
            List<Runnable> callbacks=pending.get(k);if(callbacks!=null){if(callbacks.size()<12)callbacks.add(redraw);return null;}
            ArrayList<Runnable> list=new ArrayList<>();list.add(redraw);pending.put(k,list);
        }
        try{workers.execute(()->{Bitmap decoded=null;try{decoded=context.getContentResolver().loadThumbnail(m.uri,new Size(320,320),null);if(decoded!=null&&decoded.getConfig()==Bitmap.Config.HARDWARE)decoded=decoded.copy(Bitmap.Config.ARGB_8888,false);}catch(Exception ignored){}
            List<Runnable> callbacks;synchronized(pending){if(!closed&&decoded!=null)cache.put(k,decoded);else failed.add(k);callbacks=pending.remove(k);}if(!closed&&callbacks!=null)for(Runnable r:callbacks)r.run();});}catch(RejectedExecutionException ignored){synchronized(pending){pending.remove(k);}}
        return null;
    }
    void resetFailures(){synchronized(pending){failed.clear();}}
    void close(){closed=true;workers.shutdownNow();synchronized(pending){pending.clear();failed.clear();cache.evictAll();}}
}

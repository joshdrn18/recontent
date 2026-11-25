package com.carduniverse.game;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.ProgressBar;

/** Bounded native meter: never draws beyond its measured content area. */
public final class RealmMeter extends ProgressBar {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF(), fill = new RectF();
    public RealmMeter(Context context) {
        super(context, null, android.R.attr.progressBarStyleHorizontal);
        setPadding(0,0,0,0);
        setMinimumHeight(0);
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float density=getResources().getDisplayMetrics().density;
        float inset=Math.min(density,Math.min(w,h)/4f);
        float thickness=Math.min(h-2*inset,8*density);
        if(thickness<=0||w<=2*inset)return;
        track.set(inset,(h-thickness)/2,w-inset,(h+thickness)/2);
        float radius=thickness/2;
        ink.setShader(null);ink.setStyle(Paint.Style.FILL);ink.setColor(Color.rgb(7,12,24));
        canvas.drawRoundRect(track,radius,radius,ink);
        float fraction=Math.max(0f,Math.min(1f,getProgress()/(float)Math.max(1,getMax())));
        ColorStateList tint=getProgressTintList();
        int accent=tint==null?Color.rgb(226,181,101):tint.getColorForState(getDrawableState(),tint.getDefaultColor());
        if(fraction>0){
            fill.set(track.left,track.top,track.left+track.width()*fraction,track.bottom);
            canvas.save();canvas.clipRect(fill);
            ink.setShader(new LinearGradient(0,track.top,0,track.bottom,light(accent),accent,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(track,radius,radius,ink);canvas.restore();ink.setShader(null);
        }
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.min(density,thickness/3));
        ink.setColor(Color.argb(100,Color.red(accent),Color.green(accent),Color.blue(accent)));
        canvas.drawRoundRect(track,radius,radius,ink);ink.setStyle(Paint.Style.FILL);
    }
    private int light(int c){return Color.rgb(Math.min(255,Color.red(c)+38),Math.min(255,Color.green(c)+38),Math.min(255,Color.blue(c)+38));}
}

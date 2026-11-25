package com.carduniverse.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Perspective battlefield painted behind actual server-driven card and HP controls. */
public final class ArenaTableView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean motion;
    public ArenaTableView(Context c,boolean motion){super(c);this.motion=motion;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private void line(Canvas c,float x,float y,float a,float b,int color,float stroke){p.setColor(color);p.setStrokeWidth(stroke);c.drawLine(x,y,a,b,p);}
    private void quad(Canvas c,int color,float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4){Path q=new Path();q.moveTo(x1,y1);q.lineTo(x2,y2);q.lineTo(x3,y3);q.lineTo(x4,y4);q.close();p.setColor(color);c.drawPath(q,p);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        p.setShader(new LinearGradient(0,0,0,h,new int[]{Color.rgb(40,26,43),Color.rgb(9,20,33),Color.rgb(17,46,49)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        c.save();c.scale(w/600,h/600);
        quad(c,Color.rgb(48,48,59),92,25,508,25,588,570,12,570);
        quad(c,Color.rgb(24,34,47),104,31,496,31,571,553,29,553);
        for(int i=0;i<9;i++){float x=i/8f;line(c,104+392*x,31,29+542*x,553,0x337ba6bc,1);}
        for(int i=0;i<12;i++){float t=i/11f,y=31+522*t*t;float left=104-75*t*t,right=496+75*t*t;line(c,left,y,right,y,0x337ba6bc,1);}
        p.setShader(new RadialGradient(300,300,210,new int[]{0x6669c3ca,0x22254c6b,0},null,Shader.TileMode.CLAMP));c.drawCircle(300,300,210,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x999dc5d6);c.drawOval(new RectF(196,254,404,346),p);p.setColor(0x77eac78b);c.drawOval(new RectF(210,263,390,337),p);p.setStyle(Paint.Style.FILL);
        float t=motion?(android.os.SystemClock.uptimeMillis()%6000)/6000f:0;
        for(int i=0;i<12;i++){double a=(i/12f+t)*Math.PI*2;float x=300+(float)Math.cos(a)*104,y=300+(float)Math.sin(a)*46;p.setColor(0xaadcc795);c.drawCircle(x,y,2.2f,p);}
        line(c,105,31,29,553,0x99c5a26a,2);line(c,495,31,571,553,0x99c5a26a,2);
        c.restore();if(motion&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(90);
    }
}

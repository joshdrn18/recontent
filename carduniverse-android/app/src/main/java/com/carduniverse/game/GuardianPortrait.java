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

/** Local, boss-specific placeholder art; no network request or bitmap required. */
public final class GuardianPortrait extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String slug;
    private final int accent;
    private final int dark=Color.rgb(17,22,39);
    public GuardianPortrait(Context c,String slug){super(c);this.slug=slug==null?"":slug;this.accent=color(this.slug);setContentDescription(this.slug.replace('-',' ')+" guardian portrait");}
    public static int color(String slug){
        switch(slug==null?"":slug){
            case "ancient-dragon":return Color.rgb(238,153,68);
            case "frost-titan":return Color.rgb(108,213,250);
            case "ashen-queen":return Color.rgb(248,103,86);
            case "storm-colossus":return Color.rgb(174,159,255);
            case "eclipse-seraph":return Color.rgb(205,112,235);
            case "celestial-arbiter":return Color.rgb(249,211,136);
            default:return Color.rgb(117,193,205);
        }
    }
    private int a(int c,int alpha){return Color.argb(alpha,Color.red(c),Color.green(c),Color.blue(c));}
    private void poly(Canvas c,int color,float... xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);path.close();p.setColor(color);c.drawPath(path,p);}
    private void oval(Canvas c,int color,float x,float y,float rx,float ry){p.setColor(color);c.drawOval(new RectF(x-rx,y-ry,x+rx,y+ry),p);}
    private void ring(Canvas c,float x,float y,float r,int color,float sw){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.FILL);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()<=0||getHeight()<=0)return;
        float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,w,h,new int[]{dark,a(accent,180),Color.rgb(5,9,21)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        float scale=Math.min(w/320f,h/180f);c.save();c.translate(w/2,h/2);c.scale(scale,scale);
        p.setShader(new RadialGradient(0,0,100,new int[]{a(accent,120),a(accent,24),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));c.drawCircle(0,0,100,p);p.setShader(null);
        ring(c,0,0,72,a(accent,100),1.2f);ring(c,0,0,65,a(accent,65),1);
        for(int i=0;i<12;i++){double angle=i*Math.PI/6;float x=(float)Math.cos(angle)*72,y=(float)Math.sin(angle)*72;oval(c,a(accent,190),x,y,2,2);}
        switch(slug){
            case "ancient-dragon":
                for(int side:new int[]{-1,1}){poly(c,dark,side*12,-18,side*48,-67,side*104,-56,side*80,1,side*110,25,side*39,36);poly(c,a(accent,140),side*18,-20,side*48,-55,side*91,-47,side*56,-3);poly(c,accent,side*13,-32,side*42,-74,side*29,-29);}
                poly(c,dark,-36,62,-24,-15,-32,-39,0,-59,32,-39,24,-15,36,62);
                poly(c,accent,-23,-29,-8,-37,0,-25,8,-37,23,-29,12,-11,0,-3,-12,-11);break;
            case "frost-titan":
                poly(c,dark,-66,65,-55,-22,-24,-42,-24,-63,0,-79,24,-63,24,-42,55,-22,66,65);
                for(int side:new int[]{-1,1}){poly(c,accent,side*24,-42,side*57,-63,side*49,-21,side*72,-35,side*60,15,side*30,6);poly(c,a(accent,180),side*15,8,side*43,4,side*37,58,side*17,50);}
                poly(c,accent,-19,-52,0,-66,19,-52,12,-30,-12,-30);poly(c,accent,0,-5,13,15,0,40,-13,15);break;
            case "ashen-queen":
                poly(c,dark,-65,70,-34,8,-15,-21,-20,-44,0,-60,20,-44,15,-21,34,8,65,70);
                poly(c,accent,-25,-45,-34,-75,-12,-60,0,-83,12,-60,34,-75,25,-45);
                for(int side:new int[]{-1,1}){poly(c,a(accent,170),side*16,-8,side*53,-31,side*40,3,side*64,23,side*32,39);poly(c,accent,side*25,40,side*40,22,side*49,59,side*36,67);}
                oval(c,accent,0,14,11,15);break;
            case "storm-colossus":
                poly(c,dark,-75,68,-70,-13,-39,-39,-20,-39,-20,-61,0,-74,20,-61,20,-39,39,-39,70,-13,75,68);
                for(int side:new int[]{-1,1}){poly(c,a(accent,180),side*30,-32,side*70,-48,side*82,-7,side*45,5);poly(c,accent,side*39,8,side*59,-3,side*52,18,side*68,17,side*41,50,side*47,28,side*34,30);}
                poly(c,accent,4,-25,-16,7,0,7,-6,36,21,-1,7,-1);break;
            case "eclipse-seraph":
                ring(c,0,-44,25,accent,3);oval(c,dark,0,-44,21,21);
                for(int side:new int[]{-1,1})for(int i=0;i<3;i++){float y=-48+i*20;poly(c,i==0?accent:a(accent,160),side*13,y+15,side*(90+i*7),y-13,side*(70-i*6),y+25,side*24,y+43);}
                poly(c,dark,-34,66,-21,-12,-18,-40,0,-55,18,-40,21,-12,34,66);poly(c,accent,0,-20,9,-5,0,14,-9,-5);break;
            case "celestial-arbiter":
                ring(c,0,-44,34,accent,3);ring(c,0,-44,27,a(accent,150),1);
                for(int side:new int[]{-1,1}){poly(c,accent,side*22,-24,side*72,-61,side*61,-13,side*94,-7,side*31,26);poly(c,dark,side*16,0,side*48,-12,side*55,43,side*21,65);}
                poly(c,dark,-33,70,-17,-10,-21,-44,0,-67,21,-44,17,-10,33,70);
                poly(c,accent,-18,-43,0,-56,18,-43,10,-25,-10,-25);
                poly(c,accent,0,-6,12,13,0,33,-12,13);break;
            default:poly(c,dark,0,-67,51,-20,40,51,0,73,-40,51,-51,-20);poly(c,accent,0,-39,24,0,0,39,-24,0);
        }
        c.restore();
    }
}

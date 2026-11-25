package com.carduniverse.game;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public final class RaidBossView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent; private final boolean reduceMotion;
    private float idle=0f,hit=0f; private ValueAnimator idleAnimator,hitAnimator;
    public RaidBossView(Context context,int accent,boolean reduceMotion){super(context);this.accent=accent;this.reduceMotion=reduceMotion;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("Animated raid guardian");}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(!reduceMotion&&idleAnimator==null){idleAnimator=ValueAnimator.ofFloat(0f,1f);idleAnimator.setDuration(2600);idleAnimator.setRepeatCount(ValueAnimator.INFINITE);idleAnimator.addUpdateListener(a->{idle=(float)a.getAnimatedValue();invalidate();});idleAnimator.start();}}
    @Override protected void onDetachedFromWindow(){if(idleAnimator!=null){idleAnimator.cancel();idleAnimator=null;}if(hitAnimator!=null){hitAnimator.cancel();hitAnimator=null;}super.onDetachedFromWindow();}
    public void playStrike(Runnable done){if(hitAnimator!=null)hitAnimator.cancel();if(reduceMotion){hit=1f;invalidate();if(done!=null)done.run();return;}
        hitAnimator=ValueAnimator.ofFloat(0f,1f);hitAnimator.setDuration(820);hitAnimator.setInterpolator(new AccelerateDecelerateInterpolator());hitAnimator.addUpdateListener(a->{hit=(float)a.getAnimatedValue();invalidate();});
        hitAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(done!=null)done.run();}});hitAnimator.start();}
    private int alpha(int color,int a){return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;float scale=Math.min(w/250f,h/210f),bob=reduceMotion?0f:(float)Math.sin(idle*6.283f)*2.8f;
        c.save();c.translate(w*.5f,h*.56f+bob*scale);c.scale(scale,scale);
        p.setShader(new RadialGradient(0,-24,145,new int[]{alpha(accent,82),alpha(accent,24),Color.TRANSPARENT},new float[]{0f,.56f,1f},Shader.TileMode.CLAMP));c.drawCircle(0,-24,145,p);p.setShader(null);
        p.setColor(Color.rgb(23,27,46));Path tail=new Path();tail.moveTo(24,42);tail.cubicTo(79,31,105,64,124,46);tail.cubicTo(151,20,142,-7,120,-3);tail.cubicTo(145,28,96,83,40,78);tail.close();c.drawPath(tail,p);
        Path wing=new Path();wing.moveTo(-24,-50);wing.cubicTo(-69,-123,-150,-140,-144,-56);wing.lineTo(-111,-29);wing.lineTo(-143,10);wing.cubicTo(-87,27,-54,9,-25,21);wing.close();p.setColor(Color.rgb(48,39,67));c.drawPath(wing,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);p.setColor(alpha(accent,170));c.drawLine(-25,-48,-130,-90,p);c.drawLine(-20,-23,-130,-10,p);p.setStyle(Paint.Style.FILL);
        c.save();c.scale(-1,1);c.drawPath(wing,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);p.setColor(alpha(accent,170));c.drawLine(-25,-48,-130,-90,p);c.drawLine(-20,-23,-130,-10,p);p.setStyle(Paint.Style.FILL);c.restore();
        p.setColor(Color.rgb(35,30,49));c.drawOval(new RectF(-57,-32,57,88),p);
        p.setColor(Color.rgb(67,43,65));Path chest=new Path();chest.moveTo(-38,-31);chest.quadTo(-68,-72,-42,-96);chest.lineTo(42,-96);chest.quadTo(68,-72,38,-31);chest.close();c.drawPath(chest,p);
        p.setColor(Color.rgb(105,63,86));Path head=new Path();head.moveTo(-46,-88);head.lineTo(-56,-111);head.lineTo(-24,-107);head.lineTo(-7,-128);head.lineTo(8,-107);head.lineTo(37,-116);head.lineTo(52,-93);head.lineTo(43,-62);head.quadTo(0,-42,-42,-62);head.close();c.drawPath(head,p);
        p.setColor(Color.rgb(140,81,105));Path horn=new Path();horn.moveTo(-35,-103);horn.lineTo(-64,-151);horn.lineTo(-18,-119);horn.close();c.drawPath(horn,p);c.save();c.scale(-1,1);c.drawPath(horn,p);c.restore();
        p.setColor(Color.rgb(216,183,149));c.drawOval(new RectF(-35,-78,-9,-69),p);c.drawOval(new RectF(9,-78,35,-69),p);
        p.setColor(accent);c.drawCircle(-21,-74,4.5f,p);c.drawCircle(21,-74,4.5f,p);p.setColor(Color.WHITE);c.drawCircle(-21,-74,1.5f,p);c.drawCircle(21,-74,1.5f,p);
        p.setColor(Color.rgb(80,59,78));c.drawRoundRect(new RectF(-43,6,-24,53),7,7,p);c.drawRoundRect(new RectF(24,6,43,53),7,7,p);
        p.setColor(Color.rgb(172,138,123));c.drawRoundRect(new RectF(-43,41,-18,58),6,6,p);c.drawRoundRect(new RectF(18,41,43,58),6,6,p);
        p.setColor(alpha(accent,110));for(int i=0;i<4;i++)c.drawArc(new RectF(-37,-23+i*18,37,0+i*18),0,180,false,p);
        if(hit>0f){float s=Math.min(1f,hit*1.65f);int a=(int)(255*Math.max(0f,1f-Math.abs(hit-.48f)*1.8f));p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(10);p.setColor(Color.argb(a,255,238,178));c.drawLine(-112+s*55,46,112-s*28,-104,p);p.setStrokeWidth(3.5f);p.setColor(Color.argb(Math.min(255,a+35),255,255,255));c.drawLine(-102+s*55,55,122-s*28,-94,p);p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
            if(hit>.34f&&hit<.72f){p.setColor(Color.argb((int)(a*.55f),255,247,221));c.drawCircle(20,-14,18+hit*10,p);}
            for(int i=0;i<6;i++){float x=(float)Math.cos(i*Math.PI/3)*(32+hit*55),y=(float)Math.sin(i*Math.PI/3)*(22+hit*43);p.setColor(Color.argb(a,255,202,130));c.drawCircle(x,y,2.5f,p);}}
        c.restore();}
}
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
import android.graphics.Typeface;
import android.view.View;

/**
 * A lightweight, animated native game scene used as the visual stage for CardUniverse modes.
 * It is intentionally drawn with Canvas so the game does not depend on a network image being
 * available before the first frame is shown.
 */
public final class RealmSceneView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final String mode;
    private final String title;
    private final String subtitle;
    private final int progress;
    private final int total;
    private boolean attached;
    private long startAt = System.currentTimeMillis();

    private static final int INK = Color.rgb(6, 10, 21);
    private static final int WHITE = Color.rgb(245, 246, 255);
    private static final int GOLD = Color.rgb(238, 193, 112);
    private static final int CYAN = Color.rgb(94, 224, 223);
    private static final int PURPLE = Color.rgb(165, 132, 255);
    private static final int RED = Color.rgb(255, 119, 125);

    public RealmSceneView(Context context, String mode, String title, String subtitle,
                          int progress, int total) {
        super(context);
        this.mode = mode == null ? "home" : mode;
        this.title = title == null ? "CARDUNIVERSE" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.progress = Math.max(0, progress);
        this.total = Math.max(0, total);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(false);
        setFocusable(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / 960f;
        float sy = getHeight() / 300f;
        canvas.save();
        canvas.scale(sx, sy);
        float t = ((System.currentTimeMillis() - startAt) % 9000L) / 9000f;
        drawBase(canvas, t);
        switch (mode) {
            case "adventure": drawAdventure(canvas, t); break;
            case "dungeon": drawDungeon(canvas, t); break;
            case "raid": drawRaid(canvas, t); break;
            case "arena": drawArena(canvas, t); break;
            case "summon": drawSummon(canvas, t); break;
            case "archive": drawArchive(canvas, t); break;
            case "binder": drawBinder(canvas, t); break;
            case "records": drawRecords(canvas, t); break;
            case "market": drawMarket(canvas, t); break;
            case "trade": drawTrade(canvas, t); break;
            case "guild": drawGuild(canvas, t); break;
            case "home":
            default: drawHome(canvas, t); break;
        }
        drawVignette(canvas);
        drawHud(canvas);
        canvas.restore();
        if (attached) postInvalidateDelayed(45L);
    }

    private void drawBase(Canvas c, float t) {
        p.setShader(new LinearGradient(0, 0, 0, 300,
                new int[]{Color.rgb(23, 35, 69), Color.rgb(13, 22, 42), INK},
                null, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, 960, 300, p);
        p.setShader(null);

        // Distant star field, with a soft moving pulse.
        for (int i = 0; i < 54; i++) {
            float x = (i * 173 + 31) % 960;
            float y = 15 + ((i * 83 + 17) % 172);
            float pulse = 0.35f + 0.65f * (float)Math.abs(Math.sin(t * 6.28f + i * 0.71f));
            p.setColor(Color.argb((int)(48 + pulse * 125), 202, 220, 255));
            c.drawCircle(x, y, i % 7 == 0 ? 1.8f : 0.9f, p);
        }
        glow(c, 770, 62, 88, Color.rgb(74, 105, 179), 0.28f);
        glow(c, 205, 85, 62, Color.rgb(115, 81, 174), 0.18f);
        drawHorizon(c);
    }

    private void drawHorizon(Canvas c) {
        Path back = new Path();
        back.moveTo(0, 205);
        back.cubicTo(180, 144, 255, 217, 407, 177);
        back.cubicTo(565, 130, 688, 215, 960, 151);
        back.lineTo(960, 300); back.lineTo(0, 300); back.close();
        fill(c, back, Color.rgb(22, 35, 55));
        Path front = new Path();
        front.moveTo(0, 245);
        front.cubicTo(190, 195, 330, 258, 489, 222);
        front.cubicTo(675, 183, 813, 251, 960, 207);
        front.lineTo(960, 300); front.lineTo(0, 300); front.close();
        fill(c, front, Color.rgb(12, 22, 36));
    }

        private void drawHome(Canvas c, float t) {
        // A layered sky citadel with a clear hero focal point.
        drawMountains(c, Color.rgb(42, 53, 89), Color.rgb(19, 31, 49));
        drawCastle(c, 492, 174, 1.12f, Color.rgb(67, 88, 137));
        drawFloatingIsland(c, 142, 199, 0.72f, t);
        drawFloatingIsland(c, 820, 194, 0.56f, t + 0.45f);
        drawBridge(c, 80, 239, 890, 220);
        drawTrees(c, 0, 240, 0.78f);
        glow(c, 500, 138, 150, GOLD, 0.20f);
        glow(c, 728, 196, 112, CYAN, 0.23f);
        drawHeroKnight(c, 730, 270, 0.90f, t);
        drawFloorRays(c, t, CYAN);
    }

    private void drawHeroKnight(Canvas c, float x, float y, float scale, float t) {
        c.save();
        c.translate(x, y + (float)Math.sin(t * 6.28f) * 2.2f);
        c.scale(scale, scale);
        glow(c, 0, -96, 92, CYAN, 0.28f);

        // Long split cape and silhouette layers create depth against the distant skyline.
        Path cape = new Path();
        cape.moveTo(-23, -142);
        cape.cubicTo(-82, -91, -83, -12, -42, 8);
        cape.lineTo(2, -39);
        cape.lineTo(40, 12);
        cape.cubicTo(84, -29, 57, -110, 23, -145);
        cape.close();
        fill(c, cape, Color.rgb(24, 37, 74));
        Path capeLight = new Path();
        capeLight.moveTo(-20, -127);
        capeLight.cubicTo(-54, -85, -57, -23, -39, -2);
        capeLight.lineTo(-19, -40);
        capeLight.close();
        fill(c, capeLight, Color.rgb(48, 83, 145));

        // Boots and greaves.
        p.setColor(Color.rgb(22, 30, 49));
        c.drawRoundRect(new RectF(-30, -48, -7, 9), 7, 7, p);
        c.drawRoundRect(new RectF(9, -47, 34, 10), 7, 7, p);
        p.setColor(Color.rgb(137, 164, 202));
        c.drawRoundRect(new RectF(-29, -45, -10, -18), 5, 5, p);
        c.drawRoundRect(new RectF(12, -43, 32, -16), 5, 5, p);

        // Breastplate with bright chest gem.
        Path torso = new Path();
        torso.moveTo(-28, -145); torso.lineTo(28, -145);
        torso.lineTo(42, -75); torso.lineTo(0, -51);
        torso.lineTo(-42, -75); torso.close();
        fill(c, torso, Color.rgb(93, 119, 158));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.rgb(215, 222, 242));
        c.drawPath(torso, p); p.setStyle(Paint.Style.FILL);
        Path chest = new Path();
        chest.moveTo(-18, -132); chest.lineTo(18, -132); chest.lineTo(25, -81);
        chest.lineTo(0, -67); chest.lineTo(-25, -81); chest.close();
        fill(c, chest, Color.rgb(37, 60, 105));
        glow(c, 0, -105, 27, GOLD, 0.5f);
        p.setColor(GOLD); c.drawCircle(0, -106, 7, p);
        p.setColor(Color.rgb(228, 208, 155));
        Path plate = new Path();
        plate.moveTo(-27, -140); plate.lineTo(-51, -127); plate.lineTo(-42, -96);
        plate.lineTo(-23, -103); plate.close();
        fill(c, plate, Color.rgb(184, 190, 211));
        Path plateR = new Path();
        plateR.moveTo(27, -140); plateR.lineTo(51, -127); plateR.lineTo(42, -96);
        plateR.lineTo(23, -103); plateR.close();
        fill(c, plateR, Color.rgb(184, 190, 211));

        // Arms and gauntlets.
        p.setStrokeWidth(13); p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.rgb(67, 88, 128));
        c.drawLine(-32, -128, -54, -72, p);
        c.drawLine(32, -128, 50, -79, p);
        p.setStrokeWidth(7); p.setColor(Color.rgb(197, 207, 226));
        c.drawLine(-53, -77, -47, -58, p);
        c.drawLine(49, -84, 58, -67, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        // Head, hair and crown crest.
        p.setColor(Color.rgb(220, 194, 169));
        c.drawOval(new RectF(-20, -190, 20, -145), p);
        p.setColor(Color.rgb(25, 31, 53));
        Path hair = new Path();
        hair.moveTo(-22, -166); hair.lineTo(-26, -192); hair.lineTo(-9, -205);
        hair.lineTo(12, -202); hair.lineTo(26, -185); hair.lineTo(18, -158);
        hair.lineTo(8, -180); hair.lineTo(-4, -166); hair.close();
        fill(c, hair, Color.rgb(30, 40, 65));
        p.setColor(GOLD); p.setStrokeWidth(3);
        c.drawLine(-17, -189, -3, -206, p);
        c.drawLine(3, -207, 19, -189, p);
        p.setColor(CYAN); c.drawCircle(0, -194, 4, p);

        // Raised energy blade with a subtle animated trail.
        c.save();
        c.rotate(-13 + (float)Math.sin(t * 6.28f) * 2f, 57, -68);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(8); p.setColor(Color.rgb(49, 75, 122));
        c.drawLine(57, -68, 111, -210, p);
        p.setStrokeWidth(4); p.setColor(Color.rgb(129, 226, 255));
        c.drawLine(57, -68, 111, -210, p);
        p.setStrokeWidth(1.5f); p.setColor(Color.WHITE);
        c.drawLine(61, -82, 108, -202, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        glow(c, 111, -210, 30, CYAN, 0.9f);
        c.restore();
        c.restore();
    }



    private void drawAdventure(Canvas c, float t) {
        drawCastle(c, 795, 164, 0.9f, Color.rgb(80, 94, 142));
        drawFloatingIsland(c, 690, 211, 0.72f, t);
        drawBridge(c, 80, 234, 870, 211);
        // A winding campaign path with discoverable encounter stones.
        Path road = new Path();
        road.moveTo(85, 232);
        road.cubicTo(206, 194, 212, 249, 339, 220);
        road.cubicTo(455, 193, 468, 240, 574, 215);
        road.cubicTo(676, 190, 712, 221, 854, 202);
        p.setColor(Color.argb(120, 229, 193, 132));
        p.setStrokeWidth(4); p.setStyle(Paint.Style.STROKE); p.setPathEffect(null);
        c.drawPath(road, p); p.setStyle(Paint.Style.FILL);
        float[][] nodes = {{104,226},{254,217},{402,218},{555,214},{712,210},{858,201}};
        for(int i=0;i<nodes.length;i++){
            float x=nodes[i][0], y=nodes[i][1];
            boolean cleared = total > 0 && i < Math.min(nodes.length, Math.round(progress * nodes.length / (float)total));
            boolean active = i == Math.min(nodes.length-1, Math.max(0, progress == 0 ? 0 : (int)(progress * nodes.length / (float)Math.max(1,total))));
            int color=cleared?CYAN:active?GOLD:Color.rgb(104,119,151);
            glow(c,x,y,active?35:22,color,active?0.68f:0.35f);
            p.setColor(Color.rgb(28,39,59)); c.drawCircle(x,y,14,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(color);c.drawCircle(x,y,14,p);p.setStyle(Paint.Style.FILL);
            p.setColor(cleared?CYAN:WHITE);c.drawCircle(x,y,3.5f,p);
        }
        drawTrees(c,0,238,0.75f);
    }

    private void drawDungeon(Canvas c, float t) {
        // Stone corridor perspective and a sealed gate at the far end.
        p.setShader(new LinearGradient(0,0,0,300,new int[]{Color.rgb(37,38,65),Color.rgb(14,18,34),Color.rgb(5,8,16)},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,960,300,p);p.setShader(null);
        for(int i=0;i<6;i++){
            float x=i*190-90;
            Path pillar=new Path();pillar.moveTo(x,30);pillar.lineTo(x+58,30);pillar.lineTo(x+76,300);pillar.lineTo(x-18,300);pillar.close();
            fill(c,pillar, i%2==0?Color.rgb(34,43,64):Color.rgb(24,31,50));
            p.setColor(Color.argb(65,162,177,214));p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);
            c.drawLine(x+21,35,x+8,286,p);p.setStyle(Paint.Style.FILL);
        }
        p.setColor(Color.rgb(65,66,93));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(16);
        RectF arch=new RectF(350,32,610,290);c.drawArc(arch,180,180,false,p);
        c.drawLine(350,160,350,290,p);c.drawLine(610,160,610,290,p);p.setStyle(Paint.Style.FILL);
        glow(c,480,190,108,PURPLE,0.62f+(float)Math.sin(t*6.28f)*0.09f);
        for(int i=0;i<4;i++){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
            p.setColor(Color.argb(180-25*i,185,145,255));
            c.drawCircle(480,190,36+i*17+(float)Math.sin(t*6.28f+i)*3,p);p.setStyle(Paint.Style.FILL);
        }
        p.setColor(Color.rgb(148,127,192));
        RectF door=new RectF(420,125,540,290);c.drawRoundRect(door,60,60,p);
        p.setColor(Color.rgb(21,20,41));RectF inset=new RectF(432,142,528,290);c.drawRoundRect(inset,48,48,p);
        glow(c,480,212,50,CYAN,0.75f);
        p.setColor(Color.rgb(185,228,255));c.drawCircle(480,207,7,p);
        drawFloorRays(c, t, PURPLE);
    }

    private void drawRaid(Canvas c, float t) {
        drawMountains(c, Color.rgb(55,32,53), Color.rgb(24,22,43));
        glow(c,700,140,180,RED,0.35f);
        // Colossal serpent/dragon body silhouette.
        Path body = new Path();
        body.moveTo(560,214);
        body.cubicTo(596,84,733,70,762,153);
        body.cubicTo(789,219,704,249,664,194);
        body.cubicTo(630,151,677,113,723,132);
        body.cubicTo(760,149,733,188,706,177);
        body.cubicTo(665,162,646,224,696,250);
        body.cubicTo(767,286,858,245,895,203);
        body.lineTo(960,300);body.lineTo(520,300);body.close();
        fill(c,body,Color.rgb(34,24,44));
        // Horned head and plated neck.
        Path head=new Path();head.moveTo(658,112);head.lineTo(631,69);head.lineTo(684,91);
        head.lineTo(716,47);head.lineTo(718,101);head.lineTo(776,121);head.lineTo(748,158);
        head.lineTo(690,157);head.close();fill(c,head,Color.rgb(83,43,65));
        p.setColor(GOLD);c.drawCircle(724,125,5,p);glow(c,724,125,22,RED,0.9f);
        p.setColor(Color.rgb(184,92,114));p.setStrokeWidth(3);p.setStyle(Paint.Style.STROKE);
        c.drawLine(662,147,614,172,p);c.drawLine(683,159,637,190,p);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<7;i++){
            float x=566+i*42,y=204+(float)Math.sin(t*6.28f+i*.7f)*6;
            Path spike=new Path();spike.moveTo(x,y);spike.lineTo(x+11,y-30);spike.lineTo(x+24,y+2);spike.close();
            fill(c,spike,Color.rgb(105,55,78));
        }
        drawFloorRays(c,t,RED);
    }

    private void drawArena(Canvas c, float t) {
        // Coliseum tiers.
        p.setColor(Color.rgb(47,48,76));
        Path stands=new Path();stands.moveTo(55,213);stands.lineTo(85,74);stands.quadTo(480,-5,875,74);stands.lineTo(905,213);stands.close();fill(c,stands,Color.rgb(38,47,70));
        for(int i=0;i<9;i++){
            float x=135+i*86;
            p.setColor(Color.argb(110,180,193,225));p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);
            c.drawLine(x,76,x-12,201,p);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(65,228,205,161));c.drawLine(x+5,94,x-5,203,p);
        }
        glow(c,480,194,220,GOLD,0.28f);
        p.setColor(Color.rgb(68,57,65));RectF floor=new RectF(168,158,794,290);c.drawOval(floor,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(Color.rgb(145,119,93));c.drawOval(new RectF(220,180,742,282),p);
        p.setStrokeWidth(2);p.setColor(Color.argb(100,234,208,162));c.drawOval(new RectF(300,201,662,267),p);p.setStyle(Paint.Style.FILL);
        drawFighter(c,310,194,1f,CYAN,true,t);
        drawFighter(c,650,190,1.05f,RED,false,t);
        // Animated crossing blade trails make the arena read as a live encounter.
        float clash = 0.5f + 0.5f * (float)Math.sin(t * 6.28f * 1.8f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(4 + clash * 3);
        p.setColor(Color.argb((int)(80 + clash * 140), 153, 225, 255));
        c.drawArc(new RectF(410,116,548,252),205,82,false,p);
        p.setStrokeWidth(2);p.setColor(Color.argb((int)(60+clash*130),255,207,142));
        c.drawArc(new RectF(420,125,540,244),18,82,false,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
        glow(c,480,190,44,clash>0.5f?GOLD:CYAN,0.25f+clash*0.52f);
        p.setColor(Color.argb(210,240,221,184));p.setStrokeWidth(3);
        c.drawLine(474,173,488,208,p);c.drawLine(488,173,474,208,p);
        drawFloorRays(c,t,GOLD);
    }

        private void drawFighter(Canvas c,float x,float y,float scale,int color,boolean left,float t){
        c.save();
        c.translate(x, y + (float)Math.sin(t * 6.28f + (left ? 0 : 1.6f)) * 2f);
        c.scale(scale,scale);
        float facing = left ? 1f : -1f;
        glow(c,0,-49,74,color,0.30f);

        // Cast shadow and armoured legs.
        p.setColor(Color.argb(105,0,0,0));
        c.drawOval(new RectF(-45,48,48,70),p);
        p.setColor(Color.rgb(24,29,45));
        c.drawRoundRect(new RectF(-26,-14,-5,53),7,7,p);
        c.drawRoundRect(new RectF(7,-14,28,53),7,7,p);
        p.setColor(Color.rgb(111,128,161));
        c.drawRoundRect(new RectF(-25,4,-7,37),5,5,p);
        c.drawRoundRect(new RectF(9,4,27,37),5,5,p);
        p.setColor(Color.rgb(184,191,210));
        c.drawRoundRect(new RectF(-31,42,-2,57),5,5,p);
        c.drawRoundRect(new RectF(4,42,34,57),5,5,p);

        // Cape and torso armour.
        Path cape=new Path();
        cape.moveTo(-19,-105);cape.lineTo(left?-65:65,36);cape.lineTo(0,18);
        cape.lineTo(28,41);cape.lineTo(18,-106);cape.close();
        fill(c,cape,color);
        Path torso=new Path();
        torso.moveTo(-28,-105);torso.lineTo(27,-105);torso.lineTo(36,-24);
        torso.lineTo(0,-4);torso.lineTo(-36,-24);torso.close();
        fill(c,torso,Color.rgb(79,92,121));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
        p.setColor(Color.rgb(206,211,227));c.drawPath(torso,p);p.setStyle(Paint.Style.FILL);
        Path breast=new Path();
        breast.moveTo(-18,-95);breast.lineTo(18,-95);breast.lineTo(24,-35);
        breast.lineTo(0,-22);breast.lineTo(-24,-35);breast.close();
        fill(c,breast,Color.rgb(33,42,66));
        p.setColor(GOLD);c.drawCircle(0,-61,6,p);
        glow(c,0,-61,23,color,0.38f);

        // Shoulder plates and gauntlet arms.
        p.setColor(Color.rgb(177,188,210));
        c.drawCircle(-31,-94,13,p);c.drawCircle(31,-94,13,p);
        p.setColor(Color.rgb(69,83,113));
        p.setStrokeWidth(13);p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(-31,-87,-43,-35,p);
        c.drawLine(31,-87,43,-35,p);
        p.setStrokeWidth(7);p.setColor(Color.rgb(205,211,228));
        c.drawLine(-43,-38,-48,-25,p);c.drawLine(43,-38,48,-25,p);
        p.setStrokeCap(Paint.Cap.BUTT);

        // Helm and visor.
        p.setColor(Color.rgb(223,197,168));c.drawCircle(0,-126,18,p);
        p.setColor(Color.rgb(28,34,55));
        Path helmet=new Path();
        helmet.moveTo(-21,-126);helmet.lineTo(-14,-150);helmet.lineTo(0,-162);
        helmet.lineTo(17,-149);helmet.lineTo(22,-122);helmet.lineTo(0,-135);helmet.close();
        fill(c,helmet,Color.rgb(174,187,212));
        p.setColor(Color.rgb(21,34,54));c.drawRect(-14,-128,14,-122,p);
        p.setColor(color);c.drawCircle(0,-129,3,p);
        Path plume=new Path();
        plume.moveTo(0,-159);plume.quadTo(left?18:-18,-181,left?40:-40,-169);
        plume.lineTo(left?10:-10,-151);plume.close();fill(c,plume,color);

        // Blade, held outward toward the opponent.
        c.save();
        c.rotate((left? -18f:18f)+(float)Math.sin(t*6.28f+ (left?0:2))*4f,
                facing*39,-47);
        p.setStrokeWidth(9);p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.rgb(37,43,65));c.drawLine(facing*39,-47,facing*76,-132,p);
        p.setStrokeWidth(4);p.setColor(Color.rgb(214,231,251));
        c.drawLine(facing*39,-47,facing*76,-132,p);
        p.setStrokeWidth(1.5f);p.setColor(color);
        c.drawLine(facing*42,-58,facing*74,-129,p);
        p.setStrokeCap(Paint.Cap.BUTT);
        c.restore();
        c.restore();
    }



    private void drawSummon(Canvas c, float t) {
        drawCastle(c,480,165,0.5f,Color.rgb(48,59,104));
        glow(c,480,170,190,PURPLE,0.5f+(float)Math.sin(t*6.28f)*0.08f);
        for(int i=0;i<6;i++){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(i==0?5:2);
            p.setColor(Color.argb(200-i*20, i%2==0?170:108, i%2==0?145:203, 255));
            c.drawCircle(480,196,50+i*23+(float)Math.sin(t*6.28f+i)*2,p);
        }
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<3;i++){
            float x=310+i*170;
            c.save();c.rotate((i-1)*-7,x,147);
            RectF card=new RectF(x-45,78,x+45,211);
            p.setColor(Color.rgb(34,38,65));c.drawRoundRect(card,9,9,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
            p.setColor(i==1?GOLD:Color.rgb(135,132,218));c.drawRoundRect(card,9,9,p);p.setStyle(Paint.Style.FILL);
            glow(c,x,139,40,i==1?GOLD:CYAN,0.24f);
            p.setColor(i==1?GOLD:Color.rgb(164,183,255));
            Path sigil=new Path();sigil.moveTo(x,108);sigil.lineTo(x+25,144);sigil.lineTo(x,178);sigil.lineTo(x-25,144);sigil.close();fill(c,sigil,p.getColor());
            c.restore();
        }
        drawFloorRays(c,t,PURPLE);
    }

    private void drawArchive(Canvas c, float t) {
        // A collector's gallery: framed card folios, not circular placeholders.
        p.setShader(new LinearGradient(0, 0, 0, 300,
                new int[]{Color.rgb(38, 43, 73), Color.rgb(16, 24, 43), Color.rgb(6, 10, 21)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 300, p);
        p.setShader(null);

        // Tall gallery pilasters and long horizontal display rails.
        for (int x : new int[]{38, 246, 714, 922}) {
            p.setColor(Color.rgb(47, 55, 79));
            c.drawRect(x, 76, x + 13, 257, p);
            p.setColor(Color.rgb(150, 122, 86));
            c.drawRect(x + 4, 82, x + 7, 252, p);
            p.setColor(Color.rgb(73, 72, 101));
            c.drawRect(x - 5, 76, x + 18, 83, p);
            c.drawRect(x - 5, 251, x + 18, 258, p);
        }
        p.setColor(Color.rgb(75, 67, 73));
        c.drawRect(34, 165, 926, 174, p);
        p.setColor(Color.rgb(175, 137, 88));
        c.drawRect(34, 165, 926, 167, p);
        c.drawRect(34, 172, 926, 174, p);

        int[] accents = {GOLD, CYAN, PURPLE, RED};
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 7; i++) {
                float x = 67 + i * 123;
                float y = row == 0 ? 83 : 178;
                int accent = accents[(i + row * 2) % accents.length];
                RectF frame = new RectF(x, y, x + 72, y + 78);
                p.setColor(Color.rgb(35, 42, 65));
                c.drawRoundRect(frame, 5, 5, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f);
                p.setColor(Color.rgb(175, 143, 99));
                c.drawRoundRect(frame, 5, 5, p);
                p.setStrokeWidth(1);
                p.setColor(Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)));
                c.drawRect(x + 6, y + 6, x + 66, y + 71, p);
                p.setStyle(Paint.Style.FILL);

                glow(c, x + 36, y + 34, 30, accent, 0.15f);
                Path crest = new Path();
                crest.moveTo(x + 36, y + 16);
                crest.lineTo(x + 52, y + 34);
                crest.lineTo(x + 36, y + 53);
                crest.lineTo(x + 20, y + 34);
                crest.close();
                fill(c, crest, Color.rgb(Color.red(accent) / 2 + 35,
                        Color.green(accent) / 2 + 31, Color.blue(accent) / 2 + 31));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f);
                p.setColor(accent);
                c.drawPath(crest, p);
                p.setStyle(Paint.Style.FILL);

                p.setColor(Color.rgb(225, 218, 197));
                c.drawRect(x + 14, y + 59, x + 58, y + 61, p);
                p.setColor(Color.rgb(113, 124, 151));
                c.drawRect(x + 20, y + 65, x + 52, y + 67, p);
                p.setColor(accent);
                c.drawRect(x + 30, y + 70, x + 42, y + 72, p);
            }
        }
        glow(c, 480, 116, 155, GOLD, 0.13f);
        drawFloorRays(c, t, GOLD);
    }

    private void drawBinder(Canvas c, float t) {
        // The Binder Vault is an illuminated folio library with upright rectangular albums.
        p.setShader(new LinearGradient(0, 0, 0, 300,
                new int[]{Color.rgb(27, 43, 70), Color.rgb(12, 23, 42), Color.rgb(5, 10, 21)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 300, p);
        p.setShader(null);

        // Vault arch and stone ribs frame the library without circular medallions.
        Path arch = new Path();
        arch.moveTo(245, 262);
        arch.lineTo(245, 150);
        arch.cubicTo(245, 57, 338, 40, 495, 40);
        arch.cubicTo(652, 40, 745, 57, 745, 150);
        arch.lineTo(745, 262);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(13);
        p.setColor(Color.rgb(48, 66, 91));
        c.drawPath(arch, p);
        p.setStrokeWidth(2);
        p.setColor(Color.rgb(178, 144, 97));
        c.drawPath(arch, p);
        p.setStyle(Paint.Style.FILL);

        for (int x : new int[]{56, 222, 752, 918}) {
            p.setColor(Color.rgb(43, 57, 82));
            c.drawRect(x, 68, x + 15, 263, p);
            p.setColor(Color.rgb(148, 119, 81));
            c.drawRect(x + 6, 78, x + 9, 253, p);
            p.setColor(Color.rgb(72, 76, 102));
            c.drawRect(x - 5, 68, x + 20, 77, p);
            c.drawRect(x - 5, 253, x + 20, 263, p);
        }

        int[] folioColors = {GOLD, CYAN, PURPLE, RED, Color.rgb(119, 176, 235)};
        for (int row = 0; row < 2; row++) {
            float shelfY = row == 0 ? 105 : 205;
            p.setColor(Color.rgb(60, 55, 72));
            c.drawRect(255, shelfY + 71, 735, shelfY + 79, p);
            p.setColor(Color.rgb(165, 132, 89));
            c.drawRect(255, shelfY + 71, 735, shelfY + 73, p);
            for (int i = 0; i < 8; i++) {
                float x = 265 + i * 58;
                float y = shelfY - 12;
                int accent = folioColors[(i + row * 3) % folioColors.length];
                RectF folio = new RectF(x, y, x + 49, shelfY + 69);
                p.setColor(Color.rgb(26 + i % 3 * 5, 31 + row * 4, 51 + i % 2 * 5));
                c.drawRoundRect(folio, 4, 4, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(Color.rgb(151, 122, 87));
                c.drawRoundRect(folio, 4, 4, p);
                p.setStrokeWidth(1);
                p.setColor(Color.argb(145, Color.red(accent), Color.green(accent), Color.blue(accent)));
                c.drawRect(x + 5, y + 5, x + 44, shelfY + 64, p);
                p.setStyle(Paint.Style.FILL);

                // Folio spine and title band.
                p.setColor(Color.rgb(Color.red(accent) / 2 + 25,
                        Color.green(accent) / 2 + 23, Color.blue(accent) / 2 + 25));
                c.drawRect(x + 5, y + 5, x + 13, shelfY + 64, p);
                p.setColor(accent);
                c.drawRect(x + 17, y + 15, x + 40, y + 17, p);
                c.drawRect(x + 17, y + 53, x + 40, y + 55, p);

                Path crest = new Path();
                crest.moveTo(x + 29, y + 24);
                crest.lineTo(x + 38, y + 34);
                crest.lineTo(x + 29, y + 45);
                crest.lineTo(x + 20, y + 34);
                crest.close();
                fill(c, crest, accent);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1);
                p.setColor(Color.rgb(245, 228, 188));
                c.drawPath(crest, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        // A broad illuminated reading desk anchors the lower foreground.
        Path desk = new Path();
        desk.moveTo(40, 277);
        desk.lineTo(920, 277);
        desk.lineTo(827, 300);
        desk.lineTo(132, 300);
        desk.close();
        fill(c, desk, Color.rgb(15, 24, 39));
        p.setColor(Color.rgb(153, 119, 79));
        c.drawRect(105, 276, 855, 279, p);
        glow(c, 492, 145, 180, CYAN, 0.13f + 0.03f * (float)Math.sin(t * 6.28f));
        drawFloorRays(c, t, CYAN);
    }

    private void drawRecords(Canvas c, float t) {
        drawCastle(c,480,185,0.68f,Color.rgb(57,69,103));
        glow(c,480,125,160,GOLD,0.35f);
        // Trophy on a stepped plinth.
        p.setColor(Color.rgb(72,61,55));RectF base=new RectF(375,210,585,278);c.drawRoundRect(base,8,8,p);
        p.setColor(Color.rgb(124,93,63));c.drawRect(407,184,553,216,p);
        p.setColor(GOLD);Path cup=new Path();cup.moveTo(426,91);cup.lineTo(534,91);cup.lineTo(522,163);cup.quadTo(480,205,438,163);cup.close();fill(c,cup,GOLD);
        p.setColor(Color.rgb(125,82,47));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(10);
        c.drawArc(new RectF(396,93,456,159),270,170,false,p);c.drawArc(new RectF(504,93,564,159),100,170,false,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(112,77,49));c.drawRect(471,163,489,191,p);
        p.setColor(Color.rgb(233,190,115));c.drawRect(448,190,512,200,p);
        for(int i=0;i<5;i++){float a=(float)(i*Math.PI/2.5);float x=480+(float)Math.cos(a)*115,y=126+(float)Math.sin(a)*76;glow(c,x,y,22,GOLD,0.38f);}
    }

    private void drawMarket(Canvas c, float t) {
        drawCastle(c,480,183,0.65f,Color.rgb(67,77,109));
        // Lantern-lined market arcade.
        for(int i=0;i<7;i++){
            float x=105+i*125;
            p.setColor(Color.rgb(49,54,73));RectF stall=new RectF(x-44,148,x+44,260);c.drawRoundRect(stall,6,6,p);
            p.setColor(new int[]{GOLD,CYAN,PURPLE,RED}[i%4]);c.drawRoundRect(new RectF(x-51,130,x+51,159),7,7,p);
            glow(c,x,128,36,new int[]{GOLD,CYAN,PURPLE,RED}[i%4],0.22f);
            p.setColor(Color.rgb(213,196,154));c.drawRect(x-28,174,x+28,178,p);
            c.drawRect(x-28,190,x+28,194,p);
        }
    }

    private void drawTrade(Canvas c, float t) {
        drawArchive(c,t);
        glow(c,480,170,140,CYAN,0.28f);
        // Two opposing collector crests joined by an exchange arc.
        drawShield(c,315,153,CYAN);
        drawShield(c,645,153,PURPLE);
        p.setColor(Color.rgb(220,207,165));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);
        RectF arc=new RectF(370,68,590,254);c.drawArc(arc,205,130,false,p);p.setStyle(Paint.Style.FILL);
        p.setColor(GOLD);c.drawCircle(480,95,11,p);glow(c,480,95,36,GOLD,0.7f);
    }

    private void drawGuild(Canvas c, float t) {
        drawCastle(c,480,180,1.1f,Color.rgb(68,98,96));
        drawTrees(c,0,229,0.9f);
        glow(c,480,130,115,CYAN,0.22f);
        // Banner above the guild keep.
        p.setColor(Color.rgb(34,92,77));
        Path banner=new Path();banner.moveTo(480,58);banner.lineTo(556,78);banner.lineTo(548,154);banner.lineTo(480,137);banner.close();fill(c,banner,Color.rgb(41,110,88));
        p.setColor(Color.rgb(164,247,197));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawLine(480,47,480,168,p);p.setStyle(Paint.Style.FILL);
        p.setColor(GOLD);c.drawCircle(514,103,10,p);
    }

    private void drawCastle(Canvas c,float x,float y,float s,int color){
        c.save();c.translate(x,y);c.scale(s,s);
        p.setColor(color);
        c.drawRect(-95,-63,95,75,p);
        Path roof=new Path();roof.moveTo(-115,-63);roof.lineTo(0,-147);roof.lineTo(115,-63);roof.close();fill(c,roof,Color.rgb(104,91,128));
        for(int i=-1;i<=1;i++){
            float tx=i*65;
            c.drawRect(tx-24,-100,tx+24,-40,p);
            Path tower=new Path();tower.moveTo(tx-30,-100);tower.lineTo(tx,-147);tower.lineTo(tx+30,-100);tower.close();fill(c,tower,Color.rgb(122,106,145));
            p.setColor(Color.rgb(228,191,126));c.drawRoundRect(new RectF(tx-9,-20,tx+9,18),9,9,p);p.setColor(color);
        }
        p.setColor(Color.rgb(12,19,34));c.drawRoundRect(new RectF(-22,14,22,76),20,20,p);
        p.setColor(Color.rgb(188,159,118));c.drawRect(-3,-142,3,-104,p);
        c.restore();
    }

    private void drawFloatingIsland(Canvas c,float x,float y,float s,float t){
        c.save();c.translate(x,y+(float)Math.sin(t*6.28f)*5);c.scale(s,s);
        glow(c,0,-24,72,CYAN,0.2f);
        p.setColor(Color.rgb(50,75,76));c.drawOval(new RectF(-78,-43,78,5),p);
        Path rock=new Path();rock.moveTo(-67,-22);rock.lineTo(64,-22);rock.lineTo(20,58);rock.lineTo(-26,48);rock.close();fill(c,rock,Color.rgb(33,48,63));
        p.setColor(Color.rgb(72,119,92));c.drawRoundRect(new RectF(-62,-42,62,-22),10,10,p);
        p.setColor(Color.rgb(142,184,135));for(int i=0;i<5;i++)c.drawCircle(-45+i*22,-37,3,p);
        c.restore();
    }

    private void drawBridge(Canvas c,float x1,float y1,float x2,float y2){
        p.setColor(Color.rgb(61,59,67));p.setStrokeWidth(9);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,p);
        p.setColor(Color.rgb(160,130,92));p.setStrokeWidth(2);c.drawLine(x1,y1-6,x2,y2-6,p);p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawTrees(Canvas c,float x,float y,float scale){
        c.save();c.translate(x,y);c.scale(scale,scale);
        for(int i=0;i<9;i++){
            float tx=i*116;
            p.setColor(Color.rgb(35,54,52));c.drawRect(tx-3,-27,tx+3,4,p);
            p.setColor(i%2==0?Color.rgb(37,88,70):Color.rgb(41,76,70));
            c.drawCircle(tx,-39,25,p);c.drawCircle(tx-13,-24,18,p);
        }
        c.restore();
    }

    private void drawMountains(Canvas c,int far,int near){
        Path a=new Path();a.moveTo(0,218);a.lineTo(160,78);a.lineTo(276,204);a.lineTo(420,58);a.lineTo(570,219);a.lineTo(730,82);a.lineTo(875,210);a.lineTo(960,122);a.lineTo(960,300);a.lineTo(0,300);a.close();fill(c,a,far);
        Path b=new Path();b.moveTo(0,248);b.lineTo(205,161);b.lineTo(370,255);b.lineTo(602,165);b.lineTo(780,260);b.lineTo(960,181);b.lineTo(960,300);b.lineTo(0,300);b.close();fill(c,b,near);
    }

    private void drawFloorRays(Canvas c,float t,int color){
        p.setColor(Color.argb(45,Color.red(color),Color.green(color),Color.blue(color)));
        for(int i=0;i<6;i++){
            float x=120+i*150+(float)Math.sin(t*6.28f+i)*8;
            Path ray=new Path();ray.moveTo(x,300);ray.lineTo(x+24,222);ray.lineTo(x+65,300);ray.close();fill(c,ray,p.getColor());
        }
    }

    private void drawShield(Canvas c,float x,float y,int color){
        Path shield=new Path();shield.moveTo(x,y-58);shield.lineTo(x+52,y-36);shield.lineTo(x+46,y+22);shield.quadTo(x,y+86,x-46,y+22);shield.lineTo(x-52,y-36);shield.close();
        p.setColor(Color.rgb(37,48,66));fill(c,shield,p.getColor());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(color);c.drawPath(shield,p);p.setStyle(Paint.Style.FILL);
        p.setColor(color);c.drawCircle(x,y,17,p);glow(c,x,y,37,color,0.38f);
    }

    private void drawVignette(Canvas c){
        p.setShader(new LinearGradient(0,0,0,300,new int[]{Color.argb(160,3,6,14),Color.TRANSPARENT,Color.argb(190,3,6,14)},new float[]{0f,0.45f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,960,300,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.argb(120,165,180,213));
        c.drawRoundRect(new RectF(1,1,959,299),16,16,p);p.setStyle(Paint.Style.FILL);
    }

    private void drawHud(Canvas c){
        // Text and progress live inside the scene, so the scene reads like a game screen, not a web card.
        p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        p.setTextSize(12);p.setColor(Color.argb(205,194,208,234));
        c.drawText("CARDUNIVERSE  /  FIRSTLIGHT",26,28,p);
        p.setTextSize(27);p.setColor(WHITE);
        c.drawText(title.toUpperCase(java.util.Locale.ROOT),26,66,p);
        p.setTextSize(12);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        p.setColor(Color.rgb(198,207,226));
        if(!subtitle.isEmpty()) c.drawText(subtitle.length()>85?subtitle.substring(0,82)+"…":subtitle,28,89,p);

        // Small live progress readout, styled like an in-game objective HUD.
        if(total>0){
            float ratio=Math.max(0,Math.min(1,progress/(float)total));
            p.setColor(Color.argb(170,7,12,24));c.drawRoundRect(new RectF(26,258,252,280),10,10,p);
            p.setColor(GOLD);c.drawRoundRect(new RectF(26,258,252,280),10,10,p);
            p.setColor(Color.rgb(9,15,29));c.drawRoundRect(new RectF(28,260,250,278),8,8,p);
            if(ratio>0){p.setColor(CYAN);c.drawRoundRect(new RectF(28,260,28+222*ratio,278),8,8,p);}
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(10);p.setColor(WHITE);
            c.drawText(progress+" / "+total+"  OBJECTIVE",270,274,p);
        } else {
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(10);
            p.setColor(Color.rgb(203,187,255));c.drawText("REALM ONLINE  ·  EXPLORE",28,273,p);
        }
    }

    private void glow(Canvas c,float x,float y,float radius,int color,float strength){
        int alpha=(int)(Math.max(0,Math.min(1,strength))*95);
        p.setShader(new RadialGradient(x,y,radius,
                new int[]{Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color)),
                        Color.argb(alpha/3,Color.red(color),Color.green(color),Color.blue(color)),
                        Color.TRANSPARENT},new float[]{0f,0.45f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(x,y,radius,p);p.setShader(null);
    }

    private void fill(Canvas c,Path path,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawPath(path,p);}
}

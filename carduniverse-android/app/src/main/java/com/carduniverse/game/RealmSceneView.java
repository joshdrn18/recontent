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
        // A formal championship coliseum with a rectangular duelling platform.
        p.setShader(new LinearGradient(0, 0, 0, 300,
                new int[]{Color.rgb(54, 44, 70), Color.rgb(25, 30, 52), Color.rgb(7, 12, 25)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 300, p);
        p.setShader(null);

        Path stands = new Path();
        stands.moveTo(55, 220);
        stands.lineTo(75, 92);
        stands.lineTo(155, 51);
        stands.lineTo(805, 51);
        stands.lineTo(885, 92);
        stands.lineTo(905, 220);
        stands.close();
        fill(c, stands, Color.rgb(41, 45, 67));
        for (int row = 0; row < 5; row++) {
            float y = 82 + row * 27;
            p.setColor(Color.rgb(69 + row * 3, 61 + row * 2, 72 + row * 2));
            c.drawRect(116 + row * 10, y, 844 - row * 10, y + 9, p);
            p.setColor(Color.argb(115, 202, 176, 132));
            c.drawRect(116 + row * 10, y + 8, 844 - row * 10, y + 10, p);
        }

        // Banners and tiered columns frame the arena.
        for (int i = 0; i < 6; i++) {
            float x = 150 + i * 132;
            int color = i % 2 == 0 ? CYAN : GOLD;
            p.setColor(Color.rgb(83, 74, 79));
            c.drawRect(x - 4, 35, x + 4, 188, p);
            Path banner = new Path();
            banner.moveTo(x - 26, 52);
            banner.lineTo(x + 26, 52);
            banner.lineTo(x + 21, 113);
            banner.lineTo(x, 130);
            banner.lineTo(x - 21, 113);
            banner.close();
            fill(c, banner, Color.rgb(Color.red(color) / 4 + 18,
                    Color.green(color) / 4 + 18, Color.blue(color) / 4 + 28));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(color);
            c.drawPath(banner, p);
            p.setStyle(Paint.Style.FILL);
        }

        // Raised stone platform; straight edge geometry gives it a competitive-stage silhouette.
        glow(c, 480, 209, 225, GOLD, 0.26f);
        Path platform = new Path();
        platform.moveTo(130, 198);
        platform.lineTo(228, 151);
        platform.lineTo(732, 151);
        platform.lineTo(830, 198);
        platform.lineTo(782, 279);
        platform.lineTo(178, 279);
        platform.close();
        fill(c, platform, Color.rgb(47, 48, 59));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        p.setColor(Color.rgb(165, 137, 98));
        c.drawPath(platform, p);
        p.setStrokeWidth(2);
        p.setColor(Color.argb(130, 231, 207, 163));
        c.drawLine(218, 190, 742, 190, p);
        c.drawLine(190, 250, 770, 250, p);
        p.setStyle(Paint.Style.FILL);

        // Center crest marks the duel zone.
        Path crest = new Path();
        crest.moveTo(480, 170);
        crest.lineTo(520, 213);
        crest.lineTo(480, 255);
        crest.lineTo(440, 213);
        crest.close();
        p.setColor(Color.rgb(23, 34, 50));
        c.drawPath(crest, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(GOLD);
        c.drawPath(crest, p);
        p.setStyle(Paint.Style.FILL);

        drawFighter(c, 318, 198, 0.88f, CYAN, true, t);
        drawFighter(c, 642, 198, 0.92f, RED, false, t);
        float clash = 0.5f + 0.5f * (float)Math.sin(t * 6.28f * 1.8f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(3 + clash * 3);
        p.setColor(Color.argb((int)(70 + clash * 150), 153, 225, 255));
        c.drawLine(419, 157, 479, 214, p);
        p.setColor(Color.argb((int)(70 + clash * 150), 255, 207, 142));
        c.drawLine(541, 157, 481, 214, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        glow(c, 480, 209, 50, clash > 0.5f ? GOLD : CYAN, 0.25f + clash * 0.48f);
        drawFloorRays(c, t, GOLD);
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
        // Summon Sanctum: a tall astral gateway and three engraved card reliquaries.
        drawMountains(c, Color.rgb(46, 45, 83), Color.rgb(20, 24, 50));
        glow(c, 480, 158, 230, PURPLE, 0.38f + 0.06f * (float)Math.sin(t * 6.28f));
        Path gate = new Path();
        gate.moveTo(335, 270);
        gate.lineTo(335, 126);
        gate.cubicTo(335, 35, 392, 20, 480, 20);
        gate.cubicTo(568, 20, 625, 35, 625, 126);
        gate.lineTo(625, 270);
        gate.close();
        fill(c, gate, Color.rgb(20, 27, 55));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(18);
        p.setColor(Color.rgb(63, 57, 104));
        c.drawPath(gate, p);
        p.setStrokeWidth(3);
        p.setColor(GOLD);
        c.drawPath(gate, p);
        p.setStyle(Paint.Style.FILL);

        // Portal light is architectural rather than a stack of circular rings.
        RectF portal = new RectF(378, 91, 582, 260);
        p.setShader(new LinearGradient(378, 91, 582, 260,
                new int[]{Color.rgb(91, 83, 170), Color.rgb(36, 82, 132), Color.rgb(16, 24, 60)},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(portal, 76, 76, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(Color.argb(190, 183, 171, 255));
        c.drawRoundRect(new RectF(389, 101, 571, 250), 68, 68, p);
        p.setStyle(Paint.Style.FILL);

        for (int i = 0; i < 7; i++) {
            float y = 92 + i * 24;
            float drift = (float)Math.sin(t * 6.28f + i * 0.8f) * 9;
            p.setColor(Color.argb(105, 185, 217, 255));
            p.setStrokeWidth(2);
            c.drawLine(408 + drift, y, 552 - drift, y - 8, p);
        }

        // Suspended cards, slightly angled toward the portal.
        int[] accents = {CYAN, GOLD, PURPLE};
        for (int i = 0; i < 3; i++) {
            float x = 300 + i * 180;
            float y = 139 + (float)Math.sin(t * 6.28f + i * 1.6f) * 7;
            c.save();
            c.rotate((i - 1) * -8, x, y);
            RectF card = new RectF(x - 44, y - 77, x + 44, y + 77);
            p.setColor(Color.rgb(5, 11, 27));
            c.drawRoundRect(new RectF(x - 49, y - 82, x + 49, y + 82), 8, 8, p);
            p.setColor(Color.rgb(27, 35, 69));
            c.drawRoundRect(card, 6, 6, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(accents[i]);
            c.drawRoundRect(card, 6, 6, p);
            p.setStrokeWidth(1);
            p.setColor(Color.argb(145, 236, 225, 195));
            c.drawRoundRect(new RectF(x - 34, y - 66, x + 34, y + 66), 3, 3, p);
            p.setStyle(Paint.Style.FILL);
            Path sigil = new Path();
            sigil.moveTo(x, y - 40);
            sigil.lineTo(x + 25, y);
            sigil.lineTo(x, y + 40);
            sigil.lineTo(x - 25, y);
            sigil.close();
            fill(c, sigil, accents[i]);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(WHITE);
            c.drawPath(sigil, p);
            p.setStyle(Paint.Style.FILL);
            c.restore();
        }

        p.setColor(Color.rgb(134, 105, 80));
        c.drawRect(326, 266, 634, 274, p);
        p.setColor(GOLD);
        c.drawRect(350, 263, 610, 267, p);
        drawFloorRays(c, t, PURPLE);
    }

    private void drawArchive(Canvas c, float t) {
        // A grand card museum with a featured gallery wall and framed collectible art.
        p.setShader(new LinearGradient(0, 0, 0, 300,
                new int[]{Color.rgb(47, 42, 77), Color.rgb(20, 28, 50), Color.rgb(5, 10, 22)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 300, p);
        p.setShader(null);

        Path ceiling = new Path();
        ceiling.moveTo(35, 238);
        ceiling.lineTo(35, 130);
        ceiling.cubicTo(110, 28, 245, 28, 322, 130);
        ceiling.lineTo(322, 238);
        ceiling.lineTo(35, 238);
        fill(c, ceiling, Color.rgb(24, 35, 59));
        Path rightWing = new Path();
        rightWing.moveTo(638, 238);
        rightWing.lineTo(638, 130);
        rightWing.cubicTo(715, 28, 850, 28, 925, 130);
        rightWing.lineTo(925, 238);
        rightWing.close();
        fill(c, rightWing, Color.rgb(24, 35, 59));

        p.setColor(Color.rgb(155, 126, 91));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawLine(48, 236, 312, 236, p);
        c.drawLine(648, 236, 912, 236, p);
        p.setStrokeWidth(1);
        p.setColor(Color.argb(110, 218, 191, 143));
        c.drawLine(48, 244, 912, 244, p);
        p.setStyle(Paint.Style.FILL);

        // Side folios sit in recessed display bays.
        int[] colors = {GOLD, CYAN, PURPLE, RED, Color.rgb(116, 175, 237)};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 3; i++) {
                float x = side == 0 ? 74 + i * 82 : 720 + i * 82;
                float y = 112 + (i % 2) * 15;
                int accent = colors[(i + side * 2) % colors.length];
                RectF card = new RectF(x, y, x + 63, y + 100);
                p.setColor(Color.rgb(8, 14, 27));
                c.drawRoundRect(new RectF(x - 5, y - 5, x + 68, y + 106), 8, 8, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(Color.rgb(164, 133, 93));
                c.drawRoundRect(card, 5, 5, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.rgb(31, 39, 65));
                c.drawRoundRect(new RectF(x + 5, y + 5, x + 58, y + 95), 3, 3, p);
                glow(c, x + 31, y + 43, 26, accent, 0.16f);
                Path sigil = new Path();
                sigil.moveTo(x + 31, y + 21);
                sigil.lineTo(x + 47, y + 47);
                sigil.lineTo(x + 31, y + 73);
                sigil.lineTo(x + 15, y + 47);
                sigil.close();
                fill(c, sigil, accent);
                p.setColor(Color.rgb(235, 222, 193));
                c.drawRect(x + 14, y + 80, x + 49, y + 82, p);
            }
        }

        // The featured card is elevated above a brass lectern in the centre.
        glow(c, 480, 145, 190, GOLD, 0.27f + 0.05f * (float)Math.sin(t * 6.28f));
        Path arch = new Path();
        arch.moveTo(344, 249);
        arch.lineTo(344, 118);
        arch.cubicTo(344, 34, 414, 18, 480, 18);
        arch.cubicTo(546, 18, 616, 34, 616, 118);
        arch.lineTo(616, 249);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(10);
        p.setColor(Color.rgb(57, 65, 91));
        c.drawPath(arch, p);
        p.setStrokeWidth(2);
        p.setColor(GOLD);
        c.drawPath(arch, p);
        p.setStyle(Paint.Style.FILL);

        RectF featured = new RectF(407, 74, 553, 235);
        p.setColor(Color.rgb(6, 12, 25));
        c.drawRoundRect(new RectF(399, 67, 561, 243), 10, 10, p);
        p.setColor(Color.rgb(31, 39, 68));
        c.drawRoundRect(featured, 7, 7, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(GOLD);
        c.drawRoundRect(featured, 7, 7, p);
        p.setStrokeWidth(1);
        p.setColor(Color.argb(180, 116, 223, 223));
        c.drawRoundRect(new RectF(416, 83, 544, 226), 4, 4, p);
        p.setStyle(Paint.Style.FILL);
        Path heroCrest = new Path();
        heroCrest.moveTo(480, 102);
        heroCrest.lineTo(521, 151);
        heroCrest.lineTo(480, 203);
        heroCrest.lineTo(439, 151);
        heroCrest.close();
        fill(c, heroCrest, Color.rgb(88, 88, 151));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(CYAN);
        c.drawPath(heroCrest, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(245, 227, 188));
        c.drawRect(438, 213, 522, 216, p);
        c.drawRect(451, 220, 509, 222, p);

        Path lectern = new Path();
        lectern.moveTo(370, 252);
        lectern.lineTo(590, 252);
        lectern.lineTo(560, 281);
        lectern.lineTo(400, 281);
        lectern.close();
        fill(c, lectern, Color.rgb(58, 48, 55));
        p.setColor(Color.rgb(177, 138, 88));
        c.drawRect(383, 250, 577, 254, p);
        drawFloorRays(c, t, GOLD);
    }

    private void drawBinder(Canvas c, float t) {
        // The Binder Vault is now a monumental archive hall with a central album pedestal.
        p.setShader(new LinearGradient(0, 0, 0, 300,
                new int[]{Color.rgb(24, 47, 75), Color.rgb(12, 25, 46), Color.rgb(4, 9, 21)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 960, 300, p);
        p.setShader(null);

        // Deep stone nave and a high, ribbed vault.
        Path vault = new Path();
        vault.moveTo(70, 274);
        vault.lineTo(70, 153);
        vault.cubicTo(170, -12, 790, -12, 890, 153);
        vault.lineTo(890, 274);
        vault.close();
        fill(c, vault, Color.rgb(18, 34, 57));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(16);
        p.setColor(Color.rgb(38, 62, 86));
        c.drawPath(vault, p);
        p.setStrokeWidth(2);
        p.setColor(Color.rgb(161, 135, 95));
        c.drawPath(vault, p);
        p.setStyle(Paint.Style.FILL);

        for (int i = 0; i < 6; i++) {
            float x = 170 + i * 124;
            p.setColor(Color.argb(90, 161, 194, 214));
            p.setStrokeWidth(2);
            c.drawLine(480, 35, x, 243, p);
        }

        // Shelves of individually bound albums form the side wings.
        int[] bookColors = {GOLD, CYAN, PURPLE, RED, Color.rgb(114, 171, 225)};
        for (int side = 0; side < 2; side++) {
            float shelfLeft = side == 0 ? 54 : 684;
            float shelfRight = side == 0 ? 276 : 906;
            for (int row = 0; row < 2; row++) {
                float top = row == 0 ? 95 : 190;
                p.setColor(Color.rgb(56, 54, 65));
                c.drawRect(shelfLeft, top + 66, shelfRight, top + 74, p);
                p.setColor(Color.rgb(161, 129, 86));
                c.drawRect(shelfLeft, top + 66, shelfRight, top + 69, p);
                for (int i = 0; i < 4; i++) {
                    float x = shelfLeft + 8 + i * 53;
                    float height = 54 + (i % 2) * 8;
                    int accent = bookColors[(i + row + side) % bookColors.length];
                    RectF book = new RectF(x, top + 66 - height, x + 42, top + 66);
                    p.setColor(Color.rgb(22 + i * 3, 31 + row * 3, 50 + side * 5));
                    c.drawRoundRect(book, 3, 3, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2);
                    p.setColor(accent);
                    c.drawRoundRect(book, 3, 3, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.rgb(Color.red(accent) / 2 + 24,
                            Color.green(accent) / 2 + 22, Color.blue(accent) / 2 + 26));
                    c.drawRect(x + 5, top + 66 - height + 5, x + 11, top + 61, p);
                    p.setColor(accent);
                    c.drawRect(x + 16, top + 66 - height + 12, x + 35, top + 66 - height + 14, p);
                    c.drawRect(x + 16, top + 66 - height + 39, x + 35, top + 66 - height + 41, p);
                }
            }
        }

        // Central upright binder folio — the visual anchor of the room.
        glow(c, 480, 160, 190, CYAN, 0.22f + 0.04f * (float)Math.sin(t * 6.28f));
        RectF cover = new RectF(365, 62, 595, 246);
        p.setColor(Color.rgb(4, 11, 23));
        c.drawRoundRect(new RectF(354, 55, 606, 254), 10, 10, p);
        p.setColor(Color.rgb(26, 47, 72));
        c.drawRoundRect(cover, 8, 8, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setColor(Color.rgb(178, 146, 101));
        c.drawRoundRect(cover, 8, 8, p);
        p.setStrokeWidth(1);
        p.setColor(CYAN);
        c.drawRoundRect(new RectF(379, 76, 581, 232), 4, 4, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(8, 18, 32));
        c.drawRect(389, 84, 410, 226, p);
        p.setColor(Color.rgb(135, 115, 79));
        c.drawRect(414, 80, 417, 229, p);
        Path crest = new Path();
        crest.moveTo(480, 99);
        crest.lineTo(537, 151);
        crest.lineTo(480, 210);
        crest.lineTo(423, 151);
        crest.close();
        fill(c, crest, Color.rgb(39, 79, 101));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(GOLD);
        c.drawPath(crest, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(237, 221, 186));
        c.drawRect(440, 217, 520, 220, p);
        c.drawRect(452, 225, 508, 227, p);

        // Pedestal and floor bands.
        Path pedestal = new Path();
        pedestal.moveTo(327, 257);
        pedestal.lineTo(633, 257);
        pedestal.lineTo(585, 291);
        pedestal.lineTo(375, 291);
        pedestal.close();
        fill(c, pedestal, Color.rgb(17, 29, 44));
        p.setColor(Color.rgb(164, 130, 85));
        c.drawRect(338, 253, 622, 258, p);
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
        // Keep the title and objective readout inside a compact, aspect-safe HUD.
        p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        p.setTextSize(15);
        p.setColor(Color.argb(205,194,208,234));
        c.drawText("CARDUNIVERSE  /  FIRSTLIGHT",26,28,p);
        p.setTextSize(32);
        p.setColor(WHITE);
        c.drawText(title.toUpperCase(java.util.Locale.ROOT),26,68,p);
        p.setTextSize(14);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        p.setColor(Color.rgb(198,207,226));
        if(!subtitle.isEmpty()) {
            String line = subtitle.length() > 82 ? subtitle.substring(0, 79) + "…" : subtitle;
            c.drawText(line,28,96,p);
        }

        if(total>0){
            float ratio=Math.max(0,Math.min(1,progress/(float)total));
            RectF hud = new RectF(26,246,364,288);
            p.setColor(Color.argb(214,7,12,24));
            c.drawRoundRect(hud,12,12,p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(Color.argb(205,Color.red(GOLD),Color.green(GOLD),Color.blue(GOLD)));
            c.drawRoundRect(hud,12,12,p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(15);
            p.setColor(WHITE);
            c.drawText("OBJECTIVE  ·  "+progress+" / "+total,40,266,p);
            RectF track = new RectF(40,274,350,282);
            p.setColor(Color.rgb(34,43,61));
            c.drawRoundRect(track,6,6,p);
            if(ratio>0){
                p.setColor(CYAN);
                c.drawRoundRect(new RectF(40,274,40+310*ratio,282),6,6,p);
            }
        } else {
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(15);
            p.setColor(Color.rgb(203,187,255));
            c.drawText("REALM ONLINE  ·  EXPLORE",28,274,p);
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

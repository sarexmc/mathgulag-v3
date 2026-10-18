package com.mathgulag;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MathScreen extends Screen {
    private static ResourceLocation tex(String n) {
        return new ResourceLocation(MathGulag.MODID, "textures/gui/" + n + ".png");
    }

    static final ResourceLocation VEINS = tex("veins");
    static final ResourceLocation VEINS2 = tex("veins2");
    static final ResourceLocation QBOX = tex("qbox");
    static final ResourceLocation[] SPIKE = new ResourceLocation[10];
    static final ResourceLocation[] DIGIT = new ResourceLocation[10];
    static final ResourceLocation GX = tex("glyph_x"), GEQ = tex("glyph_eq"), GQ = tex("glyph_q");

    static {
        for (int i = 0; i < 10; i++) {
            SPIKE[i] = tex("spike_" + i);
            DIGIT[i] = tex("glyph_" + i);
        }
    }

    // posicion (fraccion de pantalla) de cada digito 0..9, igual que en el diseno original
    static final float[][] POS = {
            {0.29f, 0.54f}, {0.14f, 0.44f}, {0.11f, 0.86f}, {0.70f, 0.44f}, {0.83f, 0.68f},
            {0.69f, 0.72f}, {0.39f, 0.77f}, {0.23f, 0.73f}, {0.49f, 0.86f}, {0.89f, 0.50f}};

    private int a, b, left, total, done;
    private String input = "";

    public MathScreen(int a, int b, int left, int total, int done) {
        super(Component.literal("Gulag"));
        update(a, b, left, total, done);
    }

    public void update(int a, int b, int left, int total, int done) {
        this.a = a; this.b = b; this.left = left; this.total = total; this.done = done; this.input = "";
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() { if (left > 0) left--; }

    private int size() { return Math.max(60, (int) (height * 0.20f)); }

    private boolean hit(int d, double mx, double my) {
        double cx = POS[d][0] * width, cy = POS[d][1] * height, r = size() * 0.28;
        return (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= r * r;
    }

    private ResourceLocation glyphFor(char c) {
        if (c >= '0' && c <= '9') return DIGIT[c - '0'];
        if (c == 'X') return GX;
        if (c == '=') return GEQ;
        if (c == '?') return GQ;
        return null;
    }

    /** Dibuja texto con los glifos chorreantes, centrado en cx. h = alto de cada glifo. */
    private void glyphs(GuiGraphics g, String t, int cx, int y, int h) {
        int adv = (int) (h * 0.62f);
        int x = cx - (t.length() * adv) / 2 - (h - adv) / 2;
        for (int i = 0; i < t.length(); i++) {
            ResourceLocation r = glyphFor(t.charAt(i));
            if (r != null) g.blit(r, x + i * adv, y, h, h, 0, 0, 256, 256, 256, 256);
        }
    }

    /** Capa de venas que respira, se desplaza y gira muy despacio. */
    private void veinLayer(GuiGraphics g, ResourceLocation r, double t, double ph, float baseAlpha, float dir) {
        float pulse = (float) (0.78 + 0.22 * Math.sin(t * 1.7 + ph));
        float sc = (float) (1.12 + 0.04 * Math.sin(t * 0.55 + ph));
        float rot = (float) (dir * 2.5 * Math.sin(t * 0.35 + ph));
        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().scale(sc, sc, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0);
        RenderSystem.setShaderColor(1f, 1f, 1f, baseAlpha * pulse);
        g.blit(r, 0, 0, width, height, 0, 0, 1024, 1024, 1024, 1024);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        double t = Util.getMillis() / 1000.0;
        g.fill(0, 0, width, height, 0x18180003);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        veinLayer(g, VEINS, t, 0.0, 1.0f, 1f);
        veinLayer(g, VEINS2, t, 2.3, 0.65f, -1f);

        int s = size();
        for (int d = 0; d < 10; d++) {
            double ph = d * 0.9;
            float ox = (float) (Math.sin(t * 1.1 + ph) * 3.5), oy = (float) (Math.cos(t * 0.9 + ph * 1.3) * 3.5);
            float pul = (float) (1 + 0.05 * Math.sin(t * 2.2 + ph));
            float rot = (float) (5 * Math.sin(t * 1.3 + ph));
            int ss = (int) ((s + (hit(d, mx, my) ? 8 : 0)) * pul);
            int cx = (int) (POS[d][0] * width), cy = (int) (POS[d][1] * height);
            g.pose().pushPose();
            g.pose().translate(cx + ox, cy + oy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            g.blit(SPIKE[d], -ss / 2, -ss / 2, ss, ss, 0, 0, 256, 256, 256, 256);
            g.pose().popPose();
        }

        int boxW = (int) (width * 0.40f), boxH = (int) (boxW * 0.5f), boxY = (int) (height * 0.10f);
        RenderSystem.setShaderColor(1f, 1f, 1f, (float) (0.92 + 0.08 * Math.sin(t * 2.0)));
        g.blit(QBOX, width / 2 - boxW / 2, boxY, boxW, boxH, 0, 0, 512, 256, 512, 256);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        int qh = Math.max(40, (int) (height * 0.17f));
        int qy = boxY + boxH / 2 - qh / 2 - 6;
        glyphs(g, a + " X " + b, width / 2, qy, qh);
        glyphs(g, "= " + (input.isEmpty() ? "?" : input), width / 2, qy + qh - 8, qh / 2);
        g.drawCenteredString(font, "OPERACION " + (done + 1) + "/" + MathGulag.NEEDED, width / 2, boxY + boxH - 4, 0xFFB0B8);

        int bw = (int) (width * 0.8f), x0 = width / 10, y0 = height - 12;
        g.fill(x0, y0, x0 + bw, y0 + 6, 0xFF3A0008);
        int fw = (int) (bw * Math.max(0, left) / (float) Math.max(1, total));
        g.fill(x0, y0, x0 + fw, y0 + 6, 0xFFFF3030);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int d = 0; d < 10; d++) if (hit(d, mx, my)) { press(d); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
        if (key >= 48 && key <= 57) { press(key - 48); return true; }
        if (key >= 320 && key <= 329) { press(key - 320); return true; }
        if (key == 259) { input = ""; return true; }
        return super.keyPressed(key, sc, mod);
    }

    private void press(int d) {
        input += d;
        if (input.length() >= String.valueOf(a * b).length()) {
            int v = Integer.parseInt(input);
            input = "";
            Net.CH.sendToServer(new Net.AnswerMsg(v));
        }
    }
}

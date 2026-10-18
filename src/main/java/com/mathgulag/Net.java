package com.mathgulag;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MathGulag.MODID, "main"), () -> V, V::equals, V::equals);

    public static void register() {
        CH.registerMessage(0, QuestionMsg.class, QuestionMsg::enc, QuestionMsg::dec, QuestionMsg::handle);
        CH.registerMessage(1, CloseMsg.class, CloseMsg::enc, CloseMsg::dec, CloseMsg::handle);
        CH.registerMessage(2, AnswerMsg.class, AnswerMsg::enc, AnswerMsg::dec, AnswerMsg::handle);
    }

    /** Servidor -> cliente: abre/actualiza la pantalla de la pregunta. */
    public static class QuestionMsg {
        final int a, b, left, total, done;

        public QuestionMsg(int a, int b, int left, int total, int done) {
            this.a = a; this.b = b; this.left = left; this.total = total; this.done = done;
        }

        static void enc(QuestionMsg m, FriendlyByteBuf buf) {
            buf.writeInt(m.a); buf.writeInt(m.b); buf.writeInt(m.left); buf.writeInt(m.total); buf.writeInt(m.done);
        }

        static QuestionMsg dec(FriendlyByteBuf buf) {
            return new QuestionMsg(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        static void handle(QuestionMsg m, Supplier<NetworkEvent.Context> s) {
            NetworkEvent.Context c = s.get();
            final int a = m.a, b = m.b, l = m.left, t = m.total, dn = m.done;
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.question(a, b, l, t, dn)));
            c.setPacketHandled(true);
        }
    }

    /** Servidor -> cliente: cierra la pantalla. */
    public static class CloseMsg {
        static void enc(CloseMsg m, FriendlyByteBuf buf) { }

        static CloseMsg dec(FriendlyByteBuf buf) { return new CloseMsg(); }

        static void handle(CloseMsg m, Supplier<NetworkEvent.Context> s) {
            NetworkEvent.Context c = s.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::close));
            c.setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: respuesta del jugador. */
    public static class AnswerMsg {
        final int value;

        public AnswerMsg(int value) { this.value = value; }

        static void enc(AnswerMsg m, FriendlyByteBuf buf) { buf.writeInt(m.value); }

        static AnswerMsg dec(FriendlyByteBuf buf) { return new AnswerMsg(buf.readInt()); }

        static void handle(AnswerMsg m, Supplier<NetworkEvent.Context> s) {
            NetworkEvent.Context c = s.get();
            final int v = m.value;
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p != null) GameManager.answer(p, v);
            });
            c.setPacketHandled(true);
        }
    }
}

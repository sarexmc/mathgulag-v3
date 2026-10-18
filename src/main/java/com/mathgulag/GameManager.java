package com.mathgulag;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

public class GameManager {
    static final String S = "\u00a7";
    static boolean running, solo;
    static final List<UUID> alive = new ArrayList<>();
    static UUID holder;
    static int fuseLeft, fuseTotal, beepCd, a, b, progress;
    static final Map<UUID, ItemStack> helmets = new HashMap<>();
    static final Map<UUID, GameType> modes = new HashMap<>();
    static final Random R = new Random();

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mathgulag").requires(s -> s.hasPermission(2))
                .then(Commands.literal("start").executes(c -> start(c.getSource())))
                .then(Commands.literal("stop").executes(c -> {
                    if (running) endGame(c.getSource().getServer(), S + "eJuego detenido.");
                    return 1;
                })));
    }

    static int start(CommandSourceStack src) {
        MinecraftServer s = src.getServer();
        if (running) { src.sendFailure(Component.literal("Ya hay una partida en marcha.")); return 0; }
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!p.isSpectator()) ps.add(p);
        if (ps.size() < 1) { src.sendFailure(Component.literal("No hay jugadores.")); return 0; }
        solo = ps.size() == 1;
        alive.clear(); helmets.clear(); modes.clear();
        for (ServerPlayer p : ps) {
            alive.add(p.getUUID());
            helmets.put(p.getUUID(), p.getItemBySlot(EquipmentSlot.HEAD).copy());
            modes.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
        }
        running = true;
        holder = alive.get(R.nextInt(alive.size()));
        broadcast(s, S + "c" + S + "lGULAG DE LAS MATEMATICAS" + S + "r" + S + "7 - Resuelve la multiplicacion para pasar la patata.");
        newFuse();
        newQuestion();
        giveAndAsk(s);
        return 1;
    }

    static void newFuse() {
        fuseTotal = (30 + R.nextInt(30)) * 20;
        fuseLeft = fuseTotal;
        beepCd = 0;
    }

    static void newQuestion() { a = R.nextInt(10); b = R.nextInt(10); }

    static void giveAndAsk(MinecraftServer s) {
        progress = 0;
        ServerPlayer p = s.getPlayerList().getPlayer(holder);
        if (p == null) return;
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
        sendQuestion(p);
    }

    static void sendQuestion(ServerPlayer p) {
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.QuestionMsg(a, b, fuseLeft, fuseTotal, progress));
    }

    static void removeBomb(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, helmets.getOrDefault(p.getUUID(), ItemStack.EMPTY).copy());
    }

    static void close(ServerPlayer p) {
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.CloseMsg());
    }

    static void broadcast(MinecraftServer s, String msg) {
        s.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    public static void answer(ServerPlayer p, int value) {
        if (!running || !p.getUUID().equals(holder)) return;
        MinecraftServer s = p.getServer();
        ServerLevel l = p.serverLevel();
        if (value == a * b) {
            progress++;
            l.playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1f);
            if (progress < MathGulag.NEEDED) { newQuestion(); sendQuestion(p); return; }
            removeBomb(p);
            close(p);
            holder = alive.get((alive.indexOf(holder) + 1) % alive.size());
            newQuestion();
            giveAndAsk(s);
        } else {
            fuseLeft = Math.max(1, fuseLeft - 30);
            l.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
            sendQuestion(p);
        }
    }

    public static void tick(MinecraftServer s) {
        if (!running) return;
        ServerPlayer p = s.getPlayerList().getPlayer(holder);
        if (p == null) { explode(s); return; }
        fuseLeft--;
        if (p.getItemBySlot(EquipmentSlot.HEAD).getItem() != Items.TNT)
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
        ServerLevel l = p.serverLevel();
        if (--beepCd <= 0) {
            l.playSound(null, p.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 1.2f, fuseLeft < 100 ? 2f : 1.4f);
            beepCd = Math.max(3, Math.min(20, fuseLeft / 15));
        }
        if (fuseLeft % 2 == 0)
            l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 2.3, p.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        if (fuseLeft <= 0) explode(s);
    }

    static void explode(MinecraftServer s) {
        ServerPlayer p = s.getPlayerList().getPlayer(holder);
        String name = "?";
        if (p != null) {
            name = p.getName().getString();
            ServerLevel l = p.serverLevel();
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
            l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 1f);
            removeBomb(p);
            close(p);
            if (!solo) p.setGameMode(GameType.SPECTATOR);
        }
        if (solo) {
            broadcast(s, S + "c" + S + "lBOOM! " + S + "r" + S + "7(modo prueba: sigues jugando, usa /mathgulag stop para terminar)");
            newFuse();
            newQuestion();
            giveAndAsk(s);
            return;
        }
        alive.remove(holder);
        broadcast(s, S + "c\u2620 " + name + " ha ido al gulag");
        if (alive.size() <= 1) { finish(s); return; }
        holder = alive.get(R.nextInt(alive.size()));
        newFuse();
        newQuestion();
        giveAndAsk(s);
    }

    static void finish(MinecraftServer s) {
        String w = "nadie";
        if (!alive.isEmpty()) {
            ServerPlayer p = s.getPlayerList().getPlayer(alive.get(0));
            if (p != null) w = p.getName().getString();
        }
        endGame(s, S + "6" + S + "lGana " + w + S + "r" + S + "6 - el resto, al gulag.");
    }

    static void endGame(MinecraftServer s, String msg) {
        running = false;
        for (UUID u : new ArrayList<>(modes.keySet())) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p == null) continue;
            p.setGameMode(modes.get(u));
            p.setItemSlot(EquipmentSlot.HEAD, helmets.getOrDefault(u, ItemStack.EMPTY).copy());
            close(p);
        }
        alive.clear(); helmets.clear(); modes.clear();
        broadcast(s, msg);
    }

    public static void logout(ServerPlayer p) {
        if (!running || !alive.contains(p.getUUID())) return;
        MinecraftServer s = p.getServer();
        boolean wasHolder = p.getUUID().equals(holder);
        removeBomb(p);
        GameType m = modes.get(p.getUUID());
        if (m != null) p.setGameMode(m);
        alive.remove(p.getUUID());
        if (alive.size() <= 1) { finish(s); return; }
        if (wasHolder) {
            holder = alive.get(R.nextInt(alive.size()));
            newQuestion();
            giveAndAsk(s);
        }
    }
}

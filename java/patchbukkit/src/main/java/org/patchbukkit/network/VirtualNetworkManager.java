package org.patchbukkit.network;

import io.netty.channel.Channel;

import java.net.SocketAddress;

/**
 * Emulates the NMS net.minecraft.network.Connection / NetworkManager object
 * which protocol injection frameworks and anticheats reflect on.
 */
public class VirtualNetworkManager {

    public final Channel channel;

    public VirtualNetworkManager(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public SocketAddress getRemoteAddress() {
        return channel != null ? channel.remoteAddress() : null;
    }

    public SocketAddress getLocalAddress() {
        return channel != null ? channel.localAddress() : null;
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    public boolean isMemoryConnection() {
        return false;
    }

    public boolean isEncrypted() {
        return channel instanceof PatchBukkitVirtualChannel v && v.isEncrypted();
    }

    public boolean isCompressed() {
        return channel instanceof PatchBukkitVirtualChannel v && v.isCompressed();
    }

    public int getCompressionThreshold() {
        return channel instanceof PatchBukkitVirtualChannel v ? v.getCompressionThreshold() : -1;
    }

    public void setupCompression(int threshold, boolean validateDecompressed) {
        if (channel instanceof PatchBukkitVirtualChannel v) {
            v.setupCompression(threshold, validateDecompressed);
        }
    }

    public void setupCompression(int threshold) {
        setupCompression(threshold, false);
    }

    public void setCompressionThreshold(int threshold) {
        setupCompression(threshold, false);
    }

    public void setEncryptionKey(Object decryptCipher, Object encryptCipher) {
        if (channel instanceof PatchBukkitVirtualChannel v) {
            v.setEncryptionKey(decryptCipher, encryptCipher);
        }
    }

    public void send(Object packet) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(packet);
        }
    }

    public void send(Object packet, Object listener) {
        send(packet);
    }

    public void sendPacket(Object packet) {
        send(packet);
    }

    public void disconnect(Object reason) {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    public void handleDisconnection() {
        disconnect(null);
    }

    public void setReadOnly() {
        // No-op for read-only pipeline state
    }

    public float getAverageReceivedPackets() {
        return 0.0f;
    }

    public float getAverageSentPackets() {
        return 0.0f;
    }
}

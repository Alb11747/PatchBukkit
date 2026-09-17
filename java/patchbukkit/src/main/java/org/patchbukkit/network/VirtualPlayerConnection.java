package org.patchbukkit.network;

/**
 * Acts as the NMS ServerGamePacketListenerImpl / PlayerConnection object
 * which protocol injection frameworks inspect to find the network connection.
 */
public class VirtualPlayerConnection implements io.papermc.paper.connection.PlayerConnection {

    // Mojang field name: connection
    public final VirtualNetworkManager connection;

    // Spigot / legacy field name: networkManager
    public final VirtualNetworkManager networkManager;

    // Direct channel alias for reflection libraries looking for Channel field
    public final io.netty.channel.Channel channel;

    public VirtualPlayerConnection(VirtualNetworkManager networkManager) {
        this.connection = networkManager;
        this.networkManager = networkManager;
        this.channel = networkManager != null ? networkManager.channel : null;
    }

    public VirtualNetworkManager getConnection() {
        return connection;
    }

    public VirtualNetworkManager getNetworkManager() {
        return networkManager;
    }

    public io.netty.channel.Channel getChannel() {
        return channel;
    }

    public void send(Object packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }

    public void sendPacket(Object packet) {
        send(packet);
    }

    @Override
    public void disconnect(net.kyori.adventure.text.Component reason) {
        if (connection != null && connection.channel != null) {
            connection.channel.close();
        }
    }

    @Override
    public boolean isConnected() {
        return connection != null && connection.channel != null && connection.channel.isActive();
    }

    @Override
    public boolean isTransferred() {
        return false;
    }

    @Override
    public java.net.SocketAddress getAddress() {
        return connection != null && connection.channel != null ? connection.channel.remoteAddress() : null;
    }

    @Override
    public java.net.InetSocketAddress getClientAddress() {
        java.net.SocketAddress addr = getAddress();
        return addr instanceof java.net.InetSocketAddress ? (java.net.InetSocketAddress) addr : null;
    }

    @Override
    public java.net.InetSocketAddress getVirtualHost() {
        return null;
    }

    @Override
    public java.net.InetSocketAddress getHAProxyAddress() {
        return null;
    }
}

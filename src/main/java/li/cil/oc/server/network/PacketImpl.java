package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Packet;
import net.minecraft.nbt.CompoundTag;

class PacketImpl implements Packet {
    private final String source;
    private final String destination;
    private final int port;
    private final Object[] data;
    private final int ttl;
    private final int size;

    PacketImpl(String source, String destination, int port, Object[] data, int ttl) {
        this.source = source;
        this.destination = destination;
        this.port = port;
        this.data = data != null ? data : new Object[0];
        this.ttl = ttl;
        this.size = computeSize(this.data);
    }

    PacketImpl(String source, String destination, int port, Object[] data) {
        this(source, destination, port, data, initialTtl());
    }

    private static int initialTtl() {
        Settings s = Settings.get();
        return s != null ? s.initialNetworkPacketTTL : 5;
    }

    private static int computeSize(Object[] data) {
        if (data == null) return 0;
        Settings s = Settings.get();
        int maxParts = s != null ? s.maxNetworkPacketParts : 8;
        if (data.length > maxParts) throw new IllegalArgumentException("packet has too many parts");
        int total = data.length * 2;
        for (Object arg : data) {
            total += switch (arg) {
                case null -> 1;
                case Boolean b -> 1;
                case Byte b -> 2;
                case Short sh -> 2;
                case Integer i -> 4;
                case Long l -> 8;
                case Float f -> 4;
                case Double d -> 8;
                case String str -> Math.max(str.length(), 1);
                case byte[] arr -> Math.max(arr.length, 1);
                default -> throw new IllegalArgumentException("unsupported data type: " + arg.getClass());
            };
        }
        return total;
    }

    @Override public String source() { return source; }
    @Override public String destination() { return destination; }
    @Override public int port() { return port; }
    @Override public Object[] data() { return data; }
    @Override public int size() { return size; }
    @Override public int ttl() { return ttl; }

    @Override
    public Packet hop() {
        return new PacketImpl(source, destination, port, data, ttl - 1);
    }

    @Override
    public void save(CompoundTag nbt) {
        nbt.putString("source", source);
        if (destination != null && !destination.isEmpty()) nbt.putString("dest", destination);
        nbt.putInt("port", port);
        nbt.putInt("ttl", ttl);
        nbt.putInt("dataLength", data.length);
        for (int i = 0; i < data.length; i++) {
            Object v = data[i];
            if (v instanceof Boolean b) nbt.putBoolean("data" + i, b);
            else if (v instanceof Byte b) nbt.putByte("data" + i, b);
            else if (v instanceof Short sh) nbt.putShort("data" + i, sh);
            else if (v instanceof Integer iv) nbt.putInt("data" + i, iv);
            else if (v instanceof Long l) nbt.putLong("data" + i, l);
            else if (v instanceof Float f) nbt.putFloat("data" + i, f);
            else if (v instanceof Double d) nbt.putDouble("data" + i, d);
            else if (v instanceof String s) nbt.putString("data" + i, s);
            else if (v instanceof byte[] arr) nbt.putByteArray("data" + i, arr);
        }
    }
}

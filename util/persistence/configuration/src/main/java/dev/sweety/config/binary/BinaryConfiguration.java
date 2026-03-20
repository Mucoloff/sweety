package dev.sweety.config.binary;

import dev.sweety.config.common.Configuration;

import java.io.*;
import java.util.*;

public class BinaryConfiguration extends Configuration {

    private static final String MAGIC = "CFG1";
    private static final byte VERSION = 1;

    @Override
    protected void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException {
        DataOutputStream data = new DataOutputStream(out);

        data.writeBytes(MAGIC);
        data.writeByte(VERSION);

        writeObject(data, map, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Override
    protected Map<String, Object> loadFromStream(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);

        byte[] magic = new byte[4];
        data.readFully(magic);

        if (!MAGIC.equals(new String(magic))) throw new IllegalStateException("Invalid binary config format");

        byte version = data.readByte();
        if (version != VERSION) throw new IllegalStateException("Unsupported version: " + version);

        if (!(readObject(data) instanceof Map<?, ?> map)) throw new IllegalStateException("Root must be a Map");

        //noinspection unchecked
        return (Map<String, Object>) map;
    }

    private void writeObject(DataOutputStream out, Object obj, Set<Object> visited) throws IOException {
        if (obj == null) {
            out.writeByte(0);
            return;
        }

        // cycle detection SOLO per strutture
        if (obj instanceof Map || obj instanceof List) {
            if (!visited.add(obj)) {
                throw new IllegalStateException("Recursive reference detected (cycle)");
            }
        }

        switch (obj) {
            case String s -> {
                out.writeByte(1);
                out.writeUTF(s);
            }
            case Integer i -> {
                out.writeByte(2);
                out.writeInt(i);
            }
            case Long l -> {
                out.writeByte(3);
                out.writeLong(l);
            }
            case Double d -> {
                out.writeByte(4);
                out.writeDouble(d);
            }
            case Boolean b -> {
                out.writeByte(5);
                out.writeBoolean(b);
            }
            case List<?> list -> {
                out.writeByte(6);
                writeList(out, list, visited);
            }
            case Map<?, ?> map -> {
                out.writeByte(7);
                writeMap(out, map, visited);
            }
            case Float f -> {
                out.writeByte(8);
                out.writeFloat(f);
            }
            case Byte b -> {
                out.writeByte(9);
                out.writeByte(b);
            }
            case Short s -> {
                out.writeByte(10);
                out.writeShort(s);
            }
            case Character c -> {
                out.writeByte(11);
                out.writeChar(c);
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + obj.getClass());
        }

        if (obj instanceof Map || obj instanceof List) {
            visited.remove(obj);
        }
    }

    private void writeMap(DataOutputStream out, Map<?, ?> map, Set<Object> visited) throws IOException {
        out.writeInt(map.size());

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.writeUTF(String.valueOf(entry.getKey()));
            writeObject(out, entry.getValue(), visited);
        }
    }

    private void writeList(DataOutputStream out, List<?> list, Set<Object> visited) throws IOException {
        out.writeInt(list.size());

        for (Object obj : list) {
            writeObject(out, obj, visited);
        }
    }

    private Object readObject(DataInputStream in) throws IOException {
        byte type = in.readByte();

        return switch (type) {
            case 0 -> null;
            case 1 -> in.readUTF();
            case 2 -> in.readInt();
            case 3 -> in.readLong();
            case 4 -> in.readDouble();
            case 5 -> in.readBoolean();
            case 6 -> readList(in);
            case 7 -> readMap(in);
            case 8 -> in.readFloat();
            case 9 -> in.readByte();
            case 10 -> in.readShort();
            case 11 -> in.readChar();
            default -> throw new IllegalStateException("Unknown type: " + type);
        };
    }

    private Map<String, Object> readMap(DataInputStream in) throws IOException {
        int size = in.readInt();
        Map<String, Object> map = new HashMap<>(size);

        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = readObject(in);
            map.put(key, value);
        }

        return map;
    }

    private List<Object> readList(DataInputStream in) throws IOException {
        int size = in.readInt();
        List<Object> list = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            list.add(readObject(in));
        }

        return list;
    }
}
package dev.sweety.netty.loadbalancer.common.packet;

import dev.sweety.netty.packet.model.Packet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Packer {

    public final Packet[] EMPTY = new Packet[0];

    public Packet[] pack(Packet[]... arrays) {

        int totalLen = 0;
        for (Packet[] array : arrays) totalLen += array.length;

        if (totalLen == 0) return EMPTY;

        final Packet[] response = new Packet[totalLen];

        int offset = 0;
        for (Packet[] array : arrays) {
            System.arraycopy(array, 0, response, offset, array.length);
            offset += array.length;
        }

        return response;

    }

}

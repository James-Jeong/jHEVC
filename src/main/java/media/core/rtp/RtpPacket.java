package media.core.rtp;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class RtpPacket implements Serializable {

    public static final int RTP_PACKET_MAX_SIZE = 8192;
    public static final int FIXED_HEADER_SIZE = 12;
    public static final int EXT_HEADER_SIZE = 4;
    public static final int VERSION = 2;
    private static final long serialVersionUID = -1590053946635208723L;
    private ByteBuffer buffer;

    public RtpPacket (int capacity, boolean allocateDirect) {
        this.buffer = allocateDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
    }

    public RtpPacket (boolean allocateDirect) {
        this(RTP_PACKET_MAX_SIZE, allocateDirect);
    }

    public ByteBuffer getBuffer ( ) {
        return buffer;
    }

    public byte[] getRawData ( ) {
        byte[] data = new byte[getLength()];
        this.buffer.rewind();
        this.buffer.get(data);
        return data;
    }

    public int getVersion ( ) {
        return (buffer.get(0) & 0xC0) >> 6;
    }

    public int getContributingSource ( ) {
        return buffer.get(0) & 0x0F;
    }

    public boolean hasPadding ( ) {
        return (buffer.get(0) & 0x20) == 0x020;
    }

    public boolean hasExtensions ( ) {
        return (buffer.get(0) & 0x10) == 0x010;
    }

    public boolean getMarker ( ) {
        return (buffer.get(1) & 0xff & 0x80) == 0x80;
    }

    public int getPayloadType ( ) {
        return (buffer.get(1) & 0xff & 0x7f);
    }

    public int getSeqNumber ( ) {
        return buffer.getShort(2) & 0xFFFF;
    }

    public long getTimestamp ( ) {
        return ((long) (buffer.get(4) & 0xff) << 24) |
                ((long) (buffer.get(5) & 0xff) << 16) |
                ((long) (buffer.get(6) & 0xff) << 8) |
                ((long) (buffer.get(7) & 0xff));
    }

    public long getSyncSource ( ) {
        return readUnsignedIntAsLong(8);
    }

    public void setSyncSource (long ssrc) {
        byte[] data = getRawData();
        if (data != null && data.length >= 12) {
            setLong(ssrc, data, 8, 12);
            buffer = ByteBuffer.wrap(data);
        }
    }

    private static void setLong(long n, byte[] data, int begin, int end) {
        for (end--; end >= begin; end--) {
            data[end] = (byte) (n % 256);
            n >>= 8;
        }
    }

    public long GetRTCPSyncSource ( ) {
        return (readUnsignedIntAsLong(4));
    }

    public long readUnsignedIntAsLong (int off) {
        buffer.position(off);
        return (((long) (buffer.get() & 0xff) << 24) |
                ((long) (buffer.get() & 0xff) << 16) |
                ((long) (buffer.get() & 0xff) << 8) |
                ((long) (buffer.get() & 0xff))) & 0xFFFFFFFFL;
    }

    public void getPayload (byte[] buff, int offset) {
        buffer.position(FIXED_HEADER_SIZE);
        buffer.get(buff, offset, buffer.limit() - FIXED_HEADER_SIZE);
    }

    public void getPayload (byte[] buff) {
        getPayload(buff, 0);
    }

    public void wrap (byte[] data) {
        this.buffer.clear();
        this.buffer.put(data);
        this.buffer.flip();
    }

    public void wrap (boolean mark, int payloadType, int seqNumber, long timestamp, long ssrc, byte[] data, int offset, int len) {
        buffer.clear();
        buffer.rewind();

        //no extensions, paddings and cc
        buffer.put((byte) 0x80);

        byte b = (byte) (payloadType);
        if (mark) {
            b = (byte) (b | 0x80);
        }

        buffer.put(b);

        //sequence number
        buffer.put((byte) ((seqNumber & 0xFF00) >> 8));
        buffer.put((byte) (seqNumber & 0x00FF));

        //timestamp
        buffer.put((byte) ((timestamp & 0xFF000000) >> 24));
        buffer.put((byte) ((timestamp & 0x00FF0000) >> 16));
        buffer.put((byte) ((timestamp & 0x0000FF00) >> 8));
        buffer.put((byte) ((timestamp & 0x000000FF)));

        //ssrc
        buffer.put((byte) ((ssrc & 0xFF000000) >> 24));
        buffer.put((byte) ((ssrc & 0x00FF0000) >> 16));
        buffer.put((byte) ((ssrc & 0x0000FF00) >> 8));
        buffer.put((byte) ((ssrc & 0x000000FF)));

        buffer.put(data, offset, len);
        buffer.flip();
        buffer.rewind();
    }

    @Override
    public String toString ( ) {
        return "RTP Packet[marker=" + getMarker() + ", seq=" + getSeqNumber() +
                ", timestamp=" + getTimestamp() + ", payload_size=" + getPayloadLength() +
                ", payload=" + getPayloadType() + "]";
    }

    public void shrink (int delta) {
        if (delta <= 0) {
            return;
        }

        int newLimit = buffer.limit() - delta;
        if (newLimit <= 0) {
            newLimit = 0;
        }
        this.buffer.limit(newLimit);
    }

    public int getHeaderLength ( ) {
        if (getExtensionBit())
            return FIXED_HEADER_SIZE + 4 * getCsrcCount()
                    + EXT_HEADER_SIZE + getExtensionLength();
        else
            return FIXED_HEADER_SIZE + 4 * getCsrcCount();
    }

    public int getPayloadLength ( ) {
        return buffer.limit() - getHeaderLength();
    }

    public int getExtensionLength ( ) {
        if (!getExtensionBit())
            return 0;

        //the extension length comes after the RTP header, the CSRC list, and
        //after two bytes in the extension header called "defined by profile"
        int extLenIndex = FIXED_HEADER_SIZE
                + getCsrcCount() * 4 + 2;

        return ((buffer.get(extLenIndex) << 8) | buffer.get(extLenIndex + 1) * 4);
    }

    public boolean getExtensionBit ( ) {
        buffer.rewind();
        return (buffer.get() & 0x10) == 0x10;
    }

    public int getCsrcCount ( ) {
        buffer.rewind();
        return (buffer.get() & 0x0f);
    }

    public int getPaddingSize ( ) {
        buffer.rewind();
        if ((buffer.get() & 0x4) == 0) {
            return 0;
        } else {
            return buffer.get(buffer.limit() - 1);
        }
    }

    public int getLength ( ) {
        return buffer.limit();
    }

    public int getOffset ( ) {
        return this.buffer.position();
    }

    public void grow (int delta) {
        if (delta == 0) {
            return;
        }
        int newLen = buffer.limit() + delta;
        if (newLen <= buffer.capacity()) {
            // there is more room in the underlying reserved buffer memory
            buffer.limit(newLen);
            return;
        } else {
            // create a new bigger buffer
            ByteBuffer newBuffer = buffer.isDirect() ? ByteBuffer.allocateDirect(newLen) : ByteBuffer.allocate(newLen);
            buffer.rewind();
            newBuffer.put(buffer);
            newBuffer.limit(newLen);
            // switch to new buffer
            buffer = newBuffer;
        }
    }

    public void append (byte[] data, int len) {
        if (data == null || len <= 0 || len > data.length) {
            throw new IllegalArgumentException("Invalid combination of parameters data and length to append()");
        }

        int oldLimit = buffer.limit();
        // grow buffer if necessary
        grow(len);
        // set positing to begin writing immediately after the last byte of the current buffer
        buffer.position(oldLimit);
        // set the buffer limit to exactly the old size plus the new appendix length
        buffer.limit(oldLimit + len);
        // append data
        buffer.put(data, 0, len);
    }

    public void readRegionToBuff (int off, int len, byte[] outBuff) {
        assert off >= 0;
        assert len > 0;
        assert outBuff != null;
        assert outBuff.length >= len;
        assert buffer.limit() >= off + len;

        buffer.position(off);
        buffer.get(outBuff, 0, len);
    }

}

package media.core.rtp.h265;

import media.core.rtp.RtpPacket;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class H265Packet extends RtpPacket {

    private static final int RTP_VERSION = 2;
    private static final int RTP_HEVC_PAYLOAD_HEADER_SIZE = 2;
    private static final int RTP_HEVC_FU_HEADER_SIZE = 1;
    private static final int RTP_HEVC_DONL_FIELD_SIZE = 2;
    private static final int RTP_HEVC_DOND_FIELD_SIZE = 1;
    private static final int RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE = 2;
    private static final int HEVC_SPECIFIED_NAL_UNIT_TYPES = 48;

    private ByteBuffer payload;
    private byte[] rawPayload;

    private int forbidden;
    private int type;
    private int lid;
    private int tid;

    //private FUHeader fuHeader;
    //private NalUHeader nalUHeader;

    public H265Packet(int capacity, boolean allocateDirect) {
        super(capacity, allocateDirect);
    }

    public void handle () {
        this.getPayload(this.getRawData());
        payload = this.getBuffer();
        rawPayload = this.getRawData();

        forbidden = payload.get(0);
        lid = 0;
        tid = 0;
    }

    public void getPayloadHeader () {

    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public byte[] getRawPayload() {
        return rawPayload;
    }

    public int getForbidden() {
        return forbidden;
    }

    public int getType() {
        byte[] typeBytes = new byte[6];
        System.arraycopy(rawPayload, 1, typeBytes, 0, 6);
        System.out.println("TypeBytes: "+ Arrays.toString(typeBytes));
        ByteBuffer buffer = ByteBuffer.wrap(typeBytes);
        int typeInt = buffer.getInt();
        type = (typeInt) & 0b111111;

        switch (type) {
            case 0:
                System.out.println("TRAIL_N");
                break;
            case 1:
                System.out.println("TRAIL_R");
                break;
            case 2:
                System.out.println("TSA_N");
                break;
            case 3:
                System.out.println("TSA_R");
                break;
            case 4:
                System.out.println("STSA_N");
                break;
            case 5:
                System.out.println("STSA_R");
                break;
            case 6:
                System.out.println("RADL_N");
                break;
            case 7:
                System.out.println("RADL_R");
        }

        if (type == 24) {
            System.out.println("Type is RSV_VCL24. (24)");
        }
        return type;
    }

    public int getLid() {
        return lid;
    }

    public int getTid() {
        return tid;
    }

    public void unPackAp () {

    }

    public void unPackFu () {

    }

    public void unPackNALU () {

    }

}

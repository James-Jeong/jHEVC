package media.core.rtp.h265;

import media.core.rtp.RtpPacket;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private boolean isDonlUsing;
    private boolean isDondUsing;

    private int forbidden;
    private int type;
    private int lid;
    private int tid;

    //private FUHeader fuHeader;
    //private NalUHeader nalUHeader;

    ////////////////////////////////////////////////////////////////////

    public H265Packet(int capacity, boolean allocateDirect) {
        super(capacity, allocateDirect);
    }

    public void handle () {
        this.getPayload(this.getRawData());
        payload = this.getBuffer();

        rawPayload = new byte[this.getRawData().length - RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(this.getRawData(), RtpPacket.FIXED_HEADER_SIZE, rawPayload, 0, this.getRawData().length - RtpPacket.FIXED_HEADER_SIZE);

        isDonlUsing = false;
        isDondUsing = false;

        System.out.println("Raw Data Length: " + this.rawPayload.length);
        System.out.println("RTP Version: " + this.getVersion());
        System.out.println("SSRC: " + this.getSyncSource());
        System.out.println("Payload Type: " + this.getPayloadType());
        System.out.println("Payload Length: " + this.getPayloadLength());
        System.out.println("Payload: " + Arrays.toString(this.rawPayload));

        unPackHeader();

        switch (type) {
            case 48:
                System.out.println("AP is detected.\n");
                unPackAp();
                break;
            case 49:
                System.out.println("FU Packet is detected.\n");
                unPackFu();
                break;
            case 50:
                System.out.println("PACI Packet is detected.\n");
                break;
            default:
                break;
        }
    }

    ////////////////////////////////////////////////////////////////////

    /** NAL Unit Header
     0               1
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |F|    Type   |  LayerId  | TID |
     +-------------+-----------------+
     Forbidden zero(F) : 1 bit
     NAL unit type(Type) : 6 bits
     NUH layer ID(LayerId) : 6 bits
     NUH temporal ID plus 1 (TID) : 3 bits
     Total 16 bits > 2 bytes
     */

    public void unPackHeader () {
        this.setForbidden();
        this.setType();
        this.setLid();
        this.setTid();
    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public byte[] getRawPayload() {
        return rawPayload;
    }

    private static String bytesToBinaryString(Byte b) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            builder.append(((0x80 >>> i) & b) == 0 ? '0' : '1');
        }

        return builder.toString();
    }

    public void setForbidden() {
        byte forbiddenByte = rawPayload[0];
        forbidden = forbiddenByte & 0b10000000; // 0x80
        System.out.println("Forbidden: " + forbidden + " (" + bytesToBinaryString(forbiddenByte) + ")");
    }

    public int getForbidden() {
        return forbidden;
    }

    public void setType() {
        byte typeByte = rawPayload[0];
        type = typeByte & 0b01111110;
        System.out.println("Type: " + checkType() + " (" + type + ", " + bytesToBinaryString(typeByte) + ")");
    }

    public long getType() {
        return type;
    }

    public void setLid() {
        byte res = 0b000000000;
        byte[] lidBytes = new byte[2];
        System.arraycopy(rawPayload, 0, lidBytes, 0, 2);
        System.out.println("1: " + bytesToBinaryString(lidBytes[0]) + ", 2: " + bytesToBinaryString(lidBytes[1]));
        byte temp1 = (byte) (lidBytes[0] & 0b00000001);
        byte temp2 = (byte) (lidBytes[1] & 0b11111000);
        System.out.println("1: " + bytesToBinaryString(temp1) + ", 2: " + bytesToBinaryString(temp2));
        res &= temp1;
        res <<= 7; // shift left 7 bits
        res &= temp2;
        lid = res;
        System.out.println("Layer ID: " + lid);
    }

    public long getLid() {
        return lid;
    }

    public void setTid() {
        byte tidByte = rawPayload[1];
        tid = tidByte & 0b00000111;
        System.out.println("Temporal ID: " + tid + " (" + bytesToBinaryString(tidByte) + ")");
    }

    public int getTid() {
        return tid;
    }

    ////////////////////////////////////////////////////////////////////

    // 4.4.2. Aggregation Packets (APs)
    /**
     0               1               2               3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |                          RTP Header                           |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |      PayloadHdr (Type=48)     |           NALU 1 DONL         |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |           NALU 1 Size         |            NALU 1 HDR         |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |                                                               |
     |                         NALU 1 Data . . .                     |
     |                                                               |
     +     . . .     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |               |  NALU 2 DOND  |            NALU 2 Size        |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |          NALU 2 HDR           |                               |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+            NALU 2 Data        |
     |                                                               |
     |         . . .                 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |                               :    ...OPTIONAL RTP padding    |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

    public void unPackAp () {
        final int NALU_COUNT = 2;
        List<byte[]> naluList = new ArrayList<>(NALU_COUNT);
        //long timestamp = this.getTimestamp();
        //int len = this.getPayloadLength();

        if (rawPayload.length <= 2) { return; }
        byte[] firstNaluBuf = new byte[rawPayload.length - RTP_HEVC_PAYLOAD_HEADER_SIZE];
        byte[] secondNaluBuf;
        System.arraycopy(rawPayload, RTP_HEVC_PAYLOAD_HEADER_SIZE, firstNaluBuf, 0, rawPayload.length - RTP_HEVC_PAYLOAD_HEADER_SIZE); // ignore PayloadHdr

        int curLen = 0;
        for (int i = 0; i < NALU_COUNT; i++) {
            byte[] curNalu;
            if (i == 0) {
                int curNaluSize = getNaluSize(firstNaluBuf, 0);
                curNalu = new byte[curNaluSize]; // find first nalu size
                System.arraycopy(firstNaluBuf, RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE, curNalu, 0, curNaluSize); // nalu hdr + body
                naluList.add(curNalu); // add to list
                curLen = RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE + curNaluSize; // DONL size + NALU size + NALU len(hdr + body)
                System.out.println("First NALU len: " + curLen + " bytes, remaining: " + (firstNaluBuf.length - curLen) + " bytes");
            } else {
                if (curLen >= firstNaluBuf.length) {
                    System.out.println("Fail to unpack the AP. Second NALU is not exist.");
                    return;
                }

                int curNaluSize = getNaluSize(firstNaluBuf, curLen);
                secondNaluBuf = new byte[firstNaluBuf.length - curLen];
                System.arraycopy(firstNaluBuf, curLen, secondNaluBuf, 0, firstNaluBuf.length - curLen);

                curNalu = new byte[curNaluSize];
                System.arraycopy(secondNaluBuf, RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE, curNalu, 0, curNaluSize);
                naluList.add(curNalu);
                curLen += RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE + curNaluSize; // DONL size + NALU size + NALU len(hdr + body)
                System.out.println("Second NALU len: " + curLen + " bytes, remaining: " + (firstNaluBuf.length - curLen) + " bytes");
            }

            System.out.println("Cur NALU: " + Arrays.toString(curNalu));
        }
    }

    private int getNaluSize (byte[] buf, int startIndex) {
        byte[] lenBytes = new byte[2];
        System.arraycopy(buf, startIndex, lenBytes, 0, 2);
        System.out.println("LenBytes: "+ Arrays.toString(lenBytes));

        int len = (lenBytes[0] << 8) + (lenBytes[1] & 0xff);
        System.out.println("Cur NALU size: " + len);
        return len;
    }

    public void packAp (byte[] nalu1, byte[] nalu2) {
        if (nalu1 == null || nalu1.length == 0 || nalu2 == null || nalu2.length == 0) { return; }
        if (nalu1.length <= RtpPacket.FIXED_HEADER_SIZE || nalu2.length <= RtpPacket.FIXED_HEADER_SIZE) { return; }

        byte[] rtpPayloadNalu1 = new byte[nalu1.length - RtpPacket.FIXED_HEADER_SIZE];
        byte[] rtpHdrNalu1 = new byte[RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(nalu1, 0, rtpHdrNalu1, 0, RtpPacket.FIXED_HEADER_SIZE);
        System.arraycopy(nalu1, RtpPacket.FIXED_HEADER_SIZE, rtpPayloadNalu1, 0, nalu1.length - RtpPacket.FIXED_HEADER_SIZE);

        byte[] rtpPayloadNalu2 = new byte[nalu2.length - RtpPacket.FIXED_HEADER_SIZE];
        byte[] rtpHdrNalu2 = new byte[RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(nalu2, 0, rtpHdrNalu2, 0, RtpPacket.FIXED_HEADER_SIZE);
        System.arraycopy(nalu2, RtpPacket.FIXED_HEADER_SIZE, rtpPayloadNalu2, 0, nalu2.length - RtpPacket.FIXED_HEADER_SIZE);

        System.out.println("Starting to aggregate the packets...");
        System.out.println("NALU1: " + Arrays.toString(rtpPayloadNalu1) + " (len=" + rtpPayloadNalu1.length + ")");
        System.out.println("NALU2: " + Arrays.toString(rtpPayloadNalu2) + " (len=" + rtpPayloadNalu2.length + ")");

        byte[] apData = new byte[RtpPacket.FIXED_HEADER_SIZE + nalu1.length + nalu2.length + 8]; // 8 bytes:
        System.arraycopy(rtpHdrNalu1, 0, apData, 0, RtpPacket.FIXED_HEADER_SIZE);
        int index = RtpPacket.FIXED_HEADER_SIZE;
        apData[index] = 48;
        apData[1 + index] = 1;

        apData[2 + index] = (byte) (rtpPayloadNalu1.length >> 8);
        apData[3 + index] = (byte) (rtpPayloadNalu1.length);
        System.out.println(apData[2 + index] + "(" + bytesToBinaryString(apData[2 + index]) + "), "
                + apData[3 + index] + "(" + bytesToBinaryString(apData[3 + index]) + ")");

        apData[rtpPayloadNalu1.length + 4 + index] = (byte) (rtpPayloadNalu2.length >> 8);
        apData[rtpPayloadNalu1.length + 5 + index] = (byte) (rtpPayloadNalu2.length & 0xff);

        System.arraycopy(rtpPayloadNalu1, 0, apData, 4 + index, rtpPayloadNalu1.length);
        System.arraycopy(rtpPayloadNalu2, 0, apData, 6 + index + rtpPayloadNalu1.length, rtpPayloadNalu2.length);

        System.out.println("AP: " + Arrays.toString(apData));
        System.out.println("Done.");

        wrap(apData);
    }

    ////////////////////////////////////////////////////////////////////

    public void unPackFu () {

    }

    ////////////////////////////////////////////////////////////////////

    private String checkType () {
        String typeStr;
        switch (type) {
            case 0: typeStr = "TRAIL_N"; break;
            case 1: typeStr = "TRAIL_R"; break;
            case 2: typeStr = "TSA_N"; break;
            case 3: typeStr = "TSA_R"; break;
            case 4: typeStr = "STSA_N"; break;
            case 5: typeStr = "STSA_R"; break;
            case 6: typeStr = "RADL_N"; break;
            case 7: typeStr = "RADL_R"; break;
            case 8: typeStr = "RASL_N"; break;
            case 9: typeStr = "RASL_R"; break;
            case 10: typeStr = "RSV_VCL_N10"; break;
            case 11: typeStr = "RSV_VCL_R11"; break;
            case 12: typeStr = "RSV_VCL_N12"; break;
            case 13: typeStr = "RSV_VCL_R13"; break;
            case 14: typeStr = "RSV_VCL_N14"; break;
            case 15: typeStr = "RSV_VCL_R15"; break;
            case 16: typeStr = "BLA_W_LP"; break;
            case 17: typeStr = "BLA_W_RADL"; break;
            case 18: typeStr = "BLA_N_LP"; break;
            case 19: typeStr = "IDR_W_RADL"; break;
            case 20: typeStr = "KDR_N_LP"; break;
            case 21: typeStr = "CRA_NUT"; break;
            case 22: typeStr = "RSV_IRAP_VCL22"; break;
            case 23: typeStr = "RSV_IRAP_VCL23"; break;
            case 24: case 25: case 26: case 27: case 28: case 29: case 30: case 31:
                typeStr = "RSV_VCL" + type; break;
            case 32: typeStr = "VPS_NUT"; break;
            case 33: typeStr = "SPS_NUT"; break;
            case 34: typeStr = "PPS_NUT"; break;
            case 35: typeStr = "AUD_NUT"; break;
            case 36: typeStr = "EOS_NUT"; break;
            case 37: typeStr = "EOB_NUT"; break;
            case 38: typeStr = "FD_NUT"; break;
            case 39: typeStr = "PREFIX_SEI_NUT"; break;
            case 40: typeStr = "SUFFIX_SEI_NUT"; break;
            case 41: case 42: case 43: case 44: case 45: case 46: case 47:
                typeStr = "RSV_NVCL" + type; break;
            default:
                typeStr = "UNSPEC" + type;
                break;
        }

        return typeStr;
    }

}

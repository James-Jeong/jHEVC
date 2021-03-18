package media.core.rtp.h265;

import media.core.rtp.RtpPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class H265Decoder {

    public H265Decoder() {
        // Nothing
    }

    public void handle (H265Packet h265Packet) {
        if (h265Packet == null) { return; }

        System.out.println("\tRaw Data Length: " + h265Packet.getRawPayload().length);
        System.out.println("\tRTP Version: " + h265Packet.getVersion());
        System.out.println("\tSSRC: " + h265Packet.getSyncSource());
        System.out.println("\tPayload Type: " + h265Packet.getPayloadType());
        System.out.println("\tPayload Length: " + h265Packet.getPayloadLength());
        System.out.println("\tPayload: " + Arrays.toString(h265Packet.getRawPayload()));

        unPackHeader(h265Packet);
        switch (h265Packet.getType()) {
            case 48:
                System.out.println("AP is detected.\n");
                List<byte[]> unPackedAps = unPackAp(h265Packet);
                break;
            case 49:
                System.out.println("FU Packet is detected.\n");
                H265Packet unPackedFu = unPackFu(h265Packet);
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
     *      0               1
     *      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |F|    Type   |  LayerId  | TID |
     *      +-------------+-----------------+
     *      Forbidden zero(F) : 1 bit
     *      NAL unit type(Type) : 6 bits
     *      NUH layer ID(LayerId) : 6 bits
     *      NUH temporal ID plus 1 (TID) : 3 bits
     *      Total 16 bits > 2 bytes
     */

    public void unPackHeader (H265Packet h265Packet) {
        if (h265Packet.getRawPayload() == null) { return; }
        setForbidden(h265Packet);
        setType(h265Packet);
        setLid(h265Packet);
        setTid(h265Packet);
    }

    public void setForbidden(H265Packet h265Packet) {
        byte forbiddenByte = h265Packet.getRawPayload()[0];
        int forbidden = forbiddenByte & 0b10000000; // 0x80
        System.out.println("\tForbidden: " + forbidden + " (" + bytesToBinaryString(forbiddenByte) + ")");
        h265Packet.setForbidden(forbidden);
    }

    public void setType(H265Packet h265Packet) {
        byte typeByte = h265Packet.getRawPayload()[0];
        int type = typeByte & 0b01111110;
        System.out.println("\tType: " + checkType(type) + " (" + type + ", " + bytesToBinaryString(typeByte) + ")");
        h265Packet.setType(type);
    }

    public void setLid(H265Packet h265Packet) {
        byte res = 0b000000000;
        byte[] lidBytes = new byte[2];
        System.arraycopy(h265Packet.getRawPayload(), 0, lidBytes, 0, 2);
        byte temp1 = (byte) (lidBytes[0] & 0b00000001);
        byte temp2 = (byte) (lidBytes[1] & 0b11111000);
        res &= temp1;
        res <<= 7; // shift left 7 bits
        res &= temp2;
        int lid = res;
        System.out.println("\tLayer ID: " + lid + " (" + bytesToBinaryString(temp1) + bytesToBinaryString(temp2) + ")");
        h265Packet.setLid(lid);
    }

    public void setTid(H265Packet h265Packet) {
        byte tidByte = h265Packet.getRawPayload()[1];
        int tid = tidByte & 0b00000111;
        System.out.println("\tTemporal ID: " + tid + " (" + bytesToBinaryString(tidByte) + ")");
        h265Packet.setTid(tid);
    }

    ////////////////////////////////////////////////////////////////////

    // Aggregation Packets (APs)
    /**
     *      0               1               2               3
     *      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                          RTP Header                           |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |      PayloadHdr (Type=48)     |           NALU 1 DONL         |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |           NALU 1 Size         |            NALU 1 HDR         |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |                         NALU 1 Data . . .                     |
     *      |                                                               |
     *      +     . . .     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |               |  NALU 2 DOND  |            NALU 2 Size        |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |          NALU 2 HDR           |                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+            NALU 2 Data        |
     *      |                                                               |
     *      |         . . .                 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                               :    ...OPTIONAL RTP padding    |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

    public List<byte[]> unPackAp (H265Packet h265Packet) {
        List<byte[]> naluList = new ArrayList<>();
        //long timestamp = this.getTimestamp();
        //int len = this.getPayloadLength();

        byte[] rawPayload = h265Packet.getRawPayload();
        if (rawPayload == null || rawPayload.length == 0) { return Collections.emptyList(); }

        int rawPayloadLength = rawPayload.length;
        if (rawPayloadLength <= 2) { return Collections.emptyList(); }

        byte[] firstNaluBuf = new byte[rawPayloadLength - H265Packet.RTP_HEVC_PAYLOAD_HEADER_SIZE];
        byte[] secondNaluBuf;
        System.arraycopy(rawPayload, H265Packet.RTP_HEVC_PAYLOAD_HEADER_SIZE, firstNaluBuf, 0, rawPayload.length - H265Packet.RTP_HEVC_PAYLOAD_HEADER_SIZE); // ignore PayloadHdr

        int i = 0;
        int curLen = 0;
        int curNaluSize;
        while (true) {
            byte[] curNalu;
            if (i == 0) {
                curNaluSize = getNaluSize(firstNaluBuf, 0);
                curNalu = new byte[curNaluSize]; // find first nalu size
                System.arraycopy(firstNaluBuf, H265Packet.RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE, curNalu, 0, curNaluSize); // nalu hdr + body

                naluList.add(curNalu); // add to list
                curLen = H265Packet.RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE + curNaluSize; // DONL size + NALU size + NALU len(hdr + body)
            } else {
                curNaluSize = getNaluSize(firstNaluBuf, curLen);
                if (curNaluSize == 0) {
                    break;
                }

                secondNaluBuf = new byte[firstNaluBuf.length - curLen];
                System.arraycopy(firstNaluBuf, curLen, secondNaluBuf, 0, firstNaluBuf.length - curLen);

                curNalu = new byte[curNaluSize];
                System.arraycopy(secondNaluBuf, H265Packet.RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE, curNalu, 0, curNaluSize);
                naluList.add(curNalu);

                curLen += H265Packet.RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE + curNaluSize; // DONL size + NALU size + NALU len(hdr + body)
            }

            System.out.println("\tCur NALU size: " + curNaluSize);
            System.out.println("\t[" + i + "] NALU len: " + curLen + " bytes, remaining: " + (firstNaluBuf.length - curLen) + " bytes");
            System.out.println("\tCur NALU: " + Arrays.toString(curNalu));
            i++;
        }

        System.out.println("Success to unpack the AP.\n");
        return naluList;
    }

    ////////////////////////////////////////////////////////////////////

    // Fragmentation Units (FUs)
    /**
     * The structure of FU header
     *   +---------------+
     *   |0|1|2|3|4|5|6|7|
     *   +-+-+-+-+-+-+-+-+
     *   |S|E|  FuType   |
     *   +---------------+
     * S = variable
     * E = variable
     * FuType = NAL unit type
     * The first package: S=1, E=0;
     * Tundish: S=0, E=0
     * The last package: S=0, E=1
     */

    /**
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |    PayloadHdr (Type=49)       |   FU header   | DONL (cond)   |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-|
     *    | DONL (cond)   |                                               |
     *    |-+-+-+-+-+-+-+-+                                               |
     *    |                         FU payload                            |
     *    |                                                               |
     *    |                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                               :...OPTIONAL RTP padding        |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

    public H265Packet unPackFu (H265Packet h265Packet) {
        byte[] rawPayload = h265Packet.getRawPayload();
        if (rawPayload == null || rawPayload.length == 0) { return null; }
        if (rawPayload.length <= 2) { return null; }

        //H265Packet hevcPacket = new H265Packet(RtpPacket.RTP_PACKET_MAX_SIZE, true);
        byte[] totalData = new byte[RtpPacket.RTP_PACKET_MAX_SIZE];

        // TODO: Aggregate FUs


        //hevcPacket.wrap(totalData);
        //return hevcPacket;
        return null;
    }

    ////////////////////////////////////////////////////////////////////

    private int getNaluSize (byte[] buf, int startIndex) {
        byte[] lenBytes = new byte[2];
        System.arraycopy(buf, startIndex, lenBytes, 0, 2);
        return (lenBytes[0] << 8) + (lenBytes[1] & 0xff);
    }

    private static String bytesToBinaryString(Byte b) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            builder.append(((0x80 >>> i) & b) == 0 ? '0' : '1');
        }

        return builder.toString();
    }

    private String checkType (int type) {
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

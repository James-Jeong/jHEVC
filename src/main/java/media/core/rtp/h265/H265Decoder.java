package media.core.rtp.h265;

import media.core.rtp.RtpPacket;
import media.core.rtp.h265.base.FUPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class H265Decoder {

    private static final Logger logger = LoggerFactory.getLogger(H265Decoder.class);

    private FUPosition curFuPosition = FUPosition.NONE;
    private final List<H265Packet> fuList = new ArrayList<>();

    public H265Decoder() {
        // Nothing
    }

    public void handle (H265Packet h265Packet) {
        if (h265Packet == null) { return; }

        logger.debug("\tRaw Data Length: {}", h265Packet.getRawPayload().length);
        logger.debug("\tRTP Version: {}", h265Packet.getVersion());
        logger.debug("\tSSRC: {}", h265Packet.getSyncSource());
        logger.debug("\tPayload Type: {}", h265Packet.getPayloadType());
        logger.debug("\tPayload Length: {}", h265Packet.getPayloadLength());
        logger.debug("\tRaw data: {}", h265Packet.getRawData());
        logger.debug("\tPayload: {}", h265Packet.getRawPayload());

        unPackHeader(h265Packet);
        switch (h265Packet.getType()) {
            case 48:
                logger.debug("AP is detected.");
                List<H265Packet> unPackedAps = unPackAp(h265Packet);
                for (H265Packet unpackedPacket : unPackedAps) {
                    handle(unpackedPacket);
                }
                break;
            case 49:
                logger.debug("FU is detected.");
                H265Packet unPackedFu = unPackFu(h265Packet);
                break;
            case 50:
                logger.debug("PACI is detected. Discarded.");
                break;
            default:
                break;
        }
    }

    ////////////////////////////////////////////////////////////////////

    /** Single NAL Unit
     *    0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |           PayloadHdr          |      DONL (conditional)       |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                                                               |
     *    |                  NAL unit payload data                        |
     *    |                                                               |
     *    |                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                               :...OPTIONAL RTP padding        |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

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

    private void unPackHeader (H265Packet h265Packet) {
        if (h265Packet.getRawData() == null) { return; }
        setForbidden(h265Packet);
        setType(h265Packet);
        setLid(h265Packet);
        setTid(h265Packet);
    }

    private void setForbidden(H265Packet h265Packet) {
        byte forbiddenByte = h265Packet.getRawPayload()[0];
        int forbidden = forbiddenByte & 0b10000000; // 0x80
        logger.debug("\tForbidden: {} ({})", forbidden, bytesToBinaryString(forbiddenByte));
        h265Packet.setForbidden(forbidden);
    }

    private void setType(H265Packet h265Packet) {
        byte typeByte = h265Packet.getRawPayload()[0];
        int type = typeByte & 0b01111110;
        type >>= 1;
        logger.debug("\tType: {} ({}, {})", checkType(type), type, bytesToBinaryString(typeByte));
        h265Packet.setType(type);
    }

    private void setLid(H265Packet h265Packet) {
        byte res = 0b000000000;
        byte[] lidBytes = new byte[2];
        System.arraycopy(h265Packet.getRawPayload(), 0, lidBytes, 0, 2);
        byte temp1 = (byte) (lidBytes[0] & 0b00000001);
        byte temp2 = (byte) (lidBytes[1] & 0b11111000);
        res &= temp1;
        res <<= 7; // shift left 7 bits
        res &= temp2;
        int lid = res;
        logger.debug("\tLayer ID: {} ({} {})", lid, bytesToBinaryString(temp1), bytesToBinaryString(temp2));
        h265Packet.setLid(lid);
    }

    private void setTid(H265Packet h265Packet) {
        byte tidByte = h265Packet.getRawPayload()[1];
        int tid = tidByte & 0b00000111;
        logger.debug("\tTemporal ID: {} ({})", tid, bytesToBinaryString(tidByte));
        h265Packet.setTid(tid);
    }

    ////////////////////////////////////////////////////////////////////

    /** Aggregation Packet (AP)
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

    private List<H265Packet> unPackAp (H265Packet h265Packet) {
        logger.info("Starting to unpack AP...");

        // 2) Check AP Packet Payload (hdr + body) null or length
        if (h265Packet == null) {
            logger.warn("Packet is null. Fail to unpack AP.");
            return Collections.emptyList();
        }

        byte[] rawData = h265Packet.getRawData();
        if (rawData == null || rawData.length == 0) {
            logger.warn("Raw data is null or empty. Fail to unpack AP.");
            return Collections.emptyList();
        }

        int rawDataLength = rawData.length;
        if (rawDataLength <= RtpPacket.FIXED_HEADER_SIZE) {
            logger.warn("Packet is too short. Fail to unpack AP.");
            return Collections.emptyList();
        }

        // 2) Ready for packing Divided AP Packet list
        List<H265Packet> naluList = new ArrayList<>();
        byte[] rtpHeader = new byte[RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(rawData, 0, rtpHeader, 0, RtpPacket.FIXED_HEADER_SIZE);

        byte[] rawPayload = new byte[rawDataLength - RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(rawData, RtpPacket.FIXED_HEADER_SIZE, rawPayload, 0, rawData.length - RtpPacket.FIXED_HEADER_SIZE); // ignore PayloadHdr

        int totalDataLen = 0;
        int curNaluSize;

        while (true) {
            if (totalDataLen >= rawPayload.length) {
                break;
            }

            if (totalDataLen == 0) {
                totalDataLen += H265Packet.RTP_HEVC_PAYLOAD_HEADER_SIZE;
                if (h265Packet.isDonlUsing()) {
                    totalDataLen += H265Packet.RTP_HEVC_DONL_FIELD_SIZE;
                }
            }

            curNaluSize = getNaluSize(rawPayload, totalDataLen);
            if (curNaluSize <= 0) {
                break;
            }
            totalDataLen += H265Packet.RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE;
            if (h265Packet.isDondUsing()) {
                totalDataLen += H265Packet.RTP_HEVC_DOND_FIELD_SIZE;
            }

            // 3) Divide AP Packet & Add to the list
            byte[] curNalu = new byte[RtpPacket.FIXED_HEADER_SIZE + curNaluSize + H265Packet.RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE];

            // TODO: Set RTP Header
            System.arraycopy(rtpHeader, 0, curNalu, 0, RtpPacket.FIXED_HEADER_SIZE); // [RTP Header]
            System.arraycopy(rawPayload, totalDataLen, curNalu, RtpPacket.FIXED_HEADER_SIZE, curNaluSize); // [NALU Payload (hdr + body)]

            logger.debug("\tNALU len: {} (bytes), remaining: {} (bytes)", totalDataLen, (rawPayload.length - totalDataLen));
            logger.debug("\tCur NALU: (payloadLen={}) {}", curNaluSize, curNalu);
            logger.debug("---------------------------");

            naluList.add(new H265Packet(curNalu, RtpPacket.RTP_PACKET_MAX_SIZE, true)); // [RTP Header] + [NALU Hdr + NALU Body]
            totalDataLen += curNaluSize;
        }

        if (naluList.isEmpty() || totalDataLen == 0) {
            logger.info("Fail to unpack AP. (listSize={}, totalLen={})", naluList.size(), totalDataLen);
        } else {
            logger.info("Success to unpack AP. (listSize={}, totalLen={})", naluList.size(), totalDataLen);
        }

        return naluList;
    }

    ////////////////////////////////////////////////////////////////////

    /** The structure of FU header
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

    /** Fragmentation Unit (FU)
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

    /**
     * 1. Remove the 12 bytes RTP header and 3 bytes of FU header.
     * 2. Combine all the FU packets till the end packet is found/received to from a Video Encoded frame.
     * 3. The Combined all packets (if multiple packets form a frame) to form Video Frame, it can be feed to the decoder.
     */
    private H265Packet unPackFu (H265Packet h265Packet) {
        logger.info("Starting to unpack FU...");

        // 1) Check FU Packet Payload (hdr + body) null or length
        byte[] rawPayload = h265Packet.getRawData();
        if (rawPayload == null || rawPayload.length == 0) {
            logger.warn("Payload is null. Fail to unpack FU.");
            return null;
        }
        if (rawPayload.length <= H265Packet.RTP_HEVC_PAYLOAD_HEADER_SIZE) {
            logger.warn("Payload is too short. Fail to unpack FU.");
            return null;
        }

        // 2) Ready for unpacking FU Payload
        int packetLength = h265Packet.getLength();
        int payloadLength = packetLength - RtpPacket.FIXED_HEADER_SIZE;
        byte[] rtpHdrNalu = new byte[RtpPacket.FIXED_HEADER_SIZE];
        byte[] rtpPayloadNalu = new byte[payloadLength];
        System.arraycopy(rawPayload, 0, rtpHdrNalu, 0, RtpPacket.FIXED_HEADER_SIZE);
        System.arraycopy(rawPayload, RtpPacket.FIXED_HEADER_SIZE, rtpPayloadNalu, 0, payloadLength);

        int totalHdrSize = H265Packet.RTP_HEVC_PAYLOAD_HEADER_SIZE + H265Packet.RTP_HEVC_FU_HEADER_SIZE;
        if (h265Packet.isDonlUsing()) {
            totalHdrSize += H265Packet.RTP_HEVC_DONL_FIELD_SIZE;
        }

        byte[] header = new byte[totalHdrSize];
        System.arraycopy(rtpPayloadNalu, 0, header, 0, totalHdrSize);
        byte fuHeader = header[2];
        //int type = fuHeader & 0b00111111; // expected: NALU Type
        int start = fuHeader & 0b10000000; // expected: 128 > S = 1
        int end = fuHeader & 0b01000000; // expected: 64 > E = 1

        // 3) Check FU Position
        FUPosition fuPosition = FUPosition.NONE;
        if (start == 128) {
            fuPosition = FUPosition.START;
        } else if (end == 0) {
            fuPosition = FUPosition.MIDDLE;
        } else {
            fuPosition = FUPosition.END;
        }

        if (curFuPosition == FUPosition.NONE) {
            if (fuPosition == FUPosition.START) {
                curFuPosition = fuPosition;
            } else {
                logger.warn("unPackFu: Wrong position. Not started yet. (expected: START) (decoder's FuPosition: {}, curFuPosition: {})",
                        getFuPositionStr(curFuPosition), getFuPositionStr(fuPosition));
                return null;
            }
        } else {
            if (curFuPosition == FUPosition.START) {
                if (fuPosition == FUPosition.MIDDLE) {
                    curFuPosition = fuPosition;
                } else {
                    fuList.clear();
                    curFuPosition = FUPosition.NONE;
                    logger.warn("unPackFu:  Unexpected position. (expected: MIDDLE) (decoder's FuPosition: {}, curFuPosition: {})",
                            getFuPositionStr(curFuPosition), getFuPositionStr(fuPosition));
                    return null;
                }
            } else if (curFuPosition == FUPosition.MIDDLE) {
                if (fuPosition == FUPosition.END) {
                    curFuPosition = fuPosition;
                } else {
                    fuList.clear();
                    curFuPosition = FUPosition.NONE;
                    logger.warn("unPackFu:  Unexpected position. (expected: END) (decoder's FuPosition: {}, curFuPosition: {})",
                            getFuPositionStr(curFuPosition), getFuPositionStr(fuPosition));
                    return null;
                }
            }
        }
        logger.debug("Cur FU Position : {}", getFuPositionStr(curFuPosition));

        // 4) Aggregate the FUs
        byte[] fuPayload = new byte[payloadLength - totalHdrSize];
        System.arraycopy(rtpPayloadNalu, totalHdrSize, fuPayload, 0, payloadLength - totalHdrSize);

        if (curFuPosition == FUPosition.END) {
            int totalLength = 0;
            for (H265Packet fuPacket : fuList) {
                totalLength += fuPacket.getLength();
            }

            byte[] totalData = new byte[totalLength];
            int accumLength = 0;
            for (H265Packet fuPacket : fuList) {
                byte[] data = fuPacket.getRawData();
                System.arraycopy(data, 0, totalData, accumLength, data.length);
                accumLength += data.length;
            }

            logger.debug("Total Aggregated FU: {},  len: {}", totalData, totalData.length);
            H265Packet totalPacket = new H265Packet(totalData, RtpPacket.RTP_PACKET_MAX_SIZE, true);
            fuList.clear();
            curFuPosition = FUPosition.NONE;
            logger.info("Success to unpack FU.");
            return totalPacket;
        } else if (curFuPosition == FUPosition.START) {
            byte[] rtpPacketExceptFuHeader = new byte[packetLength - totalHdrSize];
            System.arraycopy(rtpHdrNalu, 0, rtpPacketExceptFuHeader, 0, RtpPacket.FIXED_HEADER_SIZE);
            System.arraycopy(fuPayload, 0, rtpPacketExceptFuHeader, RtpPacket.FIXED_HEADER_SIZE, payloadLength - 3);

            H265Packet fuPacket = new H265Packet(rtpPacketExceptFuHeader, RtpPacket.RTP_PACKET_MAX_SIZE, true);
            this.fuList.add(fuPacket);
        } else {
            H265Packet fuPacket = new H265Packet(fuPayload, RtpPacket.RTP_PACKET_MAX_SIZE, true);
            this.fuList.add(fuPacket);
        }

        return null;
    }


    ////////////////////////////////////////////////////////////////////
    // Util Functions

    private String getFuPositionStr (FUPosition fuPosition) {
        if (fuPosition == FUPosition.NONE) {
            return "NONE";
        } else if (fuPosition == FUPosition.START) {
            return "START";
        } else if (fuPosition == FUPosition.MIDDLE) {
            return "MIDDLE";
        } else {
            return "END";
        }
    }

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
            case 1: typeStr = "TRAIL_R"; break; // General
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
            case 19: typeStr = "IDR_W_RADL"; break; // General
            case 20: typeStr = "KDR_N_LP"; break; // General
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

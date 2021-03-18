package media.core.rtp.h265;

import media.core.rtp.RtpPacket;
import media.core.rtp.h265.base.FUPosition;

import java.util.Arrays;

public class H265Encoder {

    public H265Encoder() {
        // Nothing
    }

    ////////////////////////////////////////////////////////////////////

    public H265Packet packAp (byte[] nalu1, byte[] nalu2) {
        if (nalu1 == null || nalu1.length == 0 || nalu2 == null || nalu2.length == 0) { return null; }
        if (nalu1.length <= RtpPacket.FIXED_HEADER_SIZE || nalu2.length <= RtpPacket.FIXED_HEADER_SIZE) { return null; }

        byte[] rtpPayloadNalu1 = new byte[nalu1.length - RtpPacket.FIXED_HEADER_SIZE];
        byte[] rtpHdrNalu1 = new byte[RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(nalu1, 0, rtpHdrNalu1, 0, RtpPacket.FIXED_HEADER_SIZE);
        System.arraycopy(nalu1, RtpPacket.FIXED_HEADER_SIZE, rtpPayloadNalu1, 0, nalu1.length - RtpPacket.FIXED_HEADER_SIZE);

        byte[] rtpPayloadNalu2 = new byte[nalu2.length - RtpPacket.FIXED_HEADER_SIZE];
        byte[] rtpHdrNalu2 = new byte[RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(nalu2, 0, rtpHdrNalu2, 0, RtpPacket.FIXED_HEADER_SIZE);
        System.arraycopy(nalu2, RtpPacket.FIXED_HEADER_SIZE, rtpPayloadNalu2, 0, nalu2.length - RtpPacket.FIXED_HEADER_SIZE);

        System.out.println("Starting to aggregate the packets...");
        System.out.println("\tNALU1: " + Arrays.toString(rtpPayloadNalu1) + " (len=" + rtpPayloadNalu1.length + ")");
        System.out.println("\tNALU2: " + Arrays.toString(rtpPayloadNalu2) + " (len=" + rtpPayloadNalu2.length + ")");

        byte[] apData = new byte[RtpPacket.FIXED_HEADER_SIZE + nalu1.length + nalu2.length + 8]; // 8 bytes:
        System.arraycopy(rtpHdrNalu1, 0, apData, 0, RtpPacket.FIXED_HEADER_SIZE);
        int index = RtpPacket.FIXED_HEADER_SIZE;
        apData[index] = 48;
        apData[1 + index] = 1;

        apData[2 + index] = (byte) (rtpPayloadNalu1.length >> 8);
        apData[3 + index] = (byte) (rtpPayloadNalu1.length);

        apData[rtpPayloadNalu1.length + 4 + index] = (byte) (rtpPayloadNalu2.length >> 8);
        apData[rtpPayloadNalu1.length + 5 + index] = (byte) (rtpPayloadNalu2.length & 0xff);

        System.arraycopy(rtpPayloadNalu1, 0, apData, 4 + index, rtpPayloadNalu1.length);
        System.arraycopy(rtpPayloadNalu2, 0, apData, 6 + index + rtpPayloadNalu1.length, rtpPayloadNalu2.length);

        System.out.println("\tAP: " + Arrays.toString(apData));
        System.out.println("Done.");

        return new H265Packet(apData, RtpPacket.RTP_PACKET_MAX_SIZE, true);
    }

    ////////////////////////////////////////////////////////////////////

    public H265Packet packFu (H265Packet h265Packet, FUPosition fuPosition) {
        byte[] buffer = new byte[h265Packet.getLength()];

        byte[] header = new byte[3];
        header[0] = 49;
        header[1] = 1;
        header[2] = (byte) h265Packet.getType();
        header[2] += 0x80;



        return new H265Packet(buffer, RtpPacket.RTP_PACKET_MAX_SIZE, true);
    }

}

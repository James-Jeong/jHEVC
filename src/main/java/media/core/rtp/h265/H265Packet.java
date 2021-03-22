package media.core.rtp.h265;

import media.core.rtp.RtpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class H265Packet extends RtpPacket {

    private static final Logger logger = LoggerFactory.getLogger(H265Packet.class);

    public static final int RTP_HEVC_PAYLOAD_HEADER_SIZE = 2;
    public static final int RTP_HEVC_FU_HEADER_SIZE = 1;
    public static final int RTP_HEVC_DONL_FIELD_SIZE = 2;
    public static final int RTP_HEVC_DOND_FIELD_SIZE = 1;
    public static final int RTP_HEVC_AP_NALU_LENGTH_FIELD_SIZE = 2;
    public static final int RTP_HEVC_TYPE_AP = 48;
    public static final int RTP_HEVC_TYPE_FU = 49;
    public static final int RTP_HEVC_TYPE_PACI = 50;

    private ByteBuffer payload;
    private byte[] rawPayload;

    private boolean isDonlUsing;
    private boolean isDondUsing;

    private int forbidden;
    private int type;
    private int lid;
    private int tid;

    ////////////////////////////////////////////////////////////////////

    public H265Packet (byte[] data, int capacity, boolean allocateDirect) {
        super(capacity, allocateDirect);
        initialize(data);
    }

    public void initialize (byte[] data) {
        if (data == null || data.length == 0) {
            logger.warn("Packet raw data is null or empty. Fail to initialize packet.");
            payload = null;
            rawPayload = null;
            forbidden = -1;
            type = -1;
            lid = -1;
            tid = -1;
            return;
        }

        this.wrap(data);
        this.getPayload(this.getRawData());
        payload = this.getBuffer();

        rawPayload = new byte[this.getRawData().length - RtpPacket.FIXED_HEADER_SIZE];
        System.arraycopy(this.getRawData(), RtpPacket.FIXED_HEADER_SIZE, rawPayload, 0, this.getRawData().length - RtpPacket.FIXED_HEADER_SIZE);

        isDonlUsing = false;
        isDondUsing = false;
    }

    ////////////////////////////////////////////////////////////////////

    public ByteBuffer getPayload() {
        return payload;
    }

    public byte[] getRawPayload() {
        return rawPayload;
    }

    public void setForbidden(int forbidden) {
        this.forbidden = forbidden;
    }

    public int getForbidden() {
        return forbidden;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public long getLid() {
        return lid;
    }

    public void setLid(int lid) {
        this.lid = lid;
    }

    public int getTid() {
        return tid;
    }

    public void setTid(int tid) {
        this.tid = tid;
    }

    public boolean isDonlUsing() {
        return isDonlUsing;
    }

    public void setDonlUsing(boolean donlUsing) {
        isDonlUsing = donlUsing;
    }

    public boolean isDondUsing() {
        return isDondUsing;
    }

    public void setDondUsing(boolean dondUsing) {
        isDondUsing = dondUsing;
    }
}

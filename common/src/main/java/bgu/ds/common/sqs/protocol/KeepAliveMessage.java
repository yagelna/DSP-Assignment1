package bgu.ds.common.sqs.protocol;

import java.util.Date;

public class KeepAliveMessage extends SqsMessage{
    private String instanceId;
    private Date timestamp;

    public KeepAliveMessage(String instanceId, Date timestamp) {
        super(SqsMessageType.KEEP_ALIVE);
        this.instanceId = instanceId;
        this.timestamp = timestamp;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}

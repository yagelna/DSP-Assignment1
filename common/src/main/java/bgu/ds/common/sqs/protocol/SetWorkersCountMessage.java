package bgu.ds.common.sqs.protocol;

public class SetWorkersCountMessage extends SqsMessage{
    private int workersCount;

    public SetWorkersCountMessage(int workersCount) {
        super(SqsMessageType.SET_WORKERS_COUNT);
        this.workersCount = workersCount;
    }

    public int getWorkersCount() {
        return workersCount;
    }
}

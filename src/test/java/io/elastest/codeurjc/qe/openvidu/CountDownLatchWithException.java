package io.elastest.codeurjc.qe.openvidu;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class CountDownLatchWithException {

    public class AbortedException extends Exception {

        private static final long serialVersionUID = 1L;

        public AbortedException(String errorMessage) {
            super(errorMessage);
        }
    }

    private CountDownLatch latch;
    private AtomicBoolean aborted = new AtomicBoolean(false);
    private String errorMessage = "";

    public CountDownLatchWithException(int countDown) {
        this.latch = new CountDownLatch(countDown);
    }

    public void await() throws AbortedException {
        try {
            this.latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            if (aborted.get()) {
                throw new AbortedException(errorMessage);
            }
        }
        if (aborted.get()) {
            throw new AbortedException(errorMessage);
        }
    }

    public synchronized void abort(String errorMessage) {
        this.aborted.set(true);
        this.errorMessage = errorMessage;
        while (this.latch.getCount() > 0) {
            this.countDown();
        }
    }

    public void countDown() {
        latch.countDown();
    }

}

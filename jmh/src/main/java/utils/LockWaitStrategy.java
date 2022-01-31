package utils;

import java.util.concurrent.locks.*;

public class LockWaitStrategy implements WaitStrategy {

    private ReentrantLock lock = new ReentrantLock();
    private Condition dataChanged = lock.newCondition();

    @Override
    public void block() throws InterruptedException {
        try {
            lock.lock();
            dataChanged.await();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release() {
        try {
            lock.lock();
            dataChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

}

package main.java.utils;

public interface WaitStrategy {
    void block() throws InterruptedException;

    void release();
}

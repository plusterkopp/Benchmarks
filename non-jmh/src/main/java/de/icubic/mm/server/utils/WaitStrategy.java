package de.icubic.mm.server.utils;

public interface WaitStrategy {
    void block() throws InterruptedException;

    void release();
}

package org.xvdr.extractor;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class PacketQueue  {

    final private static String TAG = "PacketQueue";

    private AdaptiveAllocator mAllocator;
    private ArrayDeque<SampleBuffer> mQueue;

    private MediaFormat mFormat;

    private long mLargestTimestampUs = 0;
    private long mSmallestTimestampUs = 0;

    public PacketQueue(int bufferCount, int bufferSize) {
        mAllocator = new AdaptiveAllocator(bufferCount, bufferSize);
        mQueue = new ArrayDeque<>(bufferCount);
    }

    synchronized public void format(MediaFormat format) {
        SampleBuffer holder = new SampleBuffer(format);
        mFormat = format;

        mQueue.add(holder);
    }

    synchronized public void sampleData(SampleBuffer buffer) {
        mLargestTimestampUs = Math.max(mLargestTimestampUs, buffer.timeUs);

        if(mSmallestTimestampUs == 0) {
            mSmallestTimestampUs = mLargestTimestampUs;
        }

        mQueue.add(buffer);
    }

    public MediaFormat getFormat() {
        return mFormat;
    }

    public boolean hasFormat() {
        return (mFormat != null);
    }

    public long bufferSizeMs() {
        return (mLargestTimestampUs - mSmallestTimestampUs) / 1000;
    }

    public long getBufferedPositionUs() {
        return mLargestTimestampUs;
    }

    private SampleBuffer poll() {
        SampleBuffer a = mQueue.poll();
        mAllocator.release(a);
        return a;
    }

    synchronized public boolean readFormat(MediaFormatHolder formatHolder) {
        SampleBuffer a = mQueue.peek();

        if(a == null || !a.isFormat()) {
            return false;
        }

        formatHolder.format = a.getFormat();
        poll();

        return true;
    }

    synchronized public boolean readSample(SampleHolder sampleHolder) {
        SampleBuffer a = mQueue.peek();

        if(a == null || !a.isSample()) {
            return false;
        }

        ByteBuffer buffer = a.data();
        buffer.rewind();

        sampleHolder.flags = a.flags;
        sampleHolder.timeUs = a.timeUs;
        sampleHolder.size = a.limit();
        sampleHolder.ensureSpaceForWrite(sampleHolder.size);
        sampleHolder.data.put(buffer); //put(a.data(), 0, sampleHolder.size);

        mSmallestTimestampUs = Math.max(mSmallestTimestampUs, sampleHolder.timeUs);
        poll();

        return true;
    }

    synchronized public SampleBuffer allocate(int bufferSize) {
        return mAllocator.allocate(bufferSize);
    }

    synchronized void release(SampleBuffer a) {
        mAllocator.release(a);
    }

    synchronized public boolean isEmpty() {
        return (mQueue.peek() == null);
    }

    synchronized public void clear() {
        mQueue.clear();
        mAllocator.releaseAll();
        mLargestTimestampUs = 0;
        mSmallestTimestampUs = 0;
    }
}

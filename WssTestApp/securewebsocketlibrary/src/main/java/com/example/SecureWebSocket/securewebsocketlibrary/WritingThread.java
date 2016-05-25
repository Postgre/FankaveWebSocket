/*
 * Copyright (C) 2015 Neo Visionaries Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//package com.neovisionaries.ws.client;
package com.example.SecureWebSocket.securewebsocketlibrary;

import static com.example.SecureWebSocket.securewebsocketlibrary.WebSocketState.CLOSED;
import static com.example.SecureWebSocket.securewebsocketlibrary.WebSocketState.CLOSING;
import java.io.IOException;
import java.util.LinkedList;
import com.example.SecureWebSocket.securewebsocketlibrary.StateManager.CloseInitiator;


class WritingThread extends Thread
{
    private static final int SHOULD_SEND     = 0;
    private static final int SHOULD_STOP     = 1;
    private static final int SHOULD_CONTINUE = 2;
    private static final int SHOULD_FLUSH    = 3;
    private static final int FLUSH_THRESHOLD = 1000;
    private final WebSocket mWebSocket;
    private final LinkedList<WebSocketFrame> mFrames;
    private final PerMessageCompressionExtension mPMCE;
    private boolean mStopRequested;
    private WebSocketFrame mCloseFrame;
    private boolean mFlushNeeded;
    private boolean mStopped;


    public WritingThread(WebSocket websocket)
    {
        super("WritingThread");

        mWebSocket = websocket;
        mFrames    = new LinkedList<WebSocketFrame>();
        mPMCE      = websocket.getPerMessageCompressionExtension();
    }


    @Override
    public void run()
    {
        try
        {
            main();
        }
        catch (Throwable t)
        {
            // An uncaught throwable was detected in the writing thread.
            WebSocketException cause = new WebSocketException(
                WebSocketError.UNEXPECTED_ERROR_IN_WRITING_THREAD,
                "An uncaught throwable was detected in the writing thread: " + t.getMessage(), t);

            // Notify the listeners.
            ListenerManager manager = mWebSocket.getListenerManager();
            manager.callOnError(cause);
            manager.callOnUnexpectedError(cause);
        }

        synchronized (this)
        {
            // Mainly for queueFrame().
            mStopped = true;
            notifyAll();
        }

        // Notify this writing thread finished.
        notifyFinished();
    }


    private void main()
    {
        mWebSocket.onWritingThreadStarted();

        while (true)
        {
            // Wait for frames to be queued.
            int result = waitForFrames();

            if (result == SHOULD_STOP)
            {
                break;
            }
            else if (result == SHOULD_FLUSH)
            {
                flushIgnoreError();
                continue;
            }
            else if (result == SHOULD_CONTINUE)
            {
                continue;
            }

            try
            {
                // Send frames.
                sendFrames(false);
            }
            catch (WebSocketException e)
            {
                // An I/O error occurred.
                break;
            }
        }

        try
        {
            // Send remaining frames, if any.
            sendFrames(true);
        }
        catch (WebSocketException e)
        {
            // An I/O error occurred.
        }
    }


    public void requestStop()
    {
        synchronized (this)
        {
            // Schedule stopping.
            mStopRequested = true;

            // Wake up this thread.
            notifyAll();
        }
    }


    public boolean queueFrame(WebSocketFrame frame)
    {
        synchronized (this)
        {
            while (true)
            {
                // If this thread has already stopped.
                if (mStopped)
                {
                    // Frames won't be sent any more. Not queued.
                    return false;
                }

                // If this thread has been requested to stop or has sent a
                // close frame to the server.
                if (mStopRequested || mCloseFrame != null)
                {
                    // Don't wait. Process the remaining task without delay.
                    break;
                }

                // If the frame is a control frame.
                if (frame.isControlFrame())
                {
                    // Queue the frame without blocking.
                    break;
                }

                // Get the upper limit of the queue size.
                int queueSize = mWebSocket.getFrameQueueSize();

                // If the upper limit is not set.
                if (queueSize == 0)
                {
                    // Add the frame to mFrames unconditionally.
                    break;
                }

                // If the current queue size has not reached the upper limit.
                if (mFrames.size() < queueSize)
                {
                    // Add the frame.
                    break;
                }

                try
                {
                    // Wait until the queue gets spaces.
                    wait();
                }
                catch (InterruptedException e)
                {
                }
            }

            // Add the frame to the queue.
            if (isHighPriorityFrame(frame))
            {
                // Add the frame at the first position so that it can be sent immediately.
                addHighPriorityFrame(frame);
            }
            else
            {
                // Add the frame at the last position.
                mFrames.addLast(frame);
            }

            // Wake up this thread.
            notifyAll();
        }

        // Queued.
        return true;
    }


    private static boolean isHighPriorityFrame(WebSocketFrame frame)
    {
        return (frame.isPingFrame() || frame.isPongFrame());
    }


    private void addHighPriorityFrame(WebSocketFrame frame)
    {
        int index = 0;

        // Determine the index at which the frame is added.
        // Among high priority frames, the order is kept in insertion order.
        for (WebSocketFrame f : mFrames)
        {
            // If a non high-priority frame was found.
            if (isHighPriorityFrame(f) == false)
            {
                break;
            }

            ++index;
        }

        mFrames.add(index, frame);
    }


    public void queueFlush()
    {
        synchronized (this)
        {
            mFlushNeeded = true;

            // Wake up this thread.
            notifyAll();
        }
    }


    private void flushIgnoreError()
    {
        try
        {
            flush();
        }
        catch (IOException e)
        {
        }
    }


    private void flush() throws IOException
    {
        mWebSocket.getOutput().flush();
    }


    private int waitForFrames()
    {
        synchronized (this)
        {
            // If this thread has been requested to stop.
            if (mStopRequested)
            {
                return SHOULD_STOP;
            }

            // If a close frame has already been sent.
            if (mCloseFrame != null)
            {
                return SHOULD_STOP;
            }

            // If the list of web socket frames to be sent is empty.
            if (mFrames.size() == 0)
            {
                // Check mFlushNeeded before calling wait().
                if (mFlushNeeded)
                {
                    mFlushNeeded = false;
                    return SHOULD_FLUSH;
                }

                try
                {
                    // Wait until a new frame is added to the list
                    // or this thread is requested to stop.
                    wait();
                }
                catch (InterruptedException e)
                {
                }
            }

            if (mStopRequested)
            {
                return SHOULD_STOP;
            }

            if (mFrames.size() == 0)
            {
                if (mFlushNeeded)
                {
                    mFlushNeeded = false;
                    return SHOULD_FLUSH;
                }

                // Spurious wakeup.
                return SHOULD_CONTINUE;
            }
        }

        return SHOULD_SEND;
    }


    private void sendFrames(boolean last) throws WebSocketException
    {
        // The timestamp at which the last flush was executed.
        long lastFlushAt = System.currentTimeMillis();

        while (true)
        {
            WebSocketFrame frame;

            synchronized (this)
            {
                // Pick up one frame from the queue.
                frame = mFrames.poll();

                // Mainly for queueFrame().
                notifyAll();

                // If the queue is empty.
                if (frame == null)
                {
                    // No frame to process.
                    break;
                }
            }

            // Send the frame to the server.
            sendFrame(frame);

            // If the frame is PING or PONG.
            if (frame.isPingFrame() || frame.isPongFrame())
            {
                // Deliver the frame to the server immediately.
                doFlush();
                lastFlushAt = System.currentTimeMillis();
                continue;
            }

            // If flush is not needed.
            if (isFlushNeeded(last) == false)
            {
                // Try to consume the next frame without flush.
                continue;
            }

            // Flush if long time has passed since the last flush.
            lastFlushAt = flushIfLongInterval(lastFlushAt);
        }

        if (isFlushNeeded(last))
        {
            doFlush();
        }
    }


    private boolean isFlushNeeded(boolean last)
    {
        return (last || mWebSocket.isAutoFlush() || mFlushNeeded || mCloseFrame != null);
    }


    private long flushIfLongInterval(long lastFlushAt) throws WebSocketException
    {
        // The current timestamp.
        long current = System.currentTimeMillis();

        // If sending frames has taken too much time since the last flush.
        if (FLUSH_THRESHOLD < (current - lastFlushAt))
        {
            // Flush without waiting for remaining frames to be processed.
            doFlush();

            // Update the timestamp at which the last flush was executed.
            return current;
        }
        else
        {
            // Flush is not needed now.
            return lastFlushAt;
        }
    }


    private void doFlush() throws WebSocketException
    {
        try
        {
            // Flush
            flush();

            synchronized (this)
            {
                mFlushNeeded = false;
            }
        }
        catch (IOException e)
        {
            // Flushing frames to the server failed.
            WebSocketException cause = new WebSocketException(
                WebSocketError.FLUSH_ERROR,
                "Flushing frames to the server failed: " + e.getMessage(), e);

            // Notify the listeners.
            ListenerManager manager = mWebSocket.getListenerManager();
            manager.callOnError(cause);
            manager.callOnSendError(cause, null);

            throw cause;
        }
    }


    private void sendFrame(WebSocketFrame frame) throws WebSocketException
    {
        // Compress the frame if appropriate.
        frame = compressFrame(frame);

        // Notify the listeners that the frame is about to be sent.
        mWebSocket.getListenerManager().callOnSendingFrame(frame);

        boolean unsent = false;

        // If a close frame has already been sent.
        if (mCloseFrame != null)
        {
            // Frames should not be sent to the server.
            unsent = true;
        }
        // If the frame is a close frame.
        else if (frame.isCloseFrame())
        {
            mCloseFrame = frame;
        }

        if (unsent)
        {
            // Notify the listeners that the frame was not sent.
            mWebSocket.getListenerManager().callOnFrameUnsent(frame);
            return;
        }

        // If the frame is a close frame.
        if (frame.isCloseFrame())
        {
            // Change the state to closing if its current value is
            // neither CLOSING nor CLOSED.
            changeToClosing();
        }

        try
        {
            // Send the frame to the server.
            mWebSocket.getOutput().write(frame);
        }
        catch (IOException e)
        {
            // An I/O error occurred when a frame was tried to be sent.
            WebSocketException cause = new WebSocketException(
                WebSocketError.IO_ERROR_IN_WRITING,
                "An I/O error occurred when a frame was tried to be sent: " + e.getMessage(), e);

            // Notify the listeners.
            ListenerManager manager = mWebSocket.getListenerManager();
            manager.callOnError(cause);
            manager.callOnSendError(cause, frame);

            throw cause;
        }

        // Notify the listeners that the frame was sent.
        mWebSocket.getListenerManager().callOnFrameSent(frame);
    }


    private void changeToClosing()
    {
        StateManager manager = mWebSocket.getStateManager();

        boolean stateChanged = false;

        synchronized (manager)
        {
            // The current state of the web socket.
            WebSocketState state = manager.getState();

            // If the current state is neither CLOSING nor CLOSED.
            if (state != CLOSING && state != CLOSED)
            {
                // Change the state to CLOSING.
                manager.changeToClosing(CloseInitiator.CLIENT);

                stateChanged = true;
            }
        }

        if (stateChanged)
        {
            // Notify the listeners of the state change.
            mWebSocket.getListenerManager().callOnStateChanged(CLOSING);
        }
    }


    private void notifyFinished()
    {
        mWebSocket.onWritingThreadFinished(mCloseFrame);
    }


    private WebSocketFrame compressFrame(WebSocketFrame frame)
    {
        // If Per-Message Compression is not enabled.
        if (mPMCE == null)
        {
            // No compression.
            return frame;
        }

        // If the frame is neither a TEXT frame nor a BINARY frame.
        if (frame.isTextFrame()   == false &&
            frame.isBinaryFrame() == false)
        {
            // No compression.
            return frame;
        }

        // If the frame is not the final frame.
        if (frame.getFin() == false)
        {
            // The compression must be applied to this frame and
            // all the subsequent continuation frames, but the
            // current implementation does not support the behavior.
            return frame;
        }

        // If the RSV1 bit is set.
        if (frame.getRsv1())
        {
            // In the current implementation, RSV1=true is allowed
            // only as Per-Message Compressed Bit (See RFC 7692,
            // 6. Framing). Therefore, RSV1=true here is regarded
            // as "already compressed".
            return frame;
        }

        // The plain payload before compression.
        byte[] payload = frame.getPayload();

        // If the payload is empty.
        if (payload == null || payload.length == 0)
        {
            // No compression.
            return frame;
        }

        // Compress the payload.
        byte[] compressed = compress(payload);

        // If the length of the compressed data is not less than
        // that of the original plain payload.
        if (payload.length <= compressed.length)
        {
            // It's better not to compress the payload.
            return frame;
        }

        // Replace the plain payload with the compressed data.
        frame.setPayload(compressed);

        // Set Per-Message Compressed Bit (See RFC 7692, 6. Framing).
        frame.setRsv1(true);

        return frame;
    }


    private byte[] compress(byte[] data)
    {
        try
        {
            // Compress the data.
            return mPMCE.compress(data);
        }
        catch (WebSocketException e)
        {
            // Failed to compress the data. Ignore this error and use
            // the plain original data. The current implementation
            // does not call any listener callback method for this error.
            return data;
        }
    }
}

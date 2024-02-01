package ch.epfl.gsn.http.rest;

import ch.epfl.gsn.beans.DataField;
import ch.epfl.gsn.beans.StreamElement;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.io.WriterOutputStream;

import com.thoughtworks.xstream.XStream;

public class RestDelivery implements DeliverySystem {

    private Continuation continuation;
    private String remoteHost;
    private ObjectOutputStream objectStream;
    private Integer limit = null;

    private static final StreamElement keepAliveMsg = new StreamElement(
            new DataField[] { new DataField("keepalive", "string") }, new Serializable[] { "keep-alive message" });

    public RestDelivery(Continuation connection, String remoteHost) throws IOException {
        this.continuation = connection;
        this.remoteHost = remoteHost;
        XStream dataStream = StreamElement4Rest.getXstream();
        objectStream = dataStream
                .createObjectOutputStream((new WriterOutputStream(continuation.getServletResponse().getWriter())));
    }

    public RestDelivery(Continuation connection, String remoteHost, Integer limit) throws IOException {
        this.continuation = connection;
        this.remoteHost = remoteHost;
        this.limit = limit;
        XStream dataStream = StreamElement4Rest.getXstream();
        objectStream = dataStream
                .createObjectOutputStream((new WriterOutputStream(continuation.getServletResponse().getWriter())));
    }

    private static transient Logger logger = LoggerFactory.getLogger(RestDelivery.class);

    public void writeStructure(DataField[] fields) throws IOException {
        objectStream.writeObject(fields);
        objectStream.flush();
        continuation.getServletResponse().flushBuffer();
    }

    public synchronized boolean writeStreamElement(StreamElement se) {
        try {
            objectStream.writeObject(new StreamElement4Rest(se));
            objectStream.flush();
            continuation.resume();
            if (limit != null) {
                limit--;
            }

            return ((LinkedBlockingQueue<Boolean>) continuation.getAttribute("status")).take();
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            return false;
        }
    }

    public boolean writeKeepAliveStreamElement() {
        logger.debug("Sending the keepalive message.");
        keepAliveMsg.setTimeStamp(System.currentTimeMillis());
        if (limit != null) {
            limit++;
        }

        return writeStreamElement(keepAliveMsg);
    }

    public void close() {
        try {
            if (objectStream != null) {
                objectStream.close();
                continuation.complete();
            }
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
        }
    }

    public boolean isClosed() {
        try {
            return continuation.getServletResponse().getWriter().checkError() || continuation.isExpired();
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        } catch (RuntimeIOException e) {
            e.printStackTrace();
            return true;
        }

    }

    @Override
    public void setTimeout(long timeoutMs) {
        continuation.setTimeout(timeoutMs);
    }

    public boolean isLimitReached() {
        if (limit != null && limit <= 0) {
            return true;
        }
        return false;
    }

    @Override
    public String getUser() {
        return remoteHost;
    }
}

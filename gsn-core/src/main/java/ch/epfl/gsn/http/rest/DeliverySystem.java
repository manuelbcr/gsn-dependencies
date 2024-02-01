package ch.epfl.gsn.http.rest;

import ch.epfl.gsn.beans.DataField;
import ch.epfl.gsn.beans.StreamElement;

import java.io.IOException;

public interface DeliverySystem {

	public abstract void writeStructure(DataField[] fields) throws IOException;

	public abstract boolean writeStreamElement(StreamElement se);

	public abstract boolean writeKeepAliveStreamElement();

	public abstract void close();

	public abstract boolean isClosed();

	public abstract void setTimeout(long timeoutMs);

	public abstract String getUser();
}
/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2016, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: src/ch/epfl/gsn/utils/protocols/AbstractHCIProtocol.java
*
* @author Jerome Rousselot
* @author Ali Salehi
*
*/

package ch.epfl.gsn.utils.protocols;

import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * This class provides a common framework to easily
 * implement a Host Controller Interface Protocol
 * in GSN.
 * Such protocols are used to communicate between a host
 * (pc, server...) and a controller (embedded system,
 * microcontroller, mote...).
 * The host can send queries and the controller sends
 * answers. Often, the controller also sends data spontaneously.
 * Support for this is in development.
 * There are two types of queries: some require an answer
 * from the controller, and some don't.
 * An implementation of AbstractHCIProtocol should be
 * used with the ProtocolManager class. The ProtocolManager
 * class deals with state issues: for example, after sending
 * a query the protocol might require you not to send any
 * query before the end of a timer or the reception of an
 * answer from the mote, whichever comes first.
 * 
 * @see ProtocolManager
 * @see AbstractHCIQuery
 */
public abstract class AbstractHCIProtocol {
	private static final transient Logger logger = LoggerFactory.getLogger(AbstractHCIProtocol.class);
	private String protocolName;
	private HashMap<String, AbstractHCIQuery> queries;

	public AbstractHCIProtocol(String name) {
		logger.debug("Initializing protocol " + name);
		protocolName = name;
		queries = new HashMap<String, AbstractHCIQuery>();
	}

	protected void addQuery(AbstractHCIQuery query) {
		queries.put(query.getName(), query);
		logger.debug("added query: " + query.getName());
	}

	/*
	 * Returns the complete list of all queries known
	 * by this protocol.
	 */

	public Collection<AbstractHCIQuery> getQueries() {
		logger.debug("returning query values: " + queries.values());
		return queries.values();
	}

	public Collection<String> getNames() {
		return queries.keySet();
	}

	/*
	 * Returns the name of the protocol represented
	 * by this class.
	 * 
	 */
	public String getName() {
		return protocolName;
	}

	public AbstractHCIQuery getQuery(String queryName) {
		for (String key : queries.keySet()) {
			if (key.equals(queryName)) {
				return queries.get(key);
			}
		}
		return null;
	}

	/*
	 * Returns null if the query does not exists, and the raw bytes
	 * to send to the wrapper if the query has been found.
	 */
	public byte[] buildRawQuery(String queryName, Vector<Object> params) {
		AbstractHCIQuery query = queries.get(queryName);
		if (query == null) {
			return null;
		} else {
			logger.debug("Protocol " + getName() + " has built a raw query of type " + query.getName());
			return query.buildRawQuery(params);
		}
	}
}

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
* File: src/ch/epfl/gsn/http/datarequest/AbstractQuery.java
*
* @author Timotee Maret
* @author Milos Stojanovic
*
*/

package ch.epfl.gsn.delivery.datarequest;

import org.slf4j.LoggerFactory;

import ch.epfl.gsn.delivery.datarequest.AbstractQuery;
import ch.epfl.gsn.delivery.datarequest.AggregationCriterion;
import ch.epfl.gsn.delivery.datarequest.LimitCriterion;
import ch.epfl.gsn.delivery.datarequest.StandardCriterion;

import org.slf4j.Logger;

import java.util.ArrayList;

public class AbstractQuery {

	// private StringBuilder standardQuery = null;
	private String[] fields;
	private LimitCriterion limitCriterion;
	private AggregationCriterion aggregation;
	private String vsName;
	private ArrayList<StandardCriterion> criteria;

	private static transient Logger logger = LoggerFactory.getLogger(AbstractQuery.class);

	public AbstractQuery(LimitCriterion limitCriterion, AggregationCriterion aggregation, String vsname,
			String[] fields, ArrayList<StandardCriterion> criteria) {
		// this.standardQuery = standardQuery;
		this.limitCriterion = limitCriterion;
		this.aggregation = aggregation;
		this.vsName = vsname;
		this.fields = fields;
		this.criteria = criteria;
	}

	public StringBuilder getStandardQuery() {
		// Standard Criteria
		StringBuilder partStandardCriteria = new StringBuilder();
		if (criteria != null) {
			StandardCriterion lastStandardCriterionLinkedToVs = null;
			StandardCriterion cc;
			for (int i = 0; i < criteria.size(); i++) {
				cc = criteria.get(i);
				if (cc.getVsname().compareTo("") == 0 || cc.getVsname().compareToIgnoreCase(vsName) == 0) {

					if (lastStandardCriterionLinkedToVs != null) {
						partStandardCriteria.append(lastStandardCriterionLinkedToVs.getCritJoin() + " "
								+ cc.getNegation() + " " + cc.getField() + " " + cc.getOperator() + " ");
					} else {
						partStandardCriteria
								.append(cc.getNegation() + " " + cc.getField() + " " + cc.getOperator() + " ");
					}

					lastStandardCriterionLinkedToVs = cc;

					if (cc.getOperator().compareToIgnoreCase("like") == 0) {
						partStandardCriteria.append("'%");
					}

					partStandardCriteria.append(cc.getValue());

					if (cc.getOperator().compareToIgnoreCase("like") == 0) {
						partStandardCriteria.append("%'");
					}
					partStandardCriteria.append(" ");
				}
			}
			if (lastStandardCriterionLinkedToVs != null) {
				partStandardCriteria.insert(0, "where ");
			}
		}

		StringBuilder partFields = new StringBuilder();
		for (int i = 0; i < fields.length; i++) {
			if (partFields.length() > 0) {
				partFields.append(", ");
			}
			if (aggregation != null) {
				if ("timed".equals(fields[i])) {
					partFields.append("max(");
				} else {
					partFields.append(aggregation.getGroupOperator() + "(");
				}
			}
			partFields.append(fields[i]);
			if (aggregation != null) {
				partFields.append(") as " + fields[i]);
			}

		}

		if (aggregation != null) {
			if (partFields.length() > 0) {
				partFields.append(", ");
			}
			partFields.append("floor(timed/" + aggregation.getTimeRange() + ") as aggregation_interval ");
		} else {
			partFields.append(" ");
		}

		// Build a final query
		StringBuilder sqlQuery = new StringBuilder();
		sqlQuery.append("select ");
		sqlQuery.append(partFields);
		sqlQuery.append("from ").append(vsName).append(" ");
		sqlQuery.append(partStandardCriteria);
		if (aggregation == null) {
			sqlQuery.append("order by timed desc ");
		} else {
			sqlQuery.append("group by aggregation_interval desc ");
		}
		logger.debug("SQL Query built >" + sqlQuery.toString() + "<");
		return sqlQuery;
	}

	/*
	 * public void setStandardQuery(StringBuilder standardQuery) {
	 * this.standardQuery = standardQuery;
	 * }
	 */
	public LimitCriterion getLimitCriterion() {
		return limitCriterion;
	}

	public void setLimitCriterion(LimitCriterion limitCriterion) {
		this.limitCriterion = limitCriterion;
	}

	public AggregationCriterion getAggregation() {
		return aggregation;
	}

	public void setAggregation(AggregationCriterion aggregation) {
		this.aggregation = aggregation;
	}

	public String getVsName() {
		return vsName;
	}

	public void setVsName(String vsName) {
		this.vsName = vsName;
	}

	public void updateCriterion(StandardCriterion criterion) {
		int index = criteria.indexOf(criterion);
		if (index != -1) {
			criteria.set(index, criterion);
		} else {
			criteria.add(criterion);
		}

	}

	public ArrayList<StandardCriterion> getCriteria() {
		return criteria;
	}

	public void setCriteria(ArrayList<StandardCriterion> criteria) {
		this.criteria = criteria;
	}

	public void addField(String fieldName) {
		String[] newFields = new String[fields.length + 1];
		System.arraycopy(fields, 0, newFields, 1, fields.length);
		fields = newFields;
		fields[0] = fieldName;
	}

	public String[] getFields() {
		return fields;
	}

	public void setFields(String[] fields) {
		this.fields = fields;
	}
}

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
* File: src/ch/epfl/gsn/storage/db/PostgresStorageManager.java
*
* @author Sofiane Sarni
*
*/

package ch.epfl.gsn.storage.db;

import org.slf4j.LoggerFactory;

import ch.epfl.gsn.beans.DataField;
import ch.epfl.gsn.beans.DataTypes;
import ch.epfl.gsn.storage.StorageManager;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public class PostgresStorageManager extends StorageManager {
    private static final transient Logger logger = LoggerFactory.getLogger(PostgresStorageManager.class);

    public PostgresStorageManager() {
        super();
        this.isPostgres = true;
    }

    @Override
    public byte convertLocalTypeToGSN(int jdbcType, int precision) {
        switch (jdbcType) {
            case Types.BIGINT:
                return DataTypes.BIGINT;
            case Types.INTEGER:
                return DataTypes.INTEGER;
            case Types.SMALLINT:
                return DataTypes.SMALLINT;
            case Types.TINYINT:
                return DataTypes.TINYINT;
            case Types.VARCHAR:
                return DataTypes.VARCHAR;
            case Types.CHAR:
                return DataTypes.CHAR;
            case Types.DOUBLE:
            case Types.DECIMAL: // This is needed for doing aggregates in datadownload servlet.
                return DataTypes.DOUBLE;
            case Types.REAL: // should also be float mapped here ?
                return DataTypes.FLOAT;
            case Types.BINARY:
            case Types.BLOB:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return DataTypes.BINARY;
            default:
                logger.error("The type can't be converted to GSN form : " + jdbcType);
                break;
        }
        return -100;
    }

    @Override
    public String getStatementDropIndex() {
        return "DROP TABLE IF EXISTS #NAME";
    }

    @Override
    public String getStatementDropView() {
        return "DROP VIEW IF EXISTS #NAME";
    }

    @Override
    public int getTableNotExistsErrNo() {
        return 0; // TODO: check error code in Postgres reference
    }

    @Override
    public String addLimit(String query, int limit, int offset) {
        return query + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public StringBuilder getStatementUselessDataRemoval(String virtualSensorName, long storageSize) {
        return new StringBuilder()
                .append("delete from ")
                .append(virtualSensorName)
                .append(" where ")
                .append(virtualSensorName)
                .append(".timed <= ( SELECT * FROM ( SELECT timed FROM ")
                .append(virtualSensorName)
                .append(" group by ")
                .append(virtualSensorName)
                .append(".timed ORDER BY ")
                .append(virtualSensorName)
                .append(".timed DESC LIMIT 1 offset ")
                .append(storageSize)
                .append("  ) AS TMP)"); // TODO: verify
    }

    @Override
    public StringBuilder getStatementRemoveUselessDataCountBased(String virtualSensorName, long storageSize) {
        return new StringBuilder()
                .append("delete from ")
                .append(virtualSensorName)
                .append(" where ")
                .append(virtualSensorName)
                .append(".timed <= ( SELECT * FROM ( SELECT timed FROM ")
                .append(virtualSensorName)
                .append(" group by ")
                .append(virtualSensorName)
                .append(".timed ORDER BY ")
                .append(virtualSensorName)
                .append(".timed DESC LIMIT 1 offset ")
                .append(storageSize).append("  ) AS TMP)"); // TODO: verify
    }

    @Override
    public StringBuilder getStatementDropTable(CharSequence tableName, Connection conn) throws SQLException {
        StringBuilder sb = new StringBuilder("Drop table if exists ");
        sb.append(tableName);
        return sb;
    }

    @Override
    public StringBuilder getStatementCreateTable(String tableName, DataField[] structure) {
        StringBuilder result = new StringBuilder("CREATE TABLE ").append(tableName);

        result.append(" (PK serial PRIMARY KEY NOT NULL , timed BIGINT NOT NULL, "); // TODO: add auto increment
                                                                                     // AUTO_INCREMENT

        for (DataField field : structure) {
            if (field.getName().equalsIgnoreCase("pk") || field.getName().equalsIgnoreCase("timed")) {
                continue;
            }
            result.append(field.getName().toUpperCase()).append(' ');
            result.append(convertGSNTypeToLocalType(field));
            result.append(" ,");
        }
        result.delete(result.length() - 2, result.length());
        result.append(")");
        return result;
    }

    @Override
    public String getJDBCPrefix() {
        return "jdbc:postgresql:";
    }

    @Override
    public String convertGSNTypeToLocalType(DataField gsnType) {
        String convertedType;
        switch (gsnType.getDataTypeID()) {
            case DataTypes.CHAR:
            case DataTypes.VARCHAR:
                // Because the parameter for the varchar is not
                // optional.
                if (gsnType.getType().trim().equalsIgnoreCase("string")) {
                    convertedType = "TEXT";
                } else {
                    convertedType = gsnType.getType();
                }
                break;
            case DataTypes.BINARY:
                convertedType = "BYTEA";
                break;
            case DataTypes.DOUBLE:
                convertedType = "DOUBLE PRECISION";
                break;
            case DataTypes.FLOAT:
                convertedType = "REAL";
                break;
            case DataTypes.TINYINT:
                convertedType = "SMALLINT";
                break;
            default:
                convertedType = DataTypes.TYPE_NAMES[gsnType.getDataTypeID()];
                break;
        }
        return convertedType;
    }

    @Override
    public String getStatementDifferenceTimeInMillis() {
        return "SELECT extract(epoch FROM now())*1000";
    }
}

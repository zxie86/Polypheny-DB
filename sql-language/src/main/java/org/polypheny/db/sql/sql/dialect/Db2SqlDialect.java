/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.sql.sql.dialect;


import org.polypheny.db.algebra.type.AlgDataTypeSystem;
import org.polypheny.db.sql.sql.SqlDialect;
import org.polypheny.db.sql.sql.SqlIntervalLiteral;
import org.polypheny.db.sql.sql.SqlIntervalQualifier;
import org.polypheny.db.sql.sql.SqlWriter;


/**
 * A <code>SqlDialect</code> implementation for the IBM DB2 database.
 */
public class Db2SqlDialect extends SqlDialect {

    public static final SqlDialect DEFAULT = new Db2SqlDialect( EMPTY_CONTEXT.withDatabaseProduct( DatabaseProduct.DB2 ) );


    /**
     * Creates a Db2SqlDialect.
     */
    public Db2SqlDialect( Context context ) {
        super( context );
    }


    @Override
    public boolean supportsCharSet() {
        return false;
    }


    @Override
    public boolean hasImplicitTableAlias() {
        return false;
    }


    @Override
    public void unparseSqlIntervalQualifier( SqlWriter writer, SqlIntervalQualifier qualifier, AlgDataTypeSystem typeSystem ) {

        // DB2 supported qualifiers. Singular form of these keywords are also acceptable.
        // YEAR/YEARS
        // MONTH/MONTHS
        // DAY/DAYS
        // HOUR/HOURS
        // MINUTE/MINUTES
        // SECOND/SECONDS

        switch ( qualifier.timeUnitRange ) {
            case YEAR:
            case MONTH:
            case DAY:
            case HOUR:
            case MINUTE:
            case SECOND:
            case MICROSECOND:
                final String timeUnit = qualifier.timeUnitRange.startUnit.name();
                writer.keyword( timeUnit );
                break;
            default:
                throw new AssertionError( "Unsupported type: " + qualifier.timeUnitRange );
        }

        if ( null != qualifier.timeUnitRange.endUnit ) {
            throw new AssertionError( "Unsupported end unit: " + qualifier.timeUnitRange.endUnit );
        }
    }


    @Override
    public void unparseSqlIntervalLiteral( SqlWriter writer, SqlIntervalLiteral literal, int leftPrec, int rightPrec ) {
        // A duration is a positive or negative number representing an interval of time.
        // If one operand is a date, the other labeled duration of YEARS, MONTHS, or DAYS.
        // If one operand is a time, the other must be labeled duration of HOURS, MINUTES, or SECONDS.
        // If one operand is a timestamp, the other operand can be any of teh duration.

        SqlIntervalLiteral.IntervalValue interval = (SqlIntervalLiteral.IntervalValue) literal.getValue();
        if ( interval.getSign() == -1 ) {
            writer.print( "-" );
        }
        writer.literal( literal.getValue().toString() );
        unparseSqlIntervalQualifier( writer, interval.getIntervalQualifier(), AlgDataTypeSystem.DEFAULT );
    }

}


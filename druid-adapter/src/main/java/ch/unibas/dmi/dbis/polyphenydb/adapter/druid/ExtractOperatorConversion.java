/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.adapter.druid;


import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.apache.calcite.avatica.util.TimeUnitRange;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexCall;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexLiteral;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperator;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.TimeZone;


/**
 * Time extract operator conversion for expressions like EXTRACT(timeUnit FROM arg).
 * Unit can be SECOND, MINUTE, HOUR, DAY (day of month), DOW (day of week), DOY (day of year), WEEK (week of week year), MONTH (1 through 12), QUARTER (1 through 4), or YEAR
 **/
public class ExtractOperatorConversion implements DruidSqlOperatorConverter {

    private static final Map<TimeUnitRange, String> EXTRACT_UNIT_MAP = ImmutableMap.<TimeUnitRange, String>builder()
            .put( TimeUnitRange.SECOND, "SECOND" )
            .put( TimeUnitRange.MINUTE, "MINUTE" )
            .put( TimeUnitRange.HOUR, "HOUR" )
            .put( TimeUnitRange.DAY, "DAY" )
            .put( TimeUnitRange.DOW, "DOW" )
            .put( TimeUnitRange.DOY, "DOY" )
            .put( TimeUnitRange.WEEK, "WEEK" )
            .put( TimeUnitRange.MONTH, "MONTH" )
            .put( TimeUnitRange.QUARTER, "QUARTER" )
            .put( TimeUnitRange.YEAR, "YEAR" )
            .build();


    @Override
    public SqlOperator polyphenyDbOperator() {
        return SqlStdOperatorTable.EXTRACT;
    }


    @Override
    public String toDruidExpression( RexNode rexNode, RelDataType rowType, DruidQuery query ) {

        final RexCall call = (RexCall) rexNode;
        final RexLiteral flag = (RexLiteral) call.getOperands().get( 0 );
        final TimeUnitRange polyphenyDbUnit = (TimeUnitRange) flag.getValue();
        final RexNode arg = call.getOperands().get( 1 );

        final String input = DruidExpressions.toDruidExpression( arg, rowType, query );
        if ( input == null ) {
            return null;
        }

        final String druidUnit = EXTRACT_UNIT_MAP.get( polyphenyDbUnit );
        if ( druidUnit == null ) {
            return null;
        }

        final TimeZone tz = arg.getType().getSqlTypeName() == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE
                ? TimeZone.getTimeZone( query.getConnectionConfig().timeZone() )
                : DateTimeUtils.UTC_ZONE;
        return DruidExpressions.applyTimeExtract( input, druidUnit, tz );
    }
}

/*
 * Copyright 2019-2022 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
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
 */

package org.polypheny.db.algebra;


import org.polypheny.db.algebra.core.Scan;
import org.polypheny.db.algebra.core.TableFunctionScan;
import org.polypheny.db.algebra.logical.LogicalAggregate;
import org.polypheny.db.algebra.logical.LogicalConditionalExecute;
import org.polypheny.db.algebra.logical.LogicalConstraintEnforcer;
import org.polypheny.db.algebra.logical.LogicalCorrelate;
import org.polypheny.db.algebra.logical.LogicalExchange;
import org.polypheny.db.algebra.logical.LogicalFilter;
import org.polypheny.db.algebra.logical.LogicalIntersect;
import org.polypheny.db.algebra.logical.LogicalJoin;
import org.polypheny.db.algebra.logical.LogicalMatch;
import org.polypheny.db.algebra.logical.LogicalMinus;
import org.polypheny.db.algebra.logical.LogicalModify;
import org.polypheny.db.algebra.logical.LogicalProject;
import org.polypheny.db.algebra.logical.LogicalSort;
import org.polypheny.db.algebra.logical.LogicalUnion;
import org.polypheny.db.algebra.logical.LogicalValues;
import org.polypheny.db.algebra.logical.graph.LogicalGraphAggregate;
import org.polypheny.db.algebra.logical.graph.LogicalGraphFilter;
import org.polypheny.db.algebra.logical.graph.LogicalGraphMatch;
import org.polypheny.db.algebra.logical.graph.LogicalGraphModify;
import org.polypheny.db.algebra.logical.graph.LogicalGraphProject;
import org.polypheny.db.algebra.logical.graph.LogicalGraphScan;
import org.polypheny.db.algebra.logical.graph.LogicalGraphSort;
import org.polypheny.db.algebra.logical.graph.LogicalGraphUnwind;
import org.polypheny.db.algebra.logical.graph.LogicalGraphValues;


/**
 * Visitor that has methods for the common logical relational expressions.
 */
public interface AlgShuttle {

    AlgNode visit( Scan scan );

    AlgNode visit( TableFunctionScan scan );

    AlgNode visit( LogicalValues values );

    AlgNode visit( LogicalFilter filter );

    AlgNode visit( LogicalProject project );

    AlgNode visit( LogicalJoin join );

    AlgNode visit( LogicalCorrelate correlate );

    AlgNode visit( LogicalUnion union );

    AlgNode visit( LogicalIntersect intersect );

    AlgNode visit( LogicalMinus minus );

    AlgNode visit( LogicalAggregate aggregate );

    AlgNode visit( LogicalMatch match );

    AlgNode visit( LogicalSort sort );

    AlgNode visit( LogicalExchange exchange );

    AlgNode visit( LogicalModify modify );

    AlgNode visit( LogicalConditionalExecute lce );

    AlgNode visit( LogicalGraphModify modify );

    AlgNode visit( LogicalGraphScan scan );

    AlgNode visit( LogicalGraphValues values );

    AlgNode visit( LogicalGraphFilter filter );

    AlgNode visit( LogicalGraphMatch match );

    AlgNode visit( LogicalGraphProject project );

    AlgNode visit( LogicalGraphAggregate aggregate );

    AlgNode visit( LogicalGraphSort sort );

    AlgNode visit( LogicalGraphUnwind unwind );

    AlgNode visit( LogicalConstraintEnforcer constraintEnforcer );

    AlgNode visit( AlgNode other );

}


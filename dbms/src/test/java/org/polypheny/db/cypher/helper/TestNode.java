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
 */

package org.polypheny.db.cypher.helper;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.util.Pair;

public class TestNode extends TestObject {

    public TestNode( @Nullable String id, @Nullable Map<String, Object> properties, @Nullable List<String> labels ) {
        super( id, properties, labels );
    }


    @SafeVarargs
    public static TestNode from( Pair<String, Object>... properties ) {
        return from( null, properties );
    }


    @SafeVarargs
    public static TestNode from( List<String> labels, Pair<String, Object>... properties ) {
        return new TestNode( null, getProps( properties ), null );
    }

}

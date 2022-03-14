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

package org.polypheny.db.schema.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;

@Getter
public abstract class GraphObject {

    private static Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();

    public final String id;
    public final GraphObjectType type;


    protected GraphObject( String id, GraphObjectType type ) {
        this.id = id;
        this.type = type;
    }


    public String toJson() {
        return gson.toJson( this );
    }


    public enum GraphObjectType {
        GRAPH,
        NODE,
        RELATIONSHIP
    }

}

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

package org.polypheny.db.catalog.entity;


import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.polypheny.db.adapter.Adapter.AdapterProperties;
import org.polypheny.db.type.PolyType;

@EqualsAndHashCode
public class CatalogAdapter implements CatalogObject {

    private static final long serialVersionUID = -5837600302561930044L;

    public final int id;
    public final String uniqueName;
    public final String adapterClazz;
    public final AdapterType type;
    public final ImmutableMap<String, String> settings;
    public final List<PolyType> unsupportedTypes;
    public final PolyType substitutionType;


    public enum AdapterType {STORE, SOURCE}


    public CatalogAdapter(
            final int id,
            @NonNull final String uniqueName,
            @NonNull final String adapterClazz,
            @NonNull final AdapterType adapterType,
            @NonNull final Map<String, String> settings ) {
        this.id = id;
        this.uniqueName = uniqueName;
        this.adapterClazz = adapterClazz;
        this.type = adapterType;
        this.settings = ImmutableMap.copyOf( settings );
        // general settings are provided by the annotations of the adapter class
        try {
            AdapterProperties annotations = Class.forName( adapterClazz ).getAnnotation( AdapterProperties.class );
            this.unsupportedTypes = Arrays.asList( annotations.unsupportedTypes() );
            this.substitutionType = annotations.substitutionType();
        } catch ( ClassNotFoundException e ) {
            throw new RuntimeException( "The provided Adapter is not correctly annotated." );
        }
    }


    // Used for creating ResultSets
    @Override
    public Serializable[] getParameterArray() {
        return new Serializable[]{ uniqueName };
    }


    @RequiredArgsConstructor
    public static class PrimitiveCatalogAdapter {

        public final String name;

    }
}

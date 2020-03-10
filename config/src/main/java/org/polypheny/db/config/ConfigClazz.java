/*
 * Copyright 2019-2020 The Polypheny Project
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

package org.polypheny.db.config;


import com.google.common.collect.ImmutableSet;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.typesafe.config.ConfigException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.config.exception.ConfigRuntimeException;
import org.reflections.Reflections;


@Slf4j
public class ConfigClazz extends Config {

    @JsonAdapter(ClassesAdapter.class)
    private final Set<Class> classes;
    @JsonAdapter(ValueAdapter.class)
    private Class value;


    public ConfigClazz( final String key, final Class superClass, final Class defaultValue ) {
        super( key );
        Reflections reflections = new Reflections( "org.polypheny.db" );
        //noinspection unchecked
        classes = ImmutableSet.copyOf( reflections.getSubTypesOf( superClass ) );
        setClazz( defaultValue );
    }


    @Override
    public Set<Class> getClazzes() {
        return classes;
    }


    @Override
    public Class getClazz() {
        return value;
    }


    @Override
    public boolean setClazz( final Class value ) {
        if ( classes.contains( value ) ) {
            if ( validate( value ) ) {
                this.value = value;
                notifyConfigListeners();
                return true;
            } else {
                return false;
            }
        } else {
            throw new ConfigRuntimeException( "This class does not implement the specified super class" );
        }
    }


    @Override
    void setValueFromFile( final com.typesafe.config.Config conf ) {
        try {
            setClazz( getByString( conf.getString( this.getKey() ) ) );
        } catch ( ConfigException.Missing e ) {
            // This should have been checked before!
            throw new ConfigRuntimeException( "No config with this key found in the configuration file." );
        } catch ( ConfigException.WrongType e ) {
            throw new ConfigRuntimeException( "The value in the config file has a type which is incompatible with this config element." );
        }

    }


    private Class getByString( String str ) throws ConfigRuntimeException {
        for ( Class c : classes ) {
            if ( str.equalsIgnoreCase( c.getName() ) ) {
                return c;
            }
        }
        throw new ConfigRuntimeException( "No class with name " + str + " found in the list of valid classes." );
    }


    class ClassesAdapter extends TypeAdapter<Set<Class>> {

        @Override
        public void write( final JsonWriter out, final Set<Class> classes ) throws IOException {
            if ( classes == null ) {
                out.nullValue();
                return;
            }
            out.beginArray();
            for ( Class c : classes ) {
                out.value( c.getName() );
            }
            out.endArray();
        }

        @Override
        public Set<Class> read( final JsonReader in ) throws IOException {
            Set<Class> set = new HashSet<>();
            in.beginArray();
            while ( in.hasNext() ) {
                try {
                    Class c = Class.forName( in.nextString() );
                    set.add( c );
                } catch ( ClassNotFoundException e ) {
                    log.error( "Caught exception!", e );
                    set.add( null );
                }
            }
            in.endArray();
            return ImmutableSet.copyOf( set );
        }
    }


    class ValueAdapter extends TypeAdapter<Class> {

        @Override
        public void write( final JsonWriter out, final Class value ) throws IOException {
            if ( value == null ) {
                out.nullValue();
                return;
            }
            out.value( value.getName() );
        }

        @Override
        public Class read( final JsonReader in ) throws IOException {
            try {
                return Class.forName( in.nextString() );
            } catch ( ClassNotFoundException e ) {
                log.error( "Caught exection!", e );
                return null;
            }
        }
    }

}
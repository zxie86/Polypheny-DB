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

package org.polypheny.db.util;


import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.apache.calcite.avatica.util.ByteString;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.runtime.Functions;


/**
 * A string, optionally with {@link Charset character set} and {@link Collation}. It is immutable.
 */
public class NlsString implements Comparable<NlsString>, Cloneable, Externalizable, Expressionizable {

    private static final LoadingCache<Pair<ByteString, Charset>, String>
            DECODE_MAP = CacheBuilder.newBuilder()
            .softValues()
            .build(
                    new CacheLoader<Pair<ByteString, Charset>, String>() {
                        @Override
                        public String load( @Nonnull Pair<ByteString, Charset> key ) {
                            final Charset charset = key.right;
                            final CharsetDecoder decoder = charset.newDecoder();
                            final byte[] bytes = key.left.getBytes();
                            final ByteBuffer buffer = ByteBuffer.wrap( bytes );
                            try {
                                return decoder.decode( buffer ).toString();
                            } catch ( CharacterCodingException ex ) {
                                throw Static.RESOURCE.charsetEncoding(
                                        //CHECKSTYLE: IGNORE 1
                                        new String( bytes, Charset.defaultCharset() ),
                                        charset.name() ).ex();
                            }
                        }
                    } );

    private String stringValue;
    private ByteString bytesValue;
    @Getter
    private String charsetName;
    @Getter
    private Charset charset;
    @Getter
    private Collation collation;


    /**
     * Creates a string in a specified character set.
     *
     * @param bytesValue Byte array constant, must not be null
     * @param charsetName Name of the character set, must not be null
     * @param collation Collation, may be null
     * @throws IllegalCharsetNameException If the given charset name is illegal
     * @throws UnsupportedCharsetException If no support for the named charset is available in this instance of the Java virtual machine
     * @throws RuntimeException If the given value cannot be represented in the given charset
     */
    public NlsString( ByteString bytesValue, String charsetName, Collation collation ) {
        this( null, Objects.requireNonNull( bytesValue ), Objects.requireNonNull( charsetName ), collation );
    }


    /**
     * Easy constructor for Java string.
     *
     * @param stringValue String constant, must not be null
     * @param charsetName Name of the character set, may be null
     * @param collation Collation, may be null
     * @throws IllegalCharsetNameException If the given charset name is illegal
     * @throws UnsupportedCharsetException If no support for the named charset is available in this instance of the Java virtual machine
     * @throws RuntimeException If the given value cannot be represented in the given charset
     */
    public NlsString( String stringValue, String charsetName, Collation collation ) {
        this( Objects.requireNonNull( stringValue ), null, charsetName, collation );
    }


    /**
     * Internal constructor; other constructors must call it.
     */
    private NlsString( String stringValue, ByteString bytesValue, String charsetName, Collation collation ) {
        if ( charsetName != null ) {
            this.charsetName = charsetName.toUpperCase( Locale.ROOT );
            this.charset = CoreUtil.getCharset( charsetName );
        } else {
            this.charsetName = null;
            this.charset = null;
        }
        if ( (stringValue != null) == (bytesValue != null) ) {
            throw new IllegalArgumentException( "Specify stringValue or bytesValue" );
        }
        if ( bytesValue != null ) {
            if ( charsetName == null ) {
                throw new IllegalArgumentException( "Bytes value requires charset" );
            }
            ValidatorUtil.validateCharset( bytesValue, charset );
        } else {
            // Java string can be malformed if LATIN1 is required.
            if ( this.charsetName != null && (this.charsetName.equals( "LATIN1" ) || this.charsetName.equals( "ISO-8859-1" )) ) {
                if ( !charset.newEncoder().canEncode( stringValue ) ) {
                    throw Static.RESOURCE.charsetEncoding( stringValue, charset.name() ).ex();
                }
            }
        }
        this.collation = collation;
        this.stringValue = stringValue;
        this.bytesValue = bytesValue;
    }


    public NlsString() {
    }


    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch ( CloneNotSupportedException e ) {
            throw new AssertionError();
        }
    }


    public int hashCode() {
        return Objects.hash( stringValue, bytesValue, charsetName, collation );
    }


    public boolean equals( Object obj ) {
        return this == obj
                || obj instanceof NlsString
                && Objects.equals( stringValue, ((NlsString) obj).stringValue )
                && Objects.equals( bytesValue, ((NlsString) obj).bytesValue )
                && Objects.equals( charsetName, ((NlsString) obj).charsetName )
                && Objects.equals( collation, ((NlsString) obj).collation );
    }


    @Override
    public int compareTo( NlsString other ) {
        // TODO jvs 18-Jan-2006:  Actual collation support.  This just uses the default collation.
        return getValue().compareTo( other.getValue() );
    }


    public String getValue() {
        if ( stringValue == null ) {
            assert bytesValue != null;
            return DECODE_MAP.getUnchecked( Pair.of( bytesValue, charset ) );
        }
        return stringValue;
    }


    /**
     * Returns a string the same as this but with spaces trimmed from the right.
     */
    public NlsString rtrim() {
        String trimmed = Functions.rtrim( getValue() );
        if ( !trimmed.equals( getValue() ) ) {
            return new NlsString( trimmed, charsetName, collation );
        }
        return this;
    }


    /**
     * Returns the string quoted for SQL, for example <code>_ISO-8859-1'is it a plane? no it''s superman!'</code>.
     *
     * @param prefix if true, prefix the character set name
     * @param suffix if true, suffix the collation clause
     * @return the quoted string
     */
    public String asSql( boolean prefix, boolean suffix ) {
        StringBuilder ret = new StringBuilder();
        if ( prefix && (null != charsetName) ) {
            ret.append( "_" );
            ret.append( charsetName );
        }
        ret.append( "'" );
        ret.append( Util.replace( getValue(), "'", "''" ) );
        ret.append( "'" );

        // NOTE see FRG-78 for why this should go away
        if ( false ) {
            if ( suffix && (null != collation) ) {
                ret.append( " " );
                ret.append( collation.toString() );
            }
        }
        return ret.toString();
    }


    /**
     * Returns the string quoted for SQL, for example <code>_ISO-8859-1'is it a plane? no it''s superman!'</code>.
     */
    public String toString() {
        return asSql( true, true );
    }


    /**
     * Concatenates some {@link NlsString} objects. The result has the charset and collation of the first element. The other elements must have matching
     * (or null) charset and collation. Concatenates all at once, not pairwise, to avoid string copies.
     *
     * @param args array of {@link NlsString} to be concatenated
     */
    public static NlsString concat( List<NlsString> args ) {
        if ( args.size() < 2 ) {
            return args.get( 0 );
        }
        String charSetName = args.get( 0 ).charsetName;
        Collation collation = args.get( 0 ).collation;
        int length = args.get( 0 ).getValue().length();

        // sum string lengths and validate
        for ( int i = 1; i < args.size(); i++ ) {
            final NlsString arg = args.get( i );
            length += arg.getValue().length();
            if ( !((arg.charsetName == null) || arg.charsetName.equals( charSetName )) ) {
                throw new IllegalArgumentException( "mismatched charsets" );
            }
            if ( !((arg.collation == null) || arg.collation.equals( collation )) ) {
                throw new IllegalArgumentException( "mismatched collations" );
            }
        }

        StringBuilder sb = new StringBuilder( length );
        for ( NlsString arg : args ) {
            sb.append( arg.getValue() );
        }
        return new NlsString( sb.toString(), charSetName, collation );
    }


    /**
     * Creates a copy of this {@code NlsString} with different content but same charset and collation.
     */
    public NlsString copy( String value ) {
        return new NlsString( value, charsetName, collation );
    }


    /**
     * Returns the value as a {@link ByteString}.
     */
    public ByteString getValueBytes() {
        return bytesValue;
    }


    @Override
    public void writeExternal( ObjectOutput out ) throws IOException {
        out.writeUTF( stringValue );
        out.writeObject( bytesValue );
        out.writeUTF( charsetName );
        out.writeObject( SerializableCharset.forCharset( charset ) );
        out.writeObject( collation );
    }


    @Override
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
        stringValue = in.readUTF();
        bytesValue = (ByteString) in.readObject();
        charsetName = in.readUTF();
        charset = ((SerializableCharset) in.readObject()).getCharset();
        collation = (Collation) in.readObject();
    }


    @Override
    public Expression getAsExpression() {
        return Expressions.new_(
                NlsString.class,
                Expressions.constant( getValue() ),
                Expressions.constant( charsetName ),
                Expressions.new_(
                        Collation.class,
                        Expressions.constant( collation.getCollationName() ),
                        Expressions.constant( collation.getCoercibility() ) ) );
    }


    public static class NlsNormalizeSerializer extends TypeAdapter<NlsString> {


        @Override
        public void write( JsonWriter out, NlsString value ) throws IOException {
            out.value( value.getValue() );
        }


        @Override
        public NlsString read( JsonReader in ) throws IOException {
            return new NlsString( in.nextString(), StandardCharsets.UTF_8.name(), Collation.IMPLICIT );
        }

    }

}


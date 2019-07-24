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
 *  The MIT License (MIT)
 *
 *  Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a
 *  copy of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.test;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptUtil;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.Is;


/**
 * Matchers for testing SQL queries.
 */
public class Matchers {

    private Matchers() {
    }


    /**
     * Allows passing the actual result from the {@code matchesSafely} method to the {@code describeMismatchSafely} method that will show the difference.
     */
    private static final ThreadLocal<Object> THREAD_ACTUAL = new ThreadLocal<>();


    /**
     * Creates a matcher that matches if the examined result set returns the given collection of rows in some order.
     *
     * Closes the result set after reading.
     *
     * For example:
     * assertThat(statement.executeQuery("select empno from emp"), returnsUnordered("empno=1234", "empno=100"));</pre>
     */
    public static Matcher<? super ResultSet> returnsUnordered( String... lines ) {
        final List<String> expectedList = Lists.newArrayList( lines );
        Collections.sort( expectedList );

        return new CustomTypeSafeMatcher<ResultSet>( Arrays.toString( lines ) ) {
            @Override
            protected void describeMismatchSafely( ResultSet item, Description description ) {
                final Object value = THREAD_ACTUAL.get();
                THREAD_ACTUAL.remove();
                description.appendText( "was " ).appendValue( value );
            }


            protected boolean matchesSafely( ResultSet resultSet ) {
                final List<String> actualList = new ArrayList<>();
                try {
                    PolyphenyDbAssert.toStringList( resultSet, actualList );
                    resultSet.close();
                } catch ( SQLException e ) {
                    throw new RuntimeException( e );
                }
                Collections.sort( actualList );

                THREAD_ACTUAL.set( actualList );
                final boolean equals = actualList.equals( expectedList );
                if ( !equals ) {
                    THREAD_ACTUAL.set( actualList );
                }
                return equals;
            }
        };
    }


    public static <E extends Comparable> Matcher<Iterable<E>> equalsUnordered( E... lines ) {
        final List<String> expectedList = Lists.newArrayList( toStringList( Arrays.asList( lines ) ) );
        Collections.sort( expectedList );
        final String description = Util.lines( expectedList );
        return new CustomTypeSafeMatcher<Iterable<E>>( description ) {
            @Override
            protected void describeMismatchSafely( Iterable<E> actuals, Description description ) {
                final List<String> actualList = Lists.newArrayList( toStringList( actuals ) );
                Collections.sort( actualList );
                description.appendText( "was " ).appendValue( Util.lines( actualList ) );
            }


            protected boolean matchesSafely( Iterable<E> actuals ) {
                final List<String> actualList = Lists.newArrayList( toStringList( actuals ) );
                Collections.sort( actualList );
                return actualList.equals( expectedList );
            }
        };
    }


    private static <E> Iterable<String> toStringList( Iterable<E> items ) {
        return StreamSupport.stream( items.spliterator(), false )
                .map( Object::toString )
                .collect( Util.toImmutableList() );
    }


    /**
     * Creates a matcher that matches when the examined object is within {@code epsilon} of the specified <code>operand</code>.
     */
    @Factory
    public static <T extends Number> Matcher<T> within( T value, double epsilon ) {
        return new IsWithin<T>( value, epsilon );
    }


    /**
     * Creates a matcher by applying a function to a value before calling another matcher.
     */
    public static <F, T> Matcher<F> compose( Matcher<T> matcher, Function<F, T> f ) {
        return new ComposingMatcher<>( matcher, f );
    }


    /**
     * Creates a Matcher that matches when the examined string is equal to the specified {@code value} when all Windows-style line endings ("\r\n") have been converted to Unix-style line endings ("\n").
     *
     * Thus, if {@code foo()} is a function that returns "hello{newline}world" in the current operating system's line endings, then
     *
     * <blockquote>
     * assertThat(foo(), isLinux("hello\nworld"));
     * </blockquote>
     *
     * will succeed on all platforms.
     *
     * @see Util#toLinux(String)
     */
    @Factory
    public static Matcher<String> isLinux( final String value ) {
        return compose( Is.is( value ), input -> input == null ? null : Util.toLinux( input ) );
    }


    /**
     * Creates a Matcher that matches a {@link RelNode} its string representation, after converting Windows-style line endings ("\r\n") to Unix-style line endings ("\n"), is equal to the given {@code value}.
     */
    @Factory
    public static Matcher<RelNode> hasTree( final String value ) {
        return compose( Is.is( value ), input -> {
            // Convert RelNode to a string with Linux line-endings
            return Util.toLinux( RelOptUtil.toString( input ) );
        } );
    }


    /**
     * Creates a matcher that matches when the examined string is equal to the specified <code>operand</code> when all Windows-style line endings ("\r\n") have been converted to Unix-style line endings ("\n").
     *
     * Thus, if {@code foo()} is a function that returns "hello{newline}world" in the current operating system's line endings, then
     *
     * <blockquote>
     * assertThat(foo(), isLinux("hello\nworld"));
     * </blockquote>
     *
     * will succeed on all platforms.
     *
     * @see Util#toLinux(String)
     */
    @Factory
    public static Matcher<String> containsStringLinux( String value ) {
        return compose( CoreMatchers.containsString( value ), Util::toLinux );
    }


    /**
     * Is the numeric value within a given difference another value?
     *
     * @param <T> Value type
     */
    public static class IsWithin<T extends Number> extends BaseMatcher<T> {

        private final T expectedValue;
        private final double epsilon;


        public IsWithin( T expectedValue, double epsilon ) {
            Preconditions.checkArgument( epsilon >= 0D );
            this.expectedValue = expectedValue;
            this.epsilon = epsilon;
        }


        public boolean matches( Object actualValue ) {
            return isWithin( actualValue, expectedValue, epsilon );
        }


        public void describeTo( Description description ) {
            description.appendValue( expectedValue + " +/-" + epsilon );
        }


        private static boolean isWithin( Object actual, Number expected, double epsilon ) {
            if ( actual == null ) {
                return expected == null;
            }
            if ( actual.equals( expected ) ) {
                return true;
            }
            final double a = ((Number) actual).doubleValue();
            final double min = expected.doubleValue() - epsilon;
            final double max = expected.doubleValue() + epsilon;
            return min <= a && a <= max;
        }
    }


    /**
     * Matcher that transforms the input value using a function before passing to another matcher.
     *
     * @param <F> From type: the type of value to be matched
     * @param <T> To type: type returned by function, and the resulting matcher
     */
    private static class ComposingMatcher<F, T> extends TypeSafeMatcher<F> {

        private final Matcher<T> matcher;
        private final Function<F, T> f;


        ComposingMatcher( Matcher<T> matcher, Function<F, T> f ) {
            this.matcher = matcher;
            this.f = f;
        }


        protected boolean matchesSafely( F item ) {
            return matcher.matches( f.apply( item ) );
        }


        public void describeTo( Description description ) {
            matcher.describeTo( description );
        }


        @Override
        protected void describeMismatchSafely( F item, Description mismatchDescription ) {
            mismatchDescription.appendText( "was " ).appendValue( f.apply( item ) );
        }
    }
}

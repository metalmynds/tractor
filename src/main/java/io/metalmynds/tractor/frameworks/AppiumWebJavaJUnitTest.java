//
// Copyright 2015-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License").
// You may not use this file except in compliance with the License.
// A copy of the License is located at
//
// http://aws.amazon.com/apache2.0
//
// or in the "license" file accompanying this file. This file is distributed
// on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied. See the License for the specific language governing
// permissions and limitations under the License.
//
package io.metalmynds.tractor.frameworks;

/**
 * POJO class for a Web Appium Java JUnit test.
 */
public class AppiumWebJavaJUnitTest {
    private final String tests;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String tests;

        /**
         * Test setter.
         *
         * @param tests Path to the tests to run.
         * @return The builder object.
         */
        public Builder withTests(String tests) {
            this.tests = tests;
            return this;
        }

        /**
         * Build method.
         *
         * @return The new POJO.
         */
        public AppiumWebJavaJUnitTest build() {
            return new AppiumWebJavaJUnitTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     *
     * @param builder The builder to use.
     */
    private AppiumWebJavaJUnitTest(Builder builder) {
        this.tests = builder.tests;
    }

    /**
     * Test getter.
     *
     * @return The path to the tests to run.
     */
    public String getTests() {
        return this.tests;
    }
}

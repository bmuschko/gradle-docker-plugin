/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker

import spock.lang.Specification
import spock.lang.TempDir

abstract class AbstractIntegrationTest extends Specification {
    @TempDir
    File temporaryFolder

    File projectDir

    def setup() {
        projectDir = temporaryFolder
        System.setOut(new FilteredPrintStream(System.out))
    }

    private static class FilteredPrintStream extends PrintStream {
        FilteredPrintStream(PrintStream source) {
            super(source)
        }

        @Override
        void write(byte[] buf, int off, int len) {
            String string = new String(buf)

            if(!string.contains(" DEBUG ")) {
                super.write(buf, off, len)
            }
        }
    }
}

/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging;

import org.gradle.api.Incubating;
import org.gradle.api.logging.LogLevel;

/**
 * Defines various logging settings.
 */
public interface LoggingConfiguration {
    LogLevel getLogLevel();

    void setLogLevel(LogLevel logLevel);

    /**
     * Returns true if logging output should be displayed in color when Gradle is running in a terminal which supports
     * color output. The default value is true.
     *
     * @return true if logging output should be displayed in color.
     */
    boolean isColorOutput();

    /**
     * Specifies whether logging output should be displayed in color.
     *
     * @param colorOutput true if logging output should be displayed in color.
     */
    void setColorOutput(boolean colorOutput);

    @Incubating
    ConsoleOutput getConsoleOutput();

    @Incubating
    void setConsoleOutput(ConsoleOutput colorOutput);

    ShowStacktrace getShowStacktrace();

    void setShowStacktrace(ShowStacktrace showStacktrace);
}

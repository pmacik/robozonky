/*
 * Copyright 2019 The RoboZonky Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robozonky.installer;

import java.net.URL;

import com.izforge.izpack.panels.userinput.processorclient.ProcessingClient;
import com.izforge.izpack.panels.userinput.validator.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class UrlValidator implements Validator {

    protected static final Logger LOGGER = LogManager.getLogger(UrlValidator.class);

    @Override
    public boolean validate(final ProcessingClient client) {
        final String text = client.getText();
        try {
            final URL url = new URL(text);
            return true;
        } catch (final Exception ex) {
            LOGGER.error("Wrong URL: {}.", text, ex);
            return false;
        }
    }
}

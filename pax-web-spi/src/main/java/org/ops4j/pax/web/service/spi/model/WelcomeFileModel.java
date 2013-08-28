/*
 * Copyright 2013 Achim Nierbeck.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.service.spi.model;

import org.ops4j.lang.NullArgumentException;

/**
 * @author achim
 *
 */
public class WelcomeFileModel extends Model {
	
	private String[] welcomeFiles;
	
	public WelcomeFileModel(final ContextModel contextModel, String[] welcomeFiles) {
		super(contextModel);
		NullArgumentException.validateNotNull(welcomeFiles, "WelcomeFiles");
		this.welcomeFiles = welcomeFiles;
	}

	/**
	 * @return the welcomeFiles
	 */
	public String[] getWelcomeFiles() {
		return welcomeFiles;
	}

}
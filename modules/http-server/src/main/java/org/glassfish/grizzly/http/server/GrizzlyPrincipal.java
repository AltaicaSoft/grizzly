/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2004 The Apache Software Foundation
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

package org.glassfish.grizzly.http.server;

import java.io.Serializable;
import java.security.Principal;

/**
 * Generic implementation of <strong>java.security.Principal</strong> that is used to represent principals authenticated
 * at the protocol handler level.
 *
 * @author Remy Maucherat
 * @version $Revision: 1.2 $ $Date: 2005/12/08 01:28:34 $
 */
public class GrizzlyPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The username of the user represented by this Principal.
     */
    protected String name = null;

    // ----------------------------------------------------------- Constructors
    public GrizzlyPrincipal(final String name) {

        this.name = name;

    }

    // --------------------------------------------------------- Public Methods

    @Override
    public String getName() {
        return name;
    }

    /**
     * Return a String representation of this object, which exposes only information that should be public.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("GrizzlyPrincipal[");
        sb.append(this.name);
        sb.append("]");
        return sb.toString();

    }
}

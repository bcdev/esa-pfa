/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.pfa.fe;

import com.bc.ceres.core.ServiceRegistry;
import com.bc.ceres.core.ServiceRegistryManager;
import org.esa.snap.BeamCoreActivator;
import org.esa.snap.util.Guardian;

import java.util.Set;

/**
 * An <code>PFAApplicationRegistry</code> provides access to multiple different
 * feature extraction applications as described by their PFAApplicationDescriptors.
 */
public class PFAApplicationRegistry {

    private final ServiceRegistry<PFAApplicationDescriptor> descriptors;

    private PFAApplicationRegistry() {
        descriptors = ServiceRegistryManager.getInstance().getServiceRegistry(PFAApplicationDescriptor.class);
        if (!BeamCoreActivator.isStarted()) {
            BeamCoreActivator.loadServices(descriptors);
        }
    }

    public static PFAApplicationRegistry getInstance() {
        return Holder.instance;
    }

    public void addDescriptor(PFAApplicationDescriptor modelDescriptor) {
        descriptors.addService(modelDescriptor);
    }

    public void removeDescriptor(PFAApplicationDescriptor modelDescriptor) {
        descriptors.removeService(modelDescriptor);
    }

    public PFAApplicationDescriptor getDescriptorByName(String name) {
        Guardian.assertNotNullOrEmpty("name", name);
        for (PFAApplicationDescriptor descriptor : descriptors.getServices()) {
            if (name.equalsIgnoreCase(descriptor.getName())) {
                return descriptor;
            }
        }
        return null;
    }

    public PFAApplicationDescriptor getDescriptorById(String id) {
        Guardian.assertNotNullOrEmpty("id", id);
        for (PFAApplicationDescriptor descriptor : descriptors.getServices()) {
            if (id.equalsIgnoreCase(descriptor.getId())) {
                return descriptor;
            }
        }
        return null;
    }

    public PFAApplicationDescriptor[] getAllDescriptors() {
        return descriptors.getServices().toArray(new PFAApplicationDescriptor[0]);
    }
    
    // Initialization on demand holder idiom
    private static class Holder {
        private static final PFAApplicationRegistry instance = new PFAApplicationRegistry();
    }
}
